# MPVRex V3.2 — SYSTEMATIC DEBUGGING (etap 5/10)

**Zakres:** tylko `laszko14/REX-Player`, linia MPV Alpha v56, bez kodu odtwarzacza i bez APK. Jedyny rejestr przypadków: [ERROR_CASES.json](ERROR_CASES.json), zgodnie z [ERROR_LEARNING.md](ERROR_LEARNING.md). Nie twórz osobnej bazy zgłoszeń ani nie kopiuj informacji z Zavro Clean. Każde sprawdzenie zaczynaj od live HEAD, `AGENTS.md`, checkpointu i wymaganych CI dla właściwego SHA.

## Standardowa procedura (obowiązkowa po FAIL)

1. **Objaw:** zbierz minimalny, zanonimizowany komunikat, komponent i warunek. Nie zapisuj tokenów, prywatnych ścieżek, URL-i strumieniowania, cookie, surowego logu ani danych użytkownika.
2. **Reprodukcja:** ustal dokładne wejście / fixture / urządzenie, wersję, SHA i to, czy błąd występuje ponownie. Gdy jest niedeterministyczny, udokumentuj warunki, nie deklaruj reprodukcji bez dowodu.
3. **Log i dowody:** odczytaj dokładny GitHub workflow, run, attempt, job, krok, kod wyjścia i odpowiednie linie logów. Logi oglądaj w narzędziu; do rejestru zapisuj wyłącznie streszczenie i referencję `run_id`/`attempt`. Wyodrębnij, które joby wykonano, a które pominięto.
4. **Klasyfikacja i hipoteza:** wyznacz `code`, `test_fixture`, `configuration`, `infrastructure`, `permissions` lub `external_dependency`. Oddziel `confidence=hypothesis` od `confirmed`. Najpierw sprawdź istniejący fingerprint (category|component|signature), potem przyczynę; nie twórz duplikatu.
5. **Minimalna poprawka:** wskaż potwierdzoną przyczynę i najmniejszą zmianę. Nie wyłączaj testów, nie zmieniaj progów i nie ignoruj nieudanych jobów; nie wprowadzaj zmian w zabezpieczeniach, podpisie ani danych aplikacji bez osobnej zgody.
6. **Test diagnostyczny:** uruchom próbę odtwarzającą wcześniejszy objaw; sprawdź, że po zmianie przechodzi. Dodaj test negatywny, gdy naprawa dotyczy walidacji lub błędnej akceptacji.
7. **Regresje:** uruchom bezpośrednie testy komponentu i istotne testy sąsiednich ścieżek. W przypadku błędu fixture nie dopasowuj reguły produkcyjnej do niepoprawnych założeń testu.
8. **SOURCE/Android CI:** po commicie i pushu sprawdź dokładny **końcowy** pełny SHA, run/attempt i oba wymagane joby: `YAML, Python i Lua`, `Android - build, testy, lint`. PR CI może testować inny checkout; nie zamieniaj go z SOURCE CI. `skipped`, pending, stary SUCCESS oraz nieodczytany wynik **nie** są PASS.
9. **Trwały zapis:** zarejestruj przypadek lub nawrót w `ERROR_CASES.json` według `ERROR_LEARNING.md` i zapisz checkpoint w zatwierdzonym etapie. Zawsze oddziel wynik pierwszego FAIL od późniejszego zweryfikowanego naprawienia; walidator pliku nie dowodzi zewnętrznego CI.

## Powtórzenia i rozróżnianie statusów

- **Ten sam fingerprint:** jeden identyfikator `MPV-ERR-xxxx`; nowe fakty jako obserwacje z unikalnym `event_key`. Zdarzenie o tym samym identyfikatorze nie może naliczać drugiego wystąpienia. Zmiana etapu nie zmienia fingerprintu.
- **OPEN/INVESTIGATING:** reprodukcja i analiza przyczyny; hipoteza nie jest dowodem.
- **FIXED_UNVERIFIED:** przyczyna i poprawka są opisane, ale potrzebne są regresje i weryfikacja live CI na właściwym SHA. Historyczny FAIL w rejestrze nie oznacza, że końcowe CI nadal jest czerwone.
- **VERIFIED:** tylko gdy potwierdzono przyczynę, naprawę, testy i wymagane CI; rejestr przechowuje deklarację, a asystent musi niezależnie sprawdzić prawdziwy wynik w GitHubie.
- **REOPENED:** nowe zdarzenie po wcześniejszym potwierdzeniu; porównaj objaw i przyczynę, nie kopiuj automatycznie poprzedniego rozwiązania.
- **BLOCKED:** udokumentuj przyczynę, brakującą czynność i status `CI_BLOCKED` lub `NOT_TESTED` odpowiednio do rzeczywistego stanu.
- **Dwa jednakowe FAIL bez nowych danych:** zmień hipotezę lub metodę diagnostyczną. Nie wykonuj serii identycznych retry i nie kasuj historii. Ponawiaj niezmieniony workflow tylko dla potwierdzonego przejściowego błędu infrastruktury; po zmianie kodu kontroluj nowy SHA.
- **Bez pobocznych etapów:** naprawę wykonuj w tym samym zatwierdzonym etapie. Nie rozpoczynaj kolejnego IMPLEMENT, dopóki nie masz wymaganych zielonych kontroli albo udokumentowanej blokady.

## Narzędzie diagnostyczne (odczyt, bez zapisów)

```bash
python3 .github/scripts/mpvrex_debugging.py validate
python3 .github/scripts/mpvrex_debugging.py match \
  --category test_fixture --component checkpoint \
  --signature active_stage_fixture_after_promotion
python3 .github/scripts/mpvrex_debugging.py triage \
  --case-id MPV-ERR-0001 --consecutive-failures 2
```

`match` ustala istniejący przypadek po fingerprint; `triage` podaje następną **sugerowaną** czynność i dziewięcioelementowy proces. Liczbę kolejnych FAIL operator wpisuje na podstawie dowodów; skrypt **nie odczytuje** zdarzeń z GitHub i nie zgaduje. `validate` to offline bramka struktury; w GitHub Actions wykonuje się w trybie read-only. Żadna komenda nie uruchamia samodzielnie GitHub Actions, nie zapisuje błędów, nie publikuje ani nie podpisuje APK.

## Przykład historyczny potwierdzony w etapie 4

MPV-ERR-0001 i MPV-ERR-0002 wynikły z nieprawidłowych założeń fixture. Przyczynę i poprawkę zapisano, a późniejszy run `42743439273`, attempt 1, na SHA `b6ba374ca057d74babe434c6a8ec06543e76c0ad` zakończył SOURCE i Android sukcesem. W rejestrze wpisy zachowują stan historycznego `FIXED_UNVERIFIED` / wcześniejszego FAIL; **nie** udawaj, że samo stare zdarzenie wskazuje końcowy sukces. Nowy wpis lub zmiana statusu wymaga wiarygodnych odrębnych dowodów.

## Granice etapu

To systematyzacja diagnostyki, identyfikacji nawrotów i kontrola spójności rejestru. Pełne bramki jakości i jednolite SOURCE/PR CI będą rozbudowane w etapach 6–7. Traktuj każdy wygenerowany plan jako wskazówkę, a nie dowód wykonania testów. Dla testów odtwarzania, dźwięku i gestów potrzebny jest rzeczywisty test urządzenia; Android build tego nie zastąpi.
