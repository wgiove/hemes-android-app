# Changelog

Alle nennenswerten Änderungen dieses Projekts. Format in Anlehnung an [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added (2026-09-18)
- Projektgerüst mit Ziel, Sicherheitsprinzipien und Struktur: `README.md`
- Architekturbeschreibung: `docs/ARCHITEKTUR.md`
- Datenschutz- & Sicherheitskonzept: `docs/DATENSCHUTZ.md`
- Roadmap mit Phasen 1–3 und Entscheidungshilfe Flutter vs. nativ: `ROADMAP.md`
- Entwicklungs-Guidelines: `docs/CONTRIBUTING.md`
- `.gitignore` (Python/Django, Flutter/Android, Secrets, OS)
- Initiales Git-Repository + `main`-Branch
- Native Kotlin/Android-Projektgerüst unter `app/`
- MediaStore-Scanner-Scaffold: fragt Medienrechte an und zählt Fotos/Videos read-only
- Sicherheitsorientiertes Manifest: nur Medien-Leserechte, kein Cleartext-Traffic
- Android-Debug-APK erfolgreich gebaut (5,6 MB; SHA-256: `6fb00a8226aa9a02389dc1e43901895678000df24f591e055dd0d81d1520ff3c`)
- Lokale Build-Toolchain dokumentiert: JDK 17, Android SDK 35, Gradle 8.7
- Lokale exakte Duplikaterkennung per SHA-256 — keine KI, keine Übertragung, keine Tokens
- Größenvorsortierung vor Hashing, um unnötige Lesevorgänge und Akkuverbrauch zu reduzieren
- UI-Aktion „Doubletten lokal finden“ mit Gruppen- und Speicheranzeige
- APK erneut erfolgreich gebaut (5,7 MB)
- Lokaler Leistungscheck: misst Medien-Scan, SHA-256-Geschwindigkeit und verfügbaren RAM
  als Entscheidungsgrundlage für ein on-device LLM
- On-device LLM Provider-Slot über `tasks-genai` 0.10.27 (Gemma 3 1B), APP bleibt ohne
  Modell voll funktionsfähig (erwartet `gemma-3-1b.task` im internen Speicher)
- Echte `LlmInference`-Integration: `createFromOptions` + `generateResponse`
  (CPU-Backend default) — echte lokale Textgenerierung statt Platzhalter
- APK durch ABI-Filter auf `arm64-v8a` reduziert (realme 9 Pro+): 59 MB → 20 MB
- App erscheint als Android-Teil-Ziel für Bilder, Videos, PDFs und Dateien
  (ACTION_SEND / SEND_MULTIPLE) und übernimmt geteilte URIs in die Warteschlange
- Modernes UI-Redesign: Karten für „Medien“ und „Lokale KI“, Material-Design-Buttons,
  Header mit App-Icon, saubere Abstände und Statuszeile
- Messenger-Chat-UI: Verlauf mit Blasen (User rechts/KI links), Eingabefeld unten,
  Werkzeugleiste, Antworten auswählbar/kopierbar
- Human-in-the-Loop (Bestätigungsansicht): serverlastige Aufträge (Word, PDF, Instagram,
  Mail) werden vor der Übergabe an Aiden zur Bestätigung angezeigt
- Hermes-Serverbrücke (`HermesBridge`): überträgt bestätigte Aufträge an eine per
  BuildConfig konfigurierte URL; Sicherheitsregel in `docs/HUMAN_IN_THE_LOOP.md`
- Django-Serverbrücke (`server/`): Pairing (Code + authorize + Token), Bearer-Auth,
  Command-Endpunkt `/api/command`, serverseitiges Audit-Log (CommandLog)
- App-Brücke produktiv verdrahtet: feste HTTPS-Adresse
  `https://aiden.adversum-business.de/mobile-api`, Pair-Button, Pairing-Code-Anzeige,
  Token-Abholung und Bearer-Command; Cleartext bleibt deaktiviert
- Django-Brücke als systemd-Dienst auf dem VServer hinter Nginx/TLS deployed und per
  öffentlichem HTTPS-End-to-End getestet (Pairing → Authorize → Token → Command)
- Werkzeuge in einem Burger-Menü gebündelt; aus dem Header entfernt, damit der Chat
  nicht mehr von unlesbaren Buttons verdrängt wird
- Hamburger-Menü als eigenes Vector-Icon mit Ripple und Dark-Mode-Farbe umgesetzt
  (Medien, Duplikate, Leistungscheck, LLM-Test und Aiden-Kopplung als lesbare Menüeinträge)

*Der Medien-Scanner und die lokale Duplikaterkennung sind funktional; Papierkorb, Pairing, LLM-Provider und Hermes-API folgen.*- Pairing-Fluss abgesichert: letzter Code wird gespeichert, Menüeintrag
  „Token abholen (Code …)" erlaubt Wiederaufnahme; Anzeige „Aiden ist gekoppelt ✓"
- Markdown in Chat-Blasen wird gerendert (**fett** erscheint fett statt als Sternchen)
- Messenger-Eingabe neu: Anhang-Icon, lokales Mikrofon-Icon und Sendepfeil statt Textbutton
- Mehrfach-Dateiauswahl über Android-Dateiauswahl; Dateien werden zunächst lokal übernommen
- lokale deutsche Spracherkennung über Android On-Device SpeechRecognizer; Transkript landet bearbeitbar im Eingabefeld, keine automatische Aiden-Weitergabe

## [v0.21-morning-briefing] – 2026-09-19
- Tagesbriefing: Notification Listener sammelt lokal Benachrichtigungen ausgewählter Apps.
- Morgen-Alarm (AlarmManager) mit einstellbarer Uhrzeit erstellt lokale Zusammenfassung.
- Menüeinträge: Tagesbriefing anzeigen, Briefing-Zeit festlegen, Benachrichtigungszugriff aktivieren.
- Briefing-Einträge rein lokal; keine Weitergabe an Aiden ohne Bestätigung.

## [v0.22-briefing-app-config] – 2026-09-19
- Konfigurierbare lokale Briefing-App-Auswahl mit Checkbox-Liste installierter startbarer Apps.
- Auswahl wird lokal gespeichert; „Alle aus“ pausiert die Sammlung.

## [v0.23-llm-import-fix] – 2026-09-19
- Modell-Import gehärtet: erst in temporäre Datei kopieren, dann atomar umbenennen;
  abgebrochener Import beschädigt das vorhandene Modell nicht mehr.
- Crash-Handler schreibt Stacktraces in eine lokale Datei (crash.log);
  Menüeintrag „Crash-Protokoll anzeigen“ zeigt sie im Chat.

## [v0.24-token-fallback-fix] – 2026-09-19
- Tokenablage ausfallsicher: Wenn der Android Keystore nicht verfügbar ist, fällt die
  App auf normale Ablage zurück und blockiert die Kopplung nicht mehr.
- saveToken fängt jetzt jede Ausnahme; der Pairing-Vorgang scheitert nicht mehr,
  nur weil verschlüsseltes Speichern fehlschlägt.

## [v0.25-briefing-app-list-fix] – 2026-09-19
- Briefing-App-Auswahl nutzt robuste installierte Nutzer-App-Liste statt nur Launcher-Abfrage.
- Checkbox-Dialog für ColorOS vereinfacht; bekannte Briefing-Apps bleiben als Fallback sichtbar.

## [v0.26-known-apps-visible] – 2026-09-19
- Gmail, Outlook, WhatsApp, Instagram, LinkedIn, To Do und Kalender sind per <queries>
  als sichtbar deklariert und damit immer in der Briefing-Auswahl vorhanden,
  auch wenn ColorOS sie als System-Apps markiert.

## [v0.27-grounded-briefing] – 2026-09-19
- Tagesbericht/Tagesbriefing/Tageszusammenfassung werden strikt aus lokalen
  Benachrichtigungen erzeugt und nicht mehr frei durch Gemma 1B generiert.
- Verhindert erfundene Namen, Termine und Fakten bei Briefing-Anfragen.

## [v0.28-german-language-guard] – 2026-09-19
- Fester deutscher Qualitätsrahmen vor normalen lokalen Antworten.
- Antwortlänge von 1024 auf 512 Tokens reduziert; Top-K auf 20 begrenzt,
  um sprachliches Driften und Fantasieausgaben zu verringern.
- Tagesbriefing bleibt deterministisch quellengebunden.
