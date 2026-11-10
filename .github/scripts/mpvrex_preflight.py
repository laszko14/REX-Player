#!/usr/bin/env python3
"""Read-only, fail-closed Git checkout preflight for owner-approved MPV Alpha v56.

Validates a local checked-out commit, NOT live GitHub HEAD or agent comprehension.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = "laszko14/REX-Player"
BASE_SHA = "74b059ab0d2c5492451c42f88ff993128a2e45c9"
BASE_BRANCH = "build/player-transparency-v56"
TASK_PREFIX = "docs/mpvrex-v32-"
REQUIRED = (
    "AGENTS.md", "CONTRIBUTING.md", "docs/AI_HANDOFF.md",
    "docs/ai/STARTUP_PREFLIGHT.md", ".github/scripts/mpvrex_preflight.py",
)
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")
ORIGIN_PATTERN = re.compile(
    r"(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)"
    r"laszko14/REX-Player(?:\.git)?/?", flags=re.IGNORECASE
)
BRANCH_PATTERN = re.compile(r"docs/mpvrex-v32-[a-z0-9][a-z0-9._-]*")


class PreflightError(Exception):
    """A safety prerequisite was not proven."""


def run_git(root: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args], cwd=root, capture_output=True, text=True, check=False
    )
    if proc.returncode:
        # Never copy raw Git stderr; it could include credentials or remote URLs.
        raise PreflightError(f"git {args[0]} exited {proc.returncode}")
    return proc.stdout.strip()


def check_checkout(
    root: Path, *, expected_branch: str, expected_sha: str | None = None,
    base_sha: str = BASE_SHA, ci_env: dict[str, str] | None = None,
) -> dict[str, object]:
    """Fail on a wrong branch, SHA, ancestry, origin, dirty tree or missing docs."""
    if not SHA_PATTERN.fullmatch(base_sha) or (
        expected_sha is not None and not SHA_PATTERN.fullmatch(expected_sha)
    ):
        raise PreflightError("Expected/base SHA must be a full 40-character lowercase hex SHA")
    if expected_branch != BASE_BRANCH and not BRANCH_PATTERN.fullmatch(expected_branch):
        raise PreflightError("Branch is outside the approved v56 line")
    root = Path(root).resolve()
    if run_git(root, "rev-parse", "--show-toplevel") != str(root):
        raise PreflightError("Run the preflight at the repository root")
    if run_git(root, "rev-parse", "--is-shallow-repository") != "false":
        raise PreflightError("Shallow history; fetch full history (fetch-depth: 0)")
    head = run_git(root, "rev-parse", "HEAD")
    if not SHA_PATTERN.fullmatch(head) or (expected_sha and head != expected_sha):
        raise PreflightError("Checkout HEAD differs from full expected SHA")
    if not ORIGIN_PATTERN.fullmatch(run_git(root, "remote", "get-url", "origin")):
        raise PreflightError("Origin is not the approved repository")
    branch_probe = subprocess.run(
        ["git", "symbolic-ref", "--quiet", "--short", "HEAD"],
        cwd=root, capture_output=True, text=True, check=False,
    )
    actual_branch = branch_probe.stdout.strip() if branch_probe.returncode == 0 else None
    if actual_branch not in (None, expected_branch):
        raise PreflightError("Checkout branch differs from expected branch")
    if ci_env is not None:
        if expected_sha is None:
            raise PreflightError("CI must specify --expected-sha")
        context = {
            "GITHUB_REPOSITORY": REPO,
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REF": f"refs/heads/{expected_branch}",
            "GITHUB_SHA": head,
        }
        for key, value in context.items():
            if ci_env.get(key) != value:
                raise PreflightError(f"GitHub Actions context mismatch: {key}")
    elif actual_branch != expected_branch:
        raise PreflightError("Detached HEAD requires verified CI push context")
    run_git(root, "rev-parse", "--verify", f"{base_sha}^{{commit}}")
    ancestry = subprocess.run(
        ["git", "merge-base", "--is-ancestor", base_sha, head],
        cwd=root, capture_output=True, check=False,
    )
    if ancestry.returncode:
        raise PreflightError("Approved v56 commit is not an ancestor of HEAD")
    if run_git(root, "status", "--porcelain", "--untracked-files=normal"):
        raise PreflightError("Checkout has tracked or untracked modifications")
    blobs: dict[str, str] = {}
    for path in REQUIRED:
        blob = run_git(root, "rev-parse", "--verify", f"HEAD:{path}")
        if run_git(root, "cat-file", "-t", blob) != "blob":
            raise PreflightError(f"Required Git object is not a blob: {path}")
        if int(run_git(root, "cat-file", "-s", blob)) <= 0 or not (root / path).is_file():
            raise PreflightError(f"Required document missing or empty: {path}")
        blobs[path] = blob
    return {"branch": expected_branch, "sha": head, "base": base_sha, "files": blobs}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-branch", required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--root", default=".")
    parser.add_argument("--ci", action="store_true")
    args = parser.parse_args(argv)
    try:
        report = check_checkout(
            Path(args.root), expected_branch=args.expected_branch,
            expected_sha=args.expected_sha, base_sha=BASE_SHA,
            ci_env=dict(os.environ) if args.ci else None,
        )
    except (OSError, ValueError, PreflightError) as exc:
        print(f"PREFLIGHT_FAIL: {exc}", file=sys.stderr)
        return 1
    print(f"PREFLIGHT_PASS branch={report['branch']} HEAD={report['sha']}")
    print(f"Approved ancestor: {report['base']}")
    for path, blob in report["files"].items():
        print(f"  {path} blob={blob}")
    print("Checkout only: verify LIVE GitHub HEAD/Actions and read the docs separately.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
