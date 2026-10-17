# Android Lint baseline — MPV Alpha

## Why there is a baseline

The first Android Lint CI check on PR #4 completed Android compilation and Kotlin unit tests, but failed on already-existing findings (395 errors, 551 warnings, 18 hints as reported on 2026-10-17).

The tracked `app/lint-baseline.xml` is a *snapshot*, not a blanket exemption:
- Findings matching the baseline are omitted from subsequent lint failure reports.
- Newly introduced lint errors still fail `./gradlew :app:lintDebug`.
- New warnings are still surfaced by lint; they are not automatically converted into build failures.
- Fixes for old findings remain welcome: a baseline does not mean that the problems are solved.

The baseline is generated from the source snapshot on branch `chore/mpv-alpha-agent-ci-20261017`, which has no modifications to Android application sources except enabling the baseline in Gradle. The snapshot must be committed once and not regenerated in regular CI runs.

## Normal checks

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --stacktrace
```

The CI workflow requires `app/lint-baseline.xml` to be **committed** and nonempty. Do not add a CI step to generate a fresh baseline just before lint: that would make newly introduced errors invisible.

## Intentionally updating the baseline

1. Prefer **fixing** reported issues rather than suppressing them.
2. If a lint rule/plugin version changes, or after a large intentional cleanup, use an explicit separate branch and inspect the source diff first.
3. Regenerate deliberately after review using the Gradle task appropriate to the Android Gradle plugin. Depending on plugin version, `./gradlew :app:updateLintBaseline` or removing the old baseline and running `./gradlew :app:lintDebug -Dlint.baselines.continue=true` will recreate it.
4. Inspect the exact `git diff -- app/lint-baseline.xml` and explain every class of newly added findings in the PR. Do not accept a bulk increase without review.
5. Re-run unit tests, lint, and the debug build. Keep device UI/playback validation as a separate manual step.

For current Android documentation, see: https://developer.android.com/studio/write/lint#snapshot
