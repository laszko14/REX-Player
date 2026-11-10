# MPVRex — AI handoff (initial stage-1 checkpoint)

> **Snapshot for human/agent handoff, not a live CI result.** Before resuming, verify the current branch HEAD and Actions run/jobs at that exact SHA. This file is intentionally basic; stage 3/10 will implement the complete checkpoint policy and validation.

## Canonical product and constraints
- Repo: `laszko14/REX-Player`.
- Owner-selected installed baseline: MPV Alpha **v56 debug**, `build/player-transparency-v56`, SHA `74b059ab0d2c5492451c42f88ff993128a2e45c9`.
- Initial V3.2 system branch: `docs/mpvrex-v32-stage01-foundation-20261110`, created from the exact v56 SHA; this is a docs and minimal CI-bootstrap line only.
- Do **not** adopt divergent `master` as product baseline, migrate historical Auto Audio code, merge PRs, modify playback or sign an APK.

## Staged modernization plan (user-approved: 10 stages)
1. Establish v56 working branch and foundational GitHub instructions — **changes staged in this branch; verify live final commit and CI before marking complete**.
2. Preflight: branch/SHA/source-of-truth enforcement.
3. Persistent checkpoints and resumption.
4. Error case registry and deduplication.
5. Systematic debugging and regression workflow.
6. Quality gates and acceptance checks.
7. SOURCE CI and PR CI modernization, exact final SHA checks.
8. Private APK authorization/signing hardening (separate consent required).
9. ChatGPT sources/instructions integration and fresh-chat tests.
10. Final verification and regression audit.

## Stage 1 intended changes
- Update `AGENTS.md` and `CONTRIBUTING.md` so the selected v56 line is not confused with the default `master`.
- Add this initial handoff and a minimally scoped CI trigger for `docs/mpvrex-v32-*`, ensuring source and Android checks run on documentation-stage commits. The CI change is a safe prerequisite, **not** completion of stages 6–7.
- Preserve app, Lua, data, signing, releases and original v56 branch untouched.

## Evidence and next decision
- Initial basis SHA: `74b059ab0d2c5492451c42f88ff993128a2e45c9`.
- Final SHA / workflow runs / failures: **not embedded here yet**. Read live GitHub and report actual state. Do not treat this omission as PASS.
- On a verified stage-1 terminal SHA: continue only after all required CI gates are green, then begin stage **2/10** with a new explicit instruction.
