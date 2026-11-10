# MPVRex V3.2 — trwały rejestr błędów (etap 4/10)

## Kanoniczne źródło prawdy
- Repo: laszko14/REX-Player; wyłącznie zatwierdzona linia MPV Alpha v56.
- Rejestr: docs/ai/ERROR_CASES.json. Walidator: .github/scripts/mpvrex_errors.py.
- Nie kopiuj błędów Zavro Clean, nie importuj historycznych usterek rozbieżnego master. Początkowa pusta lista nie oznacza, że błędów nigdy nie było. Historyczne wpisy wymagają osobnej kontroli dowodów.
- Zapisany numer runu, status lub SHA to deklaracja w danych, nie wynik żywego zapytania do GitHub. Przed CODE_VERIFIED potwierdź HEAD, run/attempt, wymagane joby i checkout na żywo.

## Algorytm diagnozy i uczenia się na błędach
1. Ustal obserwowalny objaw oraz bezpieczne, zanonimizowane warunki odtworzenia.
2. Sprawdź rejestr przed dodaniem. Klasyfikuj: code, test_fixture, configuration, infrastructure, permissions lub external_dependency. Oddziel hypothesis od confirmed.
3. Fingerprint to SHA-256 z trzech nieosobowych slugów category|component|signature; nie zawiera numeru etapu. Ten sam fingerprint oznacza jeden przypadek. Każde zdarzenie ma własny stabilny event_key. Ponowny zapis identycznego zdarzenia jest idempotentny, a nowe zdarzenie zwiększa liczbę obserwacji.
4. Zapisz potwierdzoną przyczynę, rzeczywistą poprawkę, test diagnostyczny i test regresji. Status VERIFIED wymaga confidence=confirmed, przyczyny, poprawki, testów i ci.status=SUCCESS wraz z run_id/attempt. Walidacja offline nie dowodzi jednak prawdziwości runu ani zielonego CI.
5. Nawrót po VERIFIED zapisuj jako REOPENED z nowym event_key. Nie usuwaj wcześniejszych obserwacji, testów lub potwierdzonego rozwiązania. Nie cofaj numeru etapu.
6. Po FAIL czytaj dokładny workflow, run, attempt, job, krok i log. Usuwaj przyczynę, potem weryfikuj test diagnostyczny, regresje, SOURCE oraz Android CI na końcowym pełnym SHA. PENDING, skipped, FAIL ani historyczny SUCCESS nie są PASS.

## Odczyt i walidacja

    python3 .github/scripts/mpvrex_errors.py validate
    python3 .github/scripts/mpvrex_errors.py show
    python3 -m unittest discover -s .github/scripts -p "test_*.py" -v

Walidator odrzuca: niewłaściwy projekt lub bazę, nieznane pola, nieprawidłowe identyfikatory, duplikaty fingerprintów, niespójne SHA, daty i statusy, błędną historię obserwacji, podstawowe wzorce sekretów, adresów URL i PII. Kontrola wzorców nie gwarantuje wykluczenia wszystkich wrażliwych danych; przed commitem przejrzyj wpis.

## Rejestrowanie przypadku — wyłącznie przy autoryzowanym IMPLEMENT

Przygotuj lokalny, nieśledzony plik JSON zdarzenia zawierający komplet pól: stage, category, component, signature, symptom, condition, confidence, root_cause, fix, diagnostic_tests, regression_test, status, branch, sha, ci (status/run_id/attempt), event_key, seen_on (YYYY-MM-DD). root_cause, fix i regression_test mogą być null przed potwierdzeniem. Statusy: OPEN, INVESTIGATING, FIXED_UNVERIFIED, VERIFIED, REOPENED, BLOCKED. Nie wpisuj fikcyjnych danych lub testów.

    python3 .github/scripts/mpvrex_errors.py record --write --expected-head "$(git rev-parse HEAD)" --event-file /tmp/mpvrex-incident.json

Komenda wymaga poprawnego aktywnego checkpointu, gałęzi MPVRex v56, pełnego i zgodnego SHA, właściwego origin, pełnej historii Git i czystego pliku rejestru. Wykonuje wyłącznie atomowy zapis lokalnego JSON; NIE wykonuje commit, push, GitHub API ani podpisywania APK. Następnie sprawdź diff, uruchom testy, zapisz zatwierdzony commit/push i skontroluj CI końcowego SHA.

## Prywatność, zakres i przyszłe etapy
Nie zapisuj haseł, cookie, tokenów, kluczy, certyfikatów, pełnych logów, adresów prywatnych, URL-i streamingu, danych historii odtwarzania ani surowych danych użytkownika. Używaj zanonimizowanych sygnatur i nazw komponentów, bez telemetrii. W CI uruchamiaj tylko read-only validate, nie record. Pełne Systematic Debugging, bramki jakości i SOURCE/PR CI zostaną rozwinięte w etapach 5–7. Kod odtwarzacza i proces podpisu APK w etapie 4 pozostają bez zmian.
