# NOVA module registry staging assets

The initial registry foundation is deployed to the isolated Supabase project NOVA-STAGING.

- Database schema: private
- Tables: nova_module_publishers, nova_module_registry, nova_module_user_grants, nova_module_audit
- Storage bucket: nova-modules (private, 64 MiB maximum object size)
- Edge Function: nova-module-registry (JWT required)
- Currently deployed actions: `catalog.list`, `publisher.register`, `publisher.keys.list`, `publisher.rotate`, `publisher.revoke` (publisher owners may list and manage only their own Ed25519 keys).
- Existing staging database setup: `nova_module_registry_foundation` and `nova_module_registry_server_policies_indexes`.

## Atomic key operations — deployed to staging

The PR branch adds `supabase/staging/nova-module-key-atomic-operations.sql` and updates the Edge Function to use:
- `nova_register_publisher_key`
- `nova_rotate_publisher_key`
- `nova_revoke_publisher_key`

These RPCs perform each key mutation and its success audit insert in one PostgreSQL transaction. Rotation/revocation lock the active key row; first registration serializes by publisher ID. The SQL migration `nova_module_key_atomic_operations` has been applied to NOVA-STAGING and the matching Edge Function version 4 is active with JWT verification. Security advisor returned no security lints. The RPC grants were checked: `service_role` can execute, while `anon` and `authenticated` cannot. A missing-key negative test returned the expected not-found state without inserting keys or audit rows. Positive authenticated account and concurrency tests remain pending.

The SQL files in this folder are staging-specific references, not production migrations. Never apply them to production as-is. Production requires a separately reviewed migration and rollback plan.

No secret keys, signing private keys, user records, or production data belong in this repository.
Publisher key management does not grant module publication privileges. Admin review/publish/revoke, artifact upload/download authorization, and authenticated end-to-end tests remain gated until a verified administrator/role source and the shared Ed25519 canonicalization contract are integrated.
