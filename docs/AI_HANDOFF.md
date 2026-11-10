# MPVRex — AI HANDOFF / kontynuacja V3.2

**Kanoniczny rejestr stanu:** docs/ai/CHECKPOINT_STATE.json.
**Zasady checkpointu:** docs/ai/CHECKPOINT_POLICY.md.
**Preflight:** docs/ai/STARTUP_PREFLIGHT.md.
**Nowy rejestr błędów:** docs/ai/ERROR_CASES.json; instrukcje docs/ai/ERROR_LEARNING.md.
**Historia etapów 1–2:** docs/ai/archive/AI_HANDOFF_STAGE1_2.md.

## Baza i bezpieczeństwo
- Repo: laszko14/REX-Player; właściciel korzysta z MPV Alpha v56 debug.
- Zatwierdzona baza: build/player-transparency-v56, commit 74b059ab0d2c5492451c42f88ff993128a2e45c9.
- master jest rozbieżny. Nie wykonuj merge/rebase do master, release, force-push, zmian sekretów/certyfikatu ani podpisywanego APK bez osobnej zgody.
- Nie zmieniaj funkcjonalności odtwarzacza, Lua, HUD, gestów, zapisanych ustawień, konfiguracji ani danych użytkownika.

## Zakończone i potwierdzone etapy
1/10: bazowa dokumentacja v56; SHA 99d19f3daaeba5c037a947b508e481fac2c82088, run 42729647590, attempt 1, źródła i Android SUCCESS.
2/10: automatyczny preflight; SHA 4e7db043b4d9a8d6cce47d134b141e14fecc592d, run 42734552364, attempt 1, źródła i Android SUCCESS.
3/10: trwałe checkpointy; SHA 002215acc5decc8d25c93f7bd150666a0a9e46b7, run 42737573772, attempt 1, źródła i Android SUCCESS, podpisany APK skipped. Przed etapem 4 ponownie sprawdzono HEAD, CI i pochodzenie od zatwierdzonej v56.

## Etap aktywny: 4/10 — rejestr błędów
Gałąź: docs/mpvrex-v32-stage04-error-registry-20261110.
Punkt wyjścia: 002215acc5decc8d25c93f7bd150666a0a9e46b7.
Zakres: wersjonowany JSON, schemat i walidacja, fingerprinty, wykrywanie powtórzeń, chroniony lokalny zapis, statusy hipoteza/potwierdzenie/ponowne wystąpienie, testy pozytywne i negatywne, odczyt w GitHub Actions. Nie wprowadzono żadnych domniemanych błędów do rejestru: początkowo jest pusty. To nie jest jeszcze automatyczna telemetria ani autonomiczne pobieranie logów.
**Snapshot active=CI_PENDING oznacza stan zapisany przed zakończeniem własnego CI. Nie jest potwierdzeniem sukcesu ani dowodem, że CI nadal trwa po zakończeniu runu.**

## Zarejestrowany błąd i naprawa podczas etapu 4
- MPV-ERR-0001: regresja fixture testu negatywnego checkpointu po przejściu active 3 → 4.
- Pierwszy run 42742959215, attempt 1, SHA b78af59cec876503544a5cd5258159b9c0f013e0: SOURCE FAIL tylko w test_active_stage_must_follow_verified; Android skipped wskutek zależności.
- Przyczyna potwierdzona logiem: fixture ustawiał 4, teraz prawidłowy aktywny etap; test musi ustawiać 5, by sprawdzić odrzucenie.
- Poprawiono fixture. Rejestr zachowuje FIXED_UNVERIFIED i FAIL historycznego runu; wynik następnego CI jest zawsze weryfikowany na żywo, a nowy wpis nie udaje przyszłego PASS.

## Druga usterka testowa etapu 4
- MPV-ERR-0002: run 42743225632, attempt 1, SHA fcecf8584e40e6dfae8f334e690b4b2832651dd0, SOURCE FAIL w siedmiu testach rejestru. Android skipped zależnie od SOURCE.
- Przyczyna: fixture testów wykorzystywał pusty kanoniczny rejestr; po zapisaniu MPV-ERR-0001 rejestr stał się niepusty i testy błędnie traktowały prawdziwy wpis jak własny testowy.
- Poprawka: izolowany pusty fixture plus osobny test walidacji faktycznego rejestru. Wpis FIXED_UNVERIFIED nie deklaruje sukcesu jeszcze nieprzeprowadzonego końcowego CI.

## Wymagana końcowa kontrola etapu 4
1. Odczytaj live HEAD gałęzi i sprawdź, że pochodzi od zatwierdzonej v56.
2. Zweryfikuj workflow na dokładnym końcowym SHA, attempt, wymagany job YAML/Python/Lua (w tym preflight, checkpoint, nowy rejestr i testy) oraz Android build, testy jednostkowe, Lint. Oba joby muszą zakończyć się SUCCESS.
3. Nie uruchamiaj podpisywanego APK. Nie deklaruj testów urządzenia, które nie miały miejsca.
4. Na FAIL odczytaj konkretny job/krok/log, napraw przyczynę w tym etapie i sprawdź nowe końcowe SHA. Przy CI_PENDING nie zgłaszaj CODE_VERIFIED.
5. Po zielonym CI zgłoś wynik. Nie twórz cyklicznych commitów wyłącznie dla zapisania wyniku CI samego commita. Przy następnej komendzie DALEJ przenieś zweryfikowany etap 4 do historii completed i dopiero rozpocznij etap 5/10 na osobnej gałęzi.

## Zatwierdzony plan 10 etapów
1. Instrukcje v56; 2. Preflight; 3. Checkpointy; 4. Rejestr błędów; 5. Systematic Debugging; 6. Bramki jakości; 7. SOURCE/PR CI; 8. Bezpieczne APK — osobna zgoda; 9. Integracja ChatGPT; 10. Końcowy audyt i regresje.

**Zasada wznowienia:** żywy GitHub i dokumenty z konkretnego SHA mają pierwszeństwo przed zapisanym snapshotem i starymi źródłami projektu. Nie korzystaj z instrukcji Zavro Clean zamiast zasad MPVRex.

## Kontynuacja etapu 5/10 — Systematic Debugging

Poprzedni etap 4/10 **CODE_VERIFIED** na `b6ba374ca057d74babe434c6a8ec06543e76c0ad` (workflow `42743439273`, attempt 1; źródła i Android SUCCESS, podpis APK skipped). Potwierdzono przez live GitHub przed rozpoczęciem etapu 5. Historyczne FAIL etapu 4 pozostają w jedynym rejestrze jako `FIXED_UNVERIFIED` według chwili ich zapisu; późniejszy sukces CI nie uzasadnia nadpisania historii dowodu.

Aktualna gałąź: `docs/mpvrex-v32-stage05-systematic-debugging-20261110`, utworzona bezpośrednio od powyższego SHA. Zatwierdzony zakres: `docs/ai/DEBUGGING_PROTOCOL.md`, read-only `.github/scripts/mpvrex_debugging.py` (validate, match, triage), testy, kontrola struktury procesu w SOURCE CI, aktualizacja `AGENTS.md` i `ERROR_LEARNING.md`. Brak zmian aplikacji, certyfikatu i APK.

**Status zapisany w commicie: CI_PENDING (snapshot, nie live PASS).** Przed ukończeniem etapu sprawdź aktualny HEAD na tej gałęzi oraz SOURCE i Android CI na **dokładnym końcowym SHA**, attempt i każdy wymagany job. W przypadku FAIL odczytaj dokładne logi i usuń przyczynę w etapie 5. Nie uruchamiaj etapu 6 bez weryfikacji. Po zakończeniu etapu 5 kolejne `DALEJ` rozpocznie **6/10 — bramki jakości**.


## Etap 6/10 — automatyczne bramki jakości (aktywny)

Etap 5/10 Systematic Debugging jest **CODE_VERIFIED** na dokładnym SHA `3d8611e981fe993a1ced867bf7509fa40f92550a`; workflow `42746110283`, attempt 1, oba wymagane joby SOURCE i Android SUCCESS, podpis APK skipped. Wynik i HEAD sprawdzono na żywo przed etapem 6.

Gałąź robocza: `docs/mpvrex-v32-stage06-quality-gates-20261110` (od `3d8611e981fe993a1ced867bf7509fa40f92550a`). W etapie 6 zapisano `docs/ai/QUALITY_GATES.md`, skrypt `.github/scripts/mpvrex_quality.py`, testy pozytywne i negatywne, walidację w źródłowym CI oraz korekty fixture testów, aby prawidłowo obsługiwały kolejne etapy. Zakres nie obejmuje aplikacji, APK, certyfikatów, merge ani zmian zabezpieczeń.

**Checkpoint w commicie: CI_PENDING (snapshot).** Nie stanowi dowodu końcowego PASS. Przed zgłoszeniem `CODE_VERIFIED` sprawdź rzeczywisty pełny HEAD gałęzi i zakończony run/attempt na tym SHA: job `YAML, Python i Lua` oraz `Android - build, testy, lint` muszą mieć SUCCESS. Na FAIL odczytaj krok i log, napraw przyczynę oraz przetestuj nowy SHA. Nie uruchamiaj etapu 7 bez zweryfikowanego sukcesu.

Po zakończeniu i następnym zatwierdzonym poleceniu **DALEJ**: etap **7/10 — modernizacja SOURCE CI i PR CI**. W tej aktualizacji nie uruchamiano podpisanego APK ani testu na urządzeniu. Historyczne wpisy rejestru błędów zachowują statusy i dawną dokumentację FAIL; nie wprowadzaj fikcyjnych wpisów.

## Etap 7/10 — SOURCE CI i PR CI (aktywny)

Etap 6/10 **CODE_VERIFIED** na pełnym SHA `50170ae71ddbb65993260f0c36bd8a1673f30d3c`: workflow push `42748935978`, attempt 1, źródła i Android SUCCESS; podpisany APK skipped. Sprawdzono ponownie live HEAD i wynik wymaganych jobów przed rozpoczęciem etapu 7.

Gałąź: `docs/mpvrex-v32-stage07-source-pr-ci-20261110`, od `50170ae71ddbb65993260f0c36bd8a1673f30d3c`. Zakres: docs/ai/SOURCE_PR_CI.md, .github/scripts/mpvrex_pr_ci.py, testy pozytywne/negatywne, workflow .github/workflows/build.yml, istniejące bramki jakości i dokumentacja. SOURCE push sprawdza commit zadaniowy, PR sprawdza odrębny merge SHA i dwie gałęzie; nie wolno utożsamiać tych wyników. Nie zmieniono aplikacji, APK, certyfikatu, sekretów, historii ani master.

**Checkpoint w commicie: CI_PENDING, nie live PASS.** Po push sprawdź SOURCE CI na dokładnym finalnym SHA. Dla PR CI zweryfikuj oddzielny prawdziwy run pull_request na konkretnym PR merge SHA i HEAD SHA, oba wymagane joby i attempt. Bez rzeczywistego PR runu zgłoś PR_NOT_TESTED, a nie sukces. Po CI PASS zgłoś CODE_VERIFIED tylko gdy komplet wymaganych kontroli ma potwierdzony wynik. Przy FAIL przeanalizuj log, napraw błąd w tym etapie, sprawdź nowe SHA. Zakaz merge, release i podpisu APK.

Dopiero następne **DALEJ**, po poprawnym zakończeniu etapu 7, rozpocznie etap **8/10 — bezpieczne APK (osobna zgoda na kompilację i podpis)**.

## Etap 8/10 — bezpieczne APK (aktywny)

Etap 7/10 SOURCE/PR CI potwierdzono na żywo: pełny SHA `20e9894e1ea6c60df858be8b734d041d3823fb02`, SOURCE push run `42751454971` attempt 1 SUCCESS i osobny PR CI run `42751599103` attempt 1 SUCCESS, oba wymagane joby SUCCESS. Draft PR #65 zamknięty bez merge. Gałąź etapu 8: `docs/mpvrex-v32-stage08-safe-apk-20261110`, bezpośrednio od zatwierdzonego SHA etapu 7.

Zadanie obejmuje `docs/ai/SAFE_APK.md`, fail-closed `.github/scripts/mpvrex_apk.py`, negatywne testy `test_mpvrex_apk.py`, osobny job `signed_v56_apk` w `.github/workflows/build.yml` i bramkę jakości, bez zmian aplikacji i klucza. Nowy job może działać wyłącznie przy manualnym `workflow_dispatch` właściciela, jawnej dodatkowej autoryzacji, dokładnym SHA i wersji; zwykły push/PR nie podpisuje. Historyczny master ma odrębny workflow i nie jest bazą v56.

**Snapshot w commicie: CI_PENDING, nie live PASS.** Zweryfikuj na końcowym SHA SOURCE i osobne PR CI (merge checkout), oba wymagane joby. Realne podpisanie APK wymaga odrębnej zgody i potwierdzenia dostępności dispatchu na właściwej gałęzi; bez tego nie deklaruj APK_VERIFIED. Nie twórz tagu ani release, nie wykonuj merge ani testu urządzenia. Następny etap 9/10 wyłącznie po ukończeniu 8 i kolejnej komendzie DALEJ.
