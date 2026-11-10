# MPVRex — AI handoff / wznowienie V3.2

**Kanoniczny rejestr:** [CHECKPOINT_STATE.json](ai/CHECKPOINT_STATE.json).
**Zasady:** [CHECKPOINT_POLICY.md](ai/CHECKPOINT_POLICY.md).
**Preflight:** [STARTUP_PREFLIGHT.md](ai/STARTUP_PREFLIGHT.md).

## Produkt i bezpieczeństwo
- Repozytorium: `laszko14/REX-Player`; właściciel używa MPV Alpha **v56 debug**.
- Zatwierdzona baza: `build/player-transparency-v56`, commit
  `74b059ab0d2c5492451c42f88ff993128a2e45c9`.
- `master` jest rozbieżny — nie zastępuje v56 ani nie jest celem merge.
- **Nie** zmieniaj kodu odtwarzacza, podpisu APK, danych, sekretów ani
  publikacji bez osobnego zlecenia.

## Ostatnie etapy — potwierdzone przed zapisem tego dokumentu
- 1/10: v56 instrukcje, zakończone na
  `99d19f3daaeba5c037a947b508e481fac2c82088`.
  Workflow `42729647590`, attempt 1, źródła i Android **SUCCESS**.
- 2/10: preflight, zakończony na
  `4e7db043b4d9a8d6cce47d134b141e14fecc592d`.
  Workflow `42734552364`, attempt 1, źródła i Android **SUCCESS**;
  podpis APK pominięty. Weryfikowano bezpośrednio w GitHub na tym SHA.
- 3/10: **w trakcie** na `docs/mpvrex-v32-stage03-checkpoints-20261110`, od końcowego SHA 2/10.
  Cel: trwały checkpoint, zapis lokalny, walidator, testy.
  **Nie deklaruj CODE_VERIFIED dla 3/10 przed końcowym CI.**

## Wznowienie (nie ufaj samemu checkpointowi)
1. Odczytaj **live HEAD** właściwej gałęzi w GitHubie oraz pełną historię
   run/attempt/job i wszystkie wymagane wyniki na **końcowym SHA**.
2. Odczytaj `AGENTS.md`, `CONTRIBUTING.md`, ten plik, JSON i politykę
   **z dokładnie tego SHA**; uruchom/zweryfikuj preflight na checkout.
3. Z `CHECKPOINT_STATE.json` wybierz ostatni zweryfikowany etap i `active`.
   `CI_PENDING` w snapshotcie nie oznacza, że live CI nadal trwa.
4. Wszelkie błędy źródeł/Androida naprawiaj w ramach tego samego zadania,
   potem ponownie sprawdzaj końcowe SHA. Bez sztucznego PASS.
5. Nie zaczynaj następnego etapu bez zweryfikowania poprzedniego.

## Plan zatwierdzony przez właściciela
1. Instrukcje v56; 2. Preflight; 3. Checkpointy; 4. Rejestr błędów;
5. Systematic Debugging; 6. Bramki jakości; 7. SOURCE/PR CI;
8. Bezpieczne APK (odrębna zgoda); 9. Integracja ChatGPT;
10. Audyt końcowy.

## Następna czynność
Po zmianach etapu 3 sprawdź na **dokładnym końcowym SHA** testy Python
(w tym pozytywne/negatywne checkpointy), źródła, Android assembleDebug /
testDebugUnitTest / lintDebug oraz brak uruchomienia podpisu APK.
Jeżeli zakończone sukcesem: etap 3/10 może być zgłoszony jako
`CODE_VERIFIED` z linkiem do bieżącego runu. Dopiero polecenie
„Dalej” rozpoczyna etap 4/10.

**Uwaga o cyklicznym zapisie:** sam nowy commit checkpointu zmienia SHA.
Nie wykonuj nieskończonych commitów tylko po to, by wpisać wynik własnego CI.
Prawdziwy wynik końcowy jest w GitHub Actions i w raporcie; JSON promuje go
do historii przy następnym autoryzowanym etapie.
