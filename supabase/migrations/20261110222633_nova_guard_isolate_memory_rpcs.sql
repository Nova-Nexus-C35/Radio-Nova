-- Keep SECURITY DEFINER routines outside the exposed API schema.
-- Public RPCs are SECURITY INVOKER wrappers; the private routines enforce auth.uid() and consent.
BEGIN;

CREATE OR REPLACE FUNCTION nova_guard_private.memory_list(
  p_limit integer DEFAULT 60,
  p_include_sensitive boolean DEFAULT false
)
RETURNS TABLE (
  id uuid, category text, memory_key text, memory_value text, sensitivity text,
  source_conversation_id uuid, source_message_id bigint, created_at timestamptz, updated_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_key text;
  v_memory_enabled boolean := false;
  v_sensitive_enabled boolean := false;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  SELECT p.personal_memory_enabled, p.sensitive_memory_enabled
    INTO v_memory_enabled, v_sensitive_enabled
    FROM public.nova_user_preferences AS p WHERE p.owner_user_id = v_uid;
  IF NOT COALESCE(v_memory_enabled, false) THEN RETURN; END IF;
  v_key := nova_guard_private.memory_key(v_uid);
  RETURN QUERY
  SELECT m.id, m.category, m.memory_key,
    extensions.pgp_sym_decrypt(decode(m.memory_ciphertext_b64, 'base64'), v_key),
    m.sensitivity, m.source_conversation_id, m.source_message_id, m.created_at, m.updated_at
  FROM public.nova_user_memory_items AS m
  WHERE m.owner_user_id = v_uid AND m.memory_encryption_version = 1
    AND (m.sensitivity = 'standard' OR (p_include_sensitive AND COALESCE(v_sensitive_enabled, false)))
  ORDER BY m.updated_at DESC LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 60), 500));
END;
$$;

CREATE OR REPLACE FUNCTION nova_guard_private.memory_upsert(
  p_category text, p_memory_key text, p_memory_value text, p_sensitivity text DEFAULT 'standard',
  p_source_conversation_id uuid DEFAULT NULL, p_source_message_id bigint DEFAULT NULL,
  p_automatic boolean DEFAULT false
)
RETURNS TABLE (id uuid, category text, memory_key text, memory_value text, sensitivity text, updated_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid(); v_key text;
  v_memory_enabled boolean := false; v_automatic_enabled boolean := false; v_sensitive_enabled boolean := false;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  IF p_category NOT IN ('personal','preference','interest','project','communication','other')
     OR p_memory_key IS NULL OR p_memory_key !~ '^[a-z0-9_.-]{1,120}$'
     OR p_memory_value IS NULL OR char_length(btrim(p_memory_value)) NOT BETWEEN 1 AND 4000
     OR p_sensitivity NOT IN ('standard','sensitive') THEN RAISE EXCEPTION 'Invalid memory item'; END IF;
  IF p_memory_value ~* '-----BEGIN ([A-Z ]+ )?PRIVATE KEY-----'
     OR p_memory_value ~* '(password|passwd|api[_ -]?key|access[_ -]?token)[[:space:]]*[:=][[:space:]]*[^[:space:]]{8,}'
     OR p_memory_value ~* '\mgh[pousr]_[A-Za-z0-9]{20,}\M'
     OR p_memory_value ~* '\mAIza[0-9A-Za-z_-]{30,}\M' THEN
    RAISE EXCEPTION 'Credentials and authentication secrets must use the password vault';
  END IF;
  SELECT p.personal_memory_enabled,p.automatic_personal_memory_enabled,p.sensitive_memory_enabled
    INTO v_memory_enabled,v_automatic_enabled,v_sensitive_enabled
    FROM public.nova_user_preferences AS p WHERE p.owner_user_id=v_uid;
  IF NOT COALESCE(v_memory_enabled,false)
     OR (p_automatic AND NOT COALESCE(v_automatic_enabled,false))
     OR (p_sensitivity='sensitive' AND NOT COALESCE(v_sensitive_enabled,false)) THEN
    RAISE EXCEPTION 'Memory consent does not permit this operation';
  END IF;
  IF p_source_conversation_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM public.nova_conversations c WHERE c.id=p_source_conversation_id AND c.owner_user_id=v_uid
  ) THEN RAISE EXCEPTION 'Source conversation does not belong to this account'; END IF;
  IF p_source_message_id IS NOT NULL AND (
    p_source_conversation_id IS NULL OR NOT EXISTS (
      SELECT 1 FROM public.nova_messages m JOIN public.nova_conversations c ON c.id=m.conversation_id
      WHERE m.id=p_source_message_id AND m.conversation_id=p_source_conversation_id AND c.owner_user_id=v_uid
    )
  ) THEN RAISE EXCEPTION 'Source message does not belong to this account'; END IF;
  v_key := nova_guard_private.memory_key(v_uid);
  INSERT INTO public.nova_user_memory_items AS existing (
    owner_user_id,category,memory_key,memory_ciphertext_b64,memory_encryption_version,
    sensitivity,source_conversation_id,source_message_id
  ) VALUES (
    v_uid,p_category,p_memory_key,
    encode(extensions.pgp_sym_encrypt(btrim(p_memory_value),v_key,'cipher-algo=aes256,compress-algo=0'),'base64'),
    1,p_sensitivity,p_source_conversation_id,p_source_message_id
  )
  ON CONFLICT (owner_user_id,category,memory_key) DO UPDATE SET
    memory_ciphertext_b64=EXCLUDED.memory_ciphertext_b64,
    memory_encryption_version=1,
    sensitivity=EXCLUDED.sensitivity,
    source_conversation_id=COALESCE(EXCLUDED.source_conversation_id,existing.source_conversation_id),
    source_message_id=COALESCE(EXCLUDED.source_message_id,existing.source_message_id),
    updated_at=now();
  RETURN QUERY
  SELECT m.id,m.category,m.memory_key,
    extensions.pgp_sym_decrypt(decode(m.memory_ciphertext_b64,'base64'),v_key),
    m.sensitivity,m.updated_at
  FROM public.nova_user_memory_items m
  WHERE m.owner_user_id=v_uid AND m.category=p_category AND m.memory_key=p_memory_key;
END;
$$;

CREATE OR REPLACE FUNCTION nova_guard_private.memory_delete(p_id uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE v_uid uuid := auth.uid(); v_deleted integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  DELETE FROM public.nova_user_memory_items WHERE id=p_id AND owner_user_id=v_uid;
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted > 0;
END;
$$;

CREATE OR REPLACE FUNCTION nova_guard_private.memory_clear(p_scope text DEFAULT 'all')
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE v_uid uuid := auth.uid(); v_deleted integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  IF p_scope NOT IN ('all','sensitive') THEN RAISE EXCEPTION 'Invalid memory deletion scope'; END IF;
  IF p_scope='all' THEN
    DELETE FROM public.nova_user_memory_items WHERE owner_user_id=v_uid;
  ELSE
    DELETE FROM public.nova_user_memory_items WHERE owner_user_id=v_uid AND sensitivity='sensitive';
  END IF;
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION nova_guard_private.memory_list(integer,boolean) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION nova_guard_private.memory_upsert(text,text,text,text,uuid,bigint,boolean) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION nova_guard_private.memory_delete(uuid) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION nova_guard_private.memory_clear(text) FROM PUBLIC, anon, service_role;

GRANT USAGE ON SCHEMA nova_guard_private TO authenticated;
GRANT EXECUTE ON FUNCTION nova_guard_private.memory_list(integer,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION nova_guard_private.memory_upsert(text,text,text,text,uuid,bigint,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION nova_guard_private.memory_delete(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION nova_guard_private.memory_clear(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_list(p_limit integer DEFAULT 60, p_include_sensitive boolean DEFAULT false)
RETURNS TABLE (id uuid, category text, memory_key text, memory_value text, sensitivity text, source_conversation_id uuid, source_message_id bigint, created_at timestamptz, updated_at timestamptz)
LANGUAGE sql SECURITY INVOKER SET search_path = ''
AS $$ SELECT * FROM nova_guard_private.memory_list(p_limit, p_include_sensitive); $$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_upsert(
  p_category text, p_memory_key text, p_memory_value text, p_sensitivity text DEFAULT 'standard',
  p_source_conversation_id uuid DEFAULT NULL, p_source_message_id bigint DEFAULT NULL, p_automatic boolean DEFAULT false
)
RETURNS TABLE (id uuid, category text, memory_key text, memory_value text, sensitivity text, updated_at timestamptz)
LANGUAGE sql SECURITY INVOKER SET search_path = ''
AS $$ SELECT * FROM nova_guard_private.memory_upsert(p_category,p_memory_key,p_memory_value,p_sensitivity,p_source_conversation_id,p_source_message_id,p_automatic); $$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_delete(p_id uuid)
RETURNS boolean LANGUAGE sql SECURITY INVOKER SET search_path = ''
AS $$ SELECT nova_guard_private.memory_delete(p_id); $$;

CREATE OR REPLACE FUNCTION public.nova_guard_memory_clear(p_scope text DEFAULT 'all')
RETURNS integer LANGUAGE sql SECURITY INVOKER SET search_path = ''
AS $$ SELECT nova_guard_private.memory_clear(p_scope); $$;

REVOKE ALL ON FUNCTION public.nova_guard_memory_list(integer,boolean) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_upsert(text,text,text,text,uuid,bigint,boolean) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_delete(uuid) FROM PUBLIC, anon, service_role;
REVOKE ALL ON FUNCTION public.nova_guard_memory_clear(text) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_list(integer,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_upsert(text,text,text,text,uuid,bigint,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_delete(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.nova_guard_memory_clear(text) TO authenticated;

COMMIT;