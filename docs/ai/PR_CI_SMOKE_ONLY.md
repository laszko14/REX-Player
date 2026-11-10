# MPVRex V3.2 — PR CI smoke test (tylko gałąź testowa)

Ta niewielka zmiana służy wyłącznie do wywołania prawdziwego zdarzenia pull_request przeciwko gałęzi etapu 7. Nie jest częścią implementacji produkcyjnej, nie zmienia aplikacji, workflow, podpisu ani sekretów.

Sprawdzenie wymaga merge checkout (PR GITHUB_SHA), SHA gałęzi docelowej, SHA nagłówka PR oraz zielonych SOURCE i Android jobów na merge SHA. Nie należy wykonywać merge ani wydawać APK.
