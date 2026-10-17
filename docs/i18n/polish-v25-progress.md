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

## Etap 2b — Kotlin/Compose

- Wydzielono 438 tekstów i 8 zasobów liczby mnogiej w 57 plikach produkcyjnych. Domyślne zasoby pozostają angielskie; polskie odpowiedniki są w `values-pl/ui_audit.xml`. Pozostałe języki nadal korzystają ze swoich zasobów lub poprawnego języka domyślnego.
- Uwzględniono HUD, Lua, ustawienia, napisy, odtwarzacz/OSD, powiadomienia, yt-dlp, Jellyfin, przeglądarkę, listy odtwarzania i komunikaty udostępniania.
- Niezależny przegląd wskazał dodatkowe komunikaty audio, skryptów i udostępniania, odmianę potwierdzenia usunięcia z listy i spacje w dołączanych etykietach; poprawki wprowadzono przed kompilacją.
- Kontrola zasobów, referencji oraz białych znaków przeszła. Teksty strony Web Share są korygowane osobno. Wyniki renderowania i kompilacji będą zapisane po ich zakończeniu.

## Etap 2c — strona Web Share

- Dodano 12 tekstów i zasób liczby mnogiej (16 polskich wartości) dla strony generowanej HTML/JavaScript: tytuł, pobieranie/odtwarzanie, wysyłanie plików, ZIP, stan połączenia i komunikaty przeglądarki.
- Wybrany język aplikacji jest przekazywany do strony. Polskie nazwy plików i teksty są kodowane jako bezpieczne dane JSON; endpointy i logika JavaScript pozostają bez zmian.
- Sprawdzono składnię JavaScript i zgodność parametrów zasobów. Odpowiedzi diagnostyczne serwera i komunikaty bibliotek/systemu są odrębną warstwą wymagającą rozróżnienia od etykiet interfejsu.

## Etap 3 — automatyczna kontrola

- `check_polish.py`: kompletność tłumaczeń, składnia XML i Unicode, parametry formatowania (indeks/typ/liczność), polskie formy `one/few/many/other`, znaczniki tekstu, terminologia oraz jawna lista sprawdzonych nazw/formatów, które nie wymagają tłumaczenia.
- Dodatkowy skaner wykrywa angielskie teksty w wybranych bezpośrednich wywołaniach Compose, etykietach, opisach dostępności i komunikatach. Jest zabezpieczeniem heurystycznym, nie dowodem automatycznej poprawności wszystkich zdań.
- `check_polish_previews.py`: wymaga PNG wszystkich pięciu rzeczywistych ekranów w obu rozmiarach czcionki i sprawdza pliki PNG oraz wymiary. Nie zastępuje oceny nakładania się elementów.
- 16 testów narzędzi przeszło, w tym ujemne przypadki brakującego tłumaczenia, błędnego formatu, uszkodzonego XML, zmienionej nazwy technologii, niewłaściwej terminologii i brakującego PNG.
- Oba walidatory włączono do istniejących workflow; nie dodano nowego YAML. Do podpisywania dodano sprawdzenie publicznego odcisku certyfikatu v24, bez zapisu klucza prywatnego.

## Korekty po przeglądzie i pierwszej kompilacji

- Poprawiono kontekst OSD (`appContext`) i import `R` w udostępnianiu. Odczytano pełną listę błędów kompilatora przed poprawkami.
- Dosłowne procenty w sześciu opisach oznaczono `formatted="false"`; nie zmieniono znaków ani wartości widocznych dla użytkownika.
- Polski dialog usunięcia z listy używa pełnych zdań odmienianych liczebnikowo. Inne języki zachowują dotychczasowy zasób.
- Dodano jeszcze siedem polskich komunikatów błędów Web Share (bez zmiany statusów HTTP, endpointów i kluczy JSON). Łączny wynik audytu: 1230 zmienionych/dodanych wartości tekstowych, z osobnym liczeniem form liczby mnogiej.
- Końcowy przegląd tych poprawek nie wskazał dalszych konkretnych usterek. Renderowanie i zbiorcza walidacja pozostają w toku.

## Dostosowanie testów wyszukiwarki

- Po wydzieleniu dawnych angielskich literałów cztery testy odwoływały się do nieużywanego już pola `title`. Testy sprawdzają teraz zasób oraz dotychczasowy ekran i pozycję docelową; zachowano sprawdzanie wyszukiwania po angielsku.
- Dodano regresję polskich zapytań („rozdzielczość”, „niedosłyszących”, „dwukrotnego dotknięcia”).
- Serwer Web Share zachowuje angielski wariant bez kontekstu Androida, używany w testach JVM; przy rzeczywistym kontekście korzysta z języka aplikacji.

## Etap 4 — rzeczywiste podglądy ustawień

- Dodano dziesięć scen produkcyjnych ekranów Compose: po dwa warianty Gestów, Wyglądu, Odtwarzania, Dźwięku i Przycisków Lua.
- Szerokość 360 dp, czcionka 100%/130%, wysokie widoki dla opcji poniżej pierwszego ekranu.
- Sceny nie korzystają z danych użytkownika ani rzeczywistych plików: osobna pamięć preferencji, nieaktywny rejestr zewnętrznych aktywności i DAO, kontekst językowy polski.
- To nie są atrapy przerysowane ręcznie: fixture wywołuje `Screen.Content()` dla każdego ekranu. Wynik renderowania i inspekcji PNG zostanie zapisany oddzielnie.
