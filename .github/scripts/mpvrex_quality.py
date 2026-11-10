#!/usr/bin/env python3
"""MPVRex V3.2 stage 6: fail-closed OFFLINE quality gates.

Checks consistency of project artifacts, regression tests and GitHub Actions
configuration. Does NOT run Android, read GitHub live CI, or certify CODE_VERIFIED.
"""
from __future__ import annotations

import argparse
import ast
import json
from pathlib import Path
import sys

import yaml

from mpvrex_checkpoint import load as load_checkpoint, REQUIRED_JOBS
from mpvrex_errors import load as load_errors
from mpvrex_debugging import validate_process

QUALITY_DOC = "docs/ai/QUALITY_GATES.md"
WORKFLOW = ".github/workflows/build.yml"
REQUIRED_DOCS = {
    "AGENTS.md": ("MPV Alpha", "docs/ai/QUALITY_GATES.md"),
    "CONTRIBUTING.md": ("MPV Alpha v56",),
    "docs/AI_HANDOFF.md": ("MPVRex", "CI_PENDING"),
    "docs/ai/CHECKPOINT_POLICY.md": ("CI_PENDING", "CODE_VERIFIED"),
    "docs/ai/STARTUP_PREFLIGHT.md": ("MPVRex", "SHA"),
    "docs/ai/ERROR_LEARNING.md": ("ERROR_CASES.json", "DEBUGGING_PROTOCOL.md"),
    "docs/ai/DEBUGGING_PROTOCOL.md": ("SYSTEMATIC DEBUGGING", "SOURCE", "Android"),
    QUALITY_DOC: ("## Zakres", "## Bramki", "## Testy", "## Ograniczenia",
                  "CI_PENDING", "CODE_VERIFIED", "NOT_TESTED"),
}
REQUIRED_TESTS = {
    "test_mpvrex_checkpoint.py": {
        "test_active_stage_must_follow_verified",
        "test_stage_parent_matches_last_sha",
        "test_stage5_verified_history_preserved",
    },
    "test_mpvrex_errors.py": {
        "test_checked_in_registry_valid", "test_repeat_fingerprint_merges_observations",
        "test_secrets_and_pii_rejected",
    },
    "test_mpvrex_debugging.py": {
        "test_two_failures_change_method",
        "test_triage_never_claims_live_ci",
        "test_protocol_quality_gate_rejects_incomplete_fixed",
    },
    "test_mpvrex_quality.py": {
        "test_canonical_quality_pass",
        "test_missing_android_job_rejected",
        "test_missing_regression_test_rejected",
        "test_missing_quality_gate_step_rejected",
    },
}
# These commands must be actual source CI run commands, not merely comments.
REQUIRED_SOURCE_COMMANDS = (
    "mpvrex_preflight.py",
    "mpvrex_checkpoint.py validate",
    "mpvrex_errors.py validate",
    "mpvrex_debugging.py validate",
    "mpvrex_quality.py validate",
    "check_workflows.py",
    "unittest discover",
    "check_polish.py",
)
EXPECTED_DOC_BRANCH = "docs/mpvrex-v32-"
DOCS_PUSH_CONDITION = "${{ github.event_name == 'push' && startsWith(github.ref, 'refs/heads/docs/mpvrex-v32-') }}"
ANDROID_COMMANDS = (":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")


class QualityGateError(ValueError):
    """A necessary stage-6 quality invariant does not hold."""


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise QualityGateError(reason)


def read_text(root: Path, relative: str, maximum: int = 250_000) -> str:
    path = root / relative
    require(path.is_file() and not path.is_symlink(), f"Required file absent or unsafe: {relative}")
    require(0 < path.stat().st_size <= maximum, f"Required file is empty or oversized: {relative}")
    return path.read_text(encoding="utf-8")


def load_inputs(root: Path) -> tuple[dict, dict, dict, dict, dict]:
    """Load from one root. Tests can mutate returned independent objects."""
    state = load_checkpoint(root)
    cases = load_errors(root)
    validate_process(root)
    docs = {path: read_text(root, path) for path in REQUIRED_DOCS}
    workflow = yaml.safe_load(read_text(root, WORKFLOW))
    sources = {}
    test_dir = root / ".github/scripts"
    require(test_dir.is_dir() and not test_dir.is_symlink(), "Missing test directory")
    for path in sorted(test_dir.glob("test_*.py")):
        sources[path.name] = read_text(root, f".github/scripts/{path.name}")
    return state, cases, docs, workflow, sources


def collect_tests(test_sources: dict) -> set[str]:
    """Count real unittest methods containing at least one assertion."""
    found = set()
    for file, source in test_sources.items():
        require(isinstance(file, str) and file.startswith("test_") and file.endswith(".py"),
                "Invalid test filename")
        require(isinstance(source, str) and source.strip(), f"Empty test file: {file}")
        tree = ast.parse(source, filename=file)
        for cls in (node for node in tree.body if isinstance(node, ast.ClassDef)):
            for method in (node for node in cls.body
                           if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                           and node.name.startswith("test_")):
                asserts = any(
                    isinstance(n, ast.Assert)
                    or (isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute)
                        and isinstance(n.func.value, ast.Name) and n.func.value.id == "self"
                        and n.func.attr.startswith("assert"))
                    for n in ast.walk(method)
                )
                if asserts:
                    found.add(method.name)
    return found


def check_docs(state: dict, docs: dict) -> None:
    require(set(REQUIRED_DOCS).issubset(docs), "Incomplete documentation set")
    for path, markers in REQUIRED_DOCS.items():
        content = docs[path]
        require(isinstance(content, str) and len(content.strip()) >= 100,
                f"Incomplete document: {path}")
        for marker in markers:
            require(marker in content, f"Documentation missing {marker!r}: {path}")
    active = state["active"]
    history = state["completed"][-1]
    handoff = docs["docs/AI_HANDOFF.md"]
    require(active["branch"] in handoff and active["parent_sha"] in handoff,
            "Handoff missing exact active branch and parent SHA")
    require(history["sha"] == active["parent_sha"],
            "Checkpoint active parent differs from completed SHA")
    require("6/10" in handoff or active["stage"] > 6,
            "Handoff missing current stage 6")


def check_registry(cases: dict, tests: set[str]) -> None:
    require(isinstance(cases["cases"], list), "Missing canonical incident registry")
    for case in cases["cases"]:
        if case["status"] not in {"VERIFIED", "FIXED_UNVERIFIED", "REOPENED"}:
            continue
        regression = case["regression_test"]
        if isinstance(regression, str) and regression.startswith("test_"):
            require(regression in tests, f"{case['id']}: missing regression test {regression}")
        for name in case["diagnostic_tests"]:
            if name.startswith("test_"):
                require(name in tests, f"{case['id']}: missing diagnostic test {name}")


def steps_for(workflow: dict, job: str) -> list[dict]:
    require(isinstance(workflow, dict) and isinstance(workflow.get("jobs"), dict),
            "Invalid workflow/jobs")
    j = workflow["jobs"].get(job)
    require(isinstance(j, dict), f"Missing {job} job")
    require("continue-on-error" not in j, f"Bypassed job: {job}")
    steps = j.get("steps")
    require(isinstance(steps, list) and len(steps) > 0, f"No steps in {job}")
    for step in steps:
        require(isinstance(step, dict) and "continue-on-error" not in step,
                f"Bypassed step in {job}")
    return steps


def check_workflow(workflow: dict) -> None:
    require(isinstance(workflow, dict), "Invalid workflow YAML")
    triggers = workflow.get("on", workflow.get(True))
    require(isinstance(triggers, dict) and isinstance(triggers.get("push"), dict),
            "Missing push event")
    push = triggers["push"]
    require(isinstance(push.get("branches"), list) and
            any(EXPECTED_DOC_BRANCH in str(pattern) for pattern in push["branches"]),
            "Missing MPVRex docs branch source CI trigger")
    paths = push.get("paths", [])
    require(isinstance(paths, list) and
            ".github/scripts/**" in paths and "docs/ai/**" in paths and
            ".github/workflows/**" in paths, "Quality artifacts do not trigger CI")
    source = steps_for(workflow, "sources")
    android = steps_for(workflow, "android")
    source_job = workflow["jobs"]["sources"]
    android_job = workflow["jobs"]["android"]
    require(source_job.get("name") in REQUIRED_JOBS and
            android_job.get("name") in REQUIRED_JOBS and
            source_job["name"] != android_job["name"], "Missing exact required CI job names")
    needs = android_job.get("needs")
    require(needs == "sources" or (isinstance(needs, list) and "sources" in needs),
            "Android CI must depend on sources")
    android_if = str(android_job.get("if", ""))
    require("pull_request" in android_if and EXPECTED_DOC_BRANCH in android_if and
            "push" in android_if, "Android CI does not run on docs push/PR")
    source_scripts = "\n".join(str(x.get("run", "")) for x in source)
    for cmd in REQUIRED_SOURCE_COMMANDS:
        require(cmd in source_scripts, f"Missing source quality command: {cmd}")
    quality = [x for x in source if "mpvrex_quality.py validate" in str(x.get("run", ""))]
    require(len(quality) == 1 and
            str(quality[0].get("if", "")).strip() == DOCS_PUSH_CONDITION,
            "Stage-6 quality gate must run once on docs pushes")
    for x in source:
        command = str(x.get("run", ""))
        if any(c in command for c in ("mpvrex_preflight.py", "mpvrex_checkpoint.py validate",
                                     "mpvrex_errors.py validate", "mpvrex_debugging.py validate")):
            require(str(x.get("if", "")).strip() == DOCS_PUSH_CONDITION,
                    "Essential source check is bypassable")
    require(any(x.get("with", {}).get("fetch-depth") == 0
                for x in source if isinstance(x.get("with"), dict)), "Source checkout must have full history")
    android_run = "\n".join(str(x.get("run", "")) for x in android)
    for cmd in ANDROID_COMMANDS:
        require(cmd in android_run, f"Missing Android build/test/lint command: {cmd}")
    steps_for(workflow, "signed_apk")
    signing = workflow["jobs"]["signed_apk"]
    require("refs/heads/master" in str(signing.get("if", "")) and
            EXPECTED_DOC_BRANCH not in str(signing.get("if", "")),
            "Signed APK could be triggered on docs branch")


def validate_inputs(state: dict, cases: dict, docs: dict, workflow: dict,
                    test_sources: dict) -> dict:
    """Fail closed on missing gate; never infer live PASS."""
    require(state["active"]["stage"] >= 6, "Quality gates require stage 6 or later")
    tests = collect_tests(test_sources)
    for file, required in REQUIRED_TESTS.items():
        require(file in test_sources, f"Missing regression suite {file}")
        for test in required:
            require(test in tests, f"Missing substantive negative/positive test: {test}")
    check_docs(state, docs)
    check_registry(cases, tests)
    check_workflow(workflow)
    return {
        "stage": state["active"]["stage"],
        "active_status": state["active"]["status"],
        "completed_stages": len(state["completed"]),
        "registry_cases": len(cases["cases"]),
        "discovered_asserting_tests": len(tests),
        "required_workflow_jobs": sorted(REQUIRED_JOBS),
        "live_ci_verified": False,
        "device_verified": False,
        "statement": "Offline structure and contracts only; verify run/attempt/jobs at final SHA."
    }


def validate_project(root: Path) -> dict:
    return validate_inputs(*load_inputs(root))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "report"))
    parser.add_argument("--root", default=".")
    args = parser.parse_args(argv)
    try:
        result = validate_project(Path(args.root).resolve())
        if args.action == "report":
            print(json.dumps(result, ensure_ascii=False, indent=2))
        else:
            print(f"QUALITY_GATES_PASS stage={result['stage']} "
                  f"tests={result['discovered_asserting_tests']} "
                  f"cases={result['registry_cases']} (OFFLINE only; NOT live CI)")
        return 0
    except (OSError, ValueError, SyntaxError, TypeError, yaml.YAMLError) as exc:
        print(f"QUALITY_GATES_FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
