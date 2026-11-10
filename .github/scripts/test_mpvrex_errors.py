"""MPVRex stage 4: schema, security, recurrence and safe-record regression tests."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from mpvrex_errors import RegistryError, PATH, fingerprint, load, main, record, upsert, validate

class RegistryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.canonical = json.loads((Path(__file__).resolve().parents[2] / PATH).read_text(encoding="utf-8"))

    def fresh(self):
        data = copy.deepcopy(self.canonical)
        data["cases"] = []  # Independent fixture; production registry may be populated.
        return data

    def test_checked_in_registry_valid(self):
        self.assertEqual(self.canonical, validate(copy.deepcopy(self.canonical)))

    def event(self, **extra):
        event = dict(stage=4, category="configuration", component="preflight",
                     signature="base_sha_override", symptom="Untrusted baseline override accepted",
                     condition="Alternate command-line baseline supplied", confidence="confirmed",
                     root_cause="CLI accepted a replacement for pinned baseline",
                     fix="Remove baseline override argument",
                     diagnostic_tests=["Reject alternate baseline"],
                     regression_test="test_baseline_override_rejected", status="VERIFIED",
                     branch="docs/mpvrex-v32-stage04-error-registry-20261110", sha="a"*40,
                     ci={"status":"SUCCESS", "run_id":1234, "attempt":1},
                     event_key="preflight-stage4-first", seen_on="2026-11-10")
        event.update(extra)
        return event

    def test_empty_registry(self):
        self.assertEqual([], validate(self.fresh())["cases"])

    def test_wrong_repository_or_baseline(self):
        for field, bad in (("repository","laszko14/nira-browser"),
                           ("approved_baseline_sha","f"*40)):
            item=self.fresh()
            item[field]=bad
            with self.assertRaisesRegex(RegistryError,"Wrong project"):
                validate(item)

    def test_verified_add_and_stable_fingerprint(self):
        item=upsert(self.fresh(),self.event())
        self.assertEqual("MPV-ERR-0001",item["cases"][0]["id"])
        self.assertEqual(fingerprint(self.event()),item["cases"][0]["fingerprint"])

    def test_same_observation_idempotent(self):
        item=upsert(self.fresh(),self.event())
        self.assertEqual(item,upsert(item,self.event()))

    def test_repeat_fingerprint_merges_observations(self):
        item=upsert(self.fresh(),self.event())
        item=upsert(item,self.event(event_key="preflight-stage4-second", seen_on="2026-11-11"))
        self.assertEqual(1,len(item["cases"]))
        self.assertEqual(2,len(item["cases"][0]["observations"]))

    def test_different_fingerprint_allocates_next_id(self):
        item=upsert(self.fresh(),self.event())
        item=upsert(item,self.event(component="checkpoint",signature="duplicate_sha"))
        self.assertEqual("MPV-ERR-0002",item["cases"][1]["id"])

    def test_conflicting_event_key_rejected(self):
        item=upsert(self.fresh(),self.event())
        with self.assertRaisesRegex(RegistryError,"Conflicting reuse"):
            upsert(item,self.event(sha="b"*40))

    def test_verified_requires_evidence(self):
        for change in ({"confidence":"hypothesis"},{"root_cause":None},
                       {"regression_test":None},{"diagnostic_tests":[]},
                       {"ci":{"status":"PENDING","run_id":None,"attempt":None}}):
            with self.subTest(change=change),self.assertRaisesRegex(RegistryError,"VERIFIED needs"):
                upsert(self.fresh(),self.event(**change))

    def test_hypothesis_is_not_verified(self):
        item=upsert(self.fresh(),self.event(status="INVESTIGATING",confidence="hypothesis",
            root_cause="Potential CLI issue",fix=None,regression_test=None,
            diagnostic_tests=[],ci={"status":"NOT_TESTED","run_id":None,"attempt":None}))
        self.assertEqual("INVESTIGATING",item["cases"][0]["status"])

    def test_verified_reopens_only_with_new_event(self):
        item=upsert(self.fresh(),self.event())
        new=upsert(item,self.event(stage=5,sha="b"*40,event_key="new-stage5-regression",
            seen_on="2026-11-12",status="REOPENED",confidence="hypothesis",
            root_cause=None,fix=None,regression_test=None,diagnostic_tests=[],
            ci={"status":"FAIL","run_id":1235,"attempt":1}))
        self.assertEqual(2,len(new["cases"][0]["observations"]))
        self.assertEqual("REOPENED",new["cases"][0]["status"])
        with self.assertRaisesRegex(RegistryError,"only be reopened"):
            upsert(item,self.event(status="OPEN",event_key="invalid-reopen"))

    def test_no_rewind(self):
        item=upsert(self.fresh(),self.event(stage=5))
        with self.assertRaisesRegex(RegistryError,"rewind"):
            upsert(item,self.event(stage=4,event_key="old-stage"))

    def test_duplicate_fingerprint_rejected(self):
        item=upsert(self.fresh(),self.event())
        item["cases"].append(copy.deepcopy(item["cases"][0]))
        item["cases"][1]["id"]="MPV-ERR-0002"
        with self.assertRaisesRegex(RegistryError,"Duplicate fingerprint"):
            validate(item)

    def test_fingerprint_tampering_rejected(self):
        item=upsert(self.fresh(),self.event())
        item["cases"][0]["fingerprint"]="f"*64
        with self.assertRaisesRegex(RegistryError,"Fingerprint mismatch"):
            validate(item)

    def test_secrets_and_pii_rejected(self):
        for s in ("password=abc","Bearer abc","https://host/a","user@example.com",
                  "-----BEGIN PRIVATE KEY-----","ghp_examplecredential"):
            with self.subTest(s=s),self.assertRaisesRegex(RegistryError,"credential/PII"):
                upsert(self.fresh(),self.event(symptom=s))

    def test_malformed_types_fail_closed(self):
        for change in ({"category":["configuration"]},{"confidence":[]},
                       {"status":{}},{"ci":{"status":[],"run_id":None,"attempt":None}}):
            with self.subTest(change=change),self.assertRaises(RegistryError):
                upsert(self.fresh(),self.event(**change))

    def test_unknown_fields_rejected(self):
        item=upsert(self.fresh(),self.event())
        item["cases"][0]["raw_log"]="raw private information"
        with self.assertRaisesRegex(RegistryError,"schema mismatch"):
            validate(item)

    def test_corrupt_json_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/PATH
            path.parent.mkdir(parents=True)
            path.write_text('{"schema_version":',encoding="utf-8")
            with self.assertRaisesRegex(RegistryError,"Invalid registry JSON"):
                load(Path(td))

    def test_cli_guard_requires_write(self):
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.fresh()),encoding="utf-8")
            self.assertEqual(0,main(["validate","--root",td]))
            self.assertEqual(1,main(["record","--root",td]))
            self.assertEqual(1,main(["show","--root",td,"--write"]))

    def test_record_uses_checkpoint_and_git_guard(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            path=root/PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.fresh()),encoding="utf-8")
            checkpoint={"active":{"stage":4,"branch":self.event()["branch"]}}
            with patch("mpvrex_checkpoint.load",return_value=checkpoint), \
                 patch("mpvrex_checkpoint.assert_writable_checkout") as guard, \
                 patch("mpvrex_errors.subprocess.run") as proc:
                proc.return_value.returncode=0
                proc.return_value.stdout=""
                record(root,"a"*40,self.event())
                guard.assert_called_once()
                self.assertEqual(1,len(load(root)["cases"]))
                with self.assertRaisesRegex(RegistryError,"stage differs"):
                    record(root,"a"*40,self.event(stage=5))
                with self.assertRaisesRegex(RegistryError,"Stale"):
                    record(root,"b"*40,self.event())

if __name__ == "__main__":
    unittest.main()
