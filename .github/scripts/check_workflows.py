#!/usr/bin/env python3
"""Validate GitHub Actions YAML and Python heredocs embedded in run steps.

This is a lightweight syntax gate; it does not execute workflow commands
or replace an Android build, runtime testing, or actionlint.
Requires PyYAML 6.x.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

HEREDOC = re.compile(
    r"""^\s*python(?:3)?(?:\s+-)?\s*<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1\s*(?:#.*)?$"""
)


def check_python_heredocs(run_script: str, source: str) -> list[str]:
    """Compile (do not execute) inline Python programs from a shell step."""
    errors: list[str] = []
    lines = run_script.splitlines()
    pos = 0
    while pos < len(lines):
        found = HEREDOC.match(lines[pos])
        if not found:
            pos += 1
            continue
        marker = found.group(2)
        start = pos + 1
        end = start
        while end < len(lines) and lines[end].strip() != marker:
            end += 1
        if end >= len(lines):
            errors.append(f"{source}: unclosed Python heredoc {marker!r} at run line {pos + 1}")
            break
        python_code = "\n".join(lines[start:end]) + "\n"
        try:
            compile(python_code, f"{source}:Python heredoc starting at run line {pos + 1}", "exec")
        except SyntaxError as exc:
            errors.append(f"{source}: Python syntax error at heredoc line {exc.lineno}: {exc.msg}")
        pos = end + 1
    return errors


def check_workflow(path: Path) -> tuple[int, list[str]]:
    errors: list[str] = []
    snippets = 0
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (yaml.YAMLError, UnicodeError) as exc:
        return (0, [f"{path}: invalid YAML: {exc}"])
    if not isinstance(data, dict):
        return (0, [f"{path}: workflow must be a YAML mapping"])
    # PyYAML follows YAML 1.1, where an unquoted 'on' is parsed as bool True.
    if "on" not in data and True not in data:
        errors.append(f"{path}: missing workflow trigger 'on'")
    jobs = data.get("jobs")
    if not isinstance(jobs, dict) or not jobs:
        errors.append(f"{path}: missing or empty jobs mapping")
        return (0, errors)
    for job_name, job in jobs.items():
        if not isinstance(job, dict):
            errors.append(f"{path}: job {job_name!r} must be a mapping")
            continue
        for index, step in enumerate(job.get("steps", []), start=1):
            if not isinstance(step, dict):
                errors.append(f"{path}: job {job_name}, step {index}: expected mapping")
                continue
            run_script = step.get("run")
            if isinstance(run_script, str):
                snippets += sum(1 for line in run_script.splitlines() if HEREDOC.match(line))
                errors.extend(
                    check_python_heredocs(
                        run_script, f"{path} job={job_name} step={index}"
                    )
                )
    return snippets, errors


def main() -> int:
    files = sorted(Path(".github/workflows").glob("*.yml"))
    files += sorted(Path(".github/workflows").glob("*.yaml"))
    if not files:
        print("ERROR: no GitHub Actions workflows found")
        return 1
    errors: list[str] = []
    total_snippets = 0
    for path in files:
        snippets, file_errors = check_workflow(path)
        total_snippets += snippets
        errors.extend(file_errors)
    for problem in errors:
        print(f"ERROR: {problem}")
    print(
        f"Validated {len(files)} GitHub Actions workflows and "
        f"{total_snippets} embedded Python heredocs; errors: {len(errors)}"
    )
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
