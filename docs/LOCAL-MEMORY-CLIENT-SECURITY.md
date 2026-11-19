# NOVA local personal memory — client security contract

Date: 2026-11-19  
Canonical security repository: `Cumba35/Nova-Guard`  
Client implementation under review: `Cumba35/Nova-IA`, PR #48

## Purpose and boundary

The Android local personal-memory store is a device-local feature, separate from the encrypted conversation history and separate from the existing cloud-memory service. It does not replace Nova Guard's server-side memory RPCs, consent checks, per-owner Vault keys, or authorization policies.

The local store does not grant a user a server identity, role, permission, platform-admin status, or authority to access another account's data. Offline use is limited to an account whose identity was previously stored by the authenticated client. First-run offline identity remains unresolved and must not be solved by trusting a user-supplied role or a locally editable owner flag.

## Client storage contract

The initial Android implementation uses:

- AES-GCM authenticated encryption for the serialized memory payload.
- A non-exportable AES key held by Android Keystore, generated separately for each locally stored account.
- A SHA-256-derived account namespace for the preferences file and Keystore alias; the raw account ID is not embedded in those names.
- Additional authenticated data tied to the account-specific key alias.
- A separate encrypted store from conversation history, with a maximum of 100 records.
- Metadata for category, key, value, source type, creation time, update time, and explicit confirmation time.
- A fail-closed read path: decryption errors preserve the ciphertext and surface an error rather than replacing it with an empty store.
- A missing account key is not regenerated during decryption. The read fails closed, preserving the ciphertext and avoiding a replacement key that could mask permanent data loss.

Android Keystore may be hardware-backed when supported by the device. NOVA must not claim hardware backing on every Android device. The store is not recoverable if the app is uninstalled, the device is reset, or the Keystore key is lost; an encrypted export/restore design is still required. Never regenerate a missing key and overwrite ciphertext after a decryption failure.

## Consent and write rules

1. Suggestions are disabled by default.
2. A user may enable or disable local suggestions without contacting the cloud service.
3. A suggestion is only a candidate. It is persisted only after the user selects the explicit save action.
4. Dismissing, cancelling, or rejecting a candidate must not write it.
5. An explicit user instruction to remember a fact is a direct confirmation and may write to the local store.
6. Local records are not automatically uploaded or synchronized.
7. The client forces the legacy cloud automatic-memory flag off when it updates cloud preferences. Existing cloud memory remains a separate service and is not silently migrated.
8. Cloud memories may enter the local model prompt only while the account's `personal_memory_enabled` preference is true. Disabling that preference must clear the in-memory cloud-memory cache; local device memories remain independent.
9. Sensitive memory must not be inferred or saved by this standard local store. Credential, contact, and financial-identifier rejection currently uses a basic client-side heuristic; it is not a complete data-classification system.

Credentials, API keys, authentication tokens, payment card data, and recovery codes belong in the dedicated password/secret vault, not personal memory. Biometric templates, health data, government identifiers, and other high-sensitivity records require a separate consent and protection design before any storage feature is implemented.

## Account isolation

- Every local operation must use the account identifier obtained from the existing encrypted session store, not from UI input, display name, email text, or a role claim.
- Preferences and Keystore aliases must be scoped to a cryptographic digest of that account identifier.
- Switching accounts must not display, overwrite, or delete another account's local memories.
- Sign-out must not convert the next account into the previous account's owner.
- An expired cloud session may leave a previously authenticated local account usable for local-only features, but it must not authorize cloud calls or administrative actions.
- Local identity is not a substitute for a verified server principal. Protected server operations must continue to require the Nova Guard authorization contract.

## User controls and deletion

The client must provide controls to inspect records, forget an individual record, and clear all local records. Individual deletion requires successful decryption. Explicit full deletion removes the account's encrypted payload and its Keystore key without attempting to recreate a missing key. Deletion from the local store does not delete a separate cloud record; the UI must describe that boundary clearly. A future optional sync feature must add explicit opt-in, per-account conflict handling, source provenance, revocation, and a verified delete propagation protocol before it is enabled.

## Verification status

The first implementation is in Nova IA PR #48. It adds account-scoped AES-GCM storage, explicit confirmation, local suggestion controls, and individual/full deletion. A basic privacy filter rejects common credential terms, email addresses, phone-like digit sequences, and long identifiers.

- Standalone Kotlin checks with `kotlinc` passed for extractor and privacy-filter cases.
- JUnit regression tests were added. The extractor and privacy filter were also compiled and exercised with standalone `kotlinc`; all nine privacy cases passed. JUnit/Gradle and Android integration tests remain unexecuted because the branch has no Gradle wrapper and this environment has no Android SDK.
- Android Gradle compilation, Android Keystore behavior, account-switch instrumentation, UI tests, and real-device offline tests remain pending.
- This document is a security contract and review record, not a claim that the full client integration is production-ready.

## Required follow-up

- Add Android instrumentation tests for two accounts, offline use with a previously stored session, and account switching.
- Test wrong/missing Keystore key handling without destroying ciphertext.
- Add encrypted export/restore with user-controlled recovery.
- Add a schema version and migration strategy before changing the record format.
- Replace the heuristic filter with a tested privacy classification boundary and explicit sensitive-memory consent design.
- Decide how local deletion interacts with a matching cloud record without silently syncing or retaining a forgotten value in model context.
- Review cloud-memory migration and opt-in sync separately; do not add a new provider or billable service.
- Re-run license, dependency, and security review before adding SQLCipher/Room or any third-party storage library.

## Account-scoped conversation history — implementation update 2026-11-19

The Nova IA PR #48 client review found that personal memory was already account-scoped, but encrypted local chat history and the cloud conversation identifier still used installation-wide storage. This could expose one account's conversation context after another account signs in on the same device.

The Nova IA PR now changes the local chat store to require the authenticated account ID, derives separate preference namespaces and Android Keystore aliases per account, binds AES-GCM ciphertext to the account-specific alias as AAD, and fails closed if the account key is missing. MainActivity passes the session-derived account ID for history reads, writes, and deletion. Cloud conversation IDs now use a separate account-derived namespace.

The legacy encrypted chat store and plaintext migration store are migrated only once to the first authenticated account that opens the history. Legacy data is removed only after the new encrypted write commits. Since the previous global cloud conversation ID has no reliable account attribution, it is intentionally not migrated or reused.

Static source review and post-write GitHub rereads were completed. The Android manifest has allowBackup=false. Gradle/JUnit, Keystore migration, two-account instrumentation, account-switch and real-device offline tests have not been run; this update is not a production security certification.

Residual migration risk: the previous global local history did not record its owner. Assigning it to the first authenticated account is a compatibility choice, not proof of historical ownership. This must be disclosed to the owner and verified during Android migration testing.