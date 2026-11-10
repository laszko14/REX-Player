# MPVRex — CHECKPOINT POLICY (stage 3/10)

## Source of truth
Repository: `laszko14/REX-Player`, owner-approved MPV Alpha v56 lineage `build/player-transparency-v56`
at `74b059ab0d2c5492451c42f88ff993128a2e45c9`.
Always read live GitHub branch, **full HEAD SHA**, `AGENTS.md`, and this policy at
that exact SHA. The machine-readable snapshot is **one file**:
`docs/ai/CHECKPOINT_STATE.json`. Human entry point: `docs/AI_HANDOFF.md`.
No duplicate parallel "current state" files. A read-only legacy handoff
snapshot is preserved at `docs/ai/archive/AI_HANDOFF_STAGE1_2.md`; it is
historical evidence, **not** a second live checkpoint.

The JSON records verified historical stages and **one active stage**, not
an automatic GitHub check. `completed[].evidence` contains a run ID and
matching SHA observed live at the time of recording; the validator checks
only structure, not GitHub. Revalidate required jobs and head SHA through
the official GitHub connector before resuming or asserting PASS.

## Status transitions
- Active stage: `IN_PROGRESS`, `CI_PENDING`, `CI_BLOCKED` or `NOT_TESTED`.
- `CODE_VERIFIED` is reserved for completed historical entries with live,
  externally verified success for **both** source and Android jobs at one
  terminal SHA. A skipped signing job is acceptable when no APK is requested.
- Never claim completion from a pending/skipped/cancelled/old-SHA workflow.
- After a code change, verify CI again on the new final SHA. A prior run is
  not sufficient. Never create a chain of extra commits merely to
  write the just-observed run outcome back into its own commit.
- The current active entry may still be `CI_PENDING` in a committed snapshot
  even after CI subsequently finishes. **This is intentional**, because the
  committed file cannot know the result of CI for its own SHA in advance.
  During resumption compare snapshot with **live** GitHub; never treat pending
  in an older snapshot as an unresolved CI if GitHub has since proven success.
- Promote the previous stage to `completed` and set the next `active`
  **only in the next authorized implementation**, after checking live CI.
  Keep the historical SHA and run ID. Do not fabricate missing history.

## Commands (Python 3.12+)
Read-only structural validation (also performed in CI):
```bash
python3 .github/scripts/mpvrex_checkpoint.py validate
python3 .github/scripts/mpvrex_checkpoint.py show
```

Explicit, **local-only**, atomic update of pending stage notes, after live HEAD
verification and on the checked-out, correct task branch:
```bash
python3 .github/scripts/mpvrex_checkpoint.py record --write \
  --expected-head "$(git rev-parse HEAD)" \
  --stage 3 --status CI_PENDING \
  --next-action "Check final CI on exact SHA" \
  --summary "Checkpoint and tests committed"
```
This modifies only the JSON file. It never pushes, creates commits, starts
workflows, signs an APK or edits previous verified stages. It rejects a
different stage, stale HEAD, divergent/wrong branch, uncommitted checkpoint,
wrong origin and shallow ancestry. Review the diff before committing.

## Every stage
1. Preflight: read current HEAD and GitHub Actions; check prior stage evidence.
2. Diagnose task, implement and test, review diff.
3. Record a current active snapshot with pending/blocked status only, if needed.
4. Commit/push scoped changes and verify required GitHub Actions **on the last
   full SHA**, including source and Android jobs; inspect job/step/log on FAIL.
5. Publish a short factual report with exact SHA/run, limitations, APK/device
   status. For the next stage, check the last result again before promoting it
   to historical completed and resuming.
6. Never modify a historical entry without explicit evidence and audit note.
   Every new stage has its own branch from the last verified terminal SHA.

## Limits
This mechanism does **not** automatically synchronize ChatGPT Sources, run
CI or perform live checks. No GitHub Actions job writes state back to the
repository. The operator/assistant must make an authorized commit to
persist an update; a local `record` alone is NOT durable in GitHub.
No Secrets, passwords, keystores, streaming URLs, cookies, or raw private logs.
