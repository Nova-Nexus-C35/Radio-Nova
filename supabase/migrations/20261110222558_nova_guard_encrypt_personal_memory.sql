-- Nova Guard: encrypt personal memory at rest with per-user keys held by Supabase Vault.
-- Plaintext is exposed only through owner-authenticated RPCs used by Nova IA.
BEGIN;

CREATE SCHEMA IF NOT EXISTS nova_guard_private;
REVOKE ALL ON SCHEMA nova_guard_private FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION nova_guard_private.memory_key(p_owner_user_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_name text;
  v_key text;
BEGIN
  IF p_owner_user_id IS NULL THEN
    RAISE EXCEPTION 'Owner is required';
  END IF;

  v_name := 'nova_guard_memory_key_v1_' || p_owner_user_id::text;

  SELECT ds.decrypted_secret
    INTO v_key
    FROM vault.decrypted_secrets AS ds
   WHERE ds.name = v_name
   LIMIT 1;

  IF v_key IS NULL THEN
    PERFORM vault.create_secret(
      encode(extensions.gen_random_bytes(32), 'hex'),
      v_name,
      'Per-user encryption key for Nova personal memory; managed by Nova Guard.'
    );

    SELECT ds.decrypted_secret
      INTO v_key
      FROM vault.decrypted_secrets AS ds
     WHERE ds.name = v_name
     LIMIT 1;
  END IF;

  IF v_key IS NULL THEN
    RAISE EXCEPTION 'Memory encryption key is unavailable';
  END IF;

  RETURN v_key;
END;
$$;

REVOKE ALL ON FUNCTION nova_guard_private.memory_key(uuid)
  FROM PUBLIC, anon, authenticated, service_role;

ALTER TABLE public.nova_user_memory_items
  RENAME COLUMN memory_value TO memory_ciphertext_b64;

ALTER TABLE public.nova_user_memory_items
  ADD COLUMN IF NOT EXISTS memory_encryption_version smallint NOT NULL DEFAULT 0;

-- Encrypt every existing owner's memory record in place. The data never leaves Postgres.
DO $$
DECLARE
  r record;
  v_key text;
BEGIN
  FOR r IN
    SELECT DISTINCT owner_user_id
      FROM public.nova_user_memory_items
     WHERE memory_encryption_version = 0
  LOOP
    v_key := nova_guard_private.memory_key(r.owner_user_id);

    UPDATE public.nova_user_memory_items
       SET memory_ciphertext_b64 = encode(
             extensions.pgp_sym_encrypt(
               memory_ciphertext_b64,
               v_key,
               'cipher-algo=aes256,compress-algo=0'
             ),
             'base64'
           ),
           memory_encryption_version = 1,
           updated_at = now()
     WHERE owner_user_id = r.owner_user_id
       AND memory_encryption_version = 0;
  END LOOP;
END;
$$;

ALTER TABLE public.nova_user_memory_items
  ALTER COLUMN memory_encryption_version SET DEFAULT 1;

ALTER TABLE public.nova_user_memory_items
  ADD CONSTRAINT nova_user_memory_encryption_version_positive
  CHECK (memory_encryption_version > 0);

COMMENT ON COLUMN public.nova_user_memory_items.memory_ciphertext_b64 IS
  'PGP AES-256 encrypted memory payload, base64 encoded. Plaintext is returned only by owner-authenticated Nova Guard RPCs.';
COMMENT ON COLUMN public.nova_user_memory_items.memory_encryption_version IS
  'Encryption format version. Version 1 uses pgcrypto PGP symmetric AES-256 with a per-user key held in Supabase Vault.';

CREATE OR REPLACE FUNCTION public.nova_guard_memory_list(
  p_limit integer DEFAULT 60,
  p_include_sensitive boolean DEFAULT false
)
RETURNS TABLE (
  id uuid,
  category text,
  memory_key text,
  memory_value text,
  sensitivity text,
  source_conversation_id uuid,
  source_message_id bigint,
  created_at timestamptz,
  updated_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_key text;
  v_memory_enabled boolean := false;
  v_sensitive_enabled boolean := false;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  SELECT p.personal_memory_enabled, p.sensitive_memory_enabled
    INTO v_memory_enabled, v_sensitive_enabled
    FROM public.nova_user_preferences AS p
   WHERE p.owner_user_id = v_uid;

  IF NOT COALESCE(v_memory_enabled, false) THEN
    RETURN;
  END IF;

  v_key := nova_guard_private.memory_key(v_uid);

  RETURN QUERY
  SELECT
    m.id,
    m.category,
    m.memory_key,
    extensions.pgp_sym_decrypt(decode(m.memory_ciphertext_b64, 'base64'), v_key),
    m.sensitivity,
    m.source_conversation_id,
    m.source_message_id,
    m.created_at,
    m.updated_at
  FROM public.nova_user_memory_items AS m
  WHERE m.owner_user_id = v_uid
    AND m.memory_encryption_version = 1
    AND (
      m.sensitivity = 'standard'
      OR (p_include_sensitive AND COALESCE(v_sensitive_enabled, false))
    )
  ORDER BY m.updated_at DESC
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 60), 500));
END;
$$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_upsert(
  p_category text,
  p_memory_key text,
  p_memory_value text,
  p_sensitivity text DEFAULT 'standard',
  p_source_conversation_id uuid DEFAULT NULL,
  p_source_message_id bigint DEFAULT NULL,
  p_automatic boolean DEFAULT false
)
RETURNS TABLE (
  id uuid,
  category text,
  memory_key text,
  memory_value text,
  sensitivity text,
  updated_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_key text;
  v_memory_enabled boolean := false;
  v_automatic_enabled boolean := false;
  v_sensitive_enabled boolean := false;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF p_category NOT IN ('personal', 'preference', 'interest', 'project', 'communication', 'other')
     OR p_memory_key IS NULL
     OR p_memory_key !~ '^[a-z0-9_.-]{1,120}$'
     OR p_memory_value IS NULL
     OR char_length(btrim(p_memory_value)) NOT BETWEEN 1 AND 4000
     OR p_sensitivity NOT IN ('standard', 'sensitive') THEN
    RAISE EXCEPTION 'Invalid memory item';
  END IF;

  IF p_memory_value ~* '-----BEGIN ([A-Z ]+ )?PRIVATE KEY-----'
     OR p_memory_value ~* '(password|passwd|api[_ -]?key|access[_ -]?token)[[:space:]]*[:=][[:space:]]*[^[:space:]]{8,}'
     OR p_memory_value ~* '\mgh[pousr]_[A-Za-z0-9]{20,}\M'
     OR p_memory_value ~* '\mAIza[0-9A-Za-z_-]{30,}\M' THEN
    RAISE EXCEPTION 'Credentials and authentication secrets must use the password vault';
  END IF;

  SELECT p.personal_memory_enabled,
         p.automatic_personal_memory_enabled,
         p.sensitive_memory_enabled
    INTO v_memory_enabled, v_automatic_enabled, v_sensitive_enabled
    FROM public.nova_user_preferences AS p
   WHERE p.owner_user_id = v_uid;

  IF NOT COALESCE(v_memory_enabled, false)
     OR (p_automatic AND NOT COALESCE(v_automatic_enabled, false))
     OR (p_sensitivity = 'sensitive' AND NOT COALESCE(v_sensitive_enabled, false)) THEN
    RAISE EXCEPTION 'Memory consent does not permit this operation';
  END IF;

  IF p_source_conversation_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
      FROM public.nova_conversations AS c
     WHERE c.id = p_source_conversation_id
       AND c.owner_user_id = v_uid
  ) THEN
    RAISE EXCEPTION 'Source conversation does not belong to this account';
  END IF;

  IF p_source_message_id IS NOT NULL AND (
    p_source_conversation_id IS NULL
    OR NOT EXISTS (
      SELECT 1
        FROM public.nova_messages AS m
        JOIN public.nova_conversations AS c ON c.id = m.conversation_id
       WHERE m.id = p_source_message_id
         AND m.conversation_id = p_source_conversation_id
         AND c.owner_user_id = v_uid
    )
  ) THEN
    RAISE EXCEPTION 'Source message does not belong to this account';
  END IF;

  v_key := nova_guard_private.memory_key(v_uid);

  INSERT INTO public.nova_user_memory_items AS existing (
    owner_user_id,
    category,
    memory_key,
    memory_ciphertext_b64,
    memory_encryption_version,
    sensitivity,
    source_conversation_id,
    source_message_id
  )
  VALUES (
    v_uid,
    p_category,
    p_memory_key,
    encode(
      extensions.pgp_sym_encrypt(
        btrim(p_memory_value),
        v_key,
        'cipher-algo=aes256,compress-algo=0'
      ),
      'base64'
    ),
    1,
    p_sensitivity,
    p_source_conversation_id,
    p_source_message_id
  )
  ON CONFLICT (owner_user_id, category, memory_key)
  DO UPDATE SET
    memory_ciphertext_b64 = EXCLUDED.memory_ciphertext_b64,
    memory_encryption_version = 1,
    sensitivity = EXCLUDED.sensitivity,
    source_conversation_id = COALESCE(EXCLUDED.source_conversation_id, existing.source_conversation_id),
    source_message_id = COALESCE(EXCLUDED.source_message_id, existing.source_message_id),
    updated_at = now();

  RETURN QUERY
  SELECT
    m.id,
    m.category,
    m.memory_key,
    extensions.pgp_sym_decrypt(decode(m.memory_ciphertext_b64, 'base64'), v_key),
    m.sensitivity,
    m.updated_at
  FROM public.nova_user_memory_items AS m
  WHERE m.owner_user_id = v_uid
    AND m.category = p_category
    AND m.memory_key = p_memory_key;
END;
$$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_delete(p_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_deleted integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  DELETE FROM public.nova_user_memory_items
   WHERE id = p_id
     AND owner_user_id = v_uid;

  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted > 0;
END;
$$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_clear(p_scope text DEFAULT 'all')
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_deleted integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF p_scope NOT IN ('all', 'sensitive') THEN
    RAISE EXCEPTION 'Invalid memory deletion scope';
  END IF;

  IF p_scope = 'all' THEN
    DELETE FROM public.nova_user_memory_items WHERE owner_user_id = v_uid;
  ELSE
    DELETE FROM public.nova_user_memory_items
     WHERE owner_user_id = v_uid
       AND sensitivity = 'sensitive';
  END IF;

  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted;
END;
$$;

-- No client or service role may read the encrypted table directly.
REVOKE ALL PRIVILEGES ON TABLE public.nova_user_memory_items
  FROM PUBLIC, anon, authenticated, service_role;

REVOKE ALL ON FUNCTION public.nova_guard_memory_list(integer, boolean)
  FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_upsert(text, text, text, text, uuid, bigint, boolean)
  FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_delete(uuid)
  FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_clear(text)
  FROM PUBLIC, anon, service_role;

GRANT EXECUTE ON FUNCTION public.nova_guard_memory_list(integer, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_upsert(text, text, text, text, uuid, bigint, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_delete(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_clear(text) TO authenticated;

COMMIT;