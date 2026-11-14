# NOVA module registry staging assets

The initial registry foundation is deployed to the isolated Supabase project NOVA-STAGING.

- Database schema: private
- Tables: nova_module_publishers, nova_module_registry, nova_module_user_grants, nova_module_audit
- Storage bucket: nova-modules (private, 64 MiB maximum object size)
- Edge Function: nova-module-registry (JWT required)
- Implemented Edge Function actions: `catalog.list`, `publisher.register`, `publisher.keys.list`, `publisher.rotate`, `publisher.revoke` (publisher owners may list and manage only their own Ed25519 keys).
- Database migration name applied in staging: nova_module_registry_foundation and nova_module_registry_server_policies_indexes

The SQL file is a reproducible staging bootstrap reference, not a generated Supabase CLI migration.
Never apply it to production as-is. Production requires a separately reviewed migration and rollback plan.

No secret keys, signing private keys, user records, or production data belong in this repository.
Owner-scoped publisher key registration, rotation, and revocation are implemented. Admin review/publish/revoke, artifact upload/download authorization, and authenticated end-to-end tests remain gated until a verified administrator/role source and the shared Ed25519 canonicalization contract are integrated. Publisher key management does not grant publication privileges.
