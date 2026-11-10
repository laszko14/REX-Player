# MPVRex V3.2 — Integracja ChatGPT i wznawianie pracy (etap 9/10)

## Źródła prawdy

Repozytorium: `laszko14/REX-Player`. Zatwierdzona baza MPV Alpha v56: `build/player-transparency-v56` na pełnym SHA `74b059ab0d2c5492451c42f88ff993128a2e45c9`. `master` jest rozbieżny i nie jest bazą tej modernizacji. Kod odtwarzacza i proces podpisu APK pozostają poza zakresem etapu 9.

**Tylko jedno źródło stanu maszynowego:** `docs/ai/CHECKPOINT_STATE.json`. Historia zdarzeń: `docs/ai/ERROR_CASES.json`. Instrukcje: `AGENTS.md`, `docs/AI_HANDOFF.md`, `docs/ai/CHECKPOINT_POLICY.md`, `docs/ai/STARTUP_PREFLIGHT.md`. Nie tworzymy drugiego checkpointu ani autonomicznej, nieweryfikowalnej pamięci ChatGPT.

## Protokół startu w nowej rozmowie

1. Ustal cel oraz uprawnienie: ANALYZE jest tylko do odczytu, IMPLEMENT upoważnia wyłącznie do określonego etapu; podpisane APK, release, merge, zmianę certyfikatu lub Secrets zatwierdza się oddzielnie.
2. Przez **bezpośredni konektor GitHub** odczytaj prawidłową gałąź `docs/mpvrex-v32-*`, najnowszy pełny HEAD SHA i porównaj pochodzenie od v56. Nie czytaj plików z domyślnego `master`.
3. Z tego samego jawnego SHA odczytaj pliki wymienione w `mpvrex_chat_context.DOCUMENTS`. Potwierdź zgodność `docs/AI_HANDOFF.md`, `docs/ai/CHECKPOINT_STATE.json` i bieżącego SHA.
4. Odczytaj z GitHub Actions **żywe SOURCE CI** (`push`: head SHA, branch, event, run ID, attempt, komplet wymaganych jobów) i osobne **PR CI** (`pull_request`, head SHA, numer PR, base SHA oraz rzeczywisty checkout merge SHA z rodzicami). Nie używaj wyniku SOURCE zamiast PR.
5. Odróżnij `CI_PENDING`, `CI_BLOCKED`, `NOT_TESTED`, `CODE_VERIFIED`, `APK_BLOCKED` i `DEVICE_VERIFIED`. Zapisany `CI_PENDING` może być historycznym stanem w commicie, dlatego sprawdzaj aktualny run. Brak runu, fail lub skipped obowiązkowego joba nie oznacza sukcesu.
6. Jeżeli wystąpi FAIL, odczytaj job/krok/log, sprawdź fingerprint rejestru błędów, napraw przyczynę i wykonaj regresje; nie rozpoczynaj kolejnego etapu na nierozwiązanym CI.
7. Po IMPLEMENT sprawdź pełny SHA końcowego commita, SOURCE i wymagane PR CI. Zapis `completed` poprzedniego etapu i `active=CI_PENDING` obecnego wykonuj tylko przy kolejnym autoryzowanym etapie, nie przez samopodbijający commit.

## Narzędzie wyłącznie do odczytu

```bash
python3 .github/scripts/mpvrex_chat_context.py snapshot
python3 .github/scripts/mpvrex_chat_context.py ci-check
python3 .github/scripts/mpvrex_chat_context.py check-evidence --evidence-file /tmp/mpvrex-live-evidence.json
```

- `snapshot` czyta lokalne, zweryfikowane strukturalnie instrukcje, checkpoint i rejestr błędów, pokazuje etap i wymagane pliki; zawsze zwraca `live_*_verified=false`.
- `ci-check` działa **wyłącznie wewnątrz GitHub Actions** i odrzuca złe repo, gałąź, SHA, płytką historię i PR bez prawdziwego checkoutu merge; nie sprawdza końcowego wyniku własnego workflow.
- `check-evidence` jedynie sprawdza spójność JSON uzyskanego **wcześniej z żywego konektora**; akceptacja danych nie potwierdza ich autentyczności, świeżości ani zakończenia CI bez niezależnej weryfikacji narzędziem GitHub. Format JSON zawiera dokładnie: `repository`, `branch`, `source_sha`, `source_run`, `pr_run`, `pr`; run ma `id`, `attempt`, `event`, `head_branch`, `head_sha`, `status`, `conclusion`, `jobs`, a PR `number`, `base_sha`, `head_sha`, `merge_sha`, `merge_parents`. Obowiązkowe joby muszą mieć `status=completed` i `conclusion=success`.
- Narzędzie nie pobiera z sieci, nie wykonuje zmian w GitHub, nie zapisuje plików, nie podpisuje APK i nie zapamiętuje danych między rozmowami. Każde uruchomienie wymaga dostępu do właściwego checkoutu i danych GitHub.

## Kontrole i testy

CI wywołuje `mpvrex_chat_context.py ci-check` dla push gałęzi MPVRex i testowego PR v56, obok istniejącego preflight i walidatora `mpvrex_pr_ci.py validate --ci`. Zmiana checkoutu na head-only, brak kontroli lub `continue-on-error` są zabronione. Nowe testy `test_mpvrex_chat_context.py` sprawdzają wznowienie, przestarzały SHA, złe repo/gałąź, pominięty Android, brak PR, fałszywe merge, brak pełnej historii, powielony run i niezaufany JSON.

Regresja: `python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v`, bramka `python3 .github/scripts/mpvrex_quality.py validate`, CI Android (assembleDebug, testDebugUnitTest, lintDebug), wymagany SOURCE/PR na końcowym SHA.

## Ograniczenia

Połączenie GitHub jest uprawnieniem konta ChatGPT, a nie nową funkcją kodu repozytorium. Skrypt ani plik handoff nie włączają automatycznie pluginu, pamięci, monitora CI lub pracy w tle. Nie zapisuj sekretów, prywatnych logów, adresów streamingu, historii odtwarzania ani danych osobowych. Nie używaj TinyFish ani innych zewnętrznych przeglądarek automatyzujących GitHub. Etap 10/10 — audyt końcowy — może ruszyć dopiero po potwierdzonym sukcesie etapu 9 i osobnej komendzie DALEJ. APK pozostaje bez nowej zgody niepodpisane.
