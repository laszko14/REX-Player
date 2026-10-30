# MPV Alpha v55 — zamiana osi gestu przytrzymania i regulacji prędkości

**Baza:** podpisana MPV Alpha v54 z gałęzi `fix/v54-original-audio-bypass` / commit `c6107ce14a9f1768c73f975c5d60b1d8bd71aaed` (PR #62), bez scalania do `master`.

**Zmiana:** po około 300 ms przytrzymania, w górę zwiększa, w dół zmniejsza prędkość, w prawo blokuje, w lewo odblokowuje. Blokada po 60 dp świadomego poziomego ruchu, ukośne odcinki ignorowane, regulacja pionowa dopasowana do wysokości ekranu.

**Niezmienione:** poziomy prędkości, przywracanie po puszczeniu, ustawienia, arbitraż dotyku, Lua, pozostałe filtry audio, normalizacja i balans głośności.

**Pliki:** `GestureHandler.kt`, `HoldSpeedGestureMath.kt`, `PlayerControls.kt`, polskie i angielskie `ui_audit.xml`, testy `HoldSpeedGestureMathTest.kt`.

**Weryfikacja CI:** `check_workflows.py`, Gradle `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, apksigner + SHA256 certyfikatu z repozytorium, `xyz.mpv.rex.debug`, `versionName=v55`, `versionCode=1000552`, ARM64. Workflow korzysta z istniejącego, trwałego klucza podpisu v54.

**Test telefonu po instalacji:** cztery kierunki, w górę potem blokada w prawo, ukośne ruchy, puszczenie palca, pion/poziom, gesty głośności/jasności/przewijania, napisy, Lua, balans głośności v53, streaming.

**Stan:** zmiany w PR #63, bez merge do master. Sam sukces CI nie zastępuje testu na urządzeniu.

**Bezpieczne wydanie:** to aktualizacja istniejącej aplikacji debug, a nie osobny pakiet. Nie publikować APK bez zielonego całego workflow, dokładnego certyfikatu i sprawdzonego pliku artefaktu.
