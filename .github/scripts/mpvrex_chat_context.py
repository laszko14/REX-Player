#!/usr/bin/env python3
"""MPVRex V3.2 stage 9: read-only ChatGPT resume and checkout-evidence checks.

No network, GitHub token, secrets, writes, automatic agent memory, or CI claims.
Use the direct GitHub connector to retrieve live branch, runs, jobs and PR data.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from mpvrex_checkpoint import BASE_SHA, ORIGIN, REQUIRED_JOBS, REPO, load as load_checkpoint
from mpvrex_errors import load as load_errors

SHA = re.compile(r"[0-9a-f]{40}\Z")
PR_REF = re.compile(r"refs/pull/([1-9][0-9]*)/merge\Z")
DOCUMENTS = (
    "AGENTS.md",
    "CONTRIBUTING.md",
    "docs/AI_HANDOFF.md",
    "docs/ai/CHECKPOINT_STATE.json",
    "docs/ai/CHECKPOINT_POLICY.md",
    "docs/ai/STARTUP_PREFLIGHT.md",
    "docs/ai/ERROR_LEARNING.md",
    "docs/ai/DEBUGGING_PROTOCOL.md",
    "docs/ai/QUALITY_GATES.md",
    "docs/ai/SOURCE_PR_CI.md",
    "docs/ai/SAFE_APK.md",
    "docs/ai/CHATGPT_INTEGRATION.md",
)


class ContextError(ValueError):
    """Missing, stale, cross-project or unverifiable context."""


def ensure(ok: bool, why: str) -> None:
    if not ok:
        raise ContextError(why)


def is_sha(value: object) -> bool:
    return isinstance(value, str) and bool(SHA.fullmatch(value))


def read_project(root: Path) -> tuple[dict, dict]:
    """Read one checkout only; do not claim any live GitHub verification."""
    state = load_checkpoint(root)
    cases = load_errors(root)
    for relative in DOCUMENTS:
        path = root / relative
        ensure(path.is_file() and not path.is_symlink()
               and 0 < path.stat().st_size < 250_000,
               "Missing or unsafe required instruction: " + relative)
    handoff = (root / "docs/AI_HANDOFF.md").read_text(encoding="utf-8")
    active = state["active"]
    ensure(active["branch"] in handoff and active["parent_sha"] in handoff,
           "Handoff does not match active checkpoint")
    return state, cases


def snapshot(state: dict, cases: dict) -> dict:
    """Safe, compact resume manifest; intentionally never declares live PASS."""
    return {
        "schema_version": 1,
        "project": state["project"],
        "repository": state["repository"],
        "approved_baseline": state["approved_baseline"],
        "completed_stages_recorded": len(state["completed"]),
        "last_recorded_stage": state["completed"][-1]["stage"],
        "last_recorded_sha": state["completed"][-1]["sha"],
        "active": state["active"],
        "error_cases_recorded": len(cases["cases"]),
        "read_at_exact_sha": list(DOCUMENTS),
        "live_head_verified": False,
        "live_source_ci_verified": False,
        "live_pr_ci_verified": False,
        "interpretation": "OFFLINE_ONLY: re-read GitHub live before any CODE_VERIFIED claim.",
    }


def run_git(root: Path, *args: str) -> str:
    proc = subprocess.run(["git", *args], cwd=root,
                          capture_output=True, text=True, check=False)
    ensure(proc.returncode == 0, "Cannot inspect checkout git provenance")
    return proc.stdout.strip()


def git_facts(root: Path) -> dict:
    ancestry = subprocess.run(["git", "merge-base", "--is-ancestor", BASE_SHA, "HEAD"],
                              cwd=root, capture_output=True, check=False).returncode == 0
    return {
        "head": run_git(root, "rev-parse", "HEAD"),
        "origin": run_git(root, "remote", "get-url", "origin"),
        "shallow": run_git(root, "rev-parse", "--is-shallow-repository"),
        "v56_ancestor": ancestry,
    }


def verify_ci_checkout(state: dict, env: dict, git: dict,
                       pr_context: dict | None = None) -> dict:
    """Pure check of trusted GitHub Actions inputs and checkout provenance."""
    ensure(env.get("GITHUB_ACTIONS") == "true", "Not inside a GitHub Actions run")
    ensure(env.get("GITHUB_REPOSITORY") == REPO, "Wrong project/repository")
    ensure(is_sha(env.get("GITHUB_SHA")) and git.get("head") == env["GITHUB_SHA"],
           "Checkout HEAD and GitHub SHA mismatch")
    ensure(git.get("shallow") == "false" and git.get("v56_ancestor") is True
           and isinstance(git.get("origin"), str)
           and bool(ORIGIN.fullmatch(git["origin"])),
           "Unverified origin, full history or v56 ancestry")
    active = state["active"]
    event = env.get("GITHUB_EVENT_NAME")
    if event == "push":
        ensure(env.get("GITHUB_REF") == "refs/heads/" + active["branch"],
               "Wrong source branch")
        ensure(pr_context is None, "PR proof supplied for push context")
        return {"checkout": "SOURCE", "checkout_sha": git["head"],
                "source_sha": git["head"], "live_ci_verified": False}
    if event == "pull_request":
        ref = env.get("GITHUB_REF", "")
        number = PR_REF.fullmatch(ref) if isinstance(ref, str) else None
        ensure(number is not None, "Wrong PR merge ref")
        ensure(env.get("GITHUB_HEAD_REF") == active["branch"],
               "PR head must match the active task branch")
        ensure(isinstance(pr_context, dict) and pr_context.get("checkout") == "PR_MERGE",
               "Trusted PR merge proof missing")
        ensure(pr_context.get("number") == int(number.group(1))
               and pr_context.get("merge_sha") == git["head"]
               and pr_context.get("base_sha") == active["parent_sha"]
               and pr_context.get("head_ref") == active["branch"]
               and is_sha(pr_context.get("head_sha"))
               and pr_context.get("base_ref") == state["completed"][-1]["branch"],
               "PR merge/base/head provenance does not match checkpoint")
        return {"checkout": "PR_MERGE", "checkout_sha": git["head"],
                "source_sha": pr_context["head_sha"], "live_ci_verified": False}
    raise ContextError("Unsupported CI event; no source/PR verification")


def _jobs(run: dict, context: str) -> None:
    ensure(isinstance(run.get("jobs"), list), context + ": no complete job list")
    selected = [j for j in run["jobs"]
                if isinstance(j, dict) and j.get("name") in REQUIRED_JOBS]
    names = [j["name"] for j in selected]
    ensure(len(selected) == len(REQUIRED_JOBS) and set(names) == REQUIRED_JOBS,
           context + ": missing or duplicate required job")
    ensure(all(j.get("status") == "completed" and j.get("conclusion") == "success"
               for j in selected), context + ": failed, skipped or pending job")


def _run(run: object, *, event: str, branch: str, sha: str) -> None:
    ensure(isinstance(run, dict), event + ": missing live-run metadata")
    ensure(type(run.get("id")) is int and run["id"] > 0
           and type(run.get("attempt")) is int and run["attempt"] > 0,
           event + ": run ID or attempt invalid")
    ensure(run.get("event") == event and run.get("head_branch") == branch
           and run.get("head_sha") == sha,
           event + ": wrong event, branch or source SHA")
    ensure(run.get("status") == "completed" and run.get("conclusion") == "success",
           event + ": CI not completed with success")
    _jobs(run, event)


def verify_supplied_evidence(state: dict, data: object) -> dict:
    """Cross-check connector-derived snapshots, not their authenticity or freshness."""
    ensure(isinstance(data, dict) and
           set(data) == {"repository", "branch", "source_sha",
                         "source_run", "pr_run", "pr"},
           "Incomplete or unexpected evidence schema")
    active = state["active"]
    sha = data["source_sha"]
    ensure(data["repository"] == REPO and data["branch"] == active["branch"]
           and is_sha(sha), "Cross-project, wrong branch or invalid full SHA")
    _run(data["source_run"], event="push", branch=active["branch"], sha=sha)
    _run(data["pr_run"], event="pull_request", branch=active["branch"], sha=sha)
    ensure(data["source_run"]["id"] != data["pr_run"]["id"],
           "SOURCE and PR CI must be separate runs")
    pr = data["pr"]
    ensure(isinstance(pr, dict) and
           set(pr) == {"number", "head_sha", "base_sha",
                       "merge_sha", "merge_parents"},
           "Incomplete PR metadata")
    ensure(type(pr["number"]) is int and pr["number"] > 0,
           "Missing PR number")
    ensure(pr["head_sha"] == sha and pr["base_sha"] == active["parent_sha"]
           and is_sha(pr["merge_sha"]) and pr["merge_sha"] != sha,
           "PR source, base or merge SHA mismatch")
    ensure(pr["merge_parents"] == [pr["base_sha"], sha],
           "PR merge checkout does not have expected parents")
    return {
        "evidence_consistent": True,
        "live_authenticated_by_this_script": False,
        "source_run_id": data["source_run"]["id"],
        "pr_run_id": data["pr_run"]["id"],
        "source_sha": sha,
        "pr_merge_sha": pr["merge_sha"],
        "interpretation": "Consistency only; confirm source freshness and provenance in live GitHub connector.",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("snapshot", "ci-check", "check-evidence"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--evidence-file", default=None)
    args = parser.parse_args(argv)
    try:
        root = Path(args.root).resolve()
        state, cases = read_project(root)
        if args.action == "snapshot":
            ensure(args.evidence_file is None, "Unexpected evidence input")
            report = snapshot(state, cases)
        elif args.action == "ci-check":
            ensure(args.evidence_file is None, "Unexpected evidence input")
            env = dict(os.environ)
            pr_context = None
            if env.get("GITHUB_EVENT_NAME") == "pull_request":
                from mpvrex_pr_ci import validate_live_ci
                pr_context = validate_live_ci(env, root)
            report = verify_ci_checkout(state, env, git_facts(root), pr_context)
        else:
            ensure(args.evidence_file is not None, "Missing evidence file")
            file = Path(args.evidence_file)
            ensure(file.is_file() and not file.is_symlink()
                   and file.stat().st_size <= 65536,
                   "Unsafe or oversized evidence file")
            report = verify_supplied_evidence(
                state, json.loads(file.read_text(encoding="utf-8")))
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as exc:
        print("CHATGPT_CONTEXT_FAIL: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
