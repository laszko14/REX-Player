"""Regression tests: fail closed for wrong ref, ancestry, context and source files."""
import subprocess
import tempfile
from pathlib import Path
import unittest
import contextlib
import io

from mpvrex_preflight import REPO, REQUIRED, PreflightError, check_checkout


class CheckoutTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.branch = "docs/mpvrex-v32-fixture"
        self.git("init", "-q", "-b", self.branch)
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "test@example.invalid")
        self.git("remote", "add", "origin", f"https://github.com/{REPO}.git")
        for path in REQUIRED:
            f = self.root / path
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(f"# {path}\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "base")
        self.base = self.git("rev-parse", "HEAD")
        (self.root / "docs/AI_HANDOFF.md").write_text("# updated\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "next")
        self.head = self.git("rev-parse", "HEAD")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def check(self, **kw):
        args = dict(expected_branch=self.branch, expected_sha=self.head, base_sha=self.base)
        args.update(kw)
        return check_checkout(self.root, **args)

    def test_good_descendant(self):
        result = self.check()
        self.assertEqual(self.head, result["sha"])
        self.assertEqual(set(REQUIRED), set(result["files"]))

    def test_stale_head(self):
        with self.assertRaisesRegex(PreflightError, "expected SHA"):
            self.check(expected_sha=self.base)

    def test_master_denied(self):
        with self.assertRaisesRegex(PreflightError, "outside the approved"):
            self.check(expected_branch="master")

    def test_branch_mismatch(self):
        with self.assertRaisesRegex(PreflightError, "branch differs"):
            self.check(expected_branch="docs/mpvrex-v32-other")

    def test_unrelated_base(self):
        with self.assertRaises(PreflightError):
            self.check(base_sha="f"*40)

    def test_missing_tracked_doc(self):
        self.git("rm", "docs/ai/STARTUP_PREFLIGHT.md")
        self.git("commit", "-qm", "removed required doc")
        with self.assertRaises(PreflightError):
            self.check(expected_sha=self.git("rev-parse", "HEAD"))

    def test_dirty_tree(self):
        with (self.root / "AGENTS.md").open("a", encoding="utf-8") as f:
            f.write("dirty\n")
        with self.assertRaisesRegex(PreflightError, "modifications"):
            self.check()

    def test_wrong_remote(self):
        self.git("remote", "set-url", "origin", "https://github.com/unrelated/repo.git")
        with self.assertRaisesRegex(PreflightError, "Origin"):
            self.check()

    def test_detached_local_checkout(self):
        self.git("checkout", "-q", "--detach")
        with self.assertRaisesRegex(PreflightError, "Detached HEAD"):
            self.check()

    def test_detached_verified_ci(self):
        self.git("checkout", "-q", "--detach")
        env = {"GITHUB_REPOSITORY": REPO, "GITHUB_EVENT_NAME": "push",
               "GITHUB_REF": f"refs/heads/{self.branch}", "GITHUB_SHA": self.head}
        self.assertEqual(self.head, self.check(ci_env=env)["sha"])

    def test_wrong_ci_event_branch(self):
        self.git("checkout", "-q", "--detach")
        env = {"GITHUB_REPOSITORY": REPO, "GITHUB_EVENT_NAME": "push",
               "GITHUB_REF": "refs/heads/master", "GITHUB_SHA": self.head}
        with self.assertRaisesRegex(PreflightError, "GITHUB_REF"):
            self.check(ci_env=env)

    def test_cli_forbids_overriding_approved_base(self):
        from mpvrex_preflight import main
        args = ["--expected-branch", self.branch, "--expected-sha", self.head,
                "--base-sha", self.base, "--root", str(self.root)]
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                main(args)
        self.assertEqual(2, raised.exception.code)

    def test_cli_requires_expected_head(self):
        from mpvrex_preflight import main
        args = ["--expected-branch", self.branch, "--root", str(self.root)]
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                main(args)
        self.assertEqual(2, raised.exception.code)

    def test_ci_requires_sha(self):
        self.git("checkout", "-q", "--detach")
        with self.assertRaisesRegex(PreflightError, "--expected-sha"):
            self.check(expected_sha=None, ci_env={})

class MarkdownSourceTests(unittest.TestCase):
    def test_stage2_documents_have_real_markdown_code_markup(self):
        repo = Path(__file__).resolve().parents[2]
        escaped = chr(92) + chr(96)
        for path in ("docs/AI_HANDOFF.md", "docs/ai/STARTUP_PREFLIGHT.md"):
            with self.subTest(path=path):
                text = (repo / path).read_text(encoding="utf-8")
                self.assertNotIn(escaped, text)


if __name__ == "__main__":
    unittest.main()
