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

These RPCs perform each key mutation and its success audit insert in one PostgreSQL transaction. Rotation/revocation lock the active key row; first registration serializes by publisher ID. The SQL migration `nova_module_key_atomic_operations` has been applied to NOVA-STAGING and the matching Edge Function version 4 is active with JWT verification. Security advisor returned no security lints. The RPC grants were checked: `service_role` can execute, while `anon` and `authenticated` cannot. A missing-key negative test returned the expected not-found state without inserting keys or audit rows. An isolated SQL transaction test exercised register, rotate and revoke, then injected an audit-trigger failure during a second rotation. The failure rolled back the new key and preserved the previous active key. The test transaction was rolled back and a follow-up query confirmed zero test users, keys, audit rows or temporary trigger functions remain. Authenticated Edge Function sessions and truly concurrent requests remain untested.

The SQL files in this folder are staging-specific references, not production migrations. Never apply them to production as-is. Production requires a separately reviewed migration and rollback plan.

No secret keys, signing private keys, user records, or production data belong in this repository.
Publisher key management does not grant module publication privileges. Admin review/publish/revoke, artifact upload/download authorization, and authenticated end-to-end tests remain gated until a verified administrator/role source and the shared Ed25519 canonicalization contract are integrated.

## Authenticated E2E harness

A reusable, non-production test harness is available at
`scripts/staging/nova-module-registry-e2e.ts`. It exercises the deployed Edge
Function using two distinct NOVA-STAGING user sessions and generated Ed25519
public keys. It verifies invalid-session rejection, owner-only key visibility,
cross-user rotation/revocation denial, and the register → rotate → revoke
lifecycle. It never uses a service-role credential and does not print JWTs or
private key material.

Run only against NOVA-STAGING after creating two separate staging Auth users
and obtaining their short-lived session JWTs:

```sh
NOVA_GUARD_FUNCTION_URL="https://<staging-project>.supabase.co/functions/v1/nova-module-registry" \
SUPABASE_PUBLISHABLE_KEY="<staging-publishable-key>" \
NOVA_TEST_USER_A_JWT="<user-a-session-jwt>" \
NOVA_TEST_USER_B_JWT="<user-b-session-jwt>" \
NOVA_E2E_ALLOW_MUTATIONS=true \
deno run --allow-env --allow-net scripts/staging/nova-module-registry-e2e.ts
```

The explicit mutation flag is required because the test creates and revokes
temporary publisher keys. Use disposable staging accounts only. Do not put
JWTs or keys in shell history, CI logs, issue comments, or repository files.
The harness has been committed but has **not** been executed: staging currently
has zero Auth users, so authenticated E2E evidence remains pending.
