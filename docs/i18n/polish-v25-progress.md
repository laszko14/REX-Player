# Polski interfejs MPV Alpha v25 — postęp

Baza: `6d9f0fb3c17fba1b5564f54ff50ac17bef79c714` (v24).

## Etap 1 — inwentaryzacja

- 1298 wpisów w domyślnych zasobach podlegających tłumaczeniu (w tym elementy liczby mnogiej); 20 brakujących odpowiedników polskich.
- Zasoby: `app/src/main/res/values/strings.xml`, `values-pl/strings.xml`, pozostałe katalogi językowe; osobna nazwa aplikacji debug w `app/src/debug/res/values/strings.xml`.
- Teksty bezpośrednio w Kotlin/Compose: ustawienia, edytory Lua/HUD, odtwarzacz i komunikaty, przeglądarka mediów, okna dialogowe oraz powiadomienia. Teksty protokołów, logi diagnostyczne, kody Lua użytkownika i identyfikatory preferencji nie są tłumaczeniami.
- Istniejący proces: `build.yml` (walidacja źródeł, testy JVM, Lint, kompilacja; osobny job podpisywania) i `ui-screenshot-previews.yml` (hostowe zrzuty Compose). Nie potrzeba nowego workflow.
- Ostatni tag: `mpv-alpha-v24`. Istniejący job podpisywania v24 zakończył się powodzeniem. Klucz pozostaje wyłącznie w sekretach GitHub Actions; metadane sekretów nie są dostępne dla bieżącej integracji.
- Aktualizacja musi zachować `xyz.mpv.rex.debug`, stały podpis oraz nowy tag `mpv-alpha-v25`. Wydanie dopiero po pozytywnej walidacji tego samego commita na GitHubie.
- Poprzednie lokalne zmiany Lua zachowano osobno w commicie `61fa8caf`; ten audyt nie przenosi ich na v24 i nie zmienia zachowania obecnych przycisków.

## Kolejne etapy

2. Korekta zasobów i wydzielenie tekstów interfejsu do zasobów.
3. Walidator tłumaczeń z testami: kompletność, formaty, XML, terminologia i nazwy technologii.
4. Przegląd zmian i PNG rzeczywistych ekranów ustawień w języku polskim.
5. Testy, Android Lint i kompilacja kontrolna na GitHubie.
6. Jeden podpisany APK v25 ARM64 oraz raport z ograniczeniami weryfikacji.

Każdy etap jest zapisywany osobnym commitem. Wyniki i odnośniki będą uzupełniane dopiero po wykonaniu odpowiednich kontroli.

## Etap 2a — zasoby XML

- Przejrzano wszystkie 1298 wpisów źródłowych. Poprawiono lub dodano 737 polskich wpisów: 697 zmian istniejących tekstów oraz 40 uzupełnień (braki i polskie formy liczby mnogiej).
- Zachowano identyfikatory zasobów, komplet parametrów formatowania i znaczniki tekstu.
- Zweryfikowano osobno niezmieniane nazwy własne, technologie, symbole, formaty i wspólne polsko-angielskie słowa (np. „port”, „folder”).
- Walidacja zasobów przeszła. Wydzielanie napisów z Kotlina oraz sprawdzenie wyglądu są w toku; nie jest to jeszcze walidacja APK.
