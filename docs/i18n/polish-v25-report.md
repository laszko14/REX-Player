# Audyt polskiego interfejsu MPV Alpha v25

## Zakres i liczby

Baza audytu: v24, commit `6d9f0fb3`. Przejrzano wszystkie 1298 źródłowych wpisów podlegających tłumaczeniu oraz bezpośrednie napisy w Kotlin/Compose i stronie Web Share.

**1230 poprawione lub dodane polskie wpisy tekstowe**, z formami liczby mnogiej liczonymi osobno:

- 737 zmian i uzupełnień w istniejącym `values-pl/strings.xml` (697 poprawek i 40 uzupełnień).
- 470 polskich wartości w wydzielonych zasobach `ui_audit.xml` (438 tekstów i osiem zasobów liczby mnogiej po cztery formy).
- 23 polskie wartości dla strony Web Share (19 tekstów i cztery formy liczby mnogiej).

Teksty wydzielone z kodu mają angielski język domyślny i polskie odpowiedniki. Pozostałe tłumaczenia i dotychczasowe identyfikatory zasobów zachowano. Nie tłumaczono kodów Lua, kluczy preferencji, protokołów, nazw plików użytkownika ani diagnostycznych logów.

## Najważniejsze korekty

| Przed | Po |
|---|---|
| Dwukrotny czas trwania wyszukiwania | Czas przewijania po dwukrotnym dotknięciu |
| Double Tap Seek Obszar Szerokość | Szerokość obszaru dwukrotnego dotknięcia |
| Podwójny kran (lewy) | Dwukrotne dotknięcie (lewa strona) |
| Podwójne stukanie (środek) | Dwukrotne dotknięcie (środek) |
| Zagraj / przerwij | Odtwórz / wstrzymaj |
| Użyj jednego dotknięcia dla gestu centrum | Sterowanie środkiem ekranu jednym dotknięciem |
| Sterowanie odtwarzaczem szkła | Szklany wygląd przycisków odtwarzacza |
| Tło paska widokowego szkła | Szklany wygląd paska postępu |
| Pokaż grę w nagłówku | Pokaż przycisk odtwarzania na górnym pasku |
| Catppucyna | Catppuccin |
| Current | Obecnie / Bieżący, zależnie od kontekstu |
| Włączenie / wyłączenie wysokich temperatur pionowych kanałów wideo | Włącz lub wyłącz dynamiczny strumień krótkich filmów w pionie |

„Szukaj” pozostało przy wyszukiwaniu plików/napisów; przewijanie materiału jest nazywane „przewijaniem”. Nazwy Lua, HUD, HDR, DRC, mpv, Vulkan, MediaCodec, kodeków i motywów pozostają rozpoznawalne.

## Sprawdzone źródła i ekrany

Przegląd językowy objął: gesty, wygląd/motywy, odtwarzanie, dźwięk/korektor/DRC, napisy, obraz/dekoder/HDR, HUD, przyciski i edytory Lua, zaawansowane ustawienia, yt-dlp, Jellyfin, przeglądarkę plików i sieci, listy odtwarzania, miniodtwarzacz, menu i dialogi, OSD, powiadomienia, komunikaty błędów i stronę Web Share.

Podglądy renderują produkcyjne ekrany Compose: **Gesty, Wygląd, Odtwarzanie, Dźwięk i Przyciski Lua**. Każdy ma wariant o szerokości 360 dp przy skali czcionki 100% i 130%. Używają sztucznych preferencji, bez filmów i danych użytkownika. Wysokie obrazy pokazują kolejne ustawienia; nie są zrzutami działającego filmu ani testem dotyku.

## Kontrola automatyczna i przegląd

- `python3 .github/scripts/check_polish.py` — kompletność, XML/Unicode, parametry formatowania, polskie formy liczby mnogiej, znaczniki, terminologia, nazwy technologii i heurystyczne wykrywanie wybranych angielskich literałów UI.
- `.github/i18n/polish-technical-names.json` — jawnie przejrzane wyjątki dla nazw, symboli, przykładów danych i słów mających taką samą poprawną pisownię w obu językach.
- `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` — testy walidatorów, obejmujące usterki, które powinny blokować CI.
- `python3 .github/scripts/check_polish_previews.py` — obecność dziesięciu PNG dla wszystkich wymaganych ekranów.
- Istniejące workflow wykonują testy JVM, Android Lint, kompilację i renderowanie. Nie dodano nowego workflow.
- Niezależny przegląd kodu wskazał pominięte komunikaty, odmianę potwierdzenia usunięcia z listy, zgodność innych języków i zachowanie spacji w zasobach. Poprawiono wskazane miejsca.

Wyniki faktycznie ukończonych testów i podglądy zawiera końcowa sekcja raportu.

## Granice weryfikacji i ręczna kontrola

- Testy PNG obejmują wybrane statyczne stany ustawień. Na urządzeniu wymagają kontroli: orientacja pozioma, nietypowe rozmiary/fonty systemowe, otwarte edytory/dialogi, HUD i Lua podczas filmu oraz ich interakcje.
- Komunikaty systemowe Androida, sieci i zewnętrznych bibliotek mogą pochodzić z systemu/biblioteki w innym języku. Aplikacja tłumaczy własny opis błędu; surowej diagnostyki nie zastępuje fikcyjnym tłumaczeniem.
- Nazwy i kody Lua oraz pliki i metadane użytkownika są zachowywane dokładnie; mogą zawierać dowolny język.
- Skaner literałów jest heurystyczny. Nie daje matematycznej gwarancji wykrycia każdego zdania budowanego dynamicznie. Naturalność tłumaczenia i wygląd wymagają także przeglądu człowieka.

## Aktualizacja v25

Wydanie następuje dopiero po pomyślnych testach na GitHubie. Wykorzystywany jest istniejący klucz z GitHub Actions, bez udostępniania sekretów. Pakiet musi pozostać `xyz.mpv.rex.debug`, wersja ma mieć nazwę `v25` i kod większy od `1000242` z v24. Podpis jest porównywany z publicznym odciskiem certyfikatu wydanej v24; inny podpis zatrzymuje wydanie.

## Zakończenie v25 — wyniki

- **1230** poprawionych/dodanych polskich wartości tekstowych (697 zmian i 40 uzupełnień w dawnym XML, 470 nowych wartości ustawień, 23 Web Share; formy liczby mnogiej liczone oddzielnie).
- PR #25 (scalony po zielonym CI): https://github.com/laszko14/REX-Player/pull/25
- Kontrola i18n, YAML/Python/Lua, Android Build, testy JVM i Android Lint: **SUCCESS** na head PR: https://github.com/laszko14/REX-Player/actions/runs/39082841107
- Podglądy PNG: **SUCCESS** — pięć rzeczywistych ekranów ustawień przy 100% i 130% fontu: https://github.com/laszko14/REX-Player/actions/runs/39082841122/artifacts/12119028477
- Ręczny przegląd PNG wykrył i usunął łamanie etykiety motywu (obecnie „System”) i gniecenie „Mocny” w presetach Lua; ponownie wyrenderowane obrazy potwierdzają poprawkę.
- Jeden podpisany APK ARM64 v25 (73 271 665 bajtów): https://github.com/laszko14/REX-Player/actions/runs/39083513429/artifacts/12119887651
- Workflow podpisał dotychczasowym certyfikatem i zweryfikował: xyz.mpv.rex.debug, versionName v25, versionCode 1000252. Nowy tag: mpv-alpha-v25.
- SHA-256 APK: 7c7ace24cf1854e9cf050871dc716c2fd440e83fbb0801e33047b7e7fd1a883d.
- Android Lint wykorzystuje dotychczasowy baseline. Dwa nowe pliki angielskich zasobów mają ściśle zakresowy wyjątek MissingTranslation dla języków innych niż polski, których dotąd nie tłumaczono; event-handlery w zbadanych plikach mają zakresowy wyjątek LocalContextGetResourceValueCall. Pozostałe reguły Lint działają; polskie tłumaczenia osobno kontroluje check_polish.py.
- Wymagające ręcznego sprawdzenia na urządzeniu: układ poziomy, niestandardowy rozmiar ekranu/czcionki, dialogi, gesty, HUD i przyciski Lua w trakcie filmu, systemowe komunikaty oraz zewnętrzne biblioteki. Statyczne podglądy nie gwarantują bezbłędności wszystkich stanów dynamicznych.
