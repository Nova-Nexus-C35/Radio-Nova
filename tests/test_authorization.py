import unittest

from nova_guard.authorization import (
    ApprovalEvidence, AuthorizationEngine, AuthorizationRequest, Grant,
    ModulePolicy, PermissionDeclaration, Principal, Resource,
)


NOW = 1_800_000_000.0
MODULE = ModulePolicy(
    module_id="nova-demo",
    permissions=(
        PermissionDeclaration("docs.*", "read", "self"),
        PermissionDeclaration("docs.*", "update", "self", approval=True),
        PermissionDeclaration("team.*", "read", "group"),
        PermissionDeclaration("tenant.*", "read", "tenant"),
        PermissionDeclaration("platform.*", "admin", "ecosystem", approval=True),
        PermissionDeclaration("vault.*", "read", "self"),
        PermissionDeclaration("ledger.*", "update", "self"),
    ),
    data_access=frozenset({"public", "user-private", "financial", "biometric"}),
)


def resource(kind="docs.page", owner="alice", sensitivity="user-private",
             tenant="t1", groups=frozenset(), participants=frozenset()):
    return Resource("res-1", kind, owner, sensitivity, tenant, groups, participants)


def request(res=None, action="read", scope="self", approval=None):
    return AuthorizationRequest("nova-demo", res or resource(), action, scope, NOW, approval)


class AuthorizationEngineTests(unittest.TestCase):
    def engine(self, grants=(), audit=None, modules=(MODULE,)):
        return AuthorizationEngine(modules, grants, audit)

    def grant(self, **kwargs):
        values = dict(grant_id="g1", resource="docs.*", action="read",
                      scope="self", principal_id="alice")
        values.update(kwargs)
        return Grant(**values)

    def test_allows_owner_with_explicit_grant(self):
        engine = self.engine([self.grant()])
        self.assertTrue(engine.authorize(Principal("alice", True), request()).allowed)

    def test_denies_unauthenticated(self):
        engine = self.engine([self.grant()])
        self.assertEqual(engine.authorize(Principal("alice", False), request()).reason,
                         "unauthenticated")

    def test_denies_without_grant(self):
        self.assertEqual(self.engine().authorize(Principal("alice", True), request()).reason,
                         "no-active-grant")

    def test_denies_unknown_module(self):
        req = AuthorizationRequest("missing", resource(), "read", "self", NOW)
        self.assertEqual(self.engine().authorize(Principal("alice", True), req).reason,
                         "unknown-module")

    def test_manifest_declaration_does_not_grant_access(self):
        self.assertFalse(self.engine().authorize(Principal("alice", True), request()).allowed)

    def test_denies_wrong_owner_even_with_grant(self):
        engine = self.engine([self.grant()])
        self.assertEqual(engine.authorize(Principal("bob", True), request()).reason,
                         "scope-mismatch")

    def test_revoked_grant_is_denied(self):
        engine = self.engine([self.grant(revoked=True)])
        self.assertEqual(engine.authorize(Principal("alice", True), request()).reason,
                         "no-active-grant")

    def test_expired_grant_is_denied(self):
        engine = self.engine([self.grant(expires_at=NOW)])
        self.assertEqual(engine.authorize(Principal("alice", True), request()).reason,
                         "no-active-grant")

    def test_tenant_bound_grant(self):
        grant = Grant("g2", "tenant.*", "read", "tenant",
                      principal_id="alice", tenant_id="t1")
        module = ModulePolicy("nova-demo", (PermissionDeclaration(
            "tenant.*", "read", "tenant"),), frozenset({"user-private"}))
        engine = self.engine([grant], modules=(module,))
        res = resource("tenant.invoice", tenant="t1")
        self.assertTrue(engine.authorize(Principal("alice", True, tenant_id="t1"),
                                         request(res, scope="tenant")).allowed)
        wrong = resource("tenant.invoice", tenant="t2")
        self.assertFalse(engine.authorize(Principal("alice", True, tenant_id="t1"),
                                          request(wrong, scope="tenant")).allowed)

    def test_group_membership_required(self):
        grant = Grant("g3", "team.*", "read", "group", principal_id="alice")
        module = ModulePolicy("nova-demo", (PermissionDeclaration(
            "team.*", "read", "group"),), frozenset({"user-private"}))
        engine = self.engine([grant], modules=(module,))
        res = resource("team.document", groups=frozenset({"g1"}))
        self.assertTrue(engine.authorize(Principal("alice", True, group_ids=frozenset({"g1"})),
                                         request(res, scope="group")).allowed)
        self.assertFalse(engine.authorize(Principal("alice", True, group_ids=frozenset({"g2"})),
                                          request(res, scope="group")).allowed)

    def test_approval_flag_is_not_approval_evidence(self):
        engine = self.engine([self.grant(resource="docs.*", action="update")])
        req = request(action="update")
        self.assertEqual(engine.authorize(Principal("alice", True), req).reason,
                         "approval-required-or-invalid")

    def test_approval_must_match_subject_resource_and_action(self):
        engine = self.engine([self.grant(resource="docs.*", action="update")])
        evidence = ApprovalEvidence("ap-1", "bob", "res-1", "update", True)
        self.assertFalse(engine.authorize(Principal("alice", True),
                                           request(action="update", approval=evidence)).allowed)

    def test_approved_sensitive_update_still_needs_grant(self):
        evidence = ApprovalEvidence("ap-1", "alice", "res-1", "update", True)
        decision = self.engine().authorize(Principal("alice", True),
                                           request(action="update", approval=evidence))
        self.assertEqual(decision.reason, "no-active-grant")

    def test_approved_update_with_grant(self):
        evidence = ApprovalEvidence("ap-1", "alice", "res-1", "update", True)
        engine = self.engine([self.grant(resource="docs.*", action="update")])
        self.assertTrue(engine.authorize(Principal("alice", True),
                                         request(action="update", approval=evidence)).allowed)

    def test_expired_approval_denied(self):
        evidence = ApprovalEvidence("ap-1", "alice", "res-1", "update", True, NOW)
        engine = self.engine([self.grant(resource="docs.*", action="update")])
        self.assertFalse(engine.authorize(Principal("alice", True),
                                          request(action="update", approval=evidence)).allowed)

    def test_biometric_data_owner_only(self):
        module = ModulePolicy("nova-demo", (PermissionDeclaration(
            "vault.*", "read", "self"),), frozenset({"biometric"}))
        grant = Grant("g4", "vault.*", "read", "self", principal_id="bob")
        engine = self.engine([grant], modules=(module,))
        res = resource("vault.sample", owner="alice", sensitivity="biometric")
        self.assertEqual(engine.authorize(Principal("bob", True),
                                          request(res)).reason, "biometric-owner-only")

    def test_module_must_declare_data_class(self):
        module = ModulePolicy("nova-demo", (PermissionDeclaration(
            "docs.*", "read", "self"),), frozenset({"public"}))
        engine = self.engine([self.grant()], modules=(module,))
        self.assertEqual(engine.authorize(Principal("alice", True), request()).reason,
                         "module-data-class-not-declared")

    def test_ecosystem_scope_requires_platform_admin_role_and_grant(self):
        grant = Grant("g5", "platform.*", "admin", "ecosystem",
                      role="nova.platform_admin")
        module = ModulePolicy("nova-demo", (PermissionDeclaration(
            "platform.*", "admin", "ecosystem", approval=True),), frozenset({"public"}))
        engine = self.engine([grant], modules=(module,))
        evidence = ApprovalEvidence("ap-2", "root", "res-1", "admin", True)
        req = request(resource("platform.settings", owner=None, sensitivity="public"),
                      "admin", "ecosystem", evidence)
        self.assertTrue(engine.authorize(
            Principal("root", True, roles=frozenset({"nova.platform_admin"})), req).allowed)
        self.assertFalse(engine.authorize(Principal("root", True, roles=frozenset({"admin"})),
                                          req).allowed)

    def test_audit_event_emitted(self):
        events = []
        engine = self.engine([self.grant()], events.append)
        self.assertTrue(engine.authorize(Principal("alice", True), request()).allowed)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].resource_id, "res-1")

    def test_audit_failure_fails_closed(self):
        def broken(_event):
            raise RuntimeError("sink unavailable")
        decision = self.engine([self.grant()], broken).authorize(
            Principal("alice", True), request())
        self.assertEqual(decision.reason, "audit-unavailable")
        self.assertFalse(decision.allowed)


if __name__ == "__main__":
    unittest.main()
