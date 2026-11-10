-- Tighten biometric consent immutability and require a real uploaded ciphertext object before indexing.
BEGIN;

REVOKE UPDATE ON TABLE public.nova_guard_biometric_consents FROM authenticated;
DROP POLICY IF EXISTS "Nova Guard owner updates biometric consent" ON public.nova_guard_biometric_consents;

CREATE OR REPLACE FUNCTION nova_guard_private.revoke_biometric_consent(p_consent_id uuid)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_revoked integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;

  UPDATE public.nova_guard_biometric_consents
     SET revoked_at = COALESCE(revoked_at, now())
   WHERE id = p_consent_id
     AND owner_user_id = v_uid
     AND revoked_at IS NULL;

  IF NOT FOUND THEN RETURN 0; END IF;

  UPDATE public.nova_guard_private_objects
     SET status = 'revoked', updated_at = now()
   WHERE biometric_consent_id = p_consent_id
     AND owner_user_id = v_uid
     AND status = 'active';

  GET DIAGNOSTICS v_revoked = ROW_COUNT;
  RETURN v_revoked;
END;
$$;

REVOKE ALL ON FUNCTION nova_guard_private.revoke_biometric_consent(uuid)
  FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION nova_guard_private.revoke_biometric_consent(uuid)
  TO authenticated;

CREATE OR REPLACE FUNCTION public.nova_guard_revoke_biometric_consent(p_consent_id uuid)
RETURNS integer
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $$
  SELECT nova_guard_private.revoke_biometric_consent(p_consent_id);
$$;

REVOKE ALL ON FUNCTION public.nova_guard_revoke_biometric_consent(uuid)
  FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.nova_guard_revoke_biometric_consent(uuid)
  TO authenticated;

CREATE OR REPLACE FUNCTION nova_guard_private.enforce_biometric_consent()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_purpose text;
  v_revoked_at timestamptz;
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM storage.objects AS s
     WHERE s.bucket_id = 'nova-guard-private'
       AND s.name = NEW.storage_path
  ) THEN
    RAISE EXCEPTION 'Encrypted object must be uploaded before its metadata is registered';
  END IF;

  IF NEW.object_kind IN ('biometric_template','biometric_sample','biometric_recovery') THEN
    SELECT c.purpose, c.revoked_at
      INTO v_purpose, v_revoked_at
      FROM public.nova_guard_biometric_consents AS c
     WHERE c.id = NEW.biometric_consent_id
       AND c.owner_user_id = NEW.owner_user_id;

    IF NOT FOUND OR v_revoked_at IS NOT NULL THEN
      RAISE EXCEPTION 'An active owner biometric consent is required';
    END IF;

    IF NEW.object_kind IN ('biometric_template','biometric_sample')
       AND (
         v_purpose NOT IN ('face_template_storage','face_verification')
         OR NEW.consent_scope NOT IN ('biometric_enrollment','biometric_authentication')
       ) THEN
      RAISE EXCEPTION 'Consent purpose or scope does not authorize face data storage';
    END IF;

    IF NEW.object_kind = 'biometric_recovery'
       AND (
         v_purpose <> 'device_biometric_authentication'
         OR NEW.consent_scope <> 'biometric_authentication'
       ) THEN
      RAISE EXCEPTION 'Consent purpose does not authorize biometric recovery data';
    END IF;
  ELSIF NEW.biometric_consent_id IS NOT NULL THEN
    RAISE EXCEPTION 'Non-biometric objects cannot reference biometric consent';
  END IF;

  RETURN NEW;
END;
$$;

COMMIT;