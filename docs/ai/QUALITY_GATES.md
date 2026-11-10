# MPVRex V3.2 — bramki jakości (etap 6/10)

## Zakres

Jedyny właścicielski projekt: MPV Alpha v56, repozytorium laszko14/REX-Player. Obowiązuje 10 zatwierdzonych etapów, bazowe SHA 74b059ab0d2c5492451c42f88ff993128a2e45c9 i oddzielne gałęzie docs/mpvrex-v32-*. Nie zmieniamy odtwarzacza, gestów, Lua, HUD, danych użytkownika ani procesu podpisu APK.

Bramka kontroluje **strukturę i spójność** plików, testów i kontraktu workflow. Nie zastępuje testów Androida, przeglądu diff, odczytu live GitHub, analizy logów ani testu na telefonie. Status aktywnego checkpointu CI_PENDING przed zakończeniem workflow jest poprawnym snapshotem, nie dowodem końcowego CI.

Jedynym stanem etapów pozostaje docs/ai/CHECKPOINT_STATE.json, jedynym rejestrem incydentów pozostaje docs/ai/ERROR_CASES.json. Ten etap nie wprowadza równoległego rejestru decyzji ani samozatwierdzających deklaracji CODE_VERIFIED.

## Bramki

1. **Checkpoint i statusy** — istnieje kanoniczny JSON ze zweryfikowaną strukturalnie historią, kolejnością etapów, SHA i pełnymi identyfikatorami workflow. Aktywny etap może być CI_PENDING, IN_PROGRESS, CI_BLOCKED lub NOT_TESTED; nie może zadeklarować CODE_VERIFIED bez weryfikacji zewnętrznej. Historyczny wynik success w JSON to jedynie zapis.
2. **Dokumentacja** — AGENTS.md, CONTRIBUTING.md, docs/AI_HANDOFF.md, dokumenty preflight, checkpoint, rejestr błędów, Systematic Debugging i niniejsza polityka istnieją, zawierają wymagane sekcje i wskazują aktualną gałąź oraz SHA poprzedniego etapu. Instrukcje nie mogą udawać przeprowadzonego testu.
3. **Rejestr incydentów** — zachowuje prawidłowe identyfikatory i fingerprinty. Testy diagnostyczne i regresyjne wskazane przez nazwę test_* muszą istnieć w odkrywanych modułach. FIXED_UNVERIFIED nie staje się automatycznie VERIFIED po samym green CI; potwierdzenie wymaga odrębnych dowodów.
4. **Wykrycie testów** — istnieją pakiety preflight, checkpoint, błędów, Systematic Debugging i bramek jakości. Kluczowe testy pozytywne i negatywne muszą być metodami z rzeczywistą asercją; pusty plik lub metoda bez asercji nie wystarcza.
5. **SOURCE CI** — workflow na gałęziach docs/mpvrex-v32-* musi wykonywać preflight, walidację checkpointu i błędów, Systematic Debugging, bramkę jakości, walidator YAML/Python, odkrywanie testów i kontrolę polskich zasobów. Obowiązkowych kroków nie wolno wyciszać przez continue-on-error.
6. **Android CI** — musi zależeć od SOURCE CI, uruchamiać się dla dokumentacyjnych pushy i PR, wykonywać :app:assembleDebug, :app:testDebugUnitTest i :app:lintDebug. Brak joba, pominięty job, brak zadania albo niezgodny checkout oznacza brak PASS. Kontrakt nie dowodzi, że Gradle zakończył się sukcesem.
7. **Podpis APK** — job podpisywania pozostaje odseparowany od gałęzi dokumentacyjnych i wymaga osobnej autoryzacji. Ten etap niczego nie podpisuje i nie zmienia sekretów, numeracji wersji, certyfikatu ani wydania.

## Testy

Lokalna walidacja tylko do odczytu:

    python3 .github/scripts/mpvrex_quality.py validate

Raport strukturalny (nigdy wynik live CI):

    python3 .github/scripts/mpvrex_quality.py report

Pełna regresja Python (Python 3.12, PyYAML 6.x):

    python3 -m unittest discover -s .github/scripts -p "test_*.py" -v

Testy negatywne muszą powodować FAIL przy: usuniętym jobie Android, brakującym Lint lub testach Android, pominiętej walidacji, nieuruchamianym workflow na gałęziach MPVRex, brakującej regresji, niekompletnym handoffie, fałszywej ścieżce podpisu APK i wyłączonej kontroli. Nie obniżaj progów ani nie zastępuj testów fikcyjnym PASS.

## Ograniczenia

Po każdej implementacji sprawdź GitHub HEAD gałęzi, pełny SHA, run, attempt, obydwa wymagane zadania YAML/Python/Lua oraz Android build/testy/lint i ich rzeczywisty status. CI_PENDING oznacza, że trwa, CI_BLOCKED wymaga udokumentowanej przeszkody, NOT_TESTED oznacza brak testu. Wyłącznie kompletne wymagane SUCCESS na **końcowym SHA** pozwala zgłosić CODE_VERIFIED.

W etapie 6 wdrażamy bramki dla istniejącego źródłowego workflow i linii dokumentacyjnej. Pełna modernizacja rozdzielnych SOURCE CI oraz PR CI (inne checkouty i konteksty) pozostaje etapem 7/10. Budowanie i autoryzacja podpisanego APK to dopiero etap 8/10 z osobną zgodą. Używaj bezpośredniego konektora GitHub, a nie zewnętrznych przeglądarek automatyzujących GitHub.

## SOURCE / PR CI — dodatkowa bramka etapu 7

Zasady pełnej weryfikacji znajdują się w [SOURCE_PR_CI.md](SOURCE_PR_CI.md). Po etapie 7 strukturalny walidator jakości wymaga zatwierdzonych triggerów PR do linii v56, obowiązkowej walidacji `refs/pull/NUMBER/merge` i jego dwóch rodziców (base SHA/head SHA), niezależnego checkoutu i kontroli w SOURCE oraz Android, niezmienionego bezpiecznego joba podpisu. Testy negatywne odrzucają head-only checkout, niezweryfikowane PR, pominięte walidatory i wyłączenie kontroli. Prawdziwe `PR CI SUCCESS` wymaga jednak osobnego runu zdarzenia pull_request; lokalny PASS nie jest dowodem GitHub Actions.
