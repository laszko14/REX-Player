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

### Test urządzeniowy v46 — wywiad uliczny z dużym szumem (18.10.2026)

Źródło danych: cztery zrzuty ekranu użytkownika z panelu Auto Audio dla dwóch ustawień istniejących filtrów, podczas streamingu. To **ten sam materiał testowy co wcześniej**, ale inne odcinki czasowe; niewielkie różnice statystyk nie są miarą jakości dźwięku po filtrach.

| Pomiar | DRC 2/3 | DRC 3/3 |
|---|---:|---:|
| Dialogue Enhance | 5/5 | 5/5 |
| Szum Lua | OFF | OFF |
| Długość próbek | 30,0 s | 30,0 s |
| RMS | −18,4 dBFS | −18,2 dBFS |
| Tło (szacunek) | −23,1 dBFS | −23,5 dBFS |
| Dynamika globalna | 8,0 dB | 8,5 dB |
| Centroid widma | 1675 Hz | 1661 Hz |
| Flatness | 0,04 | 0,04 |
| Rolloff | 2531 Hz | 2531 Hz |
| Rekomendacja | brak | **DRC 3→2** |

Dla testu z DRC 3/3 poszczególne fragmenty: **F1**: centroid 1663 Hz, flatness 0,04, rolloff 2602 Hz, tło −25,2 dBFS, dynamika 10,5 dB; **F2**: 1651 Hz / 0,03 / 2461 Hz / −21,3 dBFS / 6,3 dB; **F3**: 1668 Hz / 0,04 / 2555 Hz / −24,2 dBFS / 9,0 dB.

**Potwierdzone wizualnie na urządzeniu:** panel raportuje streaming, oryginalny PCM przed filtrami, 30,0 s analizy oraz sprawdzoną ciągłość PTS; przy DRC 3/3 spełnione warunki globalnej dynamiki ≤12 dB i ≥2 fragmentów ≤14 dB, a aplikacja proponuje tylko obniżenie DRC do 2/3. Przy już ustawionym DRC 2/3 propozycja nie pojawiła się. Dla porównania i A/B przyciski są widoczne i B jest dostępne, ale **brak potwierdzenia odsłuchowego i testu zastosowania/cofnięcia**.

**Ograniczenie:** duży słyszalny szum uliczny pochodzi z relacji użytkownika, **nie z detekcji klasyfikatora**. Widmo oznaczone jasne 0/3, ciemne 0/3, szumopodobne 0/3; VAD nadal nieobecny. Nie twierdzić, że v46 automatycznie odszumia ulicę. Komunikat interfejsu „bez automatycznego DRC” może być niejasny wobec bezpiecznej *propozycji zmniejszenia już aktywnego DRC* — przyszłe doprecyzowanie treści, bez zmiany logiki.

**Następny test:** na tym samym materiale przełączyć A→B→A; sprawdzić słyszalną różnicę, zachowanie łańcucha audio i dokładny powrót do DRC 3/3, a dopiero potem ewentualnie zatwierdzić 2/3. Dodatkowe sprawdzanie VAD/rozpoznawania szumu dopiero w osobnym etapie. Nie wymagano nowego APK dla tego wpisu dokumentacyjnego.

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

**Potwierdzone przez użytkownika:** testy streamingu bez zacięć we wcześniejszym etapie; dostępne pomiary i wyniki v44/v45; w v46 wykonana na fizycznym telefonie kompletna analiza streamingu 3×10 s i zaobserwowana poprawna rekomendacja **DRC 3→2** po ustawieniu DRC 3/3. Nadal **niepotwierdzone odsłuchowo**: przełączenie A/B, skuteczność brzmieniowa i trwałe zastosowanie/cofnięcie zmiany.

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

> Kontynuujemy MPV Alpha Auto Audio w repozytorium https://github.com/laszko14/REX-Player. Przeczytaj najnowszą dokumentację `docs/MPV_Alpha_Auto_Audio_KOMPLET.md` z aktualnej gałęzi `master` repozytorium GitHub (kopia w źródłach projektu ChatGPT może być nieaktualna), a następnie zweryfikuj aktualny kod i wersję aplikacji. Etapy Auto Audio v40–v46 zostały wykonane, PR #46 jest scalony, a CI PR było zielone. Nie powtarzaj zrobionych zmian. Zachowaj streaming bez drugiego pobierania, oryginalną analizę przed filtrami, tryby 15 s i 3×10 s, A/B, brak normalizacji, komplet istniejących Lua, numerację i podpis APK. **Najbliższy etap:** zakończyć weryfikację A/B i cofnięcia po potwierdzonym na telefonie teście rekomendacji DRC 3→2 w v46, następnie ocenić natywny dostęp do surowego PCM potrzebny do prawdziwego VAD. Nie nazywaj pomiarów widma wykrywaniem mowy. Stosuj Systematic Debugging. Po zmianach uruchom testy, nie deklaruj niezweryfikowanego zachowania jako pewnego.

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

## 14. Auto Audio v47 — rozwój prawdziwego VAD na rzeczywistych próbkach PCM (etap 1)

**Stan wpisu:** PR #49 (etap fundamentu v47) zweryfikowany automatycznie w GitHub Actions, ale VAD na telefonie i streaming nadal niezweryfikowane. Ostatnia wersja sprawdzona na streamingu pozostaje v46. Nie traktować v47 jako ukończonego VAD dla streamingu.

### Zdiagnozowana blokada streamingu

- Zintegrowana w aplikacji biblioteka `com.github.sfsakhawat999:mpvRex-libmpv:0.0.9` udostępnia `MPVLib` (komendy, właściwości, podglądy obrazu), ale **nie ma dostępnego callbacku, który przekazuje surowe próbki PCM aktualnego strumienia** do Kotlin/Java. `astats` i `aspectralstats` przez `ametadata` podają wyłącznie metryki.
- Nie wolno „odtwarzać” sygnału PCM z RMS/FFT, udawać VAD ani pobierać filmu drugi raz. Zmiana audio output / AudioPlaybackCapture oznaczałaby analizę po filtrach, przełączanie dźwięku albo wymaganie uprawnień, więc nie spełnia wcześniejszych założeń.
- Do VAD streamingu konieczne jest zweryfikowane rozszerzenie **natywnego toru mpv**, przekazujące kopię oryginalnych ramek PCM **przed efektami użytkownika**. Należy przygotować wyłącznie jeden ograniczony tap, nie zmieniać toru odsłuchowego, nie zapisywać pełnego streamu, nie wykonywać nowego HTTP i zachować PTS/track/speed.

### Wykonywany zakres kodu v47 — prawdziwy WebRTC VAD dla dostępnego PCM

- Zależność `com.github.gkonovalov.android-vad:webrtc:2.0.10` (MIT; algorytm WebRTC, JNI). Klasyfikator otrzymuje rzeczywiste **mono PCM16 / 16 kHz / 320 próbek co 20 ms**. To model VAD, a nie próg RMS.
- Bieżący **lokalny** dekoder `MediaExtractor/MediaCodec` w `AutoAudioAnalyzer` posiada prawdziwe, oryginalne próbki. Przesyłane są do WebRTC po bezpiecznym miksie kanałów i lekkim resamplingu. Jeden niewielki bufor (320 próbek) i licznik ramek, brak wysyłania danych.
- `vadSpeechFraction` oznacza **udział ramek zaklasyfikowanych jako mowa**, nie kalibrowane prawdopodobieństwo i nie miarę jakości dialogów. Co najmniej 50 ramek na każdy fragment; niepełne lub niedostępne wyniki są jawnie niezdefiniowane (`null`). `verifiedSpeechProbability` nadal **nie jest syntetyzowane**.
- Model służy w tym kroku **wyłącznie do bezpiecznej diagnostyki**: nie uruchamia samodzielnie Szum Lua, DRC, Dialogue Enhance ani EQ. Gdy natywnego PCM brak (stream), wynik VAD pozostaje niedostępny, a działający algorytm v46 zachowuje bezpieczne dotychczasowe reguły.
- Testy jednostkowe obejmują kompletowanie 20 ms ramek, przetwarzanie wejścia 44,1/48 kHz, zbyt krótkie próbki, nieprawidłowe PCM i rozróżnianie ułamka ramkowego od pewności modelu. Testy JVM używają kontrolowanego testowego klasyfikatora; nie są testem rzeczywistej skuteczności modelu na hałasie ulicznym.

### Wymagana walidacja przed ogłoszeniem pełnego sukcesu

1. `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `check_workflows`; sprawdzić pobieranie natywnej biblioteki WebRTC na ARM64 i brak konfliktów z libmpv.
2. Lokalny plik z mową, muzyką i ciszą: odczyt rzeczywistych wartości VAD i niezawodność działania; brak regresji v46.
3. Dopiero po przygotowaniu natywnego tapu: **streaming HTTP/HLS/DASH bez drugiego pobierania**, zgodność PTS, wybrana ścieżka audio, zachowanie 1,5×–3×, brak trzasków/zacięć, bezpieczeństwo pamięci, CPU/bateria.
4. W osobnym kroku dodać konserwatywne zasady decyzji zależnych od potwierdzonej mowy i zderzyć wyniki z szumem ulicznym. WebRTC VAD może mylić niektóre hałasy i muzykę z mową; nie używać samego udziału ramek jako procentu pewności.

**Automatyczna walidacja (PR #49):** GitHub Actions https://github.com/laszko14/REX-Player/actions/runs/39151890672 — **success** dla kontroli źródeł i zadania `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Podpisany APK nie powstał (zadanie podpisywania pominięte w PR). Zielone CI nie potwierdza działania bibliotek JNI na fizycznym telefonie ani skuteczności VAD.

**Stan fizycznego urządzenia:** brak testu VAD v47. Wymagana najpierw próba na lokalnym PCM, a dla streamingu nadal brak natywnego PCM tapu. Nie wypuszczać oznaczonego jako „VAD streaming” APK.


## 15. Auto Audio v47 — natywny PCM ze streamingu i integracja z WebRTC (weryfikacja w toku)

**Nadrzędny cel:** wykrywanie mowy na bieżącym strumieniu (HTTP/HLS/DASH), tylko z dekodowanych ramek przed wszystkimi `af`, bez ponownego pobierania, bez zmiany prędkości i bez naruszania istniejących filtrów.

### Zweryfikowany fork i repozytoria

- Fork biblioteki natywnej utworzony i posiada uprawnienia zapisu: https://github.com/laszko14/mpvRex-libmpv.
- PR natywny **#1**: https://github.com/laszko14/mpvRex-libmpv/pull/1 — osobna gałąź, jeszcze **nie scalona**.
- W `mpv` wybrano miejsce **pomiędzy dekoderem aktualnego audio a łańcuchem filtrów** (kod `player/audio.c`), w odróżnieniu od nieprawidłowego przechwytywania wyjścia po DRC/Głos/Szum.
- Dodano pasywny filtr bez zmiany audio i JNI z ograniczoną kolejką mono PCM16; po przepełnieniu przekazuje informację o brakach zamiast wymyślać próbki. Dla mpv przypięto dokładny revision `6a262d11f63733d37e3ee6d5fafcb1be1ef83952`, a patch ma kończyć build błędem, jeśli kod się rozjedzie.
- **Weryfikacja techniczna:** poprawność 4 hunków patcha wobec przypiętej wersji została przeliczona i potwierdzona (wszystkie kotwice i linie pasują). Nie jest to jeszcze potwierdzenie kompilacji natywnej ani audio na urządzeniu.

### Integracja po stronie MPV Alpha (PR na osobnej gałęzi)

- Dodano `AutoAudioNativePcmBridge` jako **opcjonalne, refleksyjne API**. Na obecnej starszej paczce libmpv wejście jest niedostępne, a analiza metadanych v46 działa bez zmian.
- Dodano `AutoAudioStreamingVad`: sesje 15 s albo 3×10 s, tylko prawdziwe porcje PCM / PTS, odbiór co 150 ms poza wątkiem audio, wykrywanie luk, cofnięć, zmian częstotliwości próbkowania oraz przepełnienia kolejki.
- Wymagane ≥85% prawdziwych ramek VAD w każdym fragmencie oraz obecność blisko początku i końca każdej próbki. Niepełny wynik to **`null`**, nigdy procent „wykrytej mowy”.
- Oryginalne statystyki FFmpeg i reguły v46 pozostają działające. Nowy `vadSpeechFraction` to tylko **odsetek ramek oznaczonych jako mowa przez WebRTC**, nie kalibrowana pewność; `verifiedSpeechProbability` zostaje niedostępne. Nie włączać automatycznie RNNoise/Dialogue Enhance/DRC na podstawie samej frakcji.
- Testy JVM obejmują pełne okna 15s i 3×10s, brakujące fragmenty, brakujące PTS, cofnięcie czasu i utracone porcje PCM.

### Warunki przed rzeczywistym APK v47

1. Uruchomić CI natywnego forka i zweryfikować rzeczywistą kompilację ARM64 oraz wytworzenie spójnej pary `libmpv.so` i `libplayer.so`. **Fork może wymagać ręcznego włączenia GitHub Actions w zakładce Actions.** Sama poprawność patcha nie oznacza sukcesu native build.
2. Wydać nowy, jasno oznaczony artefakt AAR z forka i **dopiero wtedy** podmienić zależność `mpvRex-libmpv:0.0.9` w MPV Alpha. Nie używać pojedynczego JNI z nowej wersji przy starym `libmpv.so`.
3. Przejść testy aplikacji `assembleDebug/testDebugUnitTest/lintDebug/check_workflows` po zmianie zależności oraz test na fizycznym telefonie (mowa, muzyka, cisza, szum uliczny, 1.5×–3×, HLS). Sprawdzić PTS, regresje buforowania, wydajność CPU i obecność wszystkich oryginalnych Lua.
4. Dopiero potem uruchomić podpisaną kompilację aktualizacyjną z **nowym numerem v47** i przeprowadzić testy odsłuchowe. Nie ogłaszać pełnego VAD w streamingu wcześniej.

**Stan:** kod po stronie natywnej i aplikacyjnej jest w PR-ach do weryfikacji, brak potwierdzenia build native i brak wydanego APK. Historia v40–v46 pozostaje ważna; nie powtarzać starszych etapów.


## 16. Status v47 po uruchomieniu GitHub Actions w forku (18.10.2026)

- **Aplikacja MPV Alpha:** PR #50 https://github.com/laszko14/REX-Player/pull/50 został scalony do `master`. CI https://github.com/laszko14/REX-Player/actions/runs/39155752598: `success` — kontrola źródeł, `assembleDebug`, `testDebugUnitTest` i `lintDebug`. Wprowadzono kompatybilny z dotychczasową biblioteką odbiornik opcjonalnych prawdziwych danych PCM z mpv, reguły kompletności i diagnostyczny WebRTC VAD.
- **Biblioteka natywna:** fork https://github.com/laszko14/mpvRex-libmpv, PR #1 https://github.com/laszko14/mpvRex-libmpv/pull/1. Po włączeniu GitHub Actions i dodaniu ręcznego uruchamiania startowały dwa przebiegi kompilacji czterech ABI: https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157890994 i https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157889511. **Przy zapisywaniu tego wpisu zadania natywne wciąż były w trakcie** (Download deps); sukcesu native NIE potwierdzono.
- **Warunek wdrożenia:** nie podmieniono jeszcze `com.github.sfsakhawat999:mpvRex-libmpv:0.0.9` na AAR z forka. Wymagana zgodna, przetestowana para `libmpv.so`/`libplayer.so`, publikacja osobno nazwanej paczki `com.github.laszko14`, a następnie ponowny CI i testy streamingu HLS/HTTP/DASH.
- **Brak gotowego APK v47**: nie uruchamiano podpisanej aktualizacji. Unikalny numer v47 ma być użyty dopiero dla pierwszej faktycznej kompilacji nowego podpisanego APK; bez podwójnego wydania tej samej wersji.
- **Następna czynność:** przeczytaj wynik CI w PR natywnym #1. Przy błędzie zastosuj Systematic Debugging na logach, popraw przyczynę, zweryfikuj nowe CI, dopiero potem scal PR natywny i opublikuj AAR. Nie deklaruj pełnego VAD streamingu na podstawie samych testów JVM czy lokalnego PCM.


## 17. Auto Audio v47 — błąd kompilacji Vulkan w forku natywnym (18.10.2026)

**Stan:** etap naprawy środowiska CI, **bez wydania bibliotek/AAR/APK**. Historia i warunki z sekcji 1–16 pozostają w mocy.

### Diagnoza z realnego logu

- Sprawdzono dwa uruchomione przebiegi forka: [39157889511](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157889511) oraz [39157890994](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157890994). W początkowym odczycie prace trwały; następnie pobrano pełny log zadania ARMv7 (job 117764816340).
- Podczas **Build the release artifacts**, przy konfiguracji Meson w `buildscripts/deps/mpv/_build_armv7l`, nastąpił rzeczywisty błąd: `meson.build:1346:9: ERROR: Dependency lookup for vulkan ... need '>= 1.4.309' found '1.3.275'`. Zakończenie tego zadania: kod wyjścia 1.
- Przyczyna: przypięta wersja źródeł mpv wymaga nowszych nagłówków Vulkan, a `buildscripts/scripts/mpv.sh` wytwarzał `vulkan.pc` z wersją **1.3.275**, odpowiadającą zbyt starym nagłówkom środowiska. To **nie jest wynik testu JNI, VAD ani poprawności przechwytywania PCM**.

### Minimalna poprawka w istniejącym natywnym PR #1

- Commit [dabdf9d](https://github.com/laszko14/mpvRex-libmpv/commit/dabdf9d3c7088f1d61b42713ac64be23a3726b0d) na gałęzi `feature/v47-native-pre-af-pcm-tap` zmienia wyłącznie `buildscripts/scripts/mpv.sh`.
- Przypina rzeczywiste `KhronosGroup/Vulkan-Headers` **v1.4.309**, pobiera źródło nagłówków przy braku lokalnej kopii, sprawdza `VK_HEADER_VERSION == 309` i instaluje nagłówki w prefiksie danej architektury.
- Wpis `vulkan.pc` wskazuje wersję faktycznych nagłówków i ścieżkę `-I/usr/local/include` (mapowaną przez `PKG_CONFIG_SYSROOT_DIR` na prefiks cross-kompilacji). Zostawiono `-lvulkan` od Androida, a opcja `-Dvulkan=enabled` pozostała niezmieniona; **nie wyłączono Vulkan ani żadnego istniejącego modułu odtwarzania**.
- Nowy przebieg [39159621224](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39159621224) został utworzony dla tego commita; podczas pierwszego sprawdzenia miał stan `queued`. Wynik **nie jest jeszcze potwierdzony**. Dawne przebiegi dotyczą wcześniejszego kodu i nie mogą potwierdzać skuteczności poprawki.

### Następny punkt decyzyjny

1. Sprawdzić CI nowego commita dla ARM64/ARMv7/x86/x86_64, analizując pierwszą rzeczywistą przyczynę ewentualnego błędu.
2. Sprawdzić obecność symbolu `mpv_rex_set_pcm_sink`, zgodną parę `libmpv.so`/`libplayer.so` oraz artefakt AAR. Nie uznawać samego sukcesu Meson za gotowy tor VAD.
3. Po zielonym CI dokończyć przegląd kodu natywnego, scalić PR #1 i opublikować **nową osobną wersję** biblioteki.
4. Dopiero po weryfikacji artefaktów zintegrować AAR w MPV Alpha, wykonać testy aplikacji i rzeczywisty odsłuch na streamingu. **Nie ogłaszać ani nie wydawać APK v47 przed potwierdzeniem realnego VAD.**


## 18. Auto Audio v47 — potwierdzony build natywny dla czterech ABI (18.10.2026)

**Wynik CI:** uruchomienie forka [39159621224](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39159621224) dla commita [dabdf9d](https://github.com/laszko14/mpvRex-libmpv/commit/dabdf9d3c7088f1d61b42713ac64be23a3726b0d) zakończyło się `success`. Wszystkie cztery zadania (`arm64`, `armv7l`, `x86`, `x86_64`) mają status `completed/success`, w tym `Build the release artifacts` i `upload-artifact`. To potwierdza rozwiązanie wcześniejszej blokady konfiguracji Vulkan (sekcja 17) w tym przebiegu. Natomiast obydwa starsze uruchomienia [39157889511](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157889511) i [39157890994](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39157890994) zakończyły się błędem w czterech architekturach; logi wskazywały błąd wymaganej wersji Vulkan.

### Artefakty i weryfikacja rzeczywistego AAR

- GitHub Actions udostępnia cztery nieprzeterminowane artefakty `mpv-lib-arm64`, `mpv-lib-armv7l`, `mpv-lib-x86`, `mpv-lib-x86_64`. **To odrębne AAR dla poszczególnych architektur, nie zweryfikowany wspólny AAR ze wszystkimi ABI.**
- Pobrano rzeczywisty ZIP artefaktu `mpv-lib-arm64` (artifact ID `12148578659`), otwarto `app-release.aar` i sprawdzono zawartość. Obecne są `jni/arm64-v8a/libmpv.so`, `jni/arm64-v8a/libplayer.so` oraz `classes.jar`; nie potwierdzono ABI innych niż ARM64 w tej paczce.
- W `libmpv.so` narzędzie `readelf --dyn-syms` potwierdziło **GLOBAL** eksport `mpv_rex_set_pcm_sink`. W `libplayer.so` ten symbol występuje jako referencja `UND` do parowanej biblioteki mpv; eksportowane są `Java_is_xyz_mpv_MPVLib_vadTapStart`, `vadTapStop` i `vadTapPoll`.
- `javap` na `classes.jar` potwierdził metody `MPVLib.vadTapStart()/vadTapStop()/vadTapPoll()` oraz konstruktor `VadPcmChunk(short[], int, double, int)`; zgodny z deskryptorem JNI `([SIDI)V`.
- Jest to **statyczna kontrola symboli i pakietu**, nie potwierdzenie wykonania JNI ani rzeczywistej analizy strumienia na Androidzie.

### Decyzja o dalszym wdrożeniu

- **Nie scalać natywnego PR #1 wyłącznie na podstawie zielonego CI**, dopóki nie zostanie domknięty przegląd i strategia dystrybucji wspólnej biblioteki AAR dla deklarowanych ABI. W razie osobnej dystrybucji ABI przypisać właściwy wariant na podstawie architektury, nie mieszać `libplayer.so` i `libmpv.so` z różnych buildów.
- Do następnej weryfikacji: pozostałe ABI na poziomie symboli (nie wykonano), sprawdzanie kompletu `libmpv.so/libplayer.so` i publikacja nowej wersji `com.github.laszko14` bez zastępowania oryginalnej paczki.
- Po scaleniu i prawidłowej dystrybucji natywnej dopiero podmienić zależność aplikacji i uruchomić `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `check_workflows`; następnie sprawdzić VAD na streamingu HTTP/HLS/DASH, 1,5×–3×, PTS oraz brak regresji.
- **Podpisany MPV Alpha v47 APK i działający VAD streamingu nadal nie zostały zweryfikowane ani wydane**. Nie należy utożsamiać sukcesu natywnego CI z tym wynikiem.


## 19. Auto Audio v47 — ARM64-only integracja zamiast czterech ABI (18.10.2026)

**Decyzja użytkownika:** do dalszego etapu Auto Audio stosujemy **wyłącznie ARM64 (arm64-v8a)**, zgodnie z używanym Nothing Phone (1). ARMv7/x86/x86_64 na razie nie rozwijamy, bez usuwania historycznego kodu. Aplikacja już wcześniej ograniczała docelowy APK przez `splits.abi.include("arm64-v8a")`.

### Zrealizowane działania w forku natywnym

- Zielony poprzedni build wszystkich ABI: https://github.com/laszko14/mpvRex-libmpv/actions/runs/39159621224. Statycznie sprawdzono AAR ARM64: w `libmpv.so` istnieje eksport `mpv_rex_set_pcm_sink`, a w parowanym `libplayer.so` znajdują się symbole JNI `vadTapStart/Stop/Poll`. SHA256 zweryfikowanej paczki ARM64 z poprzedniego przebiegu: `d8b0f8747b8bd754bc492dcdd0b907ff591bb6c0f02b08a093914fd38a95850b`. Jest to kontrola symboli, nie runtime.
- Na istniejącej gałęzi natywnego PR #1 zmieniono matrycę `.github/workflows/build.yml` i prefiks `buildscripts/include/ci.sh`, aby następny CI budował tylko ARM64. Reszta źródeł ABI pozostaje nienaruszona.
- Dodano `.github/workflows/publish-arm64.yml`, który **dopiero po zielonym buildzie na `master`** i weryfikacji biblioteki opublikuje osobne, niezmienne wydanie `native-v47-arm64-r1` z `mpvRex-libmpv-native-v47-arm64-r1.aar` i SHA256. Nie ma automatycznego publikowania z niezweryfikowanego PR.
- Uruchomiono nowy ARM64-only build: https://github.com/laszko14/mpvRex-libmpv/actions/runs/39163226756. Status przy tym wpisie `in_progress`; sukcesu jeszcze nie potwierdzono.

### Przygotowana integracja aplikacji (gałąź bez scalania przed release)

- Gałąź `feature/v47-arm64-native-vad-integration`: w `settings.gradle.kts` zadeklarowano izolowane repozytorium Ivy dla niezmiennego zasobu AAR GitHub Releases `com.github.laszko14`.
- `app/build.gradle.kts` zamienia stare `implementation(libs.mpv.lib)` na `com.github.laszko14:mpvRex-libmpv:native-v47-arm64-r1@aar`. W katalogu wersji usunięto wywołanie starego `com.github.sfsakhawat999:mpvRex-libmpv:0.0.9`. Nie dodano nowego HTTP w torze odtwarzania; pobranie AAR dotyczy tylko procesu kompilacji.
- **To jest integracja w przygotowaniu**: release binarny nie został jeszcze opublikowany, a Gradle nie potwierdził rozwiązania zależności ani kompilacji MPV Alpha. Nie scalać gałęzi przed rzeczywistym zielonym CI.
- Stary opcjonalny `AutoAudioNativePcmBridge`, `AutoAudioStreamingVad`, testy WebRTC i wszystkie wcześniejsze filtry pozostają bez zmian. Brak prób automatycznego włączania filtrów.

### Niezmienny warunek przed instalacyjnym APK

Sprawdzić właściwy AAR z wydania i jego SHA256, przejść `assembleDebug`, `testDebugUnitTest`, `lintDebug` i kontrole workflow, a następnie na fizycznym urządzeniu ocenić VAD na strumieniu HTTP/HLS/DASH z dialogiem, ciszą, muzyką i zakłóceniami, 15 s oraz 3×10 s, 1,5×–3×, seek i PTS. **Nie uruchamiać podpisanej aktualizacji v47 ani nie deklarować powodzenia natywnego VAD przed testem rzeczywistym.** Przed kompilacją signed APK sprawdzić ostatni numer wersji i zachować dotychczasowy certyfikat oraz package ID.



## 20. Auto Audio v47 — natywny PR #1 scalony, biblioteka ARM64 w publikacji (18.10.2026)

- Po zakończeniu natywnego CI ARM64-only potwierdzono **success** obu aktualnych przebiegów: [push 39163222657](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39163222657) i [PR 39163226756](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39163226756), commit `7680f898f2d0268ed9495be47eb2d3f943eb5720`.
- Pobrano i sprawdzono faktyczny `app-release.aar` z drugiego CI (artifact ID `12149886845`): SHA256 **`b81d9a7cf80a44b34db4a6eb8c667e78be0549cbbd0c61dd5046d96ceacd31eb`**. Wewnątrz są para `jni/arm64-v8a/libmpv.so` i `libplayer.so` oraz dodatkowe biblioteki FFmpeg; `readelf -W --dyn-syms` potwierdziło globalny eksport `mpv_rex_set_pcm_sink` i JNI `Java_is_xyz_mpv_MPVLib_vadTapStart/Stop/Poll`.
- [Natywny PR #1](https://github.com/laszko14/mpvRex-libmpv/pull/1) został **scalony** do `master` (squash commit `076cd06f86dd7647dde38bd006c5bfdd3354ea39`). Nie ma już powodu powtarzać tego etapu.
- Automatycznie rozpoczęła się kompilacja `master`: [39164435452](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39164435452). **Stan w chwili wpisu:** `in_progress`, etap `Download deps`, bez opublikowanego release. Nie utożsamiać zakończenia poprzednich PR buildów z publikacją.
- Workflow `publish-arm64.yml` po zielonym buildzie `master` ma sprawdzić eksporty i parę JNI oraz opublikować `native-v47-arm64-r1` jako zasób GitHub Release. Nie wolno pobierać wydania pod nieweryfikowanym adresem.
- Gałąź aplikacyjna `feature/v47-arm64-native-vad-integration` nadal czeka na **rzeczywiste wydanie**. Kolejny etap to: potwierdzić wydanie i SHA256 AAR, uruchomić CI Gradle na PR aplikacji, sprawdzić komplet JNI oraz testy VAD w streamingu na urządzeniu. Nie uruchamiać jeszcze podpisanego APK v47 ani automatycznej zmiany filtrów.



### Diagnostyczny build v47 bez zastępowania zainstalowanego MPV Alpha

- W workflow aplikacji `.github/workflows/build.yml` (tylko PR gałęzi `feature/v47-arm64-native-vad-integration`) dodano archiwizowanie debugowego pliku APK `mpv-alpha-auto-audio-v47-arm64-debug`, **wyłącznie jeśli cały etap `assembleDebug/testDebugUnitTest/lintDebug` zakończy się sukcesem**.
- Testowa aplikacja ma osobny `applicationIdSuffix=".debug"`; nie zastępuje podpisanego produkcyjnego `xyz.mpv.rex`, nie podmienia istniejących danych/ustawień i nie rezerwuje numeru wydania `v47`.
- To artefakt diagnostyczny do przyszłego testu urządzenia i streamingu, **nie instalacyjna aktualizacja właściwego MPV Alpha**. Nie twierdzić, że WebRTC VAD przeszedł test na telefonie, dopóki użytkownik nie prześle wyników.



## 21. Auto Audio v47 — release ARM64 opublikowany i start testów integracyjnych (18.10.2026)

- Natywny build po scaleniu PR #1, [run 39164435452](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39164435452), zakończył się **success** na `master` (`076cd06f86dd7647dde38bd006c5bfdd3354ea39`) dla ARM64.
- Odrębny workflow [Publish verified MPV Alpha ARM64 library #39165343330](https://github.com/laszko14/mpvRex-libmpv/actions/runs/39165343330) również zakończył się **success**. W jego logice sprawdzane są `libmpv.so`, `libplayer.so`, `classes.jar`, pojedyncze ABI oraz eksporty `mpv_rex_set_pcm_sink` i `vadTapStart/Stop/Poll`.
- Faktycznie opublikowano [GitHub Release native-v47-arm64-r1](https://github.com/laszko14/mpvRex-libmpv/releases/tag/native-v47-arm64-r1). Asset: `mpvRex-libmpv-native-v47-arm64-r1.aar`, rozmiar **25 165 798 B**; GitHub API zgłasza SHA-256 **`4e8b8e2a7cafebf6bd6656f22bb396421d8e6a82670dbe6224c18a7ea3103c8f`**. Towarzyszący `.aar.sha256` został również opublikowany. Są to dane faktycznie istniejącego wydania, a nie przewidywany artefakt.
- W przygotowanej gałęzi aplikacji `feature/v47-arm64-native-vad-integration` wskazano ten niezmiennie nazwany AAR przez Gradle Ivy. Następnym krokiem jest sprawdzenie rozwiązywania nowej zależności, kompilacji `assembleDebug`, `testDebugUnitTest`, `lintDebug`, testów workflow oraz udostępnienia diagnostycznego APK z `.debug`. **Nie scalać zmian aplikacji, zanim CI będzie zielone.**
- AAR i zakończenie natywnego CI **nie potwierdzają rozpoznania mowy podczas odtwarzania HTTP/HLS/DASH**. Wymagane są testy na Nothing Phone (1), szczególnie 1,5×–3×, próby 15 s i 3×10 s, ciągłość PTS, status droppedChunks, jakość rozróżniania dialogów/szumu/muzyki oraz brak ingerencji w filtry i odtwarzanie. Produkcyjny podpisany APK v47 nadal **nie jest wydany**.


## 22. Auto Audio v47 — pierwszy test urządzenia 3×10 s i korekta komunikatów (18.10.2026)

**Źródło:** cztery zrzuty ekranu użytkownika z testowej aplikacji ARM64 v47 `xyz.mpv.rex.debug` podczas odtwarzania strumienia; tryb **szczegółowy 3 × 10 s** (30 s materiału). **Użytkownik potwierdził następnie**, że materiał to **wywiad na ulicy, wiele osób, mowa niemal nieprzerwanie, obecne również szum i hałas**. Wynik WebRTC VAD 100% jest zatem możliwy w tym materiale, ale sam nie potwierdza trafności odróżniania mowy od hałasu i muzyki.

### Faktycznie zaobserwowane pomiary

- Natychmiast po udanej analizie: **WebRTC VAD • oryginalny PCM mpv: 100% pozytywnych ramek** (model binarny, **nie** prawdopodobieństwo ani wiarygodność rozpoznania). Ta wartość z ekranu potwierdza, że aplikacja otrzymuje i przetwarza native PCM z działającego streamingu, przy spełnionych warunkach kompletności VAD; sam procent **nie** potwierdza rozróżniania mowy od muzyki / szumu.
- RMS **−19,0 dBFS**, orientacyjne tło **−24,5 dBFS**, dynamika **9,0 dB**, kanały **nieznane** (ogólne metadane astats), jasność (centroid) **1695 Hz**, płaskość **0,04**, rolloff **2578 Hz**.
- Trzy fragmenty: centroid **1696 / 1710 / 1682 Hz**; płaskość **0,04 / 0,04 / 0,04**; rolloff **2648 / 2578 / 2555 Hz**; tło **−24,0 / −24,0 / −25,4 dBFS**; dynamika **8,9 / 8,8 / 8,4 dB**. Są do siebie **bardzo zbliżone**, nie zaobserwowano dużej rozbieżności.
- Zgodność progów jasnego/ciemnego/szumopodobnego widma **0/3, 0/3, 0/3** oznacza tylko brak przekroczenia **zdefiniowanych progów**, a nie brak zgodności między fragmentami. Płaskość 0,04 **nie** uzasadnia samodzielnie włączenia RNNoise.
- Na zrzutach widoczne są aktywne filtry użytkownika (m.in. EQ, Dialogue Enhance, DRC, Szum Lua 7/7; w źródłowym ciągu `af` widoczne jest `mix=0.95`). Analityczny PCM jest sprzed filtrów; wyniku źródłowego nie wolno przypisywać tym efektom.
- Jedyna zaproponowana zmiana: **DRC 3/3 → 2/3**, jako odwracalny test A/B przy dynamice źródła ~9 dB i już działającym mocnym DRC. **Brak** automatycznej aktywacji filtrów zależnych od mowy, EQ wysokich, Dialogue Enhance czy odszumiania.

### Dwie udokumentowane usterki opisu, naprawiane w tym samym PR #55

1. Dialog równocześnie pokazuje `Prawdziwy VAD … 100%` i nieprawdziwy historyczny komunikat `obecna analiza nie ma osobnego modelu VAD`. Zmieniono napis zależnie od dostępności `vadSpeechFraction`: model może być obecny, ale **jego trafność na kontrolach nie została jeszcze potwierdzona**.
2. Planer dotychczas wyświetlał `Fragmenty różnią się lub brak zgodności 2/3` także przy trzech bardzo podobnych fragmentach i 0/3 we wszystkich progach. Teraz jasno informuje, że żaden fragment nie przekracza progów, **bez deklarowania różnic między nimi**.
3. Dodano regresyjny test jednostkowy reprodukujący odczyt v47 (trzy neutralne widmowo fragmenty, VAD 100%, DRC 3/3) i wymagający jedynie odwracalnej propozycji **DRC 3 → 2**. Nie modyfikowano klasyfikatora, progów bezpieczeństwa, ścieżki native PCM, pozostałych funkcji mpv ani numeru APK. CI jeszcze do sprawdzenia.

### Następny punkt decyzyjny — konieczna próbka kontrolna

Użytkownik potwierdził dominację prawie ciągłej mowy podczas wywiadu ulicznego; nie powtarzać tego pytania. Następnie uruchomić w tej samej testowej aplikacji **3 × 10 s na materiale bez mowy** (np. fragment instrumentalny lub cisza/szum tła) i porównać wynik WebRTC VAD. Jeśli mimo braku mowy ponownie jest ~100%, podejść do VAD metodyką Systematic Debugging (kontrola PCM, trybu modelu, mono downmix, rate/PTS, rzeczywistych przerw i negatywnych testów) **bez automatycznej korekty filtrów**. Scalenie PR #55 i produkcyjny podpisany APK v47 wymagają zielonego CI po obecnych zmianach oraz dalszego testu na urządzeniu.



## 23. Auto Audio v47 — test kontrolny instrumentalu: 99% WebRTC VAD i diagnostyka PCM (19.10.2026)

### Wynik fizycznego testu użytkownika

- Diagnostyczne APK ARM64 `xyz.mpv.rex.debug` na Nothing Phone (1); bieżący streaming, **dokładne 3 × 10 s**. Materiał to **muzyka instrumentalna bez słów / dialogów** (negatywna kontrola VAD).
- Odczyt: **99% ramek WebRTC VAD oznaczono jako mowę**, mimo braku dialogów. RMS źródła **−13,0 dBFS**, szacowane tło **−29,9 dBFS**, dynamika **19,4 dB**, centroid **1496 Hz**, flatness **0,03**, rolloff **2692 Hz**.
- Osobne okna: F1 centroid 1507 Hz / tło −30,4 dBFS / dynamika 19,8 dB; F2 1469 Hz / −28,9 dBFS / 18,3 dB; F3 1496 Hz / −30,7 dBFS / 19,9 dB.
- Aktywne EQ, DRC 3/3, Dialogue Enhance 5/5, Szum Lua OFF. Propozycja Dialogue Enhance **5→4** wynikała wyłącznie z ostrożnej reguły nadmiernego przetwarzania, **nie** ze zweryfikowanego wykrycia mowy.
- Kontrola dodatnia z poprzedniego testu: wywiad uliczny z niemal stałą mową i hałasem uzyskał **100%**. Zestawienie 100% / 99% dowodzi, że taki wynik VAD **nie rozróżnia wiarygodnie tych dwóch materiałów**. Nie wolno traktować tego procentu jako prawdopodobieństwa obecności dialogu.

### Przegląd implementacji (Systematic Debugging, bez zgadywania progów)

1. `AutoAudioVadPcm.kt` odbiera *faktyczne* mono PCM16, resampluje do **16 kHz**, zbiera **320 próbek na 20 ms** i wywołuje klasyfikator WebRTC; nie podaje statystyk FFT jako PCM. Użyte `Mode.AGGRESSIVE`, `speechDurationMs=0`, `silenceDurationMs=0` — analiza źródła `gkonovalov/android-vad` v2.0.10 wykazała, że zero wyłącza podtrzymywanie, dając surowe binarne decyzje modelu. **Nie zidentyfikowano dotąd błędu wymiaru ramek ani stałego wyniku true w wrapperze**.
2. JNI `mpvRex-libmpv/app/src/main/jni/vad_tap.cpp` dostaje dekodowany sygnał przed `af`, wykonuje downmix, ogranicza kolejkę, raportuje droppedChunks; Kotlin odrzuca nieciągłe PTS i niepełne okna. Przy ponad dwóch kanałach JNI wciąż **zakłada kanał o indeksie 2 jako center**; poprawność dla niestandardowych układów trzeba zbadać osobno, bez nieuzasadnionych zmian natywnych.
3. WebRTC VAD (lekki model GMM) może klasyfikować tonalne instrumenty jako mowę; 99% przy muzyce wymaga jednak niezależnego sprawdzenia wejścia PCM i próbek na różnych materiałach. **Przyczyna źródłowa wciąż NIE jest potwierdzona**: ograniczenia modelu vs. amplituda/downmix/format/źródło.
4. Niczego nie „naprawiono” podbijaniem losowych progów czy zmianą agresywności VAD. Wyniki pozostają **diagnostyczne**; `verifiedSpeechProbability=null`, a sterowanie filtrami zależne od mowy jest zablokowane.

### Minimalna zmiana diagnostyczna w tym samym PR #55

- `AutoAudioVadPcm` liczy **z samych 320-próbkowych ramek PCM16 wysłanych do WebRTC**: RMS i szczyt w dBFS, udział próbek bliskich ciszy (|PCM|≤32), udział bliskich pełnej skali (|PCM|≥32604), liczbę ramek i liczbę pozytywnych klasyfikacji. Nie zapisuje nagrania ani nie otwiera żadnego połączenia.
- `AutoAudioStreamingVad.windowDiagnostics()` zwraca osobne pomiary F1/F2/F3 **tylko gdy wszystkie okna spełniają wcześniejsze wymagania kompletności i PTS**. Przy zgubionych pakietach / niekompletnej analizie nie generuje diagnostyki pozornie poprawnej.
- W panelu Auto Audio dodano sekcję **„Kontrola wejścia VAD”**, gdzie widać źródłową częstotliwość próbkowania, frakcję pozytywnych ramek każdego fragmentu i parametry PCM przekazanego modelowi. Doprecyzowano tekst „diagnostyczny VAD” i komunikat o braku kompletnego wyniku streamingu.
- Testy jednostkowe obejmują resampling **44,1 kHz → 16 kHz**, poprawne RMS/peak dla sygnału stałego, ciszę mimo sztucznego pozytywnego wyniku klasyfikatora, pełną skalę, trzy niezależne okna i brak wyniku przy zgubionym pakiecie. To **testy toru PCM z fałszywym klasyfikatorem**, nie certyfikacja rozpoznawania muzyki przez rzeczywisty WebRTC.
- Nie zmieniano natywnego AAR, trybów 15 s / 3×10 s, filtrów Lua, RNNoise, EQ, prędkości 1,5–3×, A/B, streamingu, podpisu ani numeru APK. Nie wydano produkcyjnego v47.

### Dalsza walidacja — wymagane przed scaleniem / produkcyjną aktualizacją

1. Sprawdzić nowy commit i wyniki GitHub Actions: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `check_workflows` (stan CI notować **po zakończeniu**, nie przewidywać).
2. Powtórzyć **3 × 10 s** na **tym samym instrumentalu i wywiadzie**, a także na ciszy, jednostajnym szumie oraz materiale mieszanym. Porównać *F1/F2/F3* i źródłowe **sample rate / PCM RMS / peak / near-silence / near-full-scale**, sprawdzić, czy wartości odpowiadają rzeczywistemu typowi materiału. Test obejmuje realny streaming i prędkości 1,5×–3×.
3. Jeśli sygnał PCM jest poprawny, przetestować **drugi niezależny klasyfikator** / detekcję muzyki na tych samych pięciu kontrolach **bez zmiany filtrów**; dopiero po pomiarze fałszywych wykryć i przeoczeń rozważać reguły sterowania.
4. Nie scalać PR #55 ani nie wydawać podpisanego v47 wyłącznie na podstawie testów jednostkowych i diagnostyki.

