# NOVA Authorization Core — reference PDP v1

## What this is

\`nova_guard.authorization.AuthorizationEngine\` is a deterministic, dependency-free, fail-closed Policy Decision Point (PDP). It provides the decision logic shared by future NOVA server adapters. It is not an identity provider, approval workflow, database, API server, session store, or deployment.

## Decision sequence

1. Reject unauthenticated or malformed principals and requests.
2. Require a known module policy and a matching declared manifest permission.
3. Require the resource sensitivity class to be declared by that module.
4. Require verified approval evidence for manifest-marked operations, destructive/publication/export/admin actions, and financial/biometric mutations.
5. Enforce resource scope (owner, participant, group, tenant, or platform administrator).
6. Require an active server-managed grant matching subject/role, resource type, action, scope, and optional tenant boundary.
7. Emit a metadata-only audit event. If the configured audit sink fails, an otherwise allowed decision becomes a denial.

A manifest permission is a request/declaration, not a grant. A manifest's \`approval: true\` flag is not approval evidence. The caller must obtain \`ApprovalEvidence\` from a trusted approval service and build the principal, memberships, resource metadata, and grants from trusted server-side sources.

## Security properties

- Deny by default; unknown module, data class, action, scope, absent/revoked/expired grant, and mismatched scope all deny.
- No implicit administrator bypass. Ecosystem-wide access requires both the \`nova.platform_admin\` role and an explicit grant for that role.
- Biometric resources are owner-only.
- Group and tenant boundaries are checked against server-supplied membership/resource metadata.
- Audit events contain IDs and decision metadata only; never add payloads, secrets, tokens, memory text, or biometric data.
- No persistence or cache is provided. The `authorize(..., grants=...)` argument accepts a fresh authoritative grant snapshot on each request; production adapters must use it so a revoked grant stops authorizing immediately. Omitting it uses the constructor snapshot and is suitable only for tests/static policies.

## Integration contract

A production adapter must:
- Authenticate sessions and resolve the principal from verified server credentials.
- Load role/group/tenant membership from authoritative storage, never from client claims alone.
- Load resource owner, tenant, groups, participants, sensitivity, module policy, and current grants from trusted stores.
- Verify approval IDs and their subject/resource/action binding server-side.
- Supply a durable audit sink and treat audit outages as a denial.
- Call this PDP on every protected read/write and enforce the returned decision before touching data.
- Test cross-account access, tenant escape, group escape, role forgery, grant revocation, expired approvals, and audit outages.
- Keep legacy data and Supabase resources unchanged during migration. This reference module does not connect to or mutate existing infrastructure.

## Limitations and rollout

The module is versioned for integration and unit testing. Until a real server adapter, authoritative identity/grant store, durable audit sink, and two-account end-to-end tests are deployed and verified, NOVA runtime authorization remains **not implemented / not production-ready**. Do not claim otherwise or move sensitive data based solely on these unit tests.

## Public design references reviewed

- Open Policy Agent: https://github.com/open-policy-agent/opa — services ask a policy engine for context-aware allow/deny decisions; the service enforces the result.
- Apache Casbin: https://github.com/casbin/pycasbin — model-driven access control patterns (ACL/RBAC/ABAC).
- OpenFGA: https://github.com/openfga/openfga — relationship-based access checks for ownership and membership.
- Cerbos: https://github.com/cerbos/cerbos — separate policy decision service and application enforcement.

NOVA adopts the separation of policy decision from enforcement and combines explicit grants with role, attribute, relationship, and scope checks. No external source code or runtime dependency is copied into NOVA.
