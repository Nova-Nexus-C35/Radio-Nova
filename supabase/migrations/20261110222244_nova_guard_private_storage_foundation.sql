-- Nova Guard: owner-only private object storage and biometric access hardening.
-- Sensitive payloads must be encrypted by the trusted client before upload.
-- GitHub stores this policy/source code only; private payload bytes remain in private Storage.

BEGIN;

-- Central private bucket for client-encrypted owner data.
INSERT INTO storage.buckets (
  id, name, public, file_size_limit, allowed_mime_types
)
VALUES (
  'nova-guard-private',
  'nova-guard-private',
  false,
  52428800,
  ARRAY['application/octet-stream']::text[]
)
ON CONFLICT (id) DO UPDATE
SET
  name = EXCLUDED.name,
  public = false,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Metadata only. Ciphertext is stored in the private bucket; no plaintext or key material belongs here.
CREATE TABLE IF NOT EXISTS public.nova_guard_private_objects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  object_kind text NOT NULL CHECK (
    object_kind IN (
      'credential_export',
      'personal_record',
      'memory_archive',
      'biometric_template',
      'biometric_sample',
      'biometric_recovery',
      'nova_ai_private',
      'owner_document'
    )
  ),
  storage_path text NOT NULL,
  content_type text NOT NULL DEFAULT 'application/octet-stream'
    CHECK (content_type = 'application/octet-stream'),
  ciphertext_bytes bigint NOT NULL CHECK (ciphertext_bytes BETWEEN 1 AND 52428800),
  ciphertext_sha256 text NOT NULL CHECK (ciphertext_sha256 ~ '^[0-9a-f]{64}$'),
  encryption_algorithm text NOT NULL DEFAULT 'AES-256-GCM'
    CHECK (encryption_algorithm = 'AES-256-GCM'),
  encryption_version smallint NOT NULL DEFAULT 1 CHECK (encryption_version > 0),
  key_reference text NOT NULL CHECK (char_length(key_reference) BETWEEN 1 AND 100),
  consent_scope text NOT NULL DEFAULT 'owner_private_storage'
    CHECK (consent_scope IN (
      'owner_private_storage',
      'biometric_enrollment',
      'biometric_authentication',
      'personal_memory'
    )),
  status text NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'revoked', 'pending_delete')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT nova_guard_private_object_owner_path
    CHECK (storage_path LIKE owner_user_id::text || '/%'),
  CONSTRAINT nova_guard_private_object_unique_path
    UNIQUE (owner_user_id, storage_path)
);

ALTER TABLE public.nova_guard_private_objects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_guard_private_objects FORCE ROW LEVEL SECURITY;

REVOKE ALL PRIVILEGES ON TABLE public.nova_guard_private_objects FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE public.nova_guard_private_objects TO authenticated;

DROP POLICY IF EXISTS "Nova Guard owner reads encrypted object metadata"
  ON public.nova_guard_private_objects;
DROP POLICY IF EXISTS "Nova Guard owner inserts encrypted object metadata"
  ON public.nova_guard_private_objects;
DROP POLICY IF EXISTS "Nova Guard owner updates encrypted object metadata"
  ON public.nova_guard_private_objects;
DROP POLICY IF EXISTS "Nova Guard owner deletes encrypted object metadata"
  ON public.nova_guard_private_objects;

CREATE POLICY "Nova Guard owner reads encrypted object metadata"
  ON public.nova_guard_private_objects
  FOR SELECT TO authenticated
  USING (owner_user_id = (SELECT auth.uid()));

CREATE POLICY "Nova Guard owner inserts encrypted object metadata"
  ON public.nova_guard_private_objects
  FOR INSERT TO authenticated
  WITH CHECK (owner_user_id = (SELECT auth.uid()));

CREATE POLICY "Nova Guard owner updates encrypted object metadata"
  ON public.nova_guard_private_objects
  FOR UPDATE TO authenticated
  USING (owner_user_id = (SELECT auth.uid()))
  WITH CHECK (owner_user_id = (SELECT auth.uid()));

CREATE POLICY "Nova Guard owner deletes encrypted object metadata"
  ON public.nova_guard_private_objects
  FOR DELETE TO authenticated
  USING (owner_user_id = (SELECT auth.uid()));

CREATE INDEX IF NOT EXISTS nova_guard_private_objects_owner_kind_idx
  ON public.nova_guard_private_objects(owner_user_id, object_kind, created_at DESC);

-- Restrict the new encrypted bucket to the owner and to owner-prefixed paths.
DROP POLICY IF EXISTS "Nova Guard owner uploads encrypted private objects" ON storage.objects;
DROP POLICY IF EXISTS "Nova Guard owner reads encrypted private objects" ON storage.objects;
DROP POLICY IF EXISTS "Nova Guard owner updates encrypted private objects" ON storage.objects;
DROP POLICY IF EXISTS "Nova Guard owner deletes encrypted private objects" ON storage.objects;

CREATE POLICY "Nova Guard owner uploads encrypted private objects"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  );

CREATE POLICY "Nova Guard owner reads encrypted private objects"
  ON storage.objects FOR SELECT TO authenticated
  USING (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  );

CREATE POLICY "Nova Guard owner updates encrypted private objects"
  ON storage.objects FOR UPDATE TO authenticated
  USING (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  )
  WITH CHECK (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  );

CREATE POLICY "Nova Guard owner deletes encrypted private objects"
  ON storage.objects FOR DELETE TO authenticated
  USING (
    bucket_id = 'nova-guard-private'
    AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  );

-- Force RLS on the existing encrypted private-personal vault.
ALTER TABLE public.nova_private_personal_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_private_personal_records FORCE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.nova_private_personal_records FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE public.nova_private_personal_records TO authenticated;

ALTER TABLE public.nova_private_personal_vaults ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_private_personal_vaults FORCE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.nova_private_personal_vaults FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE public.nova_private_personal_vaults TO authenticated;

-- Biometric profile and sample tables are owner-only. Service-role backend operations remain
-- possible, but authenticated admins do not receive direct access to the owner's biometrics.
ALTER TABLE public.nova_face_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_face_profiles FORCE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.nova_face_profiles FROM PUBLIC, anon;

DROP POLICY IF EXISTS "Owner and Nova admins manage face profiles"
  ON public.nova_face_profiles;
DROP POLICY IF EXISTS "Nova Guard owner manages own face profile"
  ON public.nova_face_profiles;
CREATE POLICY "Nova Guard owner manages own face profile"
  ON public.nova_face_profiles
  FOR ALL TO authenticated
  USING (user_id = (SELECT auth.uid()))
  WITH CHECK (user_id = (SELECT auth.uid()));

ALTER TABLE public.nova_face_samples ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nova_face_samples FORCE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.nova_face_samples FROM PUBLIC, anon;

DROP POLICY IF EXISTS "Owner and Nova admins manage face samples"
  ON public.nova_face_samples;
DROP POLICY IF EXISTS "Nova Guard owner manages own face samples"
  ON public.nova_face_samples;
CREATE POLICY "Nova Guard owner manages own face samples"
  ON public.nova_face_samples
  FOR ALL TO authenticated
  USING (owner_user_id = (SELECT auth.uid()))
  WITH CHECK (owner_user_id = (SELECT auth.uid()));

-- Existing legacy face-image bucket is also owner-only. New biometric payloads must use
-- nova-guard-private after client-side encryption, not raw image uploads.
DROP POLICY IF EXISTS "Owner and Nova admins access face images" ON storage.objects;
DROP POLICY IF EXISTS "Nova Guard owner accesses legacy face images" ON storage.objects;
CREATE POLICY "Nova Guard owner accesses legacy face images"
  ON storage.objects FOR ALL TO authenticated
  USING (
    bucket_id = 'nova-face-images'
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  )
  WITH CHECK (
    bucket_id = 'nova-face-images'
    AND (SELECT auth.uid()) = 'e2b76b60-09b4-4e01-a93f-00c9bd7e257e'::uuid
  );

COMMENT ON TABLE public.nova_guard_private_objects IS
  'Nova Guard metadata index for owner-only client-encrypted private objects. Never store plaintext payloads, passwords, biometric templates, or encryption keys in this table.';
COMMENT ON COLUMN public.nova_guard_private_objects.storage_path IS
  'Owner-prefixed path in the private nova-guard-private bucket; object bytes must be ciphertext before upload.';

COMMIT;
