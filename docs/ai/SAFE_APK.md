# MPVRex V3.2 — Bezpieczne APK (etap 8/10)

## Zakres
Jedyną zatwierdzoną linią jest MPV Alpha v56 w `laszko14/REX-Player`, bazowy commit `74b059ab0d2c5492451c42f88ff993128a2e45c9`. Gałąź `master` jest rozbieżna. Etap 8 zmienia wyłącznie dokumentację i kontrolowany proces CI, nie kod aplikacji ani certyfikat. Obowiązuje SOURCE CI oraz osobny PR CI na dokładnym końcowym SHA.

## Autoryzacja i wejście
Zwykły `push` ani `pull_request` NIE mogą uruchomić `signed_v56_apk`. Osoba będąca właścicielem repozytorium musi **osobno** zatwierdzić realne podpisanie i świadomie wykonać `workflow_dispatch` dla właściwej gałęzi `docs/mpvrex-v32-*` w istniejącym `.github/workflows/build.yml`, podając:
- `approval=SIGN_MPV_ALPHA_V56`;
- `source_sha` jako pełny 40-znakowy SHA zatwierdzonego kodu;
- `version_code_base` jako nowy kod bazowy większy od 100056 (wynikowy arm64 kod = baza * 10 + 2);
- `version_name` w formacie `v56.N`, z N > 0.

Samo `DALEJ` **nie jest zgodą** na podpisanie. Przed uruchomieniem trzeba potwierdzić aktualny zainstalowany versionCode, aby nowy był większy. Wynik debugowy musi mieć pakiet `xyz.mpv.rex.debug` oraz ABI `arm64-v8a`.

**Uwaga operacyjna:** GitHub wymaga, by workflow obsługujący `workflow_dispatch` istniał także na domyślnej gałęzi. `build.yml` już istnieje na `master`, ale jego historyczna wersja nie deklaruje tych nowych pól; możliwość uruchomienia ich na gałęzi v56 musi zostać osobno potwierdzona w rzeczywistym wywołaniu. Bez tego nie uznawaj mechanizmu za dostępny i nie przenoś workflow na rozbieżny `master` bez nowego zlecenia.

## Kontrole i bezpieczeństwo
1. Pierwszy krok podpisu: `mpvrex_apk.py authorize --ci` odrzuca inne eventy, aktora, repo, gałąź, niezgodny SHA, brak pełnej historii, niezatwierdzoną bazę v56, zmiany plików aplikacji i niezaufane workflow.
2. `sources` musi zakończyć się sukcesem, a `testDebugUnitTest` i `lintDebug` muszą przejść **przed udostępnieniem sekretów**.
3. Dopiero wtedy oryginalny klucz z istniejących GitHub Secrets podpisuje debug `assembleDebug`. Nie tworzymy nowego klucza, nie kopiujemy go do repozytorium ani artefaktu.
4. `apksigner` potwierdza podpis i SHA-256 certyfikatu z `.github/mpv-alpha-signing-certificate.sha256`; `aapt` sprawdza pakiet oraz wersję; archiwum zawiera wyłącznie bibliotekę ARM64. Wymagane jest dokładnie jedno APK.
5. Artefakt prywatny w GitHub Actions, bez tagu, force-push, merge, release i zmian domyślnej gałęzi. Zapis nie instaluje APK na telefonie.

Historyczny job `signed_apk` w `build.yml` obsługuje odrębną gałąź `master`, a `mpv-alpha-v39-debug-buffer-signed.yml` ma własny dawny kontrakt. **Nie są zatwierdzoną ścieżką nowego APK v56.** Nie zmieniamy ich uprawnień bez oddzielnego zakresu.

## Testy
Kontrola wyłącznie strukturalna: `python3 .github/scripts/mpvrex_apk.py workflow`.
Regresje: `python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v`.
CI: wymagane SOURCE i PR dla końcowego SHA, obydwa obowiązkowe joby SUCCESS. Sam lokalny test nie daje `APK_VERIFIED`.

## Status
Etap 8 można uznać za `CODE_VERIFIED` po wymaganych zielonych SOURCE/PR CI. Bez osobnego podpisania i pobrania artefaktu: `APK_BLOCKED` (brak autoryzowanej kompilacji) lub `NOT_TESTED` jeśli ścieżka wciąż wymaga sprawdzenia. `DEVICE_VERIFIED` dopiero po rzeczywistym teście telefonu. Nie podpisuj APK w ramach tego etapu bez nowego zatwierdzenia.
