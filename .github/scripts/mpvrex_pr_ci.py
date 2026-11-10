#!/usr/bin/env python3
"""MPVRex stage 7: prove PR merge checkout provenance; validate SOURCE/PR wiring.

Read-only. This does NOT verify GitHub API job results, change refs, build APKs
or grant permissions. The PR verifier intentionally rejects head-only checkout.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


from mpvrex_checkpoint import BASE_SHA, ORIGIN

REPOSITORY = "laszko14/REX-Player"
V56_BRANCH = "build/player-transparency-v56"
DOCS_PATTERN = "docs/mpvrex-v32-*"
PR_CONDITION = "$" + "{{ github.event_name == 'pull_request' && github.base_ref != 'master' }}"
ANDROID_CONDITION = "$" + "{{ github.event_name == 'pull_request' || (github.event_name == 'push' && startsWith(github.ref, 'refs/heads/docs/mpvrex-v32-')) }}"
REQUIRED_PR_COMMANDS = (
    "mpvrex_pr_ci.py validate --ci",
    "mpvrex_checkpoint.py validate",
    "mpvrex_errors.py validate",
    "mpvrex_debugging.py validate",
    "mpvrex_quality.py validate",
)
REQUIRED_ANDROID_COMMANDS = (":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")
SHA_RE = re.compile(r"[0-9a-f]{40}\Z")
REF_RE = re.compile(r"refs/pull/([1-9][0-9]*)/merge\Z")
DOCS_RE = re.compile(r"docs/mpvrex-v32-[a-z0-9][a-z0-9._-]*\Z")


class PRCheckError(ValueError):
    """PR context/checkout cannot be trusted or required checks were bypassed."""


def ensure(ok: bool, reason: str) -> None:
    if not ok:
        raise PRCheckError(reason)


def is_v56_base(ref: object) -> bool:
    return isinstance(ref, str) and (ref == V56_BRANCH or bool(DOCS_RE.fullmatch(ref)))


def valid_sha(value: object) -> bool:
    return isinstance(value, str) and bool(SHA_RE.fullmatch(value))


def safe_run_git(root: Path, *args: str) -> str:
    process = subprocess.run(["git", *args], cwd=root, capture_output=True,
                             text=True, check=False)
    ensure(process.returncode == 0, f"Git assertion failed: {args[0]} ({process.returncode})")
    return process.stdout.strip()


def is_ancestor(root: Path, ancestor: str, descendant: str) -> bool:
    process = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=root, capture_output=True, text=True, check=False,
    )
    ensure(process.returncode in (0, 1), "Git ancestry check failed")
    return process.returncode == 0


def verify_context(env: dict, event: dict, git, ancestor) -> dict:
    """Pure testable PR invariant: event base and head must match merge parents."""
    ensure(isinstance(event, dict), "Invalid GitHub event")
    ensure(env.get("GITHUB_EVENT_NAME") == "pull_request", "Not a pull_request event")
    ensure(env.get("GITHUB_REPOSITORY") == REPOSITORY, "Wrong repository")
    pr = event.get("pull_request")
    ensure(isinstance(pr, dict), "Missing pull_request payload")
    number = event.get("number")
    ensure(type(number) is int and number > 0 and pr.get("number") == number,
           "PR number mismatch")
    ensure(env.get("GITHUB_REF") == f"refs/pull/{number}/merge" and
           bool(REF_RE.fullmatch(str(env.get("GITHUB_REF")))), "Must test PR merge ref")
    base = pr.get("base")
    head = pr.get("head")
    ensure(isinstance(base, dict) and isinstance(head, dict), "PR base/head missing")
    base_ref = base.get("ref")
    head_ref = head.get("ref")
    ensure(is_v56_base(base_ref), "PR target is not owner-approved v56 line")
    ensure(isinstance(head_ref, str) and bool(head_ref.strip()) and len(head_ref) <= 255,
           "Missing PR source branch")
    ensure(env.get("GITHUB_BASE_REF") == base_ref and
           env.get("GITHUB_HEAD_REF") == head_ref, "Event and PR refs disagree")
    base_repo = base.get("repo")
    ensure(isinstance(base_repo, dict) and base_repo.get("full_name") == REPOSITORY,
           "PR target repository mismatch")
    merge_sha, base_sha, head_sha = env.get("GITHUB_SHA"), base.get("sha"), head.get("sha")
    ensure(all(valid_sha(v) for v in (merge_sha, base_sha, head_sha)),
           "Missing full PR SHA")
    ensure(git("rev-parse", "HEAD") == merge_sha, "Checkout is not GITHUB_SHA merge commit")
    ensure(git("rev-parse", "HEAD^1") == base_sha, "PR merge base parent mismatch")
    ensure(git("rev-parse", "HEAD^2") == head_sha, "PR merge head parent mismatch")
    ensure(git("rev-parse", "--is-shallow-repository") == "false",
           "Full Git history required for PR")
    ensure(bool(ORIGIN.fullmatch(git("remote", "get-url", "origin"))),
           "Git origin mismatch")
    ensure(ancestor(BASE_SHA, base_sha), "PR target not descended from approved v56")
    ensure(ancestor(BASE_SHA, head_sha), "PR head not descended from approved v56")
    ensure(ancestor(base_sha, merge_sha) and ancestor(head_sha, merge_sha),
           "Merge checkout ancestry incorrect")
    return {
        "event": "pull_request",
        "number": number,
        "base_ref": base_ref,
        "head_ref": head_ref,
        "base_sha": base_sha,
        "head_sha": head_sha,
        "merge_sha": merge_sha,
        "checkout": "PR_MERGE",
        "live_source_ci_verified": False,
        "live_pr_ci_verified": False,
        "reason": "Read-only PR context proof; exact live CI jobs must be checked separately.",
    }


def event_from_path(path: Path) -> dict:
    ensure(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= 2_000_000,
           "GitHub event payload missing or unsafe")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise PRCheckError("Invalid GitHub event JSON") from exc
    ensure(isinstance(value, dict), "Invalid GitHub event object")
    return value


def validate_live_ci(env: dict | None = None, root: Path | None = None) -> dict:
    environment = dict(os.environ if env is None else env)
    directory = Path.cwd() if root is None else root
    location = environment.get("GITHUB_EVENT_PATH")
    ensure(isinstance(location, str) and bool(location), "Missing GitHub event file")
    event = event_from_path(Path(location))
    return verify_context(environment, event,
                          lambda *args: safe_run_git(directory, *args),
                          lambda parent, child: is_ancestor(directory, parent, child))


def validate_workflow(workflow: dict) -> bool:
    """Static fail-closed contract: SOURCE push and PR merge verify different checkouts."""
    ensure(isinstance(workflow, dict) and isinstance(workflow.get("jobs"), dict),
           "Malformed Actions workflow")
    triggers = workflow.get("on", workflow.get(True))
    ensure(isinstance(triggers, dict), "Missing workflow triggers")
    pr_trigger = triggers.get("pull_request")
    ensure(isinstance(pr_trigger, dict) and
           isinstance(pr_trigger.get("branches"), list), "Missing PR base branch trigger")
    pr_branches = pr_trigger["branches"]
    ensure(len(pr_branches) == 3 and set(pr_branches) == {"master", DOCS_PATTERN, V56_BRANCH},
           "PR target filters exclude owner-approved v56 or preserve master")
    ensure("pull_request_target" not in triggers, "Unsafe privileged PR trigger")
    pushes = triggers.get("push")
    ensure(isinstance(pushes, dict) and DOCS_PATTERN in pushes.get("branches", []),
           "Missing v56 SOURCE push coverage")
    jobs = workflow["jobs"]
    source = jobs.get("sources")
    android = jobs.get("android")
    signing = jobs.get("signed_apk")
    ensure(all(isinstance(job, dict) for job in (source, android, signing)),
           "SOURCE, Android or signing job missing")
    ensure(source.get("name") == "YAML, Python i Lua" and
           android.get("name") == "Android - build, testy, lint",
           "Required CI job names changed")
    ensure("if" not in source, "SOURCE job may be bypassed in PR")
    ensure(android.get("needs") == "sources", "Android must depend on SOURCE")
    android_condition = str(android.get("if", "")).strip()
    ensure(android_condition == ANDROID_CONDITION,
           "Android PR or SOURCE CI is gated out")
    for job_name, job in (("sources", source), ("android", android)):
        ensure("continue-on-error" not in job, f"Bypassed {job_name} job")
        steps = job.get("steps")
        ensure(isinstance(steps, list) and bool(steps), f"Missing {job_name} steps")
        ensure(all(isinstance(x, dict) and "continue-on-error" not in x for x in steps),
               f"Bypassed {job_name} step")
        checkouts = [x for x in steps if str(x.get("uses", "")).startswith("actions/checkout@")]
        ensure(len(checkouts) == 1 and checkouts[0].get("with", {}).get("fetch-depth") == 0,
               f"{job_name}: checkout must retain full merge history")
        ensure("ref" not in checkouts[0].get("with", {}),
               f"{job_name}: forced checkout ref bypasses merge SHA")
    source_steps = source["steps"]
    pr_steps = [step for step in source_steps
                if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", ""))]
    ensure(len(pr_steps) == 1, "Exactly one PR provenance step required")
    pr_step = pr_steps[0]
    ensure(str(pr_step.get("if", "")).strip() == PR_CONDITION,
           "PR provenance step must have strict event guard")
    pr_run = str(pr_step.get("run", ""))
    required_lines = ["set -euo pipefail"] + ["python3 .github/scripts/" + cmd
                                                   for cmd in REQUIRED_PR_COMMANDS]
    lines = [line.strip() for line in pr_run.splitlines() if line.strip()]
    ensure(lines == required_lines,
           "PR source quality commands missing, out of order or bypassed")
    android_steps = android["steps"]
    android_pr = [step for step in android_steps
                  if "mpvrex_pr_ci.py validate --ci" in str(step.get("run", ""))]
    ensure(len(android_pr) == 1 and str(android_pr[0].get("if", "")).strip() == PR_CONDITION and
           str(android_pr[0].get("run", "")).strip() ==
           "python3 .github/scripts/mpvrex_pr_ci.py validate --ci",
           "Android PR merge checkout verification missing")
    android_scripts = "\n".join(str(x.get("run", "")) for x in android_steps)
    ensure(all(task in android_scripts for task in
               (":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")),
           "Android build/test/lint gates incomplete")
    source_scripts = "\n".join(str(x.get("run", "")) for x in source_steps)
    ensure("unittest discover" in source_scripts and
           "check_workflows.py" in source_scripts and
           "check_polish.py" in source_scripts,
           "PR source tests or syntax gate missing")
    ensure("refs/heads/master" in str(signing.get("if", "")) and
           "pull_request" not in str(signing.get("if", "")) and
           "pull_request_target" not in str(signing.get("if", "")) and
           "docs/mpvrex-v32-" not in str(signing.get("if", "")),
           "Signing must remain isolated from PR and docs branches")
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "workflow"))
    parser.add_argument("--ci", action="store_true")
    parser.add_argument("--root", default=".")
    args = parser.parse_args(argv)
    try:
        if args.action == "workflow":
            ensure(not args.ci, "Static workflow validation is not live CI")
            path = Path(args.root).resolve() / ".github/workflows/build.yml"
            ensure(path.is_file() and not path.is_symlink(), "Missing build workflow")
            import yaml  # Only SOURCE workflow gate needs this dependency; Android does not.
            validate_workflow(yaml.safe_load(path.read_text(encoding="utf-8")))
            print("PR_WORKFLOW_CONTRACT_PASS (offline, NOT PR CI)")
        else:
            ensure(args.ci, "PR validation requires explicit --ci and trusted context")
            result = validate_live_ci(root=Path(args.root).resolve())
            print("PR_MERGE_CONTEXT_PASS " + json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError) as exc:
        print(f"PR_CI_FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
