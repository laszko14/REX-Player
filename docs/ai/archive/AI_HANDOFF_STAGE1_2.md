# ARCHIWUM — checkpoint etapów 1–2 (tylko historia)

> Niezmieniony zapis `docs/AI_HANDOFF.md` z SHA `4e7db043b4d9a8d6cce47d134b141e14fecc592d`.
> Stan bieżący i wyniki CI sprawdzać w aktualnym `docs/AI_HANDOFF.md`,
> `docs/ai/CHECKPOINT_STATE.json` oraz LIVE GitHub Actions.

---

# MPVRex — AI handoff (initial stage-1 checkpoint)

> **Snapshot for human/agent handoff, not a live CI result.** Before resuming, verify the current branch HEAD and Actions run/jobs at that exact SHA. This file is intentionally basic; stage 3/10 will implement the complete checkpoint policy and validation.

## Canonical product and constraints
- Repo: `laszko14/REX-Player`.
- Owner-selected installed baseline: MPV Alpha **v56 debug**, `build/player-transparency-v56`, SHA `74b059ab0d2c5492451c42f88ff993128a2e45c9`.
- Initial V3.2 system branch: `docs/mpvrex-v32-stage01-foundation-20261110`, created from the exact v56 SHA; this is a docs and minimal CI-bootstrap line only.
- Do **not** adopt divergent `master` as product baseline, migrate historical Auto Audio code, merge PRs, modify playback or sign an APK.

## Staged modernization plan (user-approved: 10 stages)
1. Establish v56 working branch and foundational GitHub instructions — **CODE_VERIFIED**, terminal commit `99d19f3daaeba5c037a947b508e481fac2c82088`, Actions [42729647590](https://github.com/laszko14/REX-Player/actions/runs/42729647590), source and Android jobs SUCCESS.
2. Preflight: branch/SHA/source-of-truth enforcement — **implemented in stage-2 work branch; required final CI pending verification**.
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
- First implementation commit: `9e6ab57f80adb97002867fcaf318686b339b0cc1`; Actions run [42728386046](https://github.com/laszko14/REX-Player/actions/runs/42728386046), attempt 1, **completed/success on that SHA**. Required `YAML, Python i Lua` and `Android - build, testy, lint` jobs: **success**. APK signing job: **skipped by design**. No APK or device test.
- This handoff update produces a **new Git commit**. Its final full SHA and the outcome of required CI **must be checked live on that new exact SHA** before declaring `CODE_VERIFIED`. Never treat previous run as verification of the new commit.
- On a verified stage-1 terminal SHA: continue only after all required CI gates are green, then begin stage **2/10** with a new explicit instruction.

## Stage 2/10 — automated preflight (2026-11-10)

- Starting point: stage-1 verified SHA `99d19f3daaeba5c037a947b508e481fac2c82088`, Actions [42729647590](https://github.com/laszko14/REX-Player/actions/runs/42729647590), **completed/success** on exact SHA, sources+Android PASS, signing intentionally skipped.
- Branch: `docs/mpvrex-v32-stage02-preflight-20261110`, created directly from stage-1 final commit.
- Scope: `docs/ai/STARTUP_PREFLIGHT.md`, read-only script `.github/scripts/mpvrex_preflight.py`, 12 unit test cases, `AGENTS.md` and GitHub Actions source job. No app/audio, Secrets, signing, release, merge or APK changes.
- Mandatory checks: preflight on actual GitHub checkout, positive/negative unit tests, workflow syntax/Python, localization, and Android assembleDebug, testDebugUnitTest, lintDebug. On a new SHA inspect live GitHub Actions run/attempt/jobs.
- Final CI result for this checkpoint's own commit: **NOT KNOWN UNTIL LIVE FINAL-SHA CHECK**. This is not PASS. Before stage 3: verify ALL required checks on terminal full SHA, do not rely on earlier green runs.
- Next after successful verification: stage **3/10** — persistent checkpoint policy with safe resumption.

### Stage 2 hardening and terminal-verification rule
- Review found that accepting a user-overridable CLI baseline could defeat v56 lineage protection. The final implementation fixes the approved SHA in the CLI, requires an explicit full expected HEAD and adds two negative regression tests.
- GitHub Actions for the stage-2 final commit must be checked live after the final push. Do not report CODE_VERIFIED from the earlier intermediate commit's run; that run may be cancelled by concurrency policy.

### Zweryfikowany przebieg pierwszego finalnego kodu
- Commit `516c0e96638d34d484da60c1ef3584c74de1880b` — Actions [42732711851](https://github.com/laszko14/REX-Player/actions/runs/42732711851), attempt 1: **completed/success** na tym SHA. Source preflight i 30 testów Pythona: PASS; Android build/unit tests/Lint: SUCCESS; signing APK: skipped celowo.
- Dodatkowy przegląd wykrył błędnie escapowane znaczniki Markdown w dokumentacji; poprawiono je i dodano test regresyjny w kolejnym commicie. **Wynik CI ostatniego commita dokumentacyjnego sprawdzić live, zanim zostanie uznany za CODE_VERIFIED.**
