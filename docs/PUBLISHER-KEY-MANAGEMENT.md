# NOVA publisher key management API

The `nova-module-registry` Edge Function requires a valid Supabase user access token
(`Authorization: Bearer <access-token>`) and keeps the service-role/secret key on the server.

All actions use POST with a JSON body. The function checks the caller with Supabase Auth
before performing any operation.

## Register a publisher key

```json
{
  "action": "publisher.register",
  "publisherId": "nova-community",
  "publisherKeyId": "community-ed25519-2026-01",
  "publicKeyBase64": "<DER SubjectPublicKeyInfo Ed25519 public key>"
}
```

A publisher ID can only be registered once. The private signing key must remain with the
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

The caller must own the active old key. The new public key is validated as Ed25519,
linked to the old key, and the old key is revoked. If the final revocation step cannot
be confirmed, the function removes only the newly inserted key and returns
`key_rotation_requires_reconciliation`; callers must not assume rotation succeeded.

## Revoke a publisher key

```json
{
  "action": "publisher.revoke",
  "publisherId": "nova-community",
  "publisherKeyId": "community-ed25519-2026-02"
}
```

Only the owner of that active key can revoke it. Revoked keys are excluded from the
public catalog's active-publisher join.

## List published modules

```json
{ "action": "catalog.list", "limit": 50 }
```

The catalog only returns modules whose registry status is `published` and whose
publisher key remains active.

## Explicitly not enabled yet

These actions do not grant publication rights. Module upload/submission, administrator
review, publication/revocation, user grants, signed downloads, and end-to-end tests with
multiple real authenticated accounts remain disabled until a protected administrator
role source, the shared manifest-signature verifier, and the storage authorization flow
are fully integrated. The Edge Function must remain JWT-protected.
