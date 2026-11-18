import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.validate_module_manifests import duplicate_manifest_ids, main, validate

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

 def test_malformed_types_return_errors_not_exceptions(self):
  data={**VALID,"kind":[],"lifecycle":{},"data_access":[[]],"network_policy":{},"permissions":[{"resource":"demo","action":[],"scope":{},"approval":False}]}
  self.assertTrue(validate(data))

 def test_financial_mutation_requires_approval(self):
  data={**VALID,"data_access":["financial"],"permissions":[{"resource":"ledger.entries","action":"create","scope":"self","approval":False}]}
  self.assertTrue(any("financial/biometric" in e for e in validate(data)))

 def test_duplicate_ids_ignore_malformed_unhashable_ids(self):
  entries=[("bad.json",{"id":["not","hashable"]}),("one.json",{"id":"nova-demo"}),("two.json",{"id":"nova-demo"})]
  errors=duplicate_manifest_ids(entries)
  self.assertEqual(len(errors),1)
  self.assertIn("two.json",errors[0])
  self.assertIn("nova-demo",errors[0])

 def test_default_branch_max_length_matches_schema(self):
  data={**VALID,"repository":{**VALID["repository"],"default_branch":"x"*81}}
  self.assertTrue(any("default_branch" in e for e in validate(data)))

 def test_dependency_version_range_matches_schema(self):
  data={**VALID,"dependencies":[{"id":"nova-core","version_range":"x"*41,"optional":False}]}
  self.assertTrue(any("dependency values" in e for e in validate(data)))

 def test_main_reports_non_string_id_without_crashing(self):
  with tempfile.TemporaryDirectory() as tmp:
   Path(tmp,"bad.json").write_text(json.dumps({**VALID,"id":[]}),encoding="utf-8")
   with patch("scripts.validate_module_manifests.MANIFEST_DIR",Path(tmp)):
    self.assertEqual(main(),1)

 def test_accepts_existing_project_slug(self):
  self.assertEqual(validate({**VALID,"id":"contador-cup"}), [])

if __name__ == "__main__":
 unittest.main()
