"""Fail-closed NOVA authorization decision core.

This module is a reference policy decision point (PDP), not an identity provider,
database, approval service, or production deployment by itself. Callers must build
Principal, Resource, grants, and ApprovalEvidence from trusted server-side sources.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from math import isfinite
from typing import Callable, FrozenSet, Iterable, Optional, Tuple


VALID_ACTIONS = frozenset({
    "read", "create", "update", "delete", "publish", "export", "sync", "execute", "admin"
})
VALID_SCOPES = frozenset({
    "self", "participant", "group", "tenant", "ecosystem", "owner-only"
})
VALID_SENSITIVITY = frozenset({
    "public", "user-private", "sensitive", "financial", "biometric"
})
ALWAYS_APPROVAL_ACTIONS = frozenset({"delete", "publish", "export", "admin"})
MUTATING_ACTIONS = frozenset({"create", "update", "delete", "export", "admin"})


@dataclass(frozen=True)
class Principal:
    """Authenticated identity and memberships loaded by a trusted server."""
    principal_id: str
    authenticated: bool
    roles: FrozenSet[str] = field(default_factory=frozenset)
    tenant_id: Optional[str] = None
    group_ids: FrozenSet[str] = field(default_factory=frozenset)


@dataclass(frozen=True)
class Resource:
    """Resource metadata; never put resource contents or secrets in this object."""
    resource_id: str
    resource_type: str
    owner_id: Optional[str]
    sensitivity: str = "user-private"
    tenant_id: Optional[str] = None
    group_ids: FrozenSet[str] = field(default_factory=frozenset)
    participant_ids: FrozenSet[str] = field(default_factory=frozenset)


@dataclass(frozen=True)
class PermissionDeclaration:
    """Permission requested by a module manifest; it is not an authorization grant."""
    resource: str
    action: str
    scope: str
    approval: bool = False


@dataclass(frozen=True)
class ModulePolicy:
    module_id: str
    permissions: Tuple[PermissionDeclaration, ...]
    data_access: FrozenSet[str]


@dataclass(frozen=True)
class Grant:
    """A server-managed grant. Never construct this from client-supplied roles."""
    grant_id: str
    resource: str
    action: str
    scope: str
    principal_id: Optional[str] = None
    role: Optional[str] = None
    tenant_id: Optional[str] = None
    expires_at: Optional[float] = None
    revoked: bool = False


@dataclass(frozen=True)
class ApprovalEvidence:
    """Evidence returned by a trusted approval service after verification."""
    approval_id: str
    principal_id: str
    resource_id: str
    action: str
    approved: bool
    expires_at: Optional[float] = None


@dataclass(frozen=True)
class AuthorizationRequest:
    module_id: str
    resource: Resource
    action: str
    scope: str
    now: float
    approval: Optional[ApprovalEvidence] = None


@dataclass(frozen=True)
class AuditEvent:
    """Metadata-only event. Do not add request bodies, tokens, or personal content."""
    at: float
    module_id: str
    principal_id: str
    resource_id: str
    action: str
    scope: str
    allowed: bool
    reason: str
    grant_id: Optional[str] = None


@dataclass(frozen=True)
class Decision:
    allowed: bool
    reason: str
    grant_id: Optional[str] = None


AuditSink = Callable[[AuditEvent], None]


def _matches_resource(pattern: str, resource: str) -> bool:
    if not isinstance(pattern, str) or not isinstance(resource, str):
        return False
    if not pattern or not resource:
        return False
    if pattern == resource:
        return True
    if pattern.endswith(".*"):
        prefix = pattern[:-1]
        return resource.startswith(prefix) and len(resource) > len(prefix)
    return False


class AuthorizationEngine:
    """Small deterministic PDP with deny-by-default and optional audit sink."""

    def __init__(
        self,
        modules: Iterable[ModulePolicy],
        grants: Iterable[Grant],
        audit_sink: Optional[AuditSink] = None,
    ) -> None:
        self._modules = {item.module_id: item for item in modules}
        self._grants = tuple(grants)
        self._audit_sink = audit_sink

    @staticmethod
    def _scope_matches(principal: Principal, resource: Resource, scope: str) -> bool:
        if scope in {"self", "owner-only"}:
            return bool(resource.owner_id) and principal.principal_id == resource.owner_id
        if scope == "participant":
            return principal.principal_id in resource.participant_ids
        if scope == "group":
            return bool(principal.group_ids.intersection(resource.group_ids))
        if scope == "tenant":
            return bool(principal.tenant_id and resource.tenant_id
                        and principal.tenant_id == resource.tenant_id)
        if scope == "ecosystem":
            # No generic "admin" bypass: ecosystem scope is reserved for the
            # explicitly provisioned platform-admin role and grant.
            return isinstance(principal.roles, (set, frozenset)) and "nova.platform_admin" in principal.roles
        return False

    @staticmethod
    def _approval_matches(evidence: Optional[ApprovalEvidence],
                          principal: Principal, resource: Resource,
                          action: str, now: float) -> bool:
        if not isinstance(evidence, ApprovalEvidence):
            return False
        if (evidence.approved is not True or not isinstance(evidence.approval_id, str)
                or not evidence.approval_id.strip()):
            return False
        if evidence.principal_id != principal.principal_id:
            return False
        if evidence.resource_id != resource.resource_id or evidence.action != action:
            return False
        if evidence.expires_at is not None:
            if not isinstance(evidence.expires_at, (int, float)) or not isfinite(evidence.expires_at):
                return False
            if now >= evidence.expires_at:
                return False
        return True

    def _decide(self, principal: Principal, request: AuthorizationRequest) -> Decision:
        if not isinstance(principal, Principal) or not isinstance(request, AuthorizationRequest):
            return Decision(False, "invalid-request")
        resource = request.resource
        if not isinstance(resource, Resource):
            return Decision(False, "invalid-resource")
        if (not isinstance(principal.principal_id, str) or principal.authenticated is not True
                or not principal.principal_id.strip()):
            return Decision(False, "unauthenticated")
        if (not isinstance(request.module_id, str) or not request.module_id.strip()
                or not isinstance(request.action, str) or request.action not in VALID_ACTIONS
                or not isinstance(request.scope, str) or request.scope not in VALID_SCOPES
                or not isinstance(request.now, (int, float))):
            return Decision(False, "invalid-action-or-scope")
        if (not isinstance(resource.resource_id, str) or not resource.resource_id.strip()
                or not isinstance(resource.resource_type, str) or not resource.resource_type.strip()
                or not isinstance(resource.sensitivity, str)
                or resource.sensitivity not in VALID_SENSITIVITY):
            return Decision(False, "invalid-resource")
        module = self._modules.get(request.module_id)
        if module is None:
            return Decision(False, "unknown-module")
        if resource.sensitivity not in module.data_access:
            return Decision(False, "module-data-class-not-declared")

        declaration = next((p for p in module.permissions
                            if p.resource and p.action == request.action
                            and p.scope == request.scope
                            and _matches_resource(p.resource, resource.resource_type)), None)
        if declaration is None:
            return Decision(False, "permission-not-declared")

        approval_required = (
            declaration.approval
            or request.action in ALWAYS_APPROVAL_ACTIONS
            or (resource.sensitivity in {"financial", "biometric"}
                and request.action in MUTATING_ACTIONS)
        )
        if approval_required and not self._approval_matches(
            request.approval, principal, resource, request.action, request.now
        ):
            return Decision(False, "approval-required-or-invalid")

        if resource.sensitivity == "biometric" and principal.principal_id != resource.owner_id:
            return Decision(False, "biometric-owner-only")

        if not self._scope_matches(principal, resource, request.scope):
            return Decision(False, "scope-mismatch")

        for grant in self._grants:
            if not isinstance(grant, Grant) or grant.revoked is not False:
                continue
            if grant.expires_at is not None:
                if not isinstance(grant.expires_at, (int, float)) or not isfinite(grant.expires_at):
                    continue
                if request.now >= grant.expires_at:
                    continue
            if grant.action != request.action or grant.scope != request.scope:
                continue
            if not _matches_resource(grant.resource, resource.resource_type):
                continue
            if grant.tenant_id is not None and grant.tenant_id != resource.tenant_id:
                continue
            subject_matches = (
                grant.principal_id == principal.principal_id
                if grant.principal_id is not None
                else bool(grant.role and grant.role in principal.roles)
            )
            if not subject_matches:
                continue
            if request.scope == "ecosystem" and grant.role != "nova.platform_admin":
                continue
            return Decision(True, "explicit-grant", grant.grant_id)

        return Decision(False, "no-active-grant")

    def authorize(self, principal: Principal, request: AuthorizationRequest) -> Decision:
        """Evaluate and emit a metadata-only audit event; audit failure fails closed."""
        decision = self._decide(principal, request)
        principal_id = principal.principal_id if isinstance(principal, Principal) else "unknown"
        resource = request.resource if isinstance(request, AuthorizationRequest) else None
        event = AuditEvent(
            at=request.now if isinstance(request, AuthorizationRequest) else 0.0,
            module_id=request.module_id if isinstance(request, AuthorizationRequest) else "unknown",
            principal_id=principal_id,
            resource_id=resource.resource_id if resource else "unknown",
            action=request.action if isinstance(request, AuthorizationRequest) else "unknown",
            scope=request.scope if isinstance(request, AuthorizationRequest) else "unknown",
            allowed=decision.allowed,
            reason=decision.reason,
            grant_id=decision.grant_id,
        )
        if self._audit_sink is not None:
            try:
                self._audit_sink(event)
            except Exception:
                return Decision(False, "audit-unavailable")
        return decision
