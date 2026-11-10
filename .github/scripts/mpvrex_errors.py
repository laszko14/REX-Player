#!/usr/bin/env python3
"""MPVRex V3.2 stage 4: error registry; no automatic GitHub or APK actions."""
from __future__ import annotations
import argparse
import copy
from datetime import date
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

PATH = Path("docs/ai/ERROR_CASES.json")
BASE = "74b059ab0d2c5492451c42f88ff993128a2e45c9"
REPO = "laszko14/REX-Player"
PROJECT = "MPVRex / MPV Alpha"
SHA = re.compile(r"[0-9a-f]{40}\Z")
SLUG = re.compile(r"[a-z][a-z0-9_]{2,99}\Z")
EVENT_KEY = re.compile(r"[a-z0-9][a-z0-9._-]{2,79}\Z")
BRANCH = re.compile(r"docs/mpvrex-v32-[a-z0-9][a-z0-9._-]*\Z")
CATEGORIES = {"code", "test_fixture", "configuration", "infrastructure", "permissions", "external_dependency"}
STATUSES = {"OPEN", "INVESTIGATING", "FIXED_UNVERIFIED", "VERIFIED", "REOPENED", "BLOCKED"}
CI_STATUSES = {"NOT_TESTED", "PENDING", "FAIL", "SUCCESS", "BLOCKED"}
CONFIDENCE = {"unknown", "hypothesis", "confirmed"}
SECRET = re.compile(
    r"ghp_|github_pat_|-----BEGIN|bearer\b|authorization\b|password\b|passwd\b|"
    r"api[_ -]?key\b|cookie\b|secret\b|keystore\b|https?://|"
    r"[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}|[A-Za-z0-9+/]{90,}", re.I)
FIELDS = {"stage", "category", "component", "signature", "symptom", "condition",
          "confidence", "root_cause", "fix", "diagnostic_tests", "regression_test",
          "status", "branch", "sha", "ci"}
CASE_FIELDS = FIELDS | {"id", "fingerprint", "observations"}
EVENT_FIELDS = FIELDS | {"event_key", "seen_on"}

class RegistryError(ValueError):
    """An unsafe or conflicting error case."""

def require(ok, message):
    if not ok:
        raise RegistryError(message)

def public_text(value, field, max_len=500, nullable=False):
    if nullable and value is None:
        return
    require(isinstance(value, str) and bool(value.strip()) and len(value) <= max_len,
            f"{field}: missing or oversized text")
    require(all(ord(c) >= 32 and ord(c) != 127 for c in value),
            f"{field}: control character")
    require(not SECRET.search(value), f"{field}: potential credential/PII/raw URL")

def iso_day(value):
    require(isinstance(value, str) and bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", value)),
            "Date must be YYYY-MM-DD")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise RegistryError("Invalid calendar date") from exc

def fingerprint(entry):
    key = "|".join(str(entry[k]) for k in ("category", "component", "signature"))
    return hashlib.sha256(key.encode("ascii")).hexdigest()

def validate_record(entry):
    require(isinstance(entry, dict) and set(entry) == CASE_FIELDS, "Case schema mismatch")
    require(type(entry["stage"]) is int and 1 <= entry["stage"] <= 10, "Invalid stage")
    require(isinstance(entry["category"], str) and entry["category"] in CATEGORIES, "Invalid category")
    for key in ("component", "signature"):
        require(isinstance(entry[key], str) and bool(SLUG.fullmatch(entry[key])), f"Invalid {key}")
    for key in ("symptom", "condition"):
        public_text(entry[key], key, 350)
    for key in ("root_cause", "fix", "regression_test"):
        public_text(entry[key], key, nullable=True)
    tests = entry["diagnostic_tests"]
    require(isinstance(tests, list) and len(tests) <= 20 and
            all(isinstance(t, str) for t in tests) and len(set(tests)) == len(tests),
            "Invalid/duplicate diagnostic tests")
    for item in tests:
        public_text(item, "diagnostic_test", 250)
    require(isinstance(entry["confidence"], str) and entry["confidence"] in CONFIDENCE, "Invalid confidence")
    require(isinstance(entry["status"], str) and entry["status"] in STATUSES, "Invalid case status")
    require(isinstance(entry["branch"], str) and bool(BRANCH.fullmatch(entry["branch"])),
            "Wrong branch family")
    require(isinstance(entry["sha"], str) and bool(SHA.fullmatch(entry["sha"])), "Invalid SHA")
    ci = entry["ci"]
    require(isinstance(ci, dict) and set(ci) == {"status", "run_id", "attempt"}, "CI schema mismatch")
    require(isinstance(ci["status"], str) and ci["status"] in CI_STATUSES, "Invalid CI status")
    require((ci["run_id"] is None and ci["attempt"] is None) or
            (type(ci["run_id"]) is int and ci["run_id"] > 0 and
             type(ci["attempt"]) is int and ci["attempt"] > 0), "Incomplete CI run/attempt")
    require(ci["status"] not in {"SUCCESS", "FAIL"} or ci["run_id"] is not None,
            "Claimed CI result without run ID")
    if entry["status"] == "VERIFIED":
        require(entry["confidence"] == "confirmed" and
                all(entry[k] is not None for k in ("root_cause", "fix", "regression_test")) and
                len(tests) > 0 and ci["status"] == "SUCCESS" and ci["run_id"] is not None,
                "VERIFIED needs confirmed cause, fix, regression and CI evidence")
    require(entry["fingerprint"] == fingerprint(entry), "Fingerprint mismatch")
    require(isinstance(entry["observations"], list) and 1 <= len(entry["observations"]) <= 100,
            "Invalid observations")
    seen = set()
    prev_day = None
    for obs in entry["observations"]:
        require(isinstance(obs, dict) and set(obs) == {"event_key", "date", "stage", "sha"},
                "Observation schema mismatch")
        require(isinstance(obs["event_key"], str) and bool(EVENT_KEY.fullmatch(obs["event_key"])),
                "Invalid event key")
        require(obs["event_key"] not in seen, "Duplicate event key")
        seen.add(obs["event_key"])
        day = iso_day(obs["date"])
        require(prev_day is None or day >= prev_day, "Observations out of date order")
        prev_day = day
        require(type(obs["stage"]) is int and 1 <= obs["stage"] <= 10, "Invalid observation stage")
        require(isinstance(obs["sha"], str) and bool(SHA.fullmatch(obs["sha"])),
                "Invalid observation SHA")
    require(entry["stage"] == entry["observations"][-1]["stage"] and
            entry["sha"] == entry["observations"][-1]["sha"],
            "Case stage/SHA must match last observation")

def validate(data):
    require(isinstance(data, dict) and set(data) ==
            {"schema_version", "project", "repository", "approved_baseline_sha", "cases"},
            "Registry schema mismatch")
    require(type(data["schema_version"]) is int and data["schema_version"] == 1,
            "Unsupported registry schema")
    require(data["project"] == PROJECT and data["repository"] == REPO and
            data["approved_baseline_sha"] == BASE, "Wrong project or baseline")
    cases = data["cases"]
    require(isinstance(cases, list) and len(cases) <= 1000, "Invalid case list")
    fingerprints = set()
    for i, item in enumerate(cases, 1):
        validate_record(item)
        require(item["id"] == f"MPV-ERR-{i:04d}", "Case IDs must be stable and sequential")
        require(item["fingerprint"] not in fingerprints, "Duplicate fingerprint")
        fingerprints.add(item["fingerprint"])
    return data

def load(root):
    path = root / PATH
    require(not path.is_symlink() and path.is_file() and path.stat().st_size <= 1024 * 1024,
            "Registry missing, symlink or too large")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise RegistryError("Invalid registry JSON/encoding") from exc
    return validate(data)

def upsert(data, event):
    """Pure idempotent merge by fingerprint and stable event key."""
    validate(data)
    require(isinstance(event, dict) and set(event) == EVENT_FIELDS, "Event schema mismatch")
    require(isinstance(event["event_key"], str) and bool(EVENT_KEY.fullmatch(event["event_key"])),
            "Invalid event key")
    iso_day(event["seen_on"])
    obs = {"event_key": event["event_key"], "date": event["seen_on"],
           "stage": event["stage"], "sha": event["sha"]}
    item = {k: copy.deepcopy(event[k]) for k in FIELDS}
    item.update(id="MPV-ERR-0001", fingerprint=fingerprint(item), observations=[obs])
    validate_record(item)
    merged = copy.deepcopy(data)
    for index, old in enumerate(merged["cases"]):
        if old["fingerprint"] != item["fingerprint"]:
            continue
        require(item["stage"] >= old["stage"], "Cannot rewind case stage")
        prior = next((o for o in old["observations"] if o["event_key"] == obs["event_key"]), None)
        require(prior is None or prior == obs, "Conflicting reuse of event key")
        if old["status"] == "VERIFIED" and item["status"] != "VERIFIED":
            require(item["status"] == "REOPENED" and prior is None,
                    "Verified case can only be reopened by new observation")
        for key in ("root_cause", "fix", "regression_test"):
            if item[key] is None:
                item[key] = old[key]
        if not item["diagnostic_tests"]:
            item["diagnostic_tests"] = old["diagnostic_tests"]
        item["id"] = old["id"]
        item["observations"] = old["observations"] + ([] if prior else [obs])
        validate_record(item)
        merged["cases"][index] = item
        return validate(merged)
    require(len(merged["cases"]) < 1000, "Registry full")
    item["id"] = f"MPV-ERR-{len(merged['cases']) + 1:04d}"
    merged["cases"].append(item)
    return validate(merged)

def record(root, expected_head, event):
    from mpvrex_checkpoint import assert_writable_checkout, load as load_checkpoint
    data = load(root)
    checkpoint = load_checkpoint(root)
    require(event.get("stage") == checkpoint["active"]["stage"],
            "Event stage differs from active checkpoint")
    require(event.get("branch") == checkpoint["active"]["branch"],
            "Event branch differs from active checkpoint")
    require(isinstance(expected_head, str) and bool(SHA.fullmatch(expected_head)) and
            event.get("sha") == expected_head, "Stale/mismatched expected HEAD")
    updated = upsert(data, event)
    assert_writable_checkout(root, checkpoint, expected_head)
    status = subprocess.run(["git", "status", "--porcelain", "--", str(PATH)],
                            cwd=root, capture_output=True, text=True, check=False)
    require(status.returncode == 0 and not status.stdout.strip(),
            "Registry has uncommitted changes or git status failed")
    target = root / PATH
    fd, tmp = tempfile.mkstemp(dir=target.parent, prefix=".mpvrex-error-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(updated, stream, indent=2, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, target)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    return updated

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "show", "record"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--expected-head")
    parser.add_argument("--event-file")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        if args.action == "record":
            require(args.write and args.expected_head and args.event_file,
                    "record requires --write, --expected-head, and --event-file")
            event_path = Path(args.event_file).resolve()
            require(event_path.is_file() and event_path.stat().st_size <= 16384,
                    "Event file missing or too large")
            event = json.loads(event_path.read_text(encoding="utf-8"))
            result = record(root, args.expected_head, event)
            print(f"ERROR_CASE_RECORDED_LOCAL {len(result['cases'])} (not committed/pushed)")
        else:
            require(not args.write and args.expected_head is None and args.event_file is None,
                    "Read-only actions do not accept recording options")
            data = load(root)
            print("ERROR_REGISTRY_SCHEMA_PASS (offline structure only; NOT CI evidence)")
            if args.action == "show":
                for case in data["cases"]:
                    print(f"{case['id']} {case['status']} {case['fingerprint'][:12]} "
                          f"occurrences={len(case['observations'])}")
        return 0
    except (OSError, ValueError) as exc:
        print(f"ERROR_REGISTRY_FAIL: {exc}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
