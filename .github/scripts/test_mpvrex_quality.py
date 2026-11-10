"""MPVRex stage 6: positive and negative fail-closed quality gate tests."""
from __future__ import annotations
import copy
import unittest
from pathlib import Path

from mpvrex_quality import (QUALITY_DOC, QualityGateError, collect_tests,
                            load_inputs, validate_inputs, validate_project, main)
ROOT = Path(__file__).resolve().parents[2]

class QualityGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs = load_inputs(ROOT)

    def fixture(self):
        return copy.deepcopy(self.inputs)

    def check(self, values):
        return validate_inputs(*values)

    def test_canonical_quality_pass(self):
        result = validate_project(ROOT)
        self.assertEqual(len(self.inputs[0]["completed"]) + 1, result["stage"])
        self.assertEqual(len(self.inputs[0]["completed"]), result["completed_stages"])
        self.assertFalse(result["live_ci_verified"])
        self.assertFalse(result["device_verified"])
        self.assertGreaterEqual(result["discovered_asserting_tests"], 10)

    def test_missing_android_job_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        del workflow["jobs"]["android"]
        with self.assertRaisesRegex(QualityGateError, "Missing android job"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_regression_test_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        cases["cases"][0]["regression_test"] = "test_nonexistent_repair"
        with self.assertRaisesRegex(QualityGateError, "missing regression test"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_quality_gate_step_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["sources"]["steps"] = [
            s for s in workflow["jobs"]["sources"]["steps"]
            if "mpvrex_quality.py validate" not in str(s.get("run", ""))
        ]
        with self.assertRaisesRegex(QualityGateError, "Missing source quality command"):
            self.check((state, cases, docs, workflow, tests))

    def test_android_trigger_bypass_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["android"]["if"] = "github.event_name == 'pull_request'"
        with self.assertRaisesRegex(QualityGateError, "Android CI does not run"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_android_lint_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        for s in workflow["jobs"]["android"]["steps"]:
            if ":app:lintDebug" in str(s.get("run", "")):
                s["run"] = s["run"].replace(":app:lintDebug", "")
        with self.assertRaisesRegex(QualityGateError, "Missing Android build/test/lint"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_android_unit_tests_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        for s in workflow["jobs"]["android"]["steps"]:
            if ":app:testDebugUnitTest" in str(s.get("run", "")):
                s["run"] = s["run"].replace(":app:testDebugUnitTest", "")
        with self.assertRaisesRegex(QualityGateError, "Missing Android build/test/lint"):
            self.check((state, cases, docs, workflow, tests))

    def test_continue_on_error_source_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["sources"]["steps"][0]["continue-on-error"] = True
        with self.assertRaisesRegex(QualityGateError, "Bypassed step"):
            self.check((state, cases, docs, workflow, tests))

    def test_continue_on_error_android_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["android"]["continue-on-error"] = True
        with self.assertRaisesRegex(QualityGateError, "Bypassed job"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_source_dependency_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["android"]["needs"] = []
        with self.assertRaisesRegex(QualityGateError, "depend on sources"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_mpvrex_branch_trigger_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        trigger = workflow.get("on", workflow.get(True))
        trigger["push"]["branches"] = ["master"]
        with self.assertRaisesRegex(QualityGateError, "docs branch"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_python_discovery_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        for s in workflow["jobs"]["sources"]["steps"]:
            if "unittest discover" in str(s.get("run", "")):
                s["run"] = "python3 --version"
        with self.assertRaisesRegex(QualityGateError, "Missing source quality command"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_quality_document_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        del docs[QUALITY_DOC]
        with self.assertRaisesRegex(QualityGateError, "Incomplete documentation"):
            self.check((state, cases, docs, workflow, tests))

    def test_stale_handoff_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        docs["docs/AI_HANDOFF.md"] = docs["docs/AI_HANDOFF.md"].replace(
            state["active"]["branch"], "docs/mpvrex-v32-stage06-wrong-20261110"
        )
        with self.assertRaisesRegex(QualityGateError, "Handoff missing exact"):
            self.check((state, cases, docs, workflow, tests))

    def test_missing_quality_heading_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        docs[QUALITY_DOC] = docs[QUALITY_DOC].replace("## Ograniczenia", "## Reszta")
        with self.assertRaisesRegex(QualityGateError, "Documentation missing"):
            self.check((state, cases, docs, workflow, tests))

    def test_regression_suite_missing_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        del tests["test_mpvrex_errors.py"]
        with self.assertRaisesRegex(QualityGateError, "Missing regression suite"):
            self.check((state, cases, docs, workflow, tests))

    def test_assertionless_test_ignored(self):
        self.assertEqual(set(), collect_tests({"test_dummy.py": """
import unittest
class Dummy(unittest.TestCase):
    def test_only_print(self):
        print('passed')
"""}))

    def test_no_substantive_quality_test_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        tests["test_mpvrex_quality.py"] = tests["test_mpvrex_quality.py"].replace(
            "def test_missing_android_job_rejected", "def disabled_missing_android_job_rejected"
        )
        with self.assertRaisesRegex(QualityGateError, "Missing substantive"):
            self.check((state, cases, docs, workflow, tests))

    def test_bad_test_syntax_rejected(self):
        with self.assertRaises(SyntaxError):
            collect_tests({"test_broken.py": "class A:\n def test_invalid(self)\n"})

    def test_signed_apk_doc_branch_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        workflow["jobs"]["signed_apk"]["if"] = "startsWith(github.ref, 'refs/heads/docs/mpvrex-v32-')"
        with self.assertRaisesRegex(QualityGateError, "Signed APK"):
            self.check((state, cases, docs, workflow, tests))

    def test_quality_step_false_guard_rejected(self):
        state, cases, docs, workflow, files = self.fixture()
        for s in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_quality.py validate" in str(s.get("run", "")):
                s["if"] = "false && " + str(s["if"])
        with self.assertRaisesRegex(QualityGateError, "Stage-6 quality gate"):
            self.check((state, cases, docs, workflow, files))

    def test_source_check_false_guard_rejected(self):
        state, cases, docs, workflow, files = self.fixture()
        for s in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_errors.py validate" in str(s.get("run", "")):
                s["if"] = "false && " + str(s["if"])
        with self.assertRaisesRegex(QualityGateError, "Essential source check"):
            self.check((state, cases, docs, workflow, files))

    def test_quality_step_requires_guard(self):
        state, cases, docs, workflow, tests = self.fixture()
        for s in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_quality.py validate" in str(s.get("run", "")):
                s.pop("if", None)
        with self.assertRaisesRegex(QualityGateError, "Stage-6 quality gate"):
            self.check((state, cases, docs, workflow, tests))

    def test_never_fake_live_ci_in_report(self):
        report = validate_project(ROOT)
        self.assertEqual("CI_PENDING", report["active_status"])
        self.assertFalse(report["live_ci_verified"])
        self.assertFalse(report["device_verified"])

    def test_cli_read_only(self):
        self.assertEqual(0, main(["validate", "--root", str(ROOT)]))
        self.assertEqual(0, main(["report", "--root", str(ROOT)]))

    def test_wrong_stage_rejected(self):
        state, cases, docs, workflow, tests = self.fixture()
        state["active"]["stage"] = 5
        with self.assertRaisesRegex(QualityGateError, "stage 6"):
            self.check((state, cases, docs, workflow, tests))

if __name__ == "__main__":
    unittest.main()
