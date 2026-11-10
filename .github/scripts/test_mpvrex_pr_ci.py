"""Stage 7 tests: separate SOURCE SHA from pull-request merge SHA, deny bypass."""
from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

import yaml

from mpvrex_checkpoint import BASE_SHA
from mpvrex_pr_ci import (
    DOCS_PATTERN, REPOSITORY, PRCheckError, PR_CONDITION,
    validate_workflow, verify_context, main,
)

ROOT = Path(__file__).resolve().parents[2]
MERGE = "a" * 40
BASE = "b" * 40
HEAD = "c" * 40
BRANCH = "docs/mpvrex-v32-stage06-quality-gates-20261110"


class SourcePRTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = yaml.safe_load(
            (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
        )

    def context(self):
        env = {
            "GITHUB_EVENT_NAME": "pull_request",
            "GITHUB_REPOSITORY": REPOSITORY,
            "GITHUB_REF": "refs/pull/91/merge",
            "GITHUB_SHA": MERGE,
            "GITHUB_BASE_REF": BRANCH,
            "GITHUB_HEAD_REF": "docs/mpvrex-v32-stage07-source-pr-ci-20261110",
        }
        event = {
            "number": 91,
            "pull_request": {
                "number": 91,
                "base": {"ref": BRANCH, "sha": BASE,
                         "repo": {"full_name": REPOSITORY}},
                "head": {"ref": env["GITHUB_HEAD_REF"], "sha": HEAD},
            },
        }
        git = {
            ("rev-parse", "HEAD"): MERGE,
            ("rev-parse", "HEAD^1"): BASE,
            ("rev-parse", "HEAD^2"): HEAD,
            ("rev-parse", "--is-shallow-repository"): "false",
            ("remote", "get-url", "origin"): "https://github.com/laszko14/REX-Player.git",
        }
        return env, event, git

    def check(self, env=None, event=None, mapping=None, ancestor=None):
        e, v, g = self.context()
        if env is not None:
            e.update(env)
        if event is not None:
            v.update(event)
        if mapping is not None:
            g.update(mapping)
        return verify_context(e, v, lambda *args: g[args],
                              (lambda x, y: True) if ancestor is None else ancestor)

    def test_pr_merge_context_valid(self):
        result = self.check()
        self.assertEqual("PR_MERGE", result["checkout"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual(BASE, result["base_sha"])
        self.assertEqual(HEAD, result["head_sha"])
        self.assertFalse(result["live_pr_ci_verified"])

    def test_pr_checkout_head_only_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "Checkout is not"):
            self.check(mapping={("rev-parse", "HEAD"): HEAD})

    def test_pr_merge_first_parent_mismatch_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "base parent mismatch"):
            self.check(mapping={("rev-parse", "HEAD^1"): "d"*40})

    def test_pr_merge_second_parent_mismatch_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "head parent mismatch"):
            self.check(mapping={("rev-parse", "HEAD^2"): "e"*40})

    def test_pr_event_kind_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "pull_request event"):
            self.check(env={"GITHUB_EVENT_NAME": "push"})

    def test_pr_repository_mismatch_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "Wrong repository"):
            self.check(env={"GITHUB_REPOSITORY": "laszko14/nira-browser"})

    def test_pr_master_base_rejected(self):
        e, v, g = self.context()
        v["pull_request"]["base"]["ref"] = "master"
        e["GITHUB_BASE_REF"] = "master"
        with self.assertRaisesRegex(PRCheckError, "not owner-approved"):
            verify_context(e, v, lambda *args: g[args], lambda x, y: True)

    def test_pr_event_mismatched_ref_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "refs disagree"):
            self.check(env={"GITHUB_BASE_REF": "other"})

    def test_pr_number_mismatch_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "Must test PR merge ref"):
            self.check(env={"GITHUB_REF": "refs/pull/92/merge"})

    def test_pr_missing_full_history_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "Full Git history"):
            self.check(mapping={("rev-parse", "--is-shallow-repository"): "true"})

    def test_pr_origin_mismatch_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "origin mismatch"):
            self.check(mapping={("remote", "get-url", "origin"):
                                "https://github.com/evil/other.git"})

    def test_pr_non_v56_ancestry_rejected(self):
        with self.assertRaisesRegex(PRCheckError, "head not descended"):
            self.check(ancestor=lambda parent, child: child != HEAD)

    def test_pr_event_base_repo_mismatch_rejected(self):
        e,v,g=self.context()
        v["pull_request"]["base"]["repo"]["full_name"]="attacker/example"
        with self.assertRaisesRegex(PRCheckError, "target repository mismatch"):
            verify_context(e,v,lambda *args:g[args],lambda x,y:True)

    def test_pr_workflow_valid(self):
        self.assertTrue(validate_workflow(self.workflow))
        self.assertEqual(0, main(["workflow", "--root", str(ROOT)]))

    def changed(self):
        return copy.deepcopy(self.workflow)

    def test_pr_missing_target_filter_rejected(self):
        workflow = self.changed()
        workflow.get("on", workflow.get(True))["pull_request"]["branches"] = ["master"]
        with self.assertRaisesRegex(PRCheckError, "PR target filters"):
            validate_workflow(workflow)

    def test_pr_missing_source_checks_rejected(self):
        workflow = self.changed()
        workflow["jobs"]["sources"]["steps"] = [
            x for x in workflow["jobs"]["sources"]["steps"]
            if "mpvrex_pr_ci.py validate --ci" not in str(x.get("run", ""))
        ]
        with self.assertRaisesRegex(PRCheckError, "one PR provenance step"):
            validate_workflow(workflow)

    def test_pr_step_bypassed_by_condition_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", "")):
                step["if"] = "false && " + str(step.get("if"))
        with self.assertRaisesRegex(PRCheckError, "strict event guard"):
            validate_workflow(workflow)

    def test_pr_missing_module_validation_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", "")):
                step["run"] = str(step["run"]).replace("mpvrex_errors.py validate", "")
        with self.assertRaisesRegex(PRCheckError, "quality commands missing"):
            validate_workflow(workflow)

    def test_pr_missing_android_merge_verification_rejected(self):
        workflow = self.changed()
        workflow["jobs"]["android"]["steps"] = [
            x for x in workflow["jobs"]["android"]["steps"]
            if "mpvrex_pr_ci.py validate --ci" not in str(x.get("run", ""))
        ]
        with self.assertRaisesRegex(PRCheckError, "Android PR merge"):
            validate_workflow(workflow)

    def test_pr_head_ref_forced_checkout_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["sources"]["steps"]:
            if str(step.get("uses", "")).startswith("actions/checkout@"):
                step["with"]["ref"] = "github.event.pull_request.head.sha"
        with self.assertRaisesRegex(PRCheckError, "forced checkout ref"):
            validate_workflow(workflow)

    def test_pr_android_ref_forced_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["android"]["steps"]:
            if str(step.get("uses", "")).startswith("actions/checkout@"):
                step["with"]["ref"] = "github.event.pull_request.head.sha"
        with self.assertRaisesRegex(PRCheckError, "forced checkout ref"):
            validate_workflow(workflow)

    def test_pr_signed_job_wrong_guard_rejected(self):
        workflow = self.changed()
        workflow["jobs"]["signed_apk"]["if"] = "github.event_name == 'pull_request'"
        with self.assertRaisesRegex(PRCheckError, "Signing must remain"):
            validate_workflow(workflow)

    def test_pr_privileged_trigger_rejected(self):
        workflow = self.changed()
        workflow.get("on", workflow.get(True))["pull_request_target"] = {"branches": ["master"]}
        with self.assertRaisesRegex(PRCheckError, "Unsafe privileged"):
            validate_workflow(workflow)

    def test_pr_android_false_condition_rejected(self):
        workflow = self.changed()
        workflow["jobs"]["android"]["if"] = "false && " + str(workflow["jobs"]["android"]["if"])
        with self.assertRaisesRegex(PRCheckError, "Android PR or SOURCE CI"):
            validate_workflow(workflow)

    def test_pr_source_errors_ignored_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["sources"]["steps"]:
            if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", "")):
                step["run"] += "  || true\n"
        with self.assertRaisesRegex(PRCheckError, "commands missing"):
            validate_workflow(workflow)

    def test_pr_android_validation_ignored_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["android"]["steps"]:
            if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", "")):
                step["run"] += " || true"
        with self.assertRaisesRegex(PRCheckError, "Android PR merge"):
            validate_workflow(workflow)

    def test_pr_missing_android_regression_rejected(self):
        workflow = self.changed()
        for step in workflow["jobs"]["android"]["steps"]:
            if ":app:lintDebug" in str(step.get("run", "")):
                step["run"] = str(step["run"]).replace(":app:lintDebug", "")
        with self.assertRaisesRegex(PRCheckError, "build/test/lint"):
            validate_workflow(workflow)

    def test_pr_ci_cannot_fake_context_without_ci_flag(self):
        self.assertEqual(1, main(["validate", "--root", str(ROOT)]))

if __name__ == "__main__":
    unittest.main()
