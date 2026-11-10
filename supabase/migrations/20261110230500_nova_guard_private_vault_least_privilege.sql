-- Remove inherited non-DML table privileges from owner-private Nova Guard vault tables.
BEGIN;
REVOKE ALL PRIVILEGES ON TABLE public.nova_guard_private_objects FROM authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.nova_guard_private_objects TO authenticated;

REVOKE ALL PRIVILEGES ON TABLE public.nova_private_personal_records FROM authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.nova_private_personal_records TO authenticated;

REVOKE ALL PRIVILEGES ON TABLE public.nova_private_personal_vaults FROM authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.nova_private_personal_vaults TO authenticated;
COMMIT;