-- Pin biometric consent acceptance to the reviewed Nova Guard document version and digest.
BEGIN;
ALTER TABLE public.nova_guard_biometric_consents
  ALTER COLUMN consent_text_sha256 SET NOT NULL;
ALTER TABLE public.nova_guard_biometric_consents
  DROP CONSTRAINT IF EXISTS nova_guard_biometric_consent_document_version_check;
ALTER TABLE public.nova_guard_biometric_consents
  ADD CONSTRAINT nova_guard_biometric_consent_document_version_check
  CHECK (
    consent_version = 'nova-biometric-consent-v1'
    AND consent_text_sha256 = '770b63acc8cb0a4c9042e3fddc229904ed1178b3c2f5e03b21253e4838cd0b05'
  );
COMMENT ON COLUMN public.nova_guard_biometric_consents.consent_text_sha256 IS
  'SHA-256 digest of docs/BIOMETRIC-CONSENT.md; required to prove which consent text was accepted.';
COMMIT;