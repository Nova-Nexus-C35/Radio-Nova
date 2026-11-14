# NOVA module registry staging assets

The initial registry foundation is deployed to the isolated Supabase project NOVA-STAGING.

- Database schema: private
- Tables: nova_module_publishers, nova_module_registry, nova_module_user_grants, nova_module_audit
- Storage bucket: nova-modules (private, 64 MiB maximum object size)
- Edge Function: nova-module-registry (JWT required)
- Implemented Edge Function action: catalog.list
- Database migration name applied in staging: nova_module_registry_foundation and nova_module_registry_server_policies_indexes

The SQL file is a reproducible staging bootstrap reference, not a generated Supabase CLI migration.
Never apply it to production as-is. Production requires a separately reviewed migration and rollback plan.

No secret keys, signing private keys, user records, or production data belong in this repository.
Publisher registration, admin review/publish/revoke, upload/download authorization, and end-to-end tests are intentionally not exposed yet. They require a verified Nova Guard administrator/role source and the shared Ed25519 canonicalization contract.
