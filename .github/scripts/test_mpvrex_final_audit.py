"""Positive and negative regressions for MPVRex V3.2 final audit."""
from __future__ import annotations
import copy
from pathlib import Path
import unittest
import yaml

from mpvrex_checkpoint import load as load_checkpoint
from mpvrex_errors import load as load_errors
from mpvrex_final_audit import (
    AuditError, GATE, SUITES, CI_GUARD, contract, permitted_path, validate, main,
)
ROOT = Path(__file__).resolve().parents[2]


class FinalAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.state = load_checkpoint(ROOT)
        cls.cases = load_errors(ROOT)
        cls.workflow = yaml.safe_load((ROOT / ".github/workflows/build.yml").read_text())
        cls.changes = ["docs/AI_HANDOFF.md", ".github/scripts/mpvrex_final_audit.py",
                       "AGENTS.md", ".github/workflows/build.yml"]

    def values(self):
        return (copy.deepcopy(self.state), copy.deepcopy(self.cases),
                self.changes[:], set(SUITES), copy.deepcopy(self.workflow))

    def test_final_audit_canonical_pass(self):
        result = validate(ROOT)
        self.assertEqual("PASS", result["offline_audit"])
        self.assertEqual(9, result["history_stages"])
        self.assertEqual("NOT_VERIFIED_BY_THIS_TOOL", result["live_source_ci"])

    def test_stage10_active_and_history9(self):
        s,c,p,t,w = self.values()
        self.assertEqual(10, contract(s,c,p,t,w)["active_stage"])

    def test_missing_historical_stage_rejected(self):
        s,c,p,t,w = self.values()
        s["completed"].pop()
        with self.assertRaisesRegex(AuditError, "Incomplete 10-stage"):
            contract(s,c,p,t,w)

    def test_final_status_cannot_self_certify(self):
        s,c,p,t,w = self.values()
        s["active"]["status"] = "CODE_VERIFIED"
        with self.assertRaisesRegex(AuditError, "self-certify"):
            contract(s,c,p,t,w)

    def test_stage_branch_mismatch_rejected(self):
        s,c,p,t,w = self.values()
        s["completed"][2]["branch"] = "docs/mpvrex-v32-stage05-mismatch"
        with self.assertRaisesRegex(AuditError, "stage order"):
            contract(s,c,p,t,w)

    def test_missing_android_evidence_rejected(self):
        s,c,p,t,w = self.values()
        s["completed"][7]["evidence"]["required_jobs"] = ["YAML, Python i Lua"]
        with self.assertRaisesRegex(AuditError, "Android CI"):
            contract(s,c,p,t,w)

    def test_duplicate_run_rejected(self):
        s,c,p,t,w = self.values()
        s["completed"][4]["evidence"]["run_id"] = s["completed"][3]["evidence"]["run_id"]
        with self.assertRaisesRegex(AuditError, "Duplicate stage"):
            contract(s,c,p,t,w)

    def test_unapproved_app_changes_rejected(self):
        s,c,p,t,w = self.values()
        p.append("app/src/main/AndroidManifest.xml")
        with self.assertRaisesRegex(AuditError, "Unapproved application"):
            contract(s,c,p,t,w)

    def test_unapproved_release_workflow_rejected(self):
        s,c,p,t,w = self.values()
        p.append(".github/workflows/release.yml")
        with self.assertRaisesRegex(AuditError, "Unapproved application"):
            contract(s,c,p,t,w)

    def test_path_traversal_rejected(self):
        self.assertFalse(permitted_path("docs/../../app/build.gradle.kts"))
        self.assertTrue(permitted_path("docs/ai/FINAL_AUDIT.md"))

    def test_missing_suite_rejected(self):
        s,c,p,t,w = self.values()
        t.remove("test_mpvrex_final_audit.py")
        with self.assertRaisesRegex(AuditError, "Missing regression suite"):
            contract(s,c,p,t,w)

    def test_unresolved_incident_rejected(self):
        s,c,p,t,w = self.values()
        c["cases"][0]["status"] = "REOPENED"
        with self.assertRaisesRegex(AuditError, "Unresolved registered"):
            contract(s,c,p,t,w)

    def test_final_ci_gate_missing_rejected(self):
        s,c,p,t,w = self.values()
        w["jobs"]["sources"]["steps"] = [
            x for x in w["jobs"]["sources"]["steps"] if x.get("run") != GATE]
        with self.assertRaisesRegex(AuditError, "gate missing"):
            contract(s,c,p,t,w)

    def test_final_ci_gate_disabled_rejected(self):
        s,c,p,t,w = self.values()
        step = next(x for x in w["jobs"]["sources"]["steps"] if x.get("run") == GATE)
        step["if"] = "false && " + CI_GUARD
        with self.assertRaisesRegex(AuditError, "gate missing"):
            contract(s,c,p,t,w)

    def test_signed_apk_and_device_remain_unverified(self):
        r = validate(ROOT)
        self.assertEqual("NOT_TESTED", r["signed_apk"])
        self.assertEqual("NOT_TESTED", r["device"])

    def test_cli_report_offline_only(self):
        self.assertEqual(0, main(["report", "--root", str(ROOT)]))


if __name__ == "__main__":
    unittest.main()
