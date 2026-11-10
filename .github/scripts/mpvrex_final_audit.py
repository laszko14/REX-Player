#!/usr/bin/env python3
"""Offline-only final audit of MPVRex V3.2. Never certifies live CI or an APK."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess
import sys

from mpvrex_checkpoint import BASE_SHA, ORIGIN, REQUIRED_JOBS, load as load_checkpoint
from mpvrex_errors import load as load_errors
from mpvrex_quality import validate_project
from mpvrex_chat_context import read_project, DOCUMENTS

FINAL_DOC = "docs/ai/FINAL_AUDIT.md"
GATE = "python3 .github/scripts/mpvrex_final_audit.py validate"
CI_GUARD = "${{ (github.event_name == 'push' && startsWith(github.ref, 'refs/heads/docs/mpvrex-v32-')) || (github.event_name == 'pull_request' && startsWith(github.head_ref, 'docs/mpvrex-v32-') && github.base_ref != 'master') }}"
SUITES = {
    "test_mpvrex_preflight.py", "test_mpvrex_checkpoint.py",
    "test_mpvrex_errors.py", "test_mpvrex_debugging.py",
    "test_mpvrex_quality.py", "test_mpvrex_pr_ci.py",
    "test_mpvrex_apk.py", "test_mpvrex_chat_context.py",
    "test_mpvrex_final_audit.py",
}


class AuditError(ValueError):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AuditError(message)


def permitted_path(path: object) -> bool:
    return (isinstance(path, str) and bool(path) and
            not path.startswith("/") and ".." not in path.split("/") and
            (path in {"AGENTS.md", "CONTRIBUTING.md", ".github/workflows/build.yml"}
             or path.startswith("docs/") or path.startswith(".github/scripts/")))


def contract(state: dict, cases: dict, changes: list[str],
             suites: set[str], workflow: dict) -> dict:
    require(state["project"] == "MPVRex / MPV Alpha" and
            state["approved_baseline"]["sha"] == BASE_SHA, "Wrong project or v56 base")
    history, active = state["completed"], state["active"]
    require(len(history) == 9 and active["stage"] == 10, "Incomplete 10-stage history")
    require(active["status"] == "CI_PENDING", "Cannot self-certify final CI")
    require(active["branch"] == "docs/mpvrex-v32-stage10-final-audit-20261110" and
            active["parent_sha"] == history[-1]["sha"], "Wrong stage 10 branch or parent")
    seen_shas, seen_runs = set(), set()
    for n, entry in enumerate(history, 1):
        ci = entry["evidence"]
        require(entry["stage"] == n and
                entry["branch"].startswith(f"docs/mpvrex-v32-stage{n:02d}-"),
                "Wrong stage order or branch")
        require(entry["sha"] not in seen_shas and ci["run_id"] not in seen_runs,
                "Duplicate stage SHA or run")
        seen_shas.add(entry["sha"])
        seen_runs.add(ci["run_id"])
        require(entry["status"] == "CODE_VERIFIED" and ci["result"] == "success" and
                ci["sha"] == entry["sha"] and
                set(ci["required_jobs"]) == REQUIRED_JOBS and ci["attempt"] > 0,
                "Incomplete historical SOURCE and Android CI evidence")
    require(SUITES.issubset(suites), "Missing regression suite")
    require(bool(changes) and len(changes) == len(set(changes)) and
            all(permitted_path(p) for p in changes),
            "Unapproved application, signing or release changes")
    require(FINAL_DOC in DOCUMENTS, "Final report not part of ChatGPT handoff")
    require(all(case["status"] in {"FIXED_UNVERIFIED", "VERIFIED"}
                for case in cases["cases"]), "Unresolved registered incident")
    steps = workflow["jobs"]["sources"]["steps"]
    matched = [s for s in steps if s.get("run") == GATE]
    require(len(matched) == 1 and matched[0].get("if") == CI_GUARD and
            "continue-on-error" not in matched[0],
            "Final CI audit gate missing or bypassed")
    return {
        "offline_audit": "PASS", "history_stages": len(history),
        "active_stage": active["stage"], "test_suites": len(SUITES),
        "incidents": len(cases["cases"]),
        "live_source_ci": "NOT_VERIFIED_BY_THIS_TOOL",
        "live_pr_ci": "NOT_VERIFIED_BY_THIS_TOOL",
        "signed_apk": "NOT_TESTED", "device": "NOT_TESTED",
    }


def git(root: Path, *args: str) -> str:
    p = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)
    require(p.returncode == 0, "Git assertion failed: " + args[0])
    return p.stdout.strip()


def checkout_changes(root: Path, state: dict) -> list[str]:
    require(git(root, "rev-parse", "--is-shallow-repository") == "false",
            "Shallow Git checkout")
    require(bool(ORIGIN.fullmatch(git(root, "remote", "get-url", "origin"))),
            "Wrong Git origin")
    stages = [BASE_SHA] + [s["sha"] for s in state["completed"]]
    for older, newer in zip(stages, stages[1:] + ["HEAD"]):
        test = subprocess.run(["git", "merge-base", "--is-ancestor", older, newer],
                              cwd=root, capture_output=True)
        require(test.returncode == 0, "History lineage not verified")
    return git(root, "diff", "--name-only", BASE_SHA, "HEAD").splitlines()


def validate(root: Path) -> dict:
    state, cases = read_project(root)
    require((root / FINAL_DOC).is_file() and not (root / FINAL_DOC).is_symlink(),
            "Final report missing")
    import yaml
    workflow = yaml.safe_load((root / ".github/workflows/build.yml").read_text(encoding="utf-8"))
    quality = validate_project(root)
    require(quality["stage"] == 10 and quality["live_ci_verified"] is False,
            "Offline quality gate cannot certify live CI")
    suites = {f.name for f in (root / ".github/scripts").glob("test_*.py")}
    return contract(state, cases, checkout_changes(root, state), suites, workflow)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=("validate", "report"))
    p.add_argument("--root", default=".")
    args = p.parse_args(argv)
    try:
        result = validate(Path(args.root).resolve())
        if args.action == "report":
            print(json.dumps(result, indent=2, sort_keys=True))
        else:
            print("MPVREX_FINAL_AUDIT_OFFLINE_PASS; final SHA SOURCE/PR CI not verified")
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as exc:
        print("MPVREX_FINAL_AUDIT_FAIL: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
