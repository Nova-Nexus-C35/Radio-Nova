import unittest
from scripts.validate_module_manifests import validate

VALID = {
 "schema_version":"1.0","id":"nova-demo","name":"Nova Demo","version":"1.2.3",
 "kind":"module","lifecycle":"in-development",
 "repository":{"url":"https://github.com/Cumba35/Nova-Workspace","default_branch":"main"},
 "capabilities":["demo.read"],
 "permissions":[{"resource":"demo.records","action":"read","scope":"self","approval":False}],
 "data_access":["user-private"],"network_policy":"local-first","offline_support":True,
 "dependencies":[]
}

class ManifestValidationTests(unittest.TestCase):
 def test_valid_manifest(self):
  self.assertEqual(validate(VALID), [])
 def test_rejects_secret_fields(self):
  self.assertTrue(any("forbidden" in e for e in validate({**VALID,"api_key":"do-not-store"})))
 def test_requires_approval_for_delete(self):
  data={**VALID,"permissions":[{"resource":"demo.records","action":"delete","scope":"self","approval":False}]}
  self.assertTrue(any("requires explicit approval" in e for e in validate(data)))
 def test_rejects_unknown_fields(self):
  self.assertTrue(any("unknown root keys" in e for e in validate({**VALID,"runtime_url":"https://example.invalid"})))

if __name__ == "__main__":
 unittest.main()
