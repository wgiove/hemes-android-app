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

*Der Medien-Scanner ist ein funktionaler Scaffold; Pairing, Deduplizierung und Hermes-API folgen in Phase 1.*