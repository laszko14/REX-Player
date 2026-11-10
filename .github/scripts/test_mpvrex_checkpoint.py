"""MPVRex stage 3 checkpoint positive/negative regression suite."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from mpvrex_checkpoint import (ACTIVE_STATUSES, CheckpointError, load, main,
                               record, validate, STATE_PATH)


class CheckpointTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Real checked-in fixture is validated by source CI; tests mutate copies only.
        cls.canonical = json.loads((Path(__file__).resolve().parents[2] /
                                    STATE_PATH).read_text(encoding="utf-8"))

    def new(self):
        return copy.deepcopy(self.canonical)

    def test_canonical_valid(self):
        self.assertEqual(3, validate(self.new())["active"]["stage"])

    def test_wrong_repo_denied(self):
        v = self.new()
        v["repository"] = "laszko14/nira-browser"
        with self.assertRaisesRegex(CheckpointError, "Wrong project"):
            validate(v)

    def test_baseline_cannot_change(self):
        v = self.new()
        v["approved_baseline"]["sha"] = "f"*40
        with self.assertRaisesRegex(CheckpointError, "Baseline"):
            validate(v)

    def test_duplicate_completed_stage_denied(self):
        v = self.new()
        v["completed"][1]["sha"] = v["completed"][0]["sha"]
        v["completed"][1]["evidence"]["sha"] = v["completed"][0]["sha"]
        with self.assertRaisesRegex(CheckpointError, "Duplicate"):
            validate(v)

    def test_out_of_order_stage_denied(self):
        v = self.new()
        v["completed"][1]["stage"] = 9
        with self.assertRaisesRegex(CheckpointError, "sequential"):
            validate(v)

    def test_forged_ci_sha_denied(self):
        v = self.new()
        v["completed"][1]["evidence"]["sha"] = "f"*40
        with self.assertRaisesRegex(CheckpointError, "CI evidence"):
            validate(v)

    def test_missing_android_gate_denied(self):
        v = self.new()
        v["completed"][0]["evidence"]["required_jobs"] = ["YAML, Python i Lua"]
        with self.assertRaisesRegex(CheckpointError, "source and Android"):
            validate(v)

    def test_no_success_without_external_proof(self):
        v = self.new()
        v["active"]["status"] = "CODE_VERIFIED"
        with self.assertRaisesRegex(CheckpointError, "unverified"):
            validate(v)

    def test_active_stage_must_follow_verified(self):
        v = self.new()
        v["active"]["stage"] = 4
        with self.assertRaisesRegex(CheckpointError, "immediately"):
            validate(v)

    def test_stage_parent_matches_last_sha(self):
        v = self.new()
        v["active"]["parent_sha"] = "f"*40
        with self.assertRaisesRegex(CheckpointError, "previous verified"):
            validate(v)

    def test_control_character_in_free_text(self):
        v = self.new()
        v["active"]["summary"] = "unsafe\nmessage"
        with self.assertRaisesRegex(CheckpointError, "control"):
            validate(v)

    def test_all_pending_statuses_accepted(self):
        for status in ACTIVE_STATUSES:
            with self.subTest(status=status):
                v = self.new()
                v["active"]["status"] = status
                validate(v)

    def test_duplicate_keys_or_unknown_field_denied(self):
        v = self.new()
        v["claimed_pass"] = True
        with self.assertRaisesRegex(CheckpointError, "top-level"):
            validate(v)

    def test_read_only_load(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            self.assertEqual(self.new(), load(Path(td)))

    def test_corrupt_json_denied(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text('{"schema_version":', encoding="utf-8")
            with self.assertRaisesRegex(CheckpointError, "Invalid UTF-8"):
                load(Path(td))

    def test_record_atomic_with_explicit_expected_head(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            with patch("mpvrex_checkpoint.assert_writable_checkout") as guard:
                result = record(root, expected_head="c"*40, stage=3,
                                status="CI_BLOCKED", next_action="Fix missing job.",
                                summary="Reason documented.")
                guard.assert_called_once()
            self.assertEqual("CI_BLOCKED", result["active"]["status"])
            self.assertEqual("CI_BLOCKED", json.loads(path.read_text())["active"]["status"])
            self.assertEqual(self.new()["completed"], result["completed"])

    def test_record_wrong_stage_blocked(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            with self.assertRaisesRegex(CheckpointError, "another stage"):
                record(root, expected_head="c"*40, stage=4, status="CI_PENDING",
                       next_action="check CI", summary="stage 4 premature")
            self.assertEqual(self.new(), json.loads(path.read_text()))

    def test_record_fails_without_write_switch(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            self.assertEqual(1, main(["record", "--root", td]))

    def test_validate_cli_rejects_write_flags(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            self.assertEqual(1, main(["validate", "--root", td, "--write"]))

    def test_record_refuses_unverified_completed_status(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / STATE_PATH
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(self.new()), encoding="utf-8")
            with self.assertRaisesRegex(CheckpointError, "external live CI"):
                record(root, expected_head="c"*40, stage=3, status="CODE_VERIFIED",
                       next_action="next stage", summary="not actually verified")

    def test_historical_handoff_preserved_as_archive(self):
        archive = (Path(__file__).resolve().parents[2] /
                   "docs/ai/archive/AI_HANDOFF_STAGE1_2.md").read_text(encoding="utf-8")
        self.assertIn("42728386046", archive)
        self.assertIn("42732711851", archive)
        self.assertIn("docs/mpvrex-v32-stage02-preflight-20261110", archive)

    def test_state_file_does_not_contain_secrets(self):
        s = (Path(__file__).resolve().parents[2] / STATE_PATH).read_text()
        self.assertNotIn("ghp_", s)
        self.assertNotIn("github_pat_", s)
        self.assertNotIn("-----BEGIN PRIVATE KEY-----", s)


if __name__ == "__main__":
    unittest.main()
