-- Cover the optional biometric-consent foreign key for efficient lookups and deletes.
BEGIN;

CREATE INDEX IF NOT EXISTS nova_guard_private_objects_biometric_consent_id_idx
  ON public.nova_guard_private_objects (biometric_consent_id)
  WHERE biometric_consent_id IS NOT NULL;

COMMIT;
