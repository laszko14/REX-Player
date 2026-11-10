#!/usr/bin/env python3
"""MPVRex stage 5 systematic debugging: read-only incident triage and process gates.

Never queries GitHub, modifies a registry, claims live CI success or signs APKs.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

from mpvrex_checkpoint import load as load_checkpoint
from mpvrex_errors import (
    CATEGORIES, RegistryError, SLUG, fingerprint, load as load_registry,
)

PROTOCOL = Path("docs/ai/DEBUGGING_PROTOCOL.md")
WORKFLOW = (
    "observe_sanitized_symptom",
    "reproduce_with_minimal_fixture",
    "inspect_exact_workflow_attempt_job_step_and_logs",
    "classify_and_confirm_root_cause",
    "apply_minimal_fix",
    "run_targeted_diagnostic_test",
    "run_related_regressions",
    "verify_source_and_android_ci_on_final_sha",
    "update_canonical_error_registry_and_checkpoint",
)

class DebuggingError(ValueError):
    """Incomplete systematic debugging evidence or unsafe input."""

def require(condition: bool, reason: str) -> None:
    if not condition:
        raise DebuggingError(reason)

def validate_process(root: Path) -> dict:
    """Offline structural quality gate; never proof of remote CI success."""
    data = load_registry(root)
    checkpoint = load_checkpoint(root)
    require(checkpoint["active"]["stage"] >= 5, "Systematic debugging requires stage 5 or later")
    path = root / PROTOCOL
    require(not path.is_symlink() and path.is_file() and
            100 <= path.stat().st_size <= 65536, "Missing/unsafe debugging protocol")
    for case in data["cases"]:
        status = case["status"]
        if status == "FIXED_UNVERIFIED":
            require(case["confidence"] == "confirmed" and
                    bool(case["root_cause"]) and bool(case["fix"]) and
                    bool(case["diagnostic_tests"]) and bool(case["regression_test"]),
                    f"{case['id']}: fixed status requires confirmed cause, fix and tests")
        if status == "REOPENED":
            require(len(case["observations"]) >= 2,
                    f"{case['id']}: reopening needs an earlier occurrence")
        if status == "VERIFIED":
            require(case["ci"]["status"] == "SUCCESS" and
                    case["ci"]["run_id"] is not None,
                    f"{case['id']}: recorded verification requires CI reference")
    return data

def next_action(case: dict, consecutive_failures: int = 0) -> str:
    """Advisory decision only. Consecutive failures MUST be supplied, not guessed."""
    require(type(consecutive_failures) is int and 0 <= consecutive_failures <= 100,
            "consecutive_failures must be an integer from 0 to 100")
    status = case["status"]
    require(status in {"OPEN", "INVESTIGATING", "FIXED_UNVERIFIED",
                       "VERIFIED", "REOPENED", "BLOCKED"}, "Unknown case status")
    if status == "VERIFIED" and consecutive_failures >= 2:
        return "REOPEN_AND_CHANGE_DIAGNOSTIC_METHOD"
    if status == "VERIFIED" and consecutive_failures == 1:
        return "REOPEN_WITH_NEW_OBSERVATION"
    if consecutive_failures >= 2:
        return "CHANGE_HYPOTHESIS_OR_DIAGNOSTIC_METHOD"
    if status == "VERIFIED":
        return "MONITOR_FOR_REGRESSION"
    if status == "REOPENED":
        return "REPRODUCE_AND_COMPARE_WITH_PREVIOUS_FIX"
    if status == "BLOCKED":
        return "DOCUMENT_AND_RESOLVE_BLOCKER"
    if status == "OPEN":
        return "REPRODUCE_AND_COLLECT_EVIDENCE"
    if case["confidence"] != "confirmed":
        return "CONFIRM_ROOT_CAUSE"
    if not case["fix"]:
        return "APPLY_MINIMAL_FIX"
    return "RUN_DIAGNOSTIC_REGRESSIONS_AND_LIVE_CI"

def find_case(data: dict, category: str, component: str, signature: str) -> dict | None:
    require(isinstance(category, str) and category in CATEGORIES,
            "Unknown error category")
    for label, value in (("component", component), ("signature", signature)):
        require(isinstance(value, str) and bool(SLUG.fullmatch(value)),
                f"Invalid {label} slug")
    target = fingerprint({"category": category, "component": component,
                          "signature": signature})
    return next((item for item in data["cases"] if item["fingerprint"] == target), None)

def triage(data: dict, case_id: str, consecutive_failures: int = 0) -> dict:
    require(isinstance(case_id, str) and bool(re.fullmatch(r"MPV-ERR-[0-9]{4}", case_id)),
            "Invalid incident identifier")
    case = next((case for case in data["cases"] if case["id"] == case_id), None)
    require(case is not None, "Incident not present in canonical registry")
    return {
        "case_id": case["id"],
        "fingerprint": case["fingerprint"],
        "saved_status": case["status"],
        "classification": case["category"],
        "next_action": next_action(case, consecutive_failures),
        "consecutive_failures": consecutive_failures,
        "workflow": list(WORKFLOW),
        "live_ci_verified": False,
        "note": "Offline advice only; verify exact HEAD SHA, run, attempt, jobs and logs in GitHub.",
    }

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "triage", "match"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--case-id")
    parser.add_argument("--category")
    parser.add_argument("--component")
    parser.add_argument("--signature")
    parser.add_argument("--consecutive-failures", type=int, default=0)
    args = parser.parse_args(argv)
    try:
        root = Path(args.root).resolve()
        data = validate_process(root)
        if args.action == "validate":
            require(not any((args.case_id, args.category, args.component, args.signature)) and
                    args.consecutive_failures == 0, "validate is read-only and takes no case options")
            print(f"DEBUGGING_PROTOCOL_PASS cases={len(data['cases'])} (offline; NOT live CI)")
        elif args.action == "triage":
            require(args.case_id and not any((args.category, args.component, args.signature)),
                    "triage requires only --case-id")
            print(json.dumps(triage(data, args.case_id, args.consecutive_failures),
                             ensure_ascii=False, indent=2))
        else:
            require(not args.case_id and args.category and args.component and args.signature and
                    args.consecutive_failures == 0,
                    "match requires --category, --component and --signature only")
            match = find_case(data, args.category, args.component, args.signature)
            print(json.dumps({"case_id": match["id"] if match else None,
                              "matched": match is not None,
                              "action": "UPDATE_EXISTING" if match else "REPRODUCE_BEFORE_RECORDING",
                              "live_ci_verified": False}))
        return 0
    except (OSError, ValueError, RegistryError) as exc:
        print(f"DEBUGGING_PROTOCOL_FAIL: {exc}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
