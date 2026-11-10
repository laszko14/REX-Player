# MPVRex V3.2 — SOURCE CI i PR CI (etap 7/10)

## Zakres i gałęzie

Projekt wyłącznie laszko14/REX-Player — MPV Alpha v56, baza build/player-transparency-v56, SHA 74b059ab0d2c5492451c42f88ff993128a2e45c9. Nie używaj rozbieżnego master jako bazy MPV Alpha v56. Zmiany w etapie 7 dotyczą tylko GitHub Actions, testów i instrukcji; bez zmian kodu odtwarzacza, danych, wydania lub podpisanego APK.

## SOURCE CI

Zdarzenie push na gałęziach docs/mpvrex-v32-* uruchamia pełny checkout (fetch-depth: 0) na dokładnym GITHUB_SHA. Krok preflight wymaga zgodności nazwy zadaniowej gałęzi, SHA, origin i pochodzenia od zatwierdzonego v56. Następnie checkpoint, kanoniczny rejestr błędów, Systematic Debugging, bramka jakości, walidacja workflow/Python, regresje, polskie zasoby i Lua; Android wykonuje build, testy jednostkowe oraz Lint.

SOURCE CI jest weryfikowany przez run event=push, head_branch=zadaniowa gałąź, head_sha=końcowy HEAD, attempt i oba obowiązkowe joby:
- YAML, Python i Lua
- Android - build, testy, lint

Brak lub skipped któregokolwiek z tych jobów oznacza brak CODE_VERIFIED.

## PR CI

pull_request jest odrębnym zdarzeniem i ma **inny checkout**: refs/pull/NUMBER/merge, commit GITHUB_SHA będący merge-testem zmian na gałęzi docelowej. **Nie utożsamiaj GITHUB_SHA (merge) z pull_request.head.sha (kod źródłowy PR).** Obie wartości raportuj i weryfikuj osobno.

Workflow obsługuje PR kierowane do docs/mpvrex-v32-*, zatwierdzonej historycznej build/player-transparency-v56 oraz master (dla zachowania dotychczasowej kompatybilności). Dla PR do master nie wymuszaj pochodzenia od v56: ta gałąź jest rozbieżna i ma własny historyczny kontrakt CI. Dla PR do gałęzi v56 używaj tylko zwykłego pull_request z permissions.contents=read — **nie pull_request_target**. Kod i workflow PR są niezaufane; żadnych sekretów, podpisów, push ani mutacji źródeł.

Obowiązkowy fail-closed krok po checkout:
1. W SOURCE sprawdź GITHUB_EVENT_NAME, REPOSITORY, GITHUB_REF=refs/pull/NUMBER/merge, numer PR, GITHUB_BASE_REF, GITHUB_HEAD_REF i pola z GITHUB_EVENT_PATH.
2. Porównaj git rev-parse HEAD z GITHUB_SHA, HEAD^1 z pull_request.base.sha oraz HEAD^2 z pull_request.head.sha. Wymagaj pełnej historii i właściwego origin.
3. Sprawdź przodka v56 dla base i head oraz czy oba SHA są przodkami merge. Odrzuć checkout wyłącznie head, shallow checkout, rozbieżne repo, fałszywy numer lub inną bazę.
4. Uruchom walidację checkpointu, rejestru, diagnostyki i jakości; wymagane syntaktyczne kontrole i unittest oraz Android build/test/lint pozostają obowiązkowe.
5. W jobie Android ponownie sprawdź niezależny merge checkout i SHA przed Gradle.
6. Weryfikuj **żywy PR run**, numer/attempt, event=pull_request, GITHUB_SHA merge oraz wyniki wymaganych jobów; wynik push z gałęzi SOURCE nie wystarcza do uznania PR CI.

Read-only lokalna kontrola kontraktu workflow:

    python3 .github/scripts/mpvrex_pr_ci.py workflow

PR merge context (wyłącznie w prawdziwym GitHub Actions, z --ci i GITHUB_EVENT_PATH):

    python3 .github/scripts/mpvrex_pr_ci.py validate --ci

Skrypt nie uruchamia GitHub API, nie tworzy PR i **nie twierdzi, że live PR CI przeszło**. W przypadku braku PR można potwierdzić tylko kontrakt lokalny; PR CI oznacz NOT_TESTED, nie sukces. Żywe PR CI wymaga bezpiecznie autoryzowanego PR i rzeczywistego workflow.

## Kontrole

Bezpieczeństwo i regresje:
- Source push oraz PR merge nie mogą się nawzajem zastępować, ponieważ sprawdzają inne SHA i inne checkouty.
- Brak jednego joba, odfiltrowanie PR do zatwierdzonej linii, błędny if, niewykonanie PR walidatora, brak testów lub ustawienie continue-on-error powoduje FAIL.
- W workflow utrzymuj jedno źródło konfiguracji, jeden kanoniczny checkpoint i jeden rejestr błędów; nie dubluj historii wyników.
- Istniejący job signed_apk może działać tylko na odrębnie zatwierdzonym mechanizmie master; PR-y i gałęzie docs go nie uruchamiają. W tym etapie go nie modyfikuj.
- Nie zmieniaj ochrony gałęzi, certyfikatu, sekretów, wersji, pakietu, ABI ani dostępu repozytorium.
- Przed kolejnym etapem potwierdź SOURCE CI na końcowym SHA i — gdy PR CI jest elementem kryteriów akceptacji — niezależnie prawdziwy PR run na checkout merge.

## Ograniczenia

Statyczna kontrola workflow i lokalne testy symulowanych zdarzeń nie stanowią dowodu rzeczywistego PR merge checkout ani zakończonego joba Android. Źródłem prawdy jest GitHub Actions i aktualny HEAD. Jeśli PR-run nie istnieje, nie oznaczaj PR CI jako PASS; zapisz brakującą weryfikację w checkpoint/handoff. Nie wykonuj merge, release ani podpisywanego APK bez osobnej zgody. Nie uruchamiaj PR na rozbieżny master w celu testowania v56.
