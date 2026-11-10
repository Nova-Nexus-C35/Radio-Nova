-- Nova Guard: explicit owner consent and revocation for biometric ciphertext.
BEGIN;

CREATE TABLE IF NOT EXISTS public.nova_guard_biometric_consents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  purpose text NOT NULL CHECK (purpose IN (
    'face_template_storage',
    'face_verification',
    'device_biometric_authentication'
  )),
  consent_version text NOT NULL CHECK (char_length(consent_version) BETWEEN 1 AND 80),
  consent_text_sha256 text NULL CHECK (
    consent_text_sha256 IS NULL OR consent_text_sha256 ~ '^[0-9a-f]{64}$'
  ),
  device_key_reference text NULL CHECK (
    device_key_reference IS NULL OR char_length(device_key_reference) BETWEEN 1 AND 120
  ),
  granted_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT nova_guard_biometric_consent_revocation_order
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

ALTER TABLE public.nova_guard_biometric_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_guard_biometric_consents FORCE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.nova_guard_biometric_consents FROM PUBLIC, anon;
REVOKE ALL PRIVILEGES ON TABLE public.nova_guard_biometric_consents FROM authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.nova_guard_biometric_consents TO authenticated;

DROP POLICY IF EXISTS "Nova Guard owner reads biometric consents" ON public.nova_guard_biometric_consents;
DROP POLICY IF EXISTS "Nova Guard owner grants biometric consent" ON public.nova_guard_biometric_consents;
DROP POLICY IF EXISTS "Nova Guard owner updates biometric consent" ON public.nova_guard_biometric_consents;
CREATE POLICY "Nova Guard owner reads biometric consents"
  ON public.nova_guard_biometric_consents FOR SELECT TO authenticated
  USING (owner_user_id = (SELECT auth.uid()));
CREATE POLICY "Nova Guard owner grants biometric consent"
  ON public.nova_guard_biometric_consents FOR INSERT TO authenticated
  WITH CHECK (owner_user_id = (SELECT auth.uid()) AND revoked_at IS NULL);
CREATE POLICY "Nova Guard owner updates biometric consent"
  ON public.nova_guard_biometric_consents FOR UPDATE TO authenticated
  USING (owner_user_id = (SELECT auth.uid()))
  WITH CHECK (owner_user_id = (SELECT auth.uid()));

CREATE UNIQUE INDEX IF NOT EXISTS nova_guard_biometric_active_consent_idx
  ON public.nova_guard_biometric_consents(owner_user_id, purpose)
  WHERE revoked_at IS NULL;

ALTER TABLE public.nova_guard_private_objects
  ADD COLUMN IF NOT EXISTS biometric_consent_id uuid NULL
  REFERENCES public.nova_guard_biometric_consents(id) ON DELETE RESTRICT;

ALTER TABLE public.nova_guard_private_objects
  DROP CONSTRAINT IF EXISTS nova_guard_private_object_biometric_consent_check;
ALTER TABLE public.nova_guard_private_objects
  ADD CONSTRAINT nova_guard_private_object_biometric_consent_check
  CHECK (
    (object_kind IN ('biometric_template','biometric_sample','biometric_recovery')
      AND biometric_consent_id IS NOT NULL)
    OR
    (object_kind NOT IN ('biometric_template','biometric_sample','biometric_recovery')
      AND biometric_consent_id IS NULL)
  );

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
       AND v_purpose NOT IN ('face_template_storage','face_verification') THEN
      RAISE EXCEPTION 'Consent purpose does not authorize face data storage';
    END IF;

    IF NEW.object_kind = 'biometric_recovery'
       AND v_purpose <> 'device_biometric_authentication' THEN
      RAISE EXCEPTION 'Consent purpose does not authorize biometric recovery data';
    END IF;
  ELSIF NEW.biometric_consent_id IS NOT NULL THEN
    RAISE EXCEPTION 'Non-biometric objects cannot reference biometric consent';
  END IF;

  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION nova_guard_private.enforce_biometric_consent()
  FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS nova_guard_enforce_biometric_consent
  ON public.nova_guard_private_objects;
CREATE TRIGGER nova_guard_enforce_biometric_consent
  BEFORE INSERT OR UPDATE OF object_kind, owner_user_id, biometric_consent_id
  ON public.nova_guard_private_objects
  FOR EACH ROW
  EXECUTE FUNCTION nova_guard_private.enforce_biometric_consent();

CREATE OR REPLACE FUNCTION public.nova_guard_revoke_biometric_consent(p_consent_id uuid)
RETURNS integer
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_revoked integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  UPDATE public.nova_guard_biometric_consents
     SET revoked_at = COALESCE(revoked_at, now())
   WHERE id = p_consent_id
     AND owner_user_id = v_uid
     AND revoked_at IS NULL;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  UPDATE public.nova_guard_private_objects
     SET status = 'revoked',
         updated_at = now()
   WHERE biometric_consent_id = p_consent_id
     AND owner_user_id = v_uid
     AND status = 'active';

  GET DIAGNOSTICS v_revoked = ROW_COUNT;
  RETURN v_revoked;
END;
$$;

REVOKE ALL ON FUNCTION public.nova_guard_revoke_biometric_consent(uuid)
  FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.nova_guard_revoke_biometric_consent(uuid)
  TO authenticated;

-- Reads require active metadata and, for biometric objects, an active consent.
DROP POLICY IF EXISTS "Nova Guard owner reads encrypted private objects" ON storage.objects;
CREATE POLICY "Nova Guard owner reads encrypted private objects"
  ON storage.objects FOR SELECT TO authenticated
  USING (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
    AND EXISTS (
      SELECT 1
        FROM public.nova_guard_private_objects AS p
       WHERE p.owner_user_id = (SELECT auth.uid())
         AND p.storage_path = storage.objects.name
         AND p.status = 'active'
         AND (
           p.biometric_consent_id IS NULL
           OR EXISTS (
             SELECT 1
               FROM public.nova_guard_biometric_consents AS c
              WHERE c.id = p.biometric_consent_id
                AND c.owner_user_id = (SELECT auth.uid())
                AND c.revoked_at IS NULL
           )
         )
    )
  );

COMMENT ON TABLE public.nova_guard_biometric_consents IS
  'Owner-only, versioned consent records required before Nova Guard stores owner biometric ciphertext. Revocation blocks subsequent reads.';
COMMENT ON COLUMN public.nova_guard_private_objects.biometric_consent_id IS
  'Required for biometric object kinds; the referenced owner consent must remain active.';

COMMIT;