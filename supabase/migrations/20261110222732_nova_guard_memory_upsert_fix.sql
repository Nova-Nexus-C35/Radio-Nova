-- Fix the encrypted-memory upsert conflict target and ciphertext size bound.
BEGIN;
ALTER TABLE public.nova_user_memory_items
  DROP CONSTRAINT IF EXISTS nova_user_memory_items_memory_value_check;
ALTER TABLE public.nova_user_memory_items
  ADD CONSTRAINT nova_user_memory_items_memory_value_check
  CHECK (char_length(btrim(memory_ciphertext_b64)) BETWEEN 1 AND 12000);

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
  ON CONFLICT ON CONSTRAINT nova_user_memory_owner_category_key
  DO UPDATE SET
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
COMMIT;