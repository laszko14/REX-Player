"""MPVRex stage 5: systemic debugging gates, classification and non-fabrication."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from mpvrex_debugging import (DebuggingError, PROTOCOL, WORKFLOW, find_case,
                               main, next_action, triage, validate_process)
from mpvrex_errors import PATH, fingerprint, validate as validate_registry
from mpvrex_checkpoint import STATE_PATH

ROOT = Path(__file__).resolve().parents[2]

class SystematicDebuggingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.registry = json.loads((ROOT / PATH).read_text(encoding="utf-8"))
        cls.checkpoint = json.loads((ROOT / STATE_PATH).read_text(encoding="utf-8"))

    def case(self, status="FIXED_UNVERIFIED", confidence="confirmed", fix="Minimal fix"):
        return {"status": status, "confidence": confidence, "fix": fix}

    def make_root(self, cases=None, stage=5):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        registry = copy.deepcopy(self.registry)
        if cases is not None:
            registry["cases"] = cases
        checkpoint = copy.deepcopy(self.checkpoint)
        checkpoint["active"]["stage"] = stage
        if stage == 5:
            pass
        (root / PATH).parent.mkdir(parents=True)
        (root / PATH).write_text(json.dumps(registry), encoding="utf-8")
        (root / STATE_PATH).write_text(json.dumps(checkpoint), encoding="utf-8")
        (root / PROTOCOL).write_text("# Systematic debugging\n" + "protocol " * 40,
                                      encoding="utf-8")
        return root

    def test_canonical_protocol_is_valid(self):
        data = validate_process(ROOT)
        self.assertEqual(self.registry, data)
        self.assertGreaterEqual(len(WORKFLOW), 9)

    def test_exact_fingerprint_match_no_guessing(self):
        current = self.registry["cases"][0]
        matched = find_case(self.registry, current["category"],
                            current["component"], current["signature"])
        self.assertEqual(current["id"], matched["id"])
        self.assertIsNone(find_case(self.registry, "code", "media_player",
                                    "new_unconfirmed_crash"))

    def test_fingerprint_does_not_include_stage(self):
        sample = self.registry["cases"][0]
        initial = fingerprint(sample)
        other = dict(sample, stage=9, sha="f"*40)
        self.assertEqual(initial, fingerprint(other))

    def test_invalid_match_fields_rejected(self):
        for category, component, signature in (
            ("test_fixture", "UPPER", "defect"),
            ("other", "component", "defect"),
            ("code", "ok_comp", "bad/url"),
        ):
            with self.subTest(category=category, component=component), self.assertRaises(DebuggingError):
                find_case(self.registry, category, component, signature)

    def test_all_decision_branches(self):
        cases = (
            (self.case("OPEN"), 0, "REPRODUCE_AND_COLLECT_EVIDENCE"),
            (self.case("INVESTIGATING", "hypothesis"), 0, "CONFIRM_ROOT_CAUSE"),
            (self.case("INVESTIGATING", "confirmed", None), 0, "APPLY_MINIMAL_FIX"),
            (self.case("FIXED_UNVERIFIED"), 0, "RUN_DIAGNOSTIC_REGRESSIONS_AND_LIVE_CI"),
            (self.case("REOPENED"), 0, "REPRODUCE_AND_COMPARE_WITH_PREVIOUS_FIX"),
            (self.case("BLOCKED"), 0, "DOCUMENT_AND_RESOLVE_BLOCKER"),
            (self.case("VERIFIED"), 0, "MONITOR_FOR_REGRESSION"),
            (self.case("VERIFIED"), 1, "REOPEN_WITH_NEW_OBSERVATION"),
            (self.case("VERIFIED"), 2, "REOPEN_AND_CHANGE_DIAGNOSTIC_METHOD"),
            (self.case("INVESTIGATING", "hypothesis"), 2, "CHANGE_HYPOTHESIS_OR_DIAGNOSTIC_METHOD"),
        )
        for case, failures, expected in cases:
            with self.subTest(expected=expected):
                self.assertEqual(expected, next_action(case, failures))

    def test_two_failures_change_method(self):
        self.assertNotEqual(next_action(self.case(), 1), next_action(self.case(), 2))

    def test_invalid_failure_counts_rejected(self):
        for count in (-1, 101, "2", True, 2.5):
            with self.subTest(count=count), self.assertRaisesRegex(DebuggingError, "consecutive_failures"):
                next_action(self.case(), count)

    def test_triage_never_claims_live_ci(self):
        case = self.registry["cases"][0]
        result = triage(self.registry, case["id"], 2)
        self.assertFalse(result["live_ci_verified"])
        self.assertEqual(result["case_id"], case["id"])
        self.assertIn("SHA", result["note"])
        self.assertEqual(list(WORKFLOW), result["workflow"])

    def test_unknown_or_malformed_case_denied(self):
        with self.assertRaisesRegex(DebuggingError, "not present"):
            triage(self.registry, "MPV-ERR-9999")
        with self.assertRaisesRegex(DebuggingError, "identifier"):
            triage(self.registry, "../ERROR_CASES.json")

    def test_protocol_quality_gate_rejects_incomplete_fixed(self):
        sample = copy.deepcopy(self.registry["cases"][0])
        sample["diagnostic_tests"] = []
        root = self.make_root([sample])
        # Valid in stage-4 registry structure, but incomplete for stage-5 debugging.
        validate_registry(json.loads((root / PATH).read_text()))
        with self.assertRaisesRegex(DebuggingError, "fixed status requires"):
            validate_process(root)

    def test_protocol_quality_gate_rejects_bad_reopened(self):
        sample = copy.deepcopy(self.registry["cases"][0])
        sample["status"] = "REOPENED"
        root = self.make_root([sample])
        with self.assertRaisesRegex(DebuggingError, "reopening needs"):
            validate_process(root)

    def test_protocol_document_is_required(self):
        root = self.make_root()
        (root / PROTOCOL).unlink()
        with self.assertRaisesRegex(DebuggingError, "protocol"):
            validate_process(root)

    def test_checkpoint_is_required(self):
        root = self.make_root()
        (root / STATE_PATH).unlink()
        with self.assertRaises(ValueError):
            validate_process(root)

    def test_real_registry_can_be_nonempty(self):
        self.assertGreaterEqual(len(self.registry["cases"]), 2)
        self.assertGreaterEqual(len(validate_process(ROOT)["cases"]), 2)

    def test_validate_cli_read_only(self):
        self.assertEqual(0, main(["validate", "--root", str(ROOT)]))
        self.assertEqual(1, main(["validate", "--root", str(ROOT), "--case-id", "MPV-ERR-0001"]))

    def test_cli_triage_and_match(self):
        case = self.registry["cases"][0]
        self.assertEqual(0, main(["triage", "--root", str(ROOT),
                                  "--case-id", case["id"], "--consecutive-failures", "2"]))
        self.assertEqual(0, main(["match", "--root", str(ROOT),
                                  "--category", case["category"],
                                  "--component", case["component"],
                                  "--signature", case["signature"]]))

    def test_cli_rejects_missing_identity(self):
        self.assertEqual(1, main(["match", "--root", str(ROOT)]))
        self.assertEqual(1, main(["triage", "--root", str(ROOT)]))

if __name__ == "__main__":
    unittest.main()
