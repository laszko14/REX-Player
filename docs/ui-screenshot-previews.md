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
