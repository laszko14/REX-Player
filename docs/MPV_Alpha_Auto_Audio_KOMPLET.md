# MPV Alpha — Auto Audio: pełna dokumentacja projektu

**Stan odniesienia:** 18 października 2026 r.  
**Repozytorium:** https://github.com/laszko14/REX-Player  
**Kanoniczna dokumentacja w GitHubie:** https://github.com/laszko14/REX-Player/blob/master/docs/MPV_Alpha_Auto_Audio_KOMPLET.md  
**Zakres:** ustalenia z rozmów „Auto Audio 1”, „Auto Audio 2”, „Auto Audio 3”, wyniki prób v44/v45 oraz potwierdzone opisy zmian GitHub PR #40–46.  
**Ostatni potwierdzony etap Auto Audio:** v46 — PR #46 scalony do `master`, kontrola CI dla PR zakończona sukcesem. Test fizyczny v46 na telefonie **nie został jeszcze potwierdzony**.

> Ten dokument jest źródłem wiedzy i punktem kontynuacji pracy. Rozróżnia funkcje wdrożone, wyniki rzeczywistych testów i dopiero planowane elementy. Nie oznacza, że wszystkie funkcje są już sprawdzone na telefonie. Przed każdą nową zmianą sprawdź również aktualny kod repozytorium.

## 1. Cel i nadrzędny priorytet

**Auto Audio** ma oceniać **oryginalny dźwięk filmu przed filtrami użytkownika** i proponować właściwe ustawienia istniejących filtrów. Najważniejszym celem jest **naturalna, czytelna, nieostra i niesycząca mowa**, także w źle nagranych filmach i wywiadach. Dźwięk nie powinien być nadmiernie odszumiony, sztucznie wyostrzony, zniekształcony ani mieć „pompowania” głośności. Lepiej pozostawić dobry dźwięk bez zmian niż proponować niepewną korektę.

System ma umieć również **zmniejszyć lub wyłączyć** zbyt silny filtr, a nie wyłącznie zwiększać jego intensywność. Ocena parametrów oryginału nie jest automatycznie dowodem, że zastosowane filtry pogarszają brzmienie; do takiego wniosku potrzebny jest osobny pomiar albo odsłuch A/B.

## 2. Nienaruszalne wymagania

1. **Streaming jest podstawowym sposobem oglądania.** Auto Audio musi działać na filmie odtwarzanym przez internet, w tym w istniejącym torze HTTP/HLS/DASH mpv.
2. **Analiza przed filtrami.** Materiał pomiarowy to oryginalny sygnał z dekodera, sprzed Lua/EQ/Szum/Głos/DRC/Dialogue Enhance; wynik nie może zależeć od bieżących ustawień użytkownika.
3. **Bez ponownego pobierania ani otwierania filmu.** Nie uruchamiać drugiego żądania strumienia, zewnętrznego dekodowania URL ani przewijania w celu pobierania próbek.
4. **Bez zacięć i ingerencji w odtwarzanie.** Nie przerywać streamingu, nie zatrzymywać filmu, nie usuwać filtrów użytkownika, nie zmieniać prędkości odtwarzania. Należy pilnować CPU/RAM/baterii.
5. **Zachowanie 1,5×–3×.** Typowe przyspieszone oglądanie nie może być uszkodzone przez analizę.
6. **Dwa tryby:** szybki **15 s**, dokładny **3 × 10 s**.
7. **Propozycja, nie samowolna zmiana.** Jedna ostrożna zmiana naraz → możliwość odsłuchu A/B → akceptacja albo odrzucenie → przywrócenie poprzedniego stanu.
8. **Bez normalizacji głośności.** Użytkownik całkowicie z niej zrezygnował z powodu „falowania/pompowania” i pogorszenia jakości.
9. **Bez atrap.** Brak realnych danych lub pewności musi być jawnie zgłoszony. Nie wolno przedstawiać statystyk FFmpeg jako prawdziwego rozpoznawania mowy (VAD).
10. **Bez regresji.** Nie naruszać istniejących Lua, gestów, przycisków, ustawień, cache, streamingu ani pracy rozwijanej równolegle w innych obszarach MPV Alpha.
11. **Pełna odwracalność i stan użytkownika.** Każda propozycja musi zachowywać wcześniejsze konfiguracje i umożliwiać precyzyjny powrót.
12. **Systematic Debugging:** zebrać objawy, sprawdzić kod/logi i różnice względem działającego przypadku, znaleźć przyczynę, wdrożyć minimalną poprawkę, uruchomić testy i potwierdzić wynik.

## 3. Tryby i tor pomiarowy

### Szybka analiza — 15 s

Jeden 15-sekundowy fragment, lekki pomiar orientacyjny. Nie należy na jego podstawie wykonywać ryzykownych zmian zależnych od rozpoznania mowy. W v42 podkreślono, że szybki tryb pozostaje lżejszy od szczegółowego.

### Dokładna analiza — 3 × 10 s

Trzy **oddzielne** 10-sekundowe fragmenty z aktualnie odtwarzanego streamu; we wdrożeniu v41 przewidziano **2 s przerwy** pomiędzy fragmentami. Należy pokazać dane oddzielnie oraz ocenę łączną. W v43 wprowadzono wymóg zgodności co najmniej **2/3** lub **3/3** próbek przed określonymi propozycjami tonalnymi. Nie uśredniać ślepo sprzecznych scen i nie przedstawiać niepewnego wyniku jako rozpoznania dialogów.

### Faktyczny sposób pobierania informacji (v41–v46)

- W istniejącym torze mpv dołączany jest tymczasowo **pasywny filtr diagnostyczny** `@rex_auto_audio_probe` przed efektami użytkownika.
- Stosowane są statystyki FFmpeg `astats` oraz — w szczegółowym trybie — `aspectralstats`; metadane są odczytywane bez ponownego otwierania źródła.
- Po pomiarze usuwany jest **wyłącznie własny filtr diagnostyczny**; efekty użytkownika powinny pozostać nienaruszone.
- Dane: RMS, poziomy/szczyty, oszacowane tło, dynamika; w trybie widmowym centroid częstotliwości, spectral flatness (płaskość) i rolloff.
- To są **metryki**, nie surowe próbki dostępne dla zewnętrznego klasyfikatora mowy i **nie VAD**. Prawdziwy VAD wymaga sprawdzonego punktu odbioru oryginalnych próbek PCM w natywnym torze mpv.
- Jeśli pomiar jest niedostępny, uszkodzony lub zbyt krótki, należy zwrócić błąd/zachętę do ponowienia zamiast fikcyjnej rekomendacji.

## 4. Istniejące filtry i integracje

| Filtr | Zakres / informacje | Miejsce w Auto Audio |
|---|---|---|
| **Szum Lua** | OFF oraz 7 poziomów; istniejący `@rex_szum`, model RNNoise | Docelowo dobór intensywności na podstawie realnego szumu i mowy; obecnie wymagane osobne potwierdzenie mowy do decyzji zależnych od dialogów |
| **Głos Lua** | OFF i 7 profili | Łagodne korekty tonu; v42/v44 korzystają z istniejących skryptów |
| **EQ wysokie / niskie Lua** | 4 poziomy według integracji v42, plus stan wyłączony | Ostrożne sterowanie tylko istniejącymi mechanizmami Lua |
| **Dialogue Enhance** | OFF, poziomy 1–5 | v46: możliwe zmniejszenie 5→4 przy określonym stanie i pomiarze |
| **DRC** | OFF, poziomy 1–3 | v46: możliwe zmniejszenie 3→2 przy zbyt małej dynamice oryginału |
| **EQ głosu** | W panelu audio ustalono 3 presety: „Głos Naturalny”, „Dialogi Wyraźne”, „Głos Mocny” | Nie zakładać, że są już automatycznie wybierane przez Auto Audio |

**Ważne zasady integracji:** nie tworzyć drugiego filtra Szum Lua. Poprzednia poprawka (PR #38) przywróciła korzystanie z istniejącego `@rex_szum` i poprawne rozwijanie ścieżki modelu. Dla EQ wysokie/niskie v42 korzysta z komunikatów istniejących skryptów `script-message-to off/cycle`, aby nie rozjechać stanów i liczników Lua.

**Preferencje użytkownika przed analizą:** zwykle Głos 1, Szum 5 i EQ wysokie 1. To punkt wyjścia, a **nie obowiązkowy rezultat Auto Audio**. Jeżeli filtr szkodzi naturalności, system powinien móc zaproponować jego zmniejszenie/wyłączenie.

**Do sprawdzenia przy przyszłej integracji:** w opisie PR #41 zanotowano rozbieżność sterowania Szum 5 między torami (FF: `mix=.75`; Sz: `mix=.20`). W samym opisie wskazano ją jako temat do osobnego wyrównania w sterowaniu/A-B. Nie zakładać, że została już naprawiona, bez sprawdzenia kodu.

## 5. Historia ukończonych etapów

| Etap | Co dodano / zmieniono | Granice i uwagi |
|---|---|---|
| **v40 / PR #40** | Analiza oryginalnego PCM z lokalnego pliku przez MediaExtractor/MediaCodec; 15 s / 3×10 s, ostrożne propozycje i odsłuch A/B; panel wyników | Pierwotnie tylko lokalna pojedyncza ścieżka audio, bez streamingu |
| **v41 / PR #41** | Analiza bieżącego streamingu w torze mpv przed filtrami; `astats/ametadata`, bez dodatkowego HTTP, przewijania i usuwania efektów; przerwy 2 s pomiędzy trzema próbkami | Pomiar poziomów, jeszcze bez prawdziwego rozpoznawania mowy; test użytkownika wskazywał brak zacięć |
| **v42 / PR #42** | W analizie dokładnej metryki widma przez `aspectralstats`: centroid, flatness, rolloff; sterowanie istniejącym Głos i EQ Lua | Ostrożne propozycje tonalne, nie VAD; szybki pomiar pozostaje lekki |
| **v43 / PR #43** | Osobne raporty dla trzech próbek; sprawdzanie ich zgodności; blokady fałszywych rekomendacji bez VAD | Użytkownik zgłosił trzy różne wyniki, brak propozycji i wciąż zbyt ostry, lekko szumiący dźwięk |
| **v44 / PR #44** | Reguły bezpieczeństwa przed podbijaniem zbyt jasnego widma, możliwe wyłączenie istniejącego EQ wysokie albo złagodzenie Głos; szczegóły 3 fragmentów, przycisk ponowienia | Bez automatycznego rozpoznawania mowy i bez ingerencji w pobieranie |
| **v45 / PR #45** | Weryfikacja długości próbek, początku/końca, luk i ciągłości PTS; odrzucanie niekompletnych danych | Nie dodano nowych reguł doboru filtrów ani VAD |
| **v46 / PR #46** | Dwie bezpieczne propozycje ograniczania mocnych filtrów: DRC 3→2 albo Dialogue Enhance 5→4; nowe testy regresyjne | Wyłącznie poprawna analiza 3×10 s i propozycja do A/B; nie jest to ocena dźwięku po filtrach |

Wszystkie powyższe PR-y #40–46 zostały scalone. **Nie powtarzać implementacji już wykonanych etapów.** Zawsze sprawdzić najnowszy stan `master` i numerację wydań przed rozpoczęciem kolejnej pracy.

## 6. Najważniejsze rzeczywiste testy — ten sam wywiad uliczny

Test wykonano trybem **3×10 s** w wersjach **v44** i **v45**. Dane użytkownika pochodziły ze zrzutów panelu analizatora. Włączone były: **Dialogue Enhance 5/5**, **DRC 3/3**, **Szum OFF**.

| Parametr | v44 | v45 |
|---|---:|---:|
| RMS | −18,7 dBFS | −18,7 dBFS |
| Szacowane tło | −24,5 dBFS | −24,6 dBFS |
| Dynamika | 9,2 dB | 9,1 dB |
| Centroid widma | 1675 Hz | 1666 Hz |
| Spectral flatness | 0,04 | 0,04 |
| Rolloff | 2555 Hz | 2508 Hz |
| Propozycja korekty | brak | brak |

**Wniosek:** wyniki były praktycznie takie same. v45 poprawiła wiarygodność/ciągłość próbek, ale nie inteligencję wyboru filtrów. Żaden z ustalonych bezpiecznych warunków nie wywołał rekomendacji. W opisie PR #46 wyraźnie podano, że nie wykryto przekroczeń progów „jasne/ciemne/szumopodobne” — nie należy twierdzić na podstawie samych liczb, że klasyfikator rozpoznał mowę, szum albo ciemne widmo. **Najbardziej istotny problem praktyczny:** aktywne mocne Dialogue Enhance 5/5 i DRC 3/3, odczuwalnie nieoptymalny dźwięk, a Auto Audio nie reagował.

## 7. Konkretne zabezpieczenia i reguły

### v43 — ostrożność wobec sprzecznych próbek

- Trzy fragmenty oceniane osobno.
- Nie wolno podejmować ryzykownych decyzji na podstawie pojedynczego nietypowego fragmentu.
- Rekomendacje wymagające potwierdzenia mowy są zablokowane do czasu prawdziwego VAD.

### v44 — ostre/brzmiące jasno widmo

- Gdy **≥2/3 próbek są jasne i żadna nie jest ciemna**, w pierwszej kolejności można zaproponować **wyłączenie już aktywnego EQ wysokie**, zamiast jego wzmocnienia.
- Jeżeli nie ma aktywnego EQ wysokie, można jedynie ostrożnie złagodzić aktywny, agresywny **Głos 2–4 → Głos 1**.
- Ciemne widmo **nie** oznacza zgody na dodatkowe wzmocnienia Głos/EQ; może ewentualnie uzasadnić cofnięcie aktywnego ekstremalnego tłumienia w Głos 5–7.
- Jedna propozycja tonalna naraz; przy braku wiarygodnych przesłanek jasno wyjaśnić brak propozycji.

### v45 — kompletność streamingu i PTS

- Każda 10- lub 15-sekundowa próbka musi obejmować **≥85%** czasu.
- Początek i koniec próbki muszą być położone **nie dalej niż 0,5 s** od zaplanowanej granicy.
- Luka między kolejnymi ramkami **>0,75 s** dyskwalifikuje próbkę.
- Nieuporządkowane/cofnięte PTS oraz skoki znaczników czasu (np. po zmianie segmentu HLS) dyskwalifikują dane.
- Przy błędzie: komunikat i ponowienie pomiaru, **bez zmian filtrów**.
- Testy regresyjne obejmują m.in. obcięty koniec, 5-sekundową lukę, cofnięcie PTS, brak początku próbki.

### v46 — przeciwdziałanie nadmiernemu przetwarzaniu

**Reguły mogą działać tylko gdy**: analiza dokładna ma 3 pełne fragmenty i **≥29 s** wiarygodnego dźwięku; wcześniejsze sprawdzone reguły nie dały bezpieczniejszej jednej propozycji.

1. Jeśli aktywny jest **DRC 3/3**, globalna dynamika oryginału wynosi **≤12 dB** i co najmniej **2 z 3** próbek mają dynamikę **≤14 dB**, zaproponuj **DRC 3 → 2**.
2. W przeciwnym razie, jeśli dynamika nie spełnia powyższych warunków małej dynamiki, ale równocześnie działają **Dialogue Enhance 5/5** i **DRC 3/3**, zaproponuj **Dialogue Enhance 5 → 4**.
3. To **nie** automatyczna aktywacja, **nie** VAD, **nie** twierdzenie, że dźwięk po filtrach został zmierzony. Zawsze odsłuch A/B i możliwość cofnięcia. Żadnego zwiększania filtrów.

## 8. Co jest gotowe, a co nadal nie

**Wdrożone w kodzie według PR:** podstawy analizy 15 s i 3×10 s, streaming w aktualnym torze mpv, pomiary przed filtrami, podstawowe statystyki poziomów/widma, osobne próbki, ograniczone reguły bezpieczeństwa, A/B i cofnięcie, zabezpieczenia czasu/PTS, propozycje v46.

**Potwierdzone przez użytkownika:** testy streamingu bez zacięć we wcześniejszym etapie; dostępne pomiary i wyniki v44/v45. Nie ma jeszcze potwierdzenia odsłuchowego działania v46 na fizycznym telefonie.

**Nieukończone albo nieweryfikowane:**

- prawdziwy **VAD** rozpoznający rzeczywistą mowę, niezależny od heurystyk widma;
- rozróżnianie mowy, muzyki, ciszy, syczących głosek i różnych rodzajów szumu;
- wiarygodny automatyczny dobór intensywności **Szum 1–7** na podstawie dialogu i zakłóceń;
- szersza inteligencja dla Głos, EQ, Dialogue Enhance, DRC przy zachowaniu naturalnego brzmienia;
- ocena **jakości po filtrach** (obecna analiza ocenia oryginał) i wiarygodny pomiar, czy propozycja realnie pomaga;
- testy sprzętowe CPU/RAM/baterii, odtwarzania przy 1,5×–3×, zachowania A/B i różnorodnych źródeł streamingu;
- sprawdzenie rozbieżności poziomu `Szum 5` między sposobami sterowania odnotowanej w PR #41.

Nie wolno opisywać powyższych punktów jako już ukończonych.

## 9. Dalszy plan — bez powielania wersji

**Krok 1: walidacja v46 na telefonie.** Na kilku streamingach wykonać 15 s oraz 3×10 s, porównać dane, potwierdzić brak zacięć i poprawność A/B. Na materiale z małą dynamiką i DRC 3/3 zweryfikować, czy pojawia się DRC 3→2. Sprawdzić zachowanie w przypadku braku propozycji i błędów PTS. Zmieniać tylko jedną rzecz naraz.

**Krok 2: zbadać bezpieczny natywny odbiór źródłowego PCM** z bieżącego toru mpv przed efektami. Musi działać bez ponownego pobierania streamu, bez przestawiania prędkości i bez modyfikowania filtrów użytkownika. Metadane `astats`/`aspectralstats` nie zastąpią próbek PCM potrzebnych klasyfikatorowi.

**Krok 3: prawdziwy, lekki VAD.** Dopiero mając prawidłowy sygnał, rozpoznawać mowę i oszacować pewność wyniku; porównać sceny z muzyką, tłem, szumem i dialogami.

**Krok 4: rozszerzenie rekomendacji** na istniejące 7 poziomów Szum, 7 profili Głos, EQ, Dialogue Enhance i DRC. Wykrywać nadmierną ostrość i nadmierne przetwarzanie, ale nie degradować już dobrego materiału. Jedna zmiana i A/B.

**Krok 5: weryfikacja jakości po korekcie.** Oddzielnie od analizy oryginału sprawdzać, czy wynik filtracji faktycznie poprawia zrozumiałość dialogów, a nie tylko zmienia wykres.

## 10. Kompilacje, repozytorium i obowiązkowa kontrola

- Repozytorium: **https://github.com/laszko14/REX-Player**.
- Każda nowa kompilacja/wydanie musi otrzymać **nowy kolejny numer wersji**. Nigdy nie używać starego numeru dla kolejnej kompilacji.
- Sprawdzić aktualny tag, numer APK i zmiany równoległe **przed** wybraniem kolejnego numeru. v46 to punkt odniesienia **Auto Audio**, nie gwarancja, że w całym repozytorium nie powstała późniejsza wersja z innego zadania.
- Zachować **ten sam podpis/pakiet** potrzebny do aktualizacji aplikacji bez utraty danych. Przed deklaracją gotowej instalacyjnej aktualizacji zweryfikować rzeczywisty podpis i artefakt APK.
- Wymagane: `assembleDebug`, `testDebugUnitTest`, `lintDebug` i kontrola konfiguracji workflow (`check_workflows`), plus odpowiednie testy regresyjne i walidacja na telefonie.
- Nie nadpisywać równoległych zmian w przyciskach Lua, panelach, sterowaniu gestami, audio/wideo ani ustawieniach użytkownika.
- Aktualizować tę dokumentację po kolejnej wersji: **co zmieniono, dlaczego, PR/commit, status CI/APK, test telefonu, nierozwiązane problemy, następny krok**.

## 11. Punkt wznowienia projektu — instrukcja dla nowej rozmowy

> Kontynuujemy MPV Alpha Auto Audio w repozytorium https://github.com/laszko14/REX-Player. Przeczytaj najnowszą dokumentację `docs/MPV_Alpha_Auto_Audio_KOMPLET.md` z aktualnej gałęzi `master` repozytorium GitHub (kopia w źródłach projektu ChatGPT może być nieaktualna), a następnie zweryfikuj aktualny kod i wersję aplikacji. Etapy Auto Audio v40–v46 zostały wykonane, PR #46 jest scalony, a CI PR było zielone. Nie powtarzaj zrobionych zmian. Zachowaj streaming bez drugiego pobierania, oryginalną analizę przed filtrami, tryby 15 s i 3×10 s, A/B, brak normalizacji, komplet istniejących Lua, numerację i podpis APK. **Najbliższy etap:** test v46 na telefonie i ocena natywnego dostępu do surowego PCM przed próbą wdrożenia prawdziwego VAD. Nie nazywaj pomiarów widma wykrywaniem mowy. Stosuj Systematic Debugging. Po zmianach uruchom testy, nie deklaruj niezweryfikowanego zachowania jako pewnego.

## 12. Odnośniki do potwierdzonych zmian

- PR #40: https://github.com/laszko14/REX-Player/pull/40
- PR #41: https://github.com/laszko14/REX-Player/pull/41
- PR #42: https://github.com/laszko14/REX-Player/pull/42
- PR #43: https://github.com/laszko14/REX-Player/pull/43
- PR #44: https://github.com/laszko14/REX-Player/pull/44
- PR #45: https://github.com/laszko14/REX-Player/pull/45
- PR #46: https://github.com/laszko14/REX-Player/pull/46

## 13. Zasada aktualizowania dokumentacji w następnych etapach

1. **To jest kanoniczne źródło Auto Audio.** Przed nowym etapem odczytaj jego aktualną wersję z GitHub `master`, a nie polegaj tylko na starszej kopii w ChatGPT.
2. **Aktualizuj dokumentację razem ze zmianami Auto Audio:** w tym samym PR co poprawka kodu dopisz rzeczywisty zakres zmian, ich przyczynę, dotknięte pliki, PR/commit, wyniki CI i ręcznych testów. Jeżeli nie ma wyników, zaznacz „niezweryfikowane”.
3. **Nie nadpisuj historii:** zachowuj wcześniejsze ustalenia, dane v44/v45 i informacje o istniejących filtrach. Aktualizuj datę oraz „ostatni potwierdzony etap” dopiero po faktycznym wdrożeniu i weryfikacji statusu.
4. **Sprawdź zmiany równoległe:** przed edycją porównaj aktualny `master`, otwarte PR-y i numerację aplikacji. Nie powtarzaj już zrealizowanych etapów.
5. **Dokumentacja sama się nie aktualizuje.** Każda kolejna rozmowa wykonująca etap Auto Audio powinna świadomie uzupełnić ten plik i uwzględnić go w PR. Samo utworzenie APK lub scalenie kodu nie modyfikuje dokumentacji.
6. **Zmiana samej dokumentacji nie wymaga wydania nowego APK.** Nie dodawaj znacznika `[mpv-alpha-apk]` do dokumentacyjnych commitów ani nie uruchamiaj podpisanej kompilacji bez osobnej potrzeby.
