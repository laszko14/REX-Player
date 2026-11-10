#!/usr/bin/env python3
"""MPVRex stage 8: verify explicitly authorized v56 signing inputs and APK metadata.

This script never accesses GitHub Secrets or signs an APK. Live authorization,
CI success and installability cannot be inferred from a local validation.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

import yaml

REPO = "laszko14/REX-Player"
BASE = "74b059ab0d2c5492451c42f88ff993128a2e45c9"
APPROVAL = "SIGN_MPV_ALPHA_V56"
BRANCH_PREFIX = "refs/heads/docs/mpvrex-v32-"
SHA_RE = re.compile(r"[0-9a-f]{40}\Z")
VERSION_RE = re.compile(r"v56\.[1-9][0-9]*\Z")
MAX_BASE_CODE = 214748364
SIGN_IF = "${{ github.event_name == 'workflow_dispatch' && github.repository == 'laszko14/REX-Player' && github.actor == 'laszko14' && startsWith(github.ref, 'refs/heads/docs/mpvrex-v32-') && inputs.approval == 'SIGN_MPV_ALPHA_V56' }}"
REQUIRED_INPUTS = ("approval", "source_sha", "version_code_base", "version_name")


class APKError(ValueError):
    """Refuse an unsafe or unverified signing request."""


def ensure(ok: bool, message: str) -> None:
    if not ok:
        raise APKError(message)


def validate_request(env: dict, git: dict) -> dict:
    """Pure function: environmental authorization and verified checkout facts."""
    ensure(env.get("GITHUB_EVENT_NAME") == "workflow_dispatch", "Only manual dispatch may sign")
    ensure(env.get("GITHUB_REPOSITORY") == REPO, "Wrong repository")
    ensure(env.get("GITHUB_ACTOR") == "laszko14", "Owner authorization required")
    ensure(str(env.get("GITHUB_REF", "")).startswith(BRANCH_PREFIX), "Wrong source branch")
    ensure(env.get("APPROVAL") == APPROVAL, "Separate signing approval missing")
    sha = env.get("GITHUB_SHA", "")
    expected = env.get("SOURCE_SHA", "")
    ensure(bool(SHA_RE.fullmatch(str(sha))) and sha == expected, "Exact approved source SHA mismatch")
    ensure(git.get("head") == sha, "Checkout SHA mismatch")
    ensure(git.get("origin") in (
        "https://github.com/laszko14/REX-Player.git",
        "https://github.com/laszko14/REX-Player",
    ), "Unexpected Git origin")
    ensure(git.get("shallow") == "false" and git.get("ancestry") is True,
           "Full history and approved v56 ancestry required")
    ensure(git.get("clean") is True, "Dirty checkout")
    changed = git.get("changed_files")
    ensure(isinstance(changed, list), "Unverified source changes")
    allowed = ("docs/", ".github/scripts/")
    ensure(all(
        isinstance(p, str) and
        (p.startswith(allowed) or p in (
            "AGENTS.md", "CONTRIBUTING.md", ".github/workflows/build.yml"
        ))
        for p in changed), "Unreviewed app or workflow changes relative to approved v56")
    code = str(env.get("VERSION_CODE_BASE", ""))
    ensure(bool(re.fullmatch(r"[0-9]{6,9}", code)), "Invalid version base")
    number = int(code)
    ensure(100056 < number <= MAX_BASE_CODE, "Version must exceed installed v56 and fit Android")
    version = str(env.get("VERSION_NAME", ""))
    ensure(bool(VERSION_RE.fullmatch(version)) and len(version) <= 32,
           "Invalid version name; expected v56.N")
    return {"sha": sha, "version_code": number * 10 + 2, "version_name": version}


def run_git(*args: str) -> str:
    proc = subprocess.run(["git", *args], check=True, capture_output=True, text=True)
    return proc.stdout.strip()


def inspect_git() -> dict:
    ancestor = subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"],
                              check=False, capture_output=True)
    return {
        "head": run_git("rev-parse", "HEAD"),
        "origin": run_git("remote", "get-url", "origin"),
        "shallow": run_git("rev-parse", "--is-shallow-repository"),
        "ancestry": ancestor.returncode == 0,
        "clean": not run_git("status", "--porcelain", "--untracked-files=no"),
        "changed_files": run_git("diff", "--name-only", BASE, "HEAD").splitlines(),
    }


def env_request() -> dict:
    return dict(os.environ)


def parse_package(badging: str) -> dict:
    first = badging.splitlines()[0] if badging else ""
    found = {key: re.search(r"\b" + key + r"='([^']*)'", first)
             for key in ("name", "versionCode", "versionName")}
    ensure(first.startswith("package: ") and all(found.values()), "Invalid aapt package badging")
    return {key: match.group(1) for key, match in found.items()}


def verify_artifact(data: dict, root: Path) -> Path:
    build = root / "app/build/outputs/apk/debug"
    apks = sorted(build.glob("*.apk"))
    ensure(len(apks) == 1, "Expected exactly one APK")
    apk = apks[0]
    ensure("arm64-v8a" in apk.name, "Wrong APK ABI")
    with zipfile.ZipFile(apk) as archive:
        abis = {name.split("/")[1] for name in archive.namelist()
                if name.startswith("lib/") and len(name.split("/")) > 2}
    ensure(abis == {"arm64-v8a"}, "APK contains missing or unexpected native ABIs")
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    cert_output = subprocess.run([str(sdk / "apksigner"), "verify", "--print-certs", str(apk)],
                                 check=True, capture_output=True, text=True).stdout
    matches = re.findall(r"^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
                         cert_output, flags=re.MULTILINE)
    ensure(len(matches) == 1, "Missing or ambiguous verified APK signer")
    expected_cert = (root / ".github/mpv-alpha-signing-certificate.sha256").read_text().strip()
    ensure(bool(re.fullmatch(r"[0-9a-fA-F]{64}", expected_cert)), "Invalid pinned certificate")
    ensure(matches[0].lower() == expected_cert.lower(), "Signing certificate changed")
    badge_output = subprocess.run([str(sdk / "aapt"), "dump", "badging", str(apk)],
                                  check=True, capture_output=True, text=True).stdout
    pkg = parse_package(badge_output)
    ensure(pkg == {"name": "xyz.mpv.rex.debug",
                   "versionCode": str(data["version_code"]),
                   "versionName": data["version_name"]}, "Package or version mismatch")
    out = root / "dist"
    out.mkdir(parents=True, exist_ok=True)
    destination = out / ("MPV-Alpha-" + data["version_name"] + "-"
                         + data["sha"][:12] + "-arm64-v8a.apk")
    shutil.copy2(apk, destination)
    output = os.environ.get("GITHUB_OUTPUT")
    ensure(bool(output), "Missing GitHub artifact output")
    with open(output, "a", encoding="utf-8") as handle:
        handle.write("path=" + destination.relative_to(root).as_posix() + "\n")
    return destination


def validate_workflow(workflow: dict) -> bool:
    """Fail-closed contract for the separate owner-reviewed manual v56 path."""
    ensure(isinstance(workflow, dict), "Invalid workflow")
    triggers = workflow.get("on", workflow.get(True))
    ensure(isinstance(triggers, dict) and "pull_request_target" not in triggers,
           "Privileged PR triggers are forbidden")
    dispatch = triggers.get("workflow_dispatch")
    ensure(isinstance(dispatch, dict), "Missing explicit manual inputs")
    inputs = dispatch.get("inputs")
    ensure(isinstance(inputs, dict) and all(
        isinstance(inputs.get(k), dict) and inputs[k].get("required") is True
        and inputs[k].get("type") == "string" for k in REQUIRED_INPUTS
    ), "Required authorization inputs missing")
    job = workflow.get("jobs", {}).get("signed_v56_apk")
    ensure(isinstance(job, dict), "Missing isolated v56 APK job")
    ensure(job.get("if") == SIGN_IF, "Manual-only signing guard changed")
    ensure(job.get("needs") == "sources", "Signing must depend on SOURCE checks")
    ensure(job.get("permissions") == {"contents": "read"}, "Signing must not write to repository")
    ensure("continue-on-error" not in job, "Bypassed signed job")
    steps = job.get("steps")
    ensure(isinstance(steps, list) and len(steps) >= 9, "Incomplete signing pipeline")
    ensure(all(isinstance(step, dict) and "continue-on-error" not in step for step in steps),
           "Bypassed signing step")
    checkout = [s for s in steps if str(s.get("uses", "")).startswith("actions/checkout@")]
    ensure(len(checkout) == 1 and checkout[0].get("with", {}).get("fetch-depth") == 0
           and "ref" not in checkout[0].get("with", {}), "Signing must checkout exact full SHA")
    commands = [str(s.get("run", "")) for s in steps]
    auth = [i for i, s in enumerate(commands) if "mpvrex_apk.py authorize --ci" in s]
    tests = [i for i, s in enumerate(commands) if ":app:testDebugUnitTest" in s and ":app:lintDebug" in s]
    signing = [i for i, s in enumerate(commands) if ":app:assembleDebug" in s]
    verify = [i for i, s in enumerate(commands) if "mpvrex_apk.py verify --ci" in s]
    secret = [i for i, step in enumerate(steps)
              if any(str(v).find("secrets.") >= 0 for v in
                     list(step.get("env", {}).values()))]
    uploads = [i for i, step in enumerate(steps)
               if str(step.get("uses", "")).startswith("actions/upload-artifact@")]
    ensure(len(auth) == len(tests) == len(signing) == len(verify) == len(uploads) == 1
           and len(secret) >= 2 and auth[0] < tests[0] < min(secret)
           <= signing[0] < verify[0] < uploads[0], "Authorization, tests and signing order unsafe")
    code = "\n".join(commands)
    ensure("git push" not in code and "git tag" not in code and
           "gh release" not in code and "curl " not in code,
           "Signed job must not push, tag or publish")
    ensure("MPV_ALPHA_KEYSTORE_BASE64" in code and
           "mpv-alpha-signing-certificate.sha256" not in code,
           "Keystore restoration contract changed")
    return True


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=("authorize", "verify", "workflow"))
    p.add_argument("--ci", action="store_true")
    p.add_argument("--root", default=".")
    args = p.parse_args(argv)
    try:
        if args.action == "workflow":
            ensure(not args.ci, "Workflow check is offline")
            with open(Path(args.root) / ".github/workflows/build.yml", encoding="utf-8") as f:
                validate_workflow(yaml.safe_load(f))
            print("SAFE_APK_WORKFLOW_PASS (offline only)")
        else:
            ensure(args.ci and os.environ.get("GITHUB_ACTIONS") == "true", "Live CI required")
            data = validate_request(env_request(), inspect_git())
            if args.action == "verify":
                result = verify_artifact(data, Path(args.root).resolve())
                print("APK_METADATA_VERIFIED " + result.name)
            else:
                print("V56_SIGNING_REQUEST_AUTHORIZED SHA=" + data["sha"])
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError, yaml.YAMLError,
            zipfile.BadZipFile, KeyError) as exc:
        print("SAFE_APK_FAIL: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
