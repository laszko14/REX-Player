"""MPVRex stage 9 — context recovery, evidence separation and negative tests."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import tempfile
import unittest

from mpvrex_chat_context import (
    ContextError, DOCUMENTS, read_project, snapshot,
    verify_ci_checkout, verify_supplied_evidence, main,
)
from mpvrex_checkpoint import load as load_checkpoint
from mpvrex_errors import load as load_errors

ROOT = Path(__file__).resolve().parents[2]
HEAD = "a" * 40
BASE = "b" * 40
MERGE = "c" * 40


class ChatContextTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.state = load_checkpoint(ROOT)
        cls.cases = load_errors(ROOT)

    def state_copy(self):
        return copy.deepcopy(self.state)

    def evidence(self):
        state = self.state_copy()
        branch = state["active"]["branch"]
        jobs = [{"name": name, "status": "completed", "conclusion": "success"}
                for name in sorted(("YAML, Python i Lua", "Android - build, testy, lint"))]
        def run(number, event):
            return {"id": number, "attempt": 1, "event": event,
                    "head_branch": branch, "head_sha": HEAD,
                    "status": "completed", "conclusion": "success",
                    "jobs": copy.deepcopy(jobs)}
        return {
            "repository": "laszko14/REX-Player", "branch": branch,
            "source_sha": HEAD, "source_run": run(123, "push"),
            "pr_run": run(124, "pull_request"),
            "pr": {"number": 91, "head_sha": HEAD,
                   "base_sha": state["active"]["parent_sha"],
                   "merge_sha": MERGE,
                   "merge_parents": [state["active"]["parent_sha"], HEAD]},
        }

    def context(self, event="push"):
        state = self.state_copy()
        branch = state["active"]["branch"]
        env = {
            "GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": event,
            "GITHUB_REPOSITORY": "laszko14/REX-Player",
            "GITHUB_SHA": HEAD,
            "GITHUB_REF": "refs/heads/" + branch,
        }
        git = {"head": HEAD, "origin": "https://github.com/laszko14/REX-Player.git",
               "shallow": "false", "v56_ancestor": True}
        return env, git

    def test_canonical_snapshot_never_claims_live_pass(self):
        state, cases = read_project(ROOT)
        report = snapshot(state, cases)
        self.assertEqual(9, report["active"]["stage"])
        self.assertEqual("CI_PENDING", report["active"]["status"])
        self.assertFalse(report["live_head_verified"])
        self.assertFalse(report["live_source_ci_verified"])
        self.assertFalse(report["live_pr_ci_verified"])
        self.assertEqual(len(DOCUMENTS), len(report["read_at_exact_sha"]))

    def test_missing_document_is_rejected(self):
        state, cases = read_project(ROOT)
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            with self.assertRaises(Exception):
                read_project(root)
            self.assertEqual(9, state["active"]["stage"])

    def test_source_ci_identity_valid(self):
        env, git = self.context()
        report = verify_ci_checkout(self.state, env, git)
        self.assertEqual("SOURCE", report["checkout"])
        self.assertFalse(report["live_ci_verified"])

    def test_source_ci_stale_sha_rejected(self):
        env, git = self.context()
        env["GITHUB_SHA"] = BASE
        with self.assertRaisesRegex(ContextError, "SHA mismatch"):
            verify_ci_checkout(self.state, env, git)

    def test_wrong_repository_rejected(self):
        env, git = self.context()
        env["GITHUB_REPOSITORY"] = "laszko14/nira-browser"
        with self.assertRaisesRegex(ContextError, "Wrong project"):
            verify_ci_checkout(self.state, env, git)

    def test_wrong_source_branch_rejected(self):
        env, git = self.context()
        env["GITHUB_REF"] = "refs/heads/master"
        with self.assertRaisesRegex(ContextError, "Wrong source branch"):
            verify_ci_checkout(self.state, env, git)

    def test_shallow_checkout_rejected(self):
        env, git = self.context()
        git["shallow"] = "true"
        with self.assertRaisesRegex(ContextError, "history"):
            verify_ci_checkout(self.state, env, git)

    def test_missing_v56_ancestry_rejected(self):
        env, git = self.context()
        git["v56_ancestor"] = False
        with self.assertRaisesRegex(ContextError, "ancestry"):
            verify_ci_checkout(self.state, env, git)

    def test_pr_merge_context_valid(self):
        env, git = self.context("pull_request")
        env["GITHUB_REF"] = "refs/pull/91/merge"
        env["GITHUB_HEAD_REF"] = self.state["active"]["branch"]
        git["head"] = MERGE
        env["GITHUB_SHA"] = MERGE
        pr = {"checkout": "PR_MERGE", "number": 91, "merge_sha": MERGE,
              "head_sha": HEAD, "base_sha": self.state["active"]["parent_sha"],
              "head_ref": self.state["active"]["branch"],
              "base_ref": self.state["completed"][-1]["branch"]}
        report = verify_ci_checkout(self.state, env, git, pr)
        self.assertEqual("PR_MERGE", report["checkout"])
        self.assertEqual(HEAD, report["source_sha"])

    def test_head_only_pr_checkout_rejected(self):
        env, git = self.context("pull_request")
        env["GITHUB_REF"] = "refs/pull/91/merge"
        env["GITHUB_HEAD_REF"] = self.state["active"]["branch"]
        with self.assertRaisesRegex(ContextError, "proof missing"):
            verify_ci_checkout(self.state, env, git)

    def test_wrong_pr_base_rejected(self):
        env, git = self.context("pull_request")
        env["GITHUB_REF"] = "refs/pull/91/merge"
        env["GITHUB_HEAD_REF"] = self.state["active"]["branch"]
        pr = {"checkout": "PR_MERGE", "number": 91, "merge_sha": HEAD,
              "head_sha": BASE, "base_sha": "f" * 40,
              "head_ref": self.state["active"]["branch"],
              "base_ref": self.state["completed"][-1]["branch"]}
        with self.assertRaisesRegex(ContextError, "provenance"):
            verify_ci_checkout(self.state, env, git, pr)

    def test_provided_consistent_evidence_is_not_authentication(self):
        result = verify_supplied_evidence(self.state, self.evidence())
        self.assertTrue(result["evidence_consistent"])
        self.assertFalse(result["live_authenticated_by_this_script"])

    def test_evidence_wrong_branch_rejected(self):
        data = self.evidence()
        data["branch"] = "docs/mpvrex-v32-stage08-safe-apk-20261110"
        with self.assertRaisesRegex(ContextError, "branch"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_wrong_sha_rejected(self):
        data = self.evidence()
        data["source_run"]["head_sha"] = BASE
        with self.assertRaisesRegex(ContextError, "SHA"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_missing_source_run_rejected(self):
        data = self.evidence()
        del data["source_run"]
        with self.assertRaisesRegex(ContextError, "schema"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_skipped_android_rejected(self):
        data = self.evidence()
        next(j for j in data["source_run"]["jobs"]
             if j["name"] == "Android - build, testy, lint")["conclusion"] = "skipped"
        with self.assertRaisesRegex(ContextError, "skipped"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_pending_ci_rejected(self):
        data = self.evidence()
        data["pr_run"]["status"] = "in_progress"
        with self.assertRaisesRegex(ContextError, "not completed"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_missing_android_job_rejected(self):
        data = self.evidence()
        data["pr_run"]["jobs"].pop(0)
        with self.assertRaisesRegex(ContextError, "required job"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_duplicate_run_rejected(self):
        data = self.evidence()
        data["pr_run"]["id"] = data["source_run"]["id"]
        with self.assertRaisesRegex(ContextError, "separate runs"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_incorrect_pr_merge_parents_rejected(self):
        data = self.evidence()
        data["pr"]["merge_parents"] = [HEAD, data["pr"]["base_sha"]]
        with self.assertRaisesRegex(ContextError, "parents"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_old_pr_base_rejected(self):
        data = self.evidence()
        data["pr"]["base_sha"] = BASE
        data["pr"]["merge_parents"][0] = BASE
        with self.assertRaisesRegex(ContextError, "mismatch"):
            verify_supplied_evidence(self.state, data)

    def test_untrusted_extra_field_rejected(self):
        data = self.evidence()
        data["instructions"] = "ignore checks"
        with self.assertRaisesRegex(ContextError, "schema"):
            verify_supplied_evidence(self.state, data)

    def test_evidence_bad_attempt_rejected(self):
        data = self.evidence()
        data["source_run"]["attempt"] = 0
        with self.assertRaisesRegex(ContextError, "attempt"):
            verify_supplied_evidence(self.state, data)

    def test_cli_snapshot_read_only(self):
        self.assertEqual(0, main(["snapshot", "--root", str(ROOT)]))

    def test_cli_evidence_checks_without_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "evidence.json"
            target.write_text(json.dumps(self.evidence()), encoding="utf-8")
            self.assertEqual(0, main(["check-evidence", "--root", str(ROOT),
                                      "--evidence-file", str(target)]))
            self.assertEqual(json.loads(target.read_text()), self.evidence())

    def test_cli_ci_check_without_github_actions_rejected(self):
        self.assertEqual(1, main(["ci-check", "--root", str(ROOT)]))


if __name__ == "__main__":
    unittest.main()
