# Development Guidelines — Hermes Mobile Companion

Regeln für die Entwicklung dieses Projekts. Sie sind verbindlich — auch (gerade) für Aiden, der den Code-Stand hier pflegt.

## Arbeitsweise

- **Commits klein und atomar** — ein logischer Schritt je Commit.
- **Konventionelle Commit-Messages**: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- **Jede abgeschlossene Meilenstein-Etappe** bekommt einen **Git-Tag** (`v0.1-mvp`, …) und einen **CHANGELOG.md**-Eintrag.
- **Credentials niemals committen** — keine Token, Passwörter, API-Keys oder Pfad-Tricks in Code/README/Logs. `.env`-Muster nur als `.env.example`.
- **Datenschutzkonzept beachten** (siehe `docs/DATENSCHUTZ.md`) — jede neue Funktion muss das „keine Kontrolle über das Gerät/die Daten"-Prinzip wahren und Freigaben einhalten.

## Code-Konventionen

- **Python (Server/Django)**: PEP 8, Type Hints, Docstrings auf Deutsch/Englisch wo sinnvoll.
- **Flutter/Dart** (falls genutzt): `flutter analyze` fehlerfrei; State-Verwaltung schlank (z. B. Provider/Riverpod).
- **Migrations**: Django-Migrationen immer mitspeichern.
- **Logs/Audit**: niemals sensible Daten (Dateipfade, die viele Rückschlüsse auf User-Inhalt erlauben, nur abgekürzt), niemals Tokens.

## Tests / Qualität

- Server: pytest für API + Pairing-Logik anstreben.
- App: Widget-Tests für Freigabe-Flows (Kill-Switch, Papierkorb) mindestens.
- Vor jedem Tag: `manage.py check`, `flutter analyze` (falls vorhanden), dort, wo es auf der Build-Umgebung läuft.

## Branching

- Hauptbranch `main`; Features auf `feat/…`-Branches, mergt nach Review/Test.
- Da ich (Aiden/Hermes) direkt arbeite, halte ich `main` trotzdem stabil und testbar.

## Definition of Done

Ein Schritt ist *done*, wenn:
- Code vorhanden + auf der Zielumgebung lauffähig (verifiziert, nicht nur geschrieben),
- Tests/Checks grün,
- CHANGELOG aktualisiert,
- Datenschutz-Grundsätze nicht verletzt,
- commitet (+ getaggt falls Meilenstein).