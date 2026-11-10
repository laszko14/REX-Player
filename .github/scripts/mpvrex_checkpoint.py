#!/usr/bin/env python3
"""MPVRex checkpoint registry: offline validation, read-only resume, safe local save.

No GitHub API calls, CI result guessing, commits or pushes. Live HEAD and CI
must be independently verified via the authorized GitHub connector.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

REPO = "laszko14/REX-Player"
BASE_BRANCH = "build/player-transparency-v56"
BASE_SHA = "74b059ab0d2c5492451c42f88ff993128a2e45c9"
STATE_PATH = Path("docs/ai/CHECKPOINT_STATE.json")
SHA40 = re.compile(r"[0-9a-f]{40}\Z")
BRANCH = re.compile(r"docs/mpvrex-v32-[a-z0-9][a-z0-9._-]*\Z")
ACTIVE_STATUSES = {"IN_PROGRESS", "CI_PENDING", "CI_BLOCKED", "NOT_TESTED"}
REQUIRED_JOBS = {"YAML, Python i Lua", "Android - build, testy, lint"}
ORIGIN = re.compile(
    r"(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)"
    r"laszko14/REX-Player(?:\.git)?/?\Z", flags=re.IGNORECASE
)


class CheckpointError(ValueError):
    """Checkpoint is incomplete, conflicting or unsafe."""


def fail_if(condition: bool, reason: str) -> None:
    if condition:
        raise CheckpointError(reason)


def _text(value: object, name: str, maximum: int = 500) -> str:
    fail_if(not isinstance(value, str) or not value.strip() or len(value) > maximum,
            f"{name} must be a nonempty, bounded string")
    fail_if(any(ord(c) < 32 for c in value), f"{name} contains control characters")
    return value


def _sha(value: object, name: str) -> str:
    fail_if(not isinstance(value, str) or not SHA40.fullmatch(value), f"{name} must be a full SHA")
    return value


def validate(data: object) -> dict:
    """Validate claims structurally; a saved run ID is NOT live CI evidence."""
    fail_if(not isinstance(data, dict), "Expected a JSON object")
    required = {"schema_version", "project", "repository", "approved_baseline", "completed", "active"}
    fail_if(set(data) != required, "Unexpected or missing top-level keys")
    fail_if(type(data["schema_version"]) is not int or data["schema_version"] != 1,
            "Unsupported schema version")
    fail_if(data["project"] != "MPVRex / MPV Alpha" or data["repository"] != REPO,
            "Wrong project or repository")
    baseline = data["approved_baseline"]
    fail_if(not isinstance(baseline, dict) or set(baseline) != {"branch", "sha"},
            "Malformed baseline")
    fail_if(baseline["branch"] != BASE_BRANCH or baseline["sha"] != BASE_SHA,
            "Baseline must be exact owner-approved v56 commit")
    completed = data["completed"]
    fail_if(not isinstance(completed, list) or not completed or len(completed) > 10,
            "Completed stages must be an ordered nonempty list <= 10")
    unique_branches: set[str] = set()
    unique_shas: set[str] = set()
    for number, item in enumerate(completed, 1):
        fail_if(not isinstance(item, dict) or set(item) !=
                {"stage", "branch", "sha", "status", "evidence"},
                f"Completed stage {number}: wrong schema")
        fail_if(type(item["stage"]) is not int or item["stage"] != number,
                f"Completed stages must be sequential: {number}")
        fail_if(not isinstance(item["branch"], str) or not BRANCH.fullmatch(item["branch"]),
                "Completed branch must follow MPVRex naming policy")
        sha = _sha(item["sha"], "completed.sha")
        fail_if(item["branch"] in unique_branches or sha in unique_shas,
                "Duplicate completed stage branch/SHA")
        unique_branches.add(item["branch"])
        unique_shas.add(sha)
        fail_if(item["status"] != "CODE_VERIFIED", "Historical completion must be CODE_VERIFIED")
        evidence = item["evidence"]
        fail_if(not isinstance(evidence, dict) or set(evidence) !=
                {"run_id", "attempt", "sha", "result", "required_jobs"},
                "Invalid CI evidence schema")
        fail_if(type(evidence["run_id"]) is not int or evidence["run_id"] <= 0 or
                type(evidence["attempt"]) is not int or evidence["attempt"] <= 0,
                "Invalid workflow run/attempt")
        fail_if(evidence["sha"] != sha or evidence["result"] != "success",
                "CI evidence must match saved stage SHA and success conclusion")
        jobs = evidence["required_jobs"]
        fail_if(not isinstance(jobs, list) or len(jobs) != 2 or
                set(jobs) != REQUIRED_JOBS, "Both exact source and Android job names required")
    active = data["active"]
    fail_if(not isinstance(active, dict) or set(active) !=
            {"stage", "branch", "status", "parent_sha", "next_action", "summary"},
            "Invalid active stage schema")
    fail_if(type(active["stage"]) is not int or active["stage"] != len(completed) + 1 or
            active["stage"] > 10, "Active stage must immediately follow completed stages")
    fail_if(not isinstance(active["branch"], str) or not BRANCH.fullmatch(active["branch"]) or
            active["branch"] in unique_branches, "Invalid/duplicate active branch")
    fail_if(active["status"] not in ACTIVE_STATUSES, "Cannot claim unverified active-stage success")
    fail_if(_sha(active["parent_sha"], "active.parent_sha") != completed[-1]["sha"],
            "Active parent must be previous verified terminal SHA")
    _text(active["next_action"], "next_action")
    _text(active["summary"], "summary")
    return data


def load(root: Path) -> dict:
    path = root / STATE_PATH
    fail_if(path.is_symlink(), "Checkpoint file must not be a symlink")
    fail_if(not path.is_file() or path.stat().st_size > 32768,
            "Checkpoint JSON missing or too large")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise CheckpointError("Invalid UTF-8 / checkpoint JSON") from exc
    return validate(data)


def git(root: Path, *args: str) -> str:
    proc = subprocess.run(["git", *args], cwd=root, capture_output=True,
                          text=True, check=False)
    if proc.returncode:
        raise CheckpointError(f"Git assertion failed: {args[0]} (exit {proc.returncode})")
    return proc.stdout.strip()


def assert_writable_checkout(root: Path, data: dict, expected_head: str) -> None:
    _sha(expected_head, "expected-head")
    fail_if(git(root, "rev-parse", "--show-toplevel") != str(root),
            "Run at repository root")
    fail_if(git(root, "rev-parse", "HEAD") != expected_head,
            "Stale expected HEAD; re-read live GitHub before saving")
    fail_if(git(root, "symbolic-ref", "--short", "-q", "HEAD") != data["active"]["branch"],
            "Attempt to write checkpoint to wrong/detached branch")
    fail_if(not ORIGIN.fullmatch(git(root, "remote", "get-url", "origin")),
            "Origin mismatch")
    fail_if(git(root, "rev-parse", "--is-shallow-repository") != "false",
            "Full Git history required")
    ancestry = subprocess.run(["git", "merge-base", "--is-ancestor", BASE_SHA, "HEAD"],
                              cwd=root, capture_output=True, check=False)
    fail_if(ancestry.returncode != 0, "Current branch is not a v56 descendant")
    # The checkpoint itself must not have already been modified locally.
    fail_if(bool(git(root, "status", "--porcelain", "--", str(STATE_PATH))),
            "Checkpoint has uncommitted changes; inspect before overwriting")


def record(root: Path, *, expected_head: str, stage: int, status: str,
           next_action: str, summary: str) -> dict:
    data = load(root)
    fail_if(type(stage) is not int or stage != data["active"]["stage"],
            "Cannot write another stage or overwrite verified history")
    fail_if(status not in ACTIVE_STATUSES, "Recording CODE_VERIFIED needs external live CI proof")
    _text(next_action, "next_action")
    _text(summary, "summary")
    assert_writable_checkout(root, data, expected_head)
    updated = copy.deepcopy(data)
    updated["active"].update(status=status, next_action=next_action, summary=summary)
    validate(updated)
    serialized = json.dumps(updated, ensure_ascii=False, indent=2) + "\n"
    target = root / STATE_PATH
    descriptor, temporary = tempfile.mkstemp(prefix=".checkpoint-", dir=target.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as out:
            out.write(serialized)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return updated


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "show", "record"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--expected-head")
    parser.add_argument("--stage", type=int)
    parser.add_argument("--status")
    parser.add_argument("--next-action")
    parser.add_argument("--summary")
    parser.add_argument("--write", action="store_true",
                        help="Explicitly permit local checkpoint update, never a Git push")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        if args.action == "record":
            if (not args.write or args.expected_head is None or args.stage is None
                    or args.status is None or args.next_action is None or args.summary is None):
                raise CheckpointError("record requires --write and all stage/status/HEAD/action fields")
            data = record(root, expected_head=args.expected_head, stage=args.stage,
                          status=args.status, next_action=args.next_action,
                          summary=args.summary)
            print("CHECKPOINT_SAVED_LOCAL (not committed, not pushed)")
        else:
            if args.write or any(x is not None for x in
                (args.expected_head, args.stage, args.status, args.next_action, args.summary)):
                raise CheckpointError("validate/show are read-only and do not accept record flags")
            data = load(root)
            print("CHECKPOINT_SCHEMA_PASS (offline structure only; NOT evidence of CI)")
        if args.action in {"show", "record"}:
            last = data["completed"][-1]
            active = data["active"]
            print(f"Last recorded: stage {last['stage']}, {last['sha']}")
            print(f"Active: stage {active['stage']} [{active['status']}] {active['branch']}")
            print(f"Next: {active['next_action']}")
            print("Recheck LIVE branch HEAD and required source/Android CI with GitHub.")
        return 0
    except (OSError, CheckpointError) as exc:
        print(f"CHECKPOINT_FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
