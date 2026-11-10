# MPVRex V3.2 — STARTUP PREFLIGHT (etap 2/10)

## Baza
Projekt: MPV Alpha v56 debug, repozytorium \`laszko14/REX-Player\`.
Zatwierdzona baza historyczna: \`build/player-transparency-v56\` /
\`74b059ab0d2c5492451c42f88ff993128a2e45c9\`.
Aktualne gałęzie zadaniowe \`docs/mpvrex-v32-*\` muszą pochodzić z tej bazy.
Rozbieżne \`master\` nie jest podstawą instalowanej aplikacji.

## Preflight — obowiązkowy przed rozpoczęciem pracy
1. Rozpoznaj zakres i upoważnienie: ANALYZE, IMPLEMENT albo VERIFY.
2. Odczytaj LIVE w GitHubie pełne SHA zatwierdzonej gałęzi i aktualnej gałęzi zadania.
3. Odczytaj z JAWNEGO tego samego SHA: \`AGENTS.md\`, \`CONTRIBUTING.md\`,
   \`docs/AI_HANDOFF.md\` i dokumentację funkcji związanej z zadaniem.
4. Sprawdź faktyczną historię CI na tym SHA (workflow/run/attempt/job), a nie tylko checkpoint.
5. Potwierdź pochodzenie gałęzi od v56, bezpieczeństwo zmian i brak konfliktów.
6. Przed implementacją na lokalnym checkout uruchom poniższy walidator (bez edycji plików):

\`\`\`bash
python3 .github/scripts/mpvrex_preflight.py \
  --expected-branch docs/mpvrex-v32-stage02-preflight-20261110 \
  --expected-sha "$(git rev-parse HEAD)"
\`\`\`

Wymagany Python 3.12+ i pełna historia Git. Skrypt weryfikuje checkout,
40-znakowy SHA, dozwoloną nazwę gałęzi, przodka v56, \`origin\`, brak zmian
i obecność niepustych *śledzonych* dokumentów w HEAD. Nie wykonuje zapisów,
fetch, push, buildów, zmian ustawień ani operacji na APK. Przy FAIL nie ignoruj
błędu — ustal przyczynę. Na płytkim checkout pobierz pełną historię; przy
modyfikacjach najpierw przejrzyj stan i uruchom ponownie na czystym HEAD.

## CI
Dla zatwierdzonych pushy gałęzi \`docs/mpvrex-v32-*\` źródłowy job CI wykonuje:
\`\`\`bash
python3 .github/scripts/mpvrex_preflight.py \
  --ci --expected-branch "$GITHUB_REF_NAME" --expected-sha "$GITHUB_SHA"
\`\`\`
Waliduje również GitHub push context, pozwalając na detached HEAD tylko w
zgodnym kontekście CI. Checkout musi mieć \`fetch-depth: 0\`.
Testy regresji są wykonywane przez istniejący \`unittest discover\` w
\`.github/scripts/\`; pełne Android build, unit tests i Lint są osobno wymagane.

## Granice weryfikacji
Skrypt sprawdza **lokalny checkout względem przekazanego SHA**, nie odpytuje
GitHub o najnowszy HEAD i nie sprawdza, czy AI przeczytało pliki.
Samo \`--expected-sha "$(git rev-parse HEAD)"\` nie potwierdza świeżości:
weryfikacja LIVE przez bezpośredni konektor GitHub jest zawsze obowiązkowa.
Nie dowodzi również poprawnego merge PR, wyników SOURCE/PR CI, podpisu APK
ani testu na telefonie. Te kwestie kontroluj osobno zgodnie z zakresem zadania.

Prawdziwy sukces oznacza, że wszystkie wymagane kontrole są zielone na
**końcowym pełnym SHA**. \`CI_PENDING\`, \`skipped\` i brak danych nie są PASS.
Nie uruchamiaj starych workflow podpisujących lub zmieniających Kotlin.
W razie błędu podaj dokładne fakty, nie zgaduj i nie obniżaj bramek.
