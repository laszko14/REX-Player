"""Stage 8 fail-closed tests: explicit human authorization and isolated APK job."""
import copy
from pathlib import Path
import unittest

import yaml

from mpvrex_apk import (
    APPROVAL, APKError, BASE, REPO, SIGN_IF, parse_package,
    validate_request, validate_workflow, main,
)

ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 40


class SafeAPKTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = yaml.safe_load(
            (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8"))

    def request(self):
        env = {
            "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REPOSITORY": REPO,
            "GITHUB_ACTOR": "laszko14",
            "GITHUB_REF": "refs/heads/docs/mpvrex-v32-stage08-safe-apk-20261110",
            "GITHUB_SHA": SHA, "SOURCE_SHA": SHA, "APPROVAL": APPROVAL,
            "VERSION_CODE_BASE": "100057", "VERSION_NAME": "v56.1",
        }
        git = {
            "head": SHA, "origin": "https://github.com/laszko14/REX-Player.git",
            "shallow": "false", "ancestry": True, "clean": True,
            "changed_files": ["AGENTS.md", ".github/workflows/build.yml",
                              "docs/ai/SAFE_APK.md"],
        }
        return env, git

    def test_authorized_manual_request(self):
        e, g = self.request()
        self.assertEqual(1000572, validate_request(e, g)["version_code"])

    def test_push_cannot_sign(self):
        e, g = self.request()
        e["GITHUB_EVENT_NAME"] = "push"
        with self.assertRaisesRegex(APKError, "manual"):
            validate_request(e, g)

    def test_pr_cannot_sign(self):
        e, g = self.request()
        e["GITHUB_EVENT_NAME"] = "pull_request"
        with self.assertRaises(APKError):
            validate_request(e, g)

    def test_unapproved_actor_cannot_sign(self):
        e, g = self.request()
        e["GITHUB_ACTOR"] = "attacker"
        with self.assertRaisesRegex(APKError, "Owner"):
            validate_request(e, g)

    def test_wrong_branch_cannot_sign(self):
        e, g = self.request()
        e["GITHUB_REF"] = "refs/heads/master"
        with self.assertRaisesRegex(APKError, "branch"):
            validate_request(e, g)

    def test_wrong_sha_cannot_sign(self):
        e, g = self.request()
        e["SOURCE_SHA"] = "b" * 40
        with self.assertRaisesRegex(APKError, "SHA"):
            validate_request(e, g)

    def test_unapproved_request_cannot_sign(self):
        e, g = self.request()
        e["APPROVAL"] = "yes"
        with self.assertRaisesRegex(APKError, "approval"):
            validate_request(e, g)

    def test_wrong_ancestry_cannot_sign(self):
        e, g = self.request()
        g["ancestry"] = False
        with self.assertRaisesRegex(APKError, "ancestry"):
            validate_request(e, g)

    def test_player_source_change_cannot_sign(self):
        e, g = self.request()
        g["changed_files"].append("app/src/main/AndroidManifest.xml")
        with self.assertRaisesRegex(APKError, "Unreviewed"):
            validate_request(e, g)

    def test_other_workflow_change_cannot_sign(self):
        e, g = self.request()
        g["changed_files"].append(".github/workflows/release.yml")
        with self.assertRaisesRegex(APKError, "Unreviewed"):
            validate_request(e, g)

    def test_bad_version_rejected(self):
        e, g = self.request()
        e["VERSION_CODE_BASE"] = "100056"
        with self.assertRaisesRegex(APKError, "exceed"):
            validate_request(e, g)
        e["VERSION_CODE_BASE"] = "100057"
        e["VERSION_NAME"] = "v57"
        with self.assertRaisesRegex(APKError, "version name"):
            validate_request(e, g)

    def test_parse_package(self):
        result = parse_package("package: name='xyz.mpv.rex.debug' versionCode='1000572' versionName='v56.1'\n")
        self.assertEqual("1000572", result["versionCode"])

    def test_workflow_manual_guard_valid(self):
        self.assertTrue(validate_workflow(self.workflow))
        self.assertEqual(0, main(["workflow", "--root", str(ROOT)]))

    def test_automatic_signing_guard_rejected(self):
        wf = copy.deepcopy(self.workflow)
        wf["jobs"]["signed_v56_apk"]["if"] = "${{ github.event_name == 'push' }}"
        with self.assertRaisesRegex(APKError, "guard"):
            validate_workflow(wf)

    def test_missing_manual_inputs_rejected(self):
        wf = copy.deepcopy(self.workflow)
        wf.get("on", wf.get(True))["workflow_dispatch"]["inputs"].pop("approval")
        with self.assertRaisesRegex(APKError, "inputs"):
            validate_workflow(wf)

    def test_missing_source_dependency_rejected(self):
        wf = copy.deepcopy(self.workflow)
        wf["jobs"]["signed_v56_apk"]["needs"] = []
        with self.assertRaisesRegex(APKError, "SOURCE"):
            validate_workflow(wf)

    def test_write_permission_rejected(self):
        wf = copy.deepcopy(self.workflow)
        wf["jobs"]["signed_v56_apk"]["permissions"]["contents"] = "write"
        with self.assertRaisesRegex(APKError, "write"):
            validate_workflow(wf)

    def test_secrets_before_tests_rejected(self):
        wf = copy.deepcopy(self.workflow)
        steps = wf["jobs"]["signed_v56_apk"]["steps"]
        secret = next(i for i,s in enumerate(steps) if "MPV_ALPHA_KEYSTORE_BASE64" in str(s.get("env", {})))
        tests = next(i for i,s in enumerate(steps) if ":app:testDebugUnitTest" in str(s.get("run","")))
        steps[secret], steps[tests] = steps[tests], steps[secret]
        with self.assertRaisesRegex(APKError, "order"):
            validate_workflow(wf)

    def test_tag_push_rejected(self):
        wf = copy.deepcopy(self.workflow)
        wf["jobs"]["signed_v56_apk"]["steps"][-2]["run"] += "\ngit push origin refs/tags/unsafe"
        with self.assertRaisesRegex(APKError, "push"):
            validate_workflow(wf)


if __name__ == "__main__":
    unittest.main()
