# NOVA publisher key management API

The `nova-module-registry` Edge Function requires a valid Supabase user access token
(`Authorization: Bearer <access-token>`) and keeps the service-role/secret key on the server.

All actions use POST with a JSON body. The function checks the caller with Supabase Auth
before performing any operation. The caller ID passed to database RPCs is taken only from
that verified session; the client cannot choose the owner identity.

## Register a publisher key

```json
{
  "action": "publisher.register",
  "publisherId": "nova-community",
  "publisherKeyId": "community-ed25519-2026-01",
  "publicKeyBase64": "<DER SubjectPublicKeyInfo Ed25519 public key>"
}
```

A publisher ID can only be registered once. Registration and its success audit event are
written in one database transaction. A transaction-scoped advisory lock serializes first
registrations for the same publisher ID. The private signing key must remain with the
publisher and must never be uploaded to NOVA or committed to a repository.

## Rotate a publisher key

```json
{
  "action": "publisher.rotate",
  "publisherId": "nova-community",
  "oldPublisherKeyId": "community-ed25519-2026-01",
  "publisherKeyId": "community-ed25519-2026-02",
  "publicKeyBase64": "<new DER SubjectPublicKeyInfo Ed25519 public key>"
}
```

The caller must own the active old key. A single database transaction locks that key,
inserts the new public key, revokes the old key, and writes the success audit event.
Concurrent rotations serialize on the old key; only one can succeed. If insertion,
revocation, or audit writing fails, PostgreSQL rolls back the complete operation. The
function returns an error and callers must not assume rotation succeeded.

## Revoke a publisher key

```json
{
  "action": "publisher.revoke",
  "publisherId": "nova-community",
  "publisherKeyId": "community-ed25519-2026-02"
}
```

Only the owner of that active key can revoke it. Revocation and its success audit event
are one database transaction. Concurrent revocations serialize on the key row. Revoked
keys are excluded from the public catalog's active-publisher join.

## List publisher keys

```json
{ "action": "publisher.keys.list", "publisherId": "nova-community" }
```

The publisher ID filter is optional. Results are always scoped to the verified caller.

## List published modules

```json
{ "action": "catalog.list", "limit": 50 }
```

The catalog only returns modules whose registry status is `published` and whose
publisher key remains active.

## Deployment gate

The RPC migration is `supabase/staging/nova-module-key-atomic-operations.sql`.
It was applied to NOVA-STAGING on 2026-11-14, and the matching Edge Function version 4
is deployed with `verify_jwt=true`. An isolated SQL transaction test passed for register/rotate/revoke and verified rollback of a rotation when its audit insert was deliberately failed. The test user and all test rows were rolled back. Authenticated Edge Function account tests and simultaneous concurrency checks remain pending. Do not apply this staging SQL to production.

## Explicitly not enabled yet

These actions do not grant publication rights. Module upload/submission, administrator
review, publication/revocation, user grants, signed downloads, and end-to-end tests with
multiple real authenticated accounts remain disabled until a protected administrator
role source, the shared manifest-signature verifier, and the storage authorization flow
are fully integrated. The Edge Function must remain JWT-protected.
