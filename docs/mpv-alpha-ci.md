# MPV Alpha – szybkie GitHub Actions

## Aktualizacja APK na telefon

Uruchom recznie workflow **MPV Alpha - kontrola i podpisany APK na zadanie**
(`Actions > Run workflow > master`) albo dodaj `[mpv-alpha-apk]` do wiadomosci
commita na `master`. Powstanie tylko **jeden podpisany APK ARM64**
(`MPV-Alpha-vN-arm64-v8a.apk`) do instalacji jako aktualizacja.

Wersje `mpv-alpha-vN` sa rezerwowane tagiem po udanej kompilacji.
Klucz podpisu, pakiet `xyz.mpv.rex.debug`, odcisk certyfikatu i
`versionCode` pozostaja sprawdzane. Nie zmieniaj numeracji recznie.

## Kiedy co sie uruchamia

- **Push na master bez znacznika:** tylko szybkie kontrole zrodel (YAML,
  Python, tlumaczenia, Lua), bez generowania APK.
- **Pull request do master:** kontrola zrodel + pelna kompilacja debug,
  testy jednostkowe i Android Lint. Nie tworzymy dodatkowego artefaktu APK.
- **Push na master ze znacznikiem `[mpv-alpha-apk]`:** kontrola zrodel +
  dokladnie jedna podpisana kompilacja APK, bez rownoleglego builda debug.
- **Reczne uruchomienie glownego workflow:** podpisana kompilacja APK.
- **MPV Alpha - podglady interfejsu (PNG):** tylko uruchomienie reczne;
  podglady nie sa warunkiem zbudowania APK, a zapisane PNG wygasaja po 3 dniach.

Historyczny workflow dla 30 guzikow Lua zostal usuniety – jego
niepodpisana kompilacja dublowala pelna kontrole APK w pull requestach.
Pozostale stare migracyjne workflowy nie sa standardowa droga wydawania
MPV Alpha; nie nalezy ich uzywac do zwyklych aktualizacji.

## Wydajnosc

Glowny workflow juz wykorzystuje Gradle build cache, configuration cache,
Gradle actions cache i buduje tylko `arm64-v8a`. Nie dodajemy drugiego
cache projektowego `app/build`, ktory moglby przenosic nieaktualne pliki
miedzy wersjami. Dokladny zysk czasowy nalezy sprawdzac po kolejnych
wykonaniach `Actions`; czas kompilacji Kotlin moze sie roznic.
