# MPV Alpha — podglądy HUD i przycisków Lua przed wydaniem APK

Workflow: [MPV Alpha - podglad HUD i Lua (PNG)](../.github/workflows/ui-screenshot-previews.yml).

## Jak korzystać (Android / GitHub)

1. Przy zmianie plików wyglądu kontrolek w nowym Pull Request uruchamia się automatycznie zadanie **MPV Alpha - podglad HUD i Lua (PNG)**.
2. Można je również uruchomić ręcznie: **GitHub → Actions → MPV Alpha - podglad HUD i Lua (PNG) → Run workflow** (wybierz właściwą gałąź).
3. Po ukończeniu pobierz artefakt **MPV-Alpha-HUD-Lua-podglady-PNG** z zakładki tego przebiegu. ZIP zawiera minimum pięć obrazów PNG: ciemne i jasne tło, kontrast włączony/wyłączony, pogrubienie i układ pionowy.
4. Dopiero po obejrzeniu obrazów zdecyduj, czy zmiany zasługują na **kolejną** wersję podpisanego APK. Workflow podglądu nie dotyka podpisywania, tagów ani numerów wersji.

## Co dokładnie pokazują zrzuty?

Sceny używają **faktycznego komponentu Compose `ContrastVideoLabel`** odpowiadającego za renderowanie liter w MPV Alpha, wraz z przykładowymi przyciskami i pozycjami HUD. Tła są syntetyczne i deterministyczne: nie zawierają materiału z filmów użytkownika. Rozmieszczenie przycisków jest **ilustracją** ułatwiającą porównanie wyglądu czcionki; to nie jest automatyczny zrzut pełnego odtwarzacza ani sprawdzian gestów, położeń zapisanych w ustawieniach i faktycznego wideo. Zmiany w samym układzie panelu wymagają rozszerzenia fixture'a w `app/src/screenshotTest/` lub testu na urządzeniu.

## Polecenia Gradle

```sh
./gradlew :app:updateDebugScreenshotTest --no-daemon --no-configuration-cache
```

Obrazy trafiają do `app/src/screenshotTestDebug/reference/` i do ZIP z GitHub Actions. Samo wygenerowanie podglądów nie wymaga kompilacji i podpisania kompletnego APK ani emulatora, ale kompiluje część kodu UI na potrzeby renderowania host-side. W razie potrzeby po zaakceptowaniu referencji można użyć `./gradlew :app:validateDebugScreenshotTest` i otrzymać raport porównawczy.

## Gdy podgląd się nie powiedzie

Sprawdź sekcję **Wygeneruj podglady Android Compose** w logach GitHub Actions. Nie traktuj nieudanego zadania jako poprawnego podglądu ani nie wydawaj APK tylko dlatego, że zwykły kompilator przeszedł. Potencjalne problemy eksperymentalnego narzędzia (Compose Screenshot Testing 0.0.1-alpha16) należy diagnozować na podstawie logów.

## Podgląd układu produkcyjnego (dodane)

Nowe sceny z pliku `PlayerLayoutScreenshots.kt` używają **produkcyjnego komponentu `LuaButtonGrid`** z testowym zapisem 35 miejsc (również puste) oraz **wspólnego z aplikacją `HudBarContent`**. Przykłady obejmują:
- 5 kolumn / 7 rzędów w trybie edycji, również puste komórki;
- ten sam układ w trybie odtwarzania (puste komórki pozostawiają odstępy);
- układ pionowy z ograniczoną szerokością;
- 3 i 7 kolumn z różnymi rozmiarami i odstępami;
- pasek HUD w poziomie i pionie, zawijanie elementów oraz duży rozmiar;
- wspólną scenę Lua + HUD do oceny ich wzajemnego rozmieszczenia.

Sceny używają sztucznych danych i wyłącznie syntetycznych teł. Renderują rzeczywiste **wewnętrzne układy** przycisków i HUD, ale nie odtwarzają pełnej nakładki `PlayerControls` ani prawdziwego odtwarzania mpv. Nie zastępują testów gestów, przeciągania, zasłaniania przez inne kontrolki i rzeczywistej orientacji telefonu. Własne ustawienia użytkownika nie są pobierane ani przesyłane.

Kiedy zmieniasz rozmieszczenie Lua/HUD, aktualizuj wspólne produkcyjne komponenty oraz sceny; przy zmianach położenia w `PlayerControls` potrzebne są jeszcze testy całego ekranu na urządzeniu.
