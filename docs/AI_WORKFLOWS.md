# AI workflows — ChatGPT, Work, Codex, GitHub

This document complements [../AGENTS.md](../AGENTS.md) and [../CONTRIBUTING.md](../CONTRIBUTING.md). If anything disagrees, inspect current code and follow repository rules. A reusable private ChatGPT plugin may guide these processes, but its availability in a particular chat or Codex session is not guaranteed.

## 1. Persistent project context
- At task start, read `AGENTS.md`, current branch/diff, relevant code, tests, CI, and project documentation. Previous chat summaries are leads, **not proof** of current implementation.
- Keep stable instructions here or in `AGENTS.md`; store feature decisions in focused `docs/` files. A session handoff records: goal, observed status, completed with evidence, validation performed, next steps, risks/blockers, and exact file/PR/CI links.
- Preserve existing preferences, user data and unrelated commits. When picking the next `vXX` APK version, inspect latest actual tags/artifacts and **never reuse a build version**.

## 2. Work for multi-step tasks
- Use ChatGPT Work/cloud browser for authorized navigation, research across apps, file operations and finished multi-step outputs where available. Use specialized connected app APIs for GitHub/Drive/Notion when possible.
- Define acceptance criteria and permitted side effects first; check each execution result. Work access and connectors depend on the account/session. Do not claim background work or website interaction unless a tool actually did it.

## 3. Codex development process
- Defect: obtain reproducible symptoms/logs, inspect recent diffs, trace the root cause, compare a known-good implementation, test one hypothesis, implement the smallest change, run regression checks.
- Feature: scope behavior, test boundary cases when feasible, implement narrowly, perform code review, inspect the final diff, verify actual results before delivery.
- Repository verification entry points currently documented in `AGENTS.md`: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --stacktrace` and `python3 .github/scripts/check_workflows.py` (PyYAML 6.x). Device playback, gestures, UI and streaming still need real device/emulator testing. Never confuse an illustrative screenshot with device evidence.
- For APK releases, verify version, package, signing compatibility, ABI and resulting artifact before claiming users can update without uninstalling. A release or upload is an external action requiring explicit authorization.
- Watch for `.github/workflows/mpv*.yml`: some CI jobs patch Kotlin during build and may push to `master`. Distinguish committed code from build-time modifications.

## 4. Plugin and skill authoring
- Use task-specific reusable skills; do not duplicate `AGENTS.md` in a plugin. Skill-only plugins provide instructions **not** new network tools. MCP integrations require real endpoints, trusted auth and scoped permissions.
- Validate manifests, sample scenarios and version changes before publishing or installing. Never add credentials to a public repository.

## 5. Scheduled tasks and condition watches
- Reminders, recurring reporting and changes/alerts must be created only when the user requests them using the platform scheduler; don't pretend ordinary chat is a background worker.
- Preserve timezone, named trigger, recurrence and negative-condition silence. Confirm the schedule and task state.

## 6. MCP / Apps SDK integrations
- First discover existing connected tools. For a new MCP service: specify actual data source, authorization, read/write capabilities, validation, error recovery and prompt-injection defenses.
- For interactive app UI: separate presentation from controlled, authorized actions. Check current official API documentation instead of guessing supported methods.

## Completion/handoff template
- **Goal & scope:** What the user asked, what stayed out of scope.
- **Evidence inspected:** branch, commit, files, CI logs, external docs.
- **Changes:** exact files and PR/commit.
- **Verification:** commands and output; explicitly state what wasn't run.
- **Next step/blockers:** precise safe continuation, not speculation.

This file is a process reference, not proof any code was changed, CI ran, or an APK was built.
