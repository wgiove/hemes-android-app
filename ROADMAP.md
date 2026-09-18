# Roadmap — Hermes Mobile Companion

Verbindliche Ausbaustufen. Jedes abgeschlossene Stück landet als **Git-Tag** (z. B. `v0.1-mvp`) mit CHANGELOG-Eintrag.

---

## Phase 1 — MVP (Android-First, privat)

**Ziel:** Aus einem einzigen Gerät heraus kann Hermes deine Fotos/Videos finden, Duplikate erkennen und ausgewählte Inhalte zur Analyse an den Server senden.

- [x] Projektgerüst, README, Architektur, Datenschutzkonzept
- [ ] Server-Brücke (Django): Pairing-API, Command-API, Qdrant-Client-Brain
- [ ] App-Skeleton (Flutter): Pairing-Screen, Gedankenfluss, Settings
- [ ] Medien-Scanner (MediaStore): Fotos + Videos lesen, Vorschau
- [ ] Exakte Duplikaterkennung (SHA-256), Gruppe + Papierkorb-Ablauf
- [ ] Auswahl & Übertragung an Hermes → Qdrant-Index
- [ ] Audit-Log + Kill-Switch (App + Server)
- [ ] Git-Tag `v0.1-mvp`

## Phase 2 — Content & ähnliche Medien

- [ ] Perceptual Hash: ähnliche Bilder gruppieren
- [ ] Instagram-Content-Vorbereitung (Bildwahl, Reihenfolge, Caption, Hashtags)
- [ ] Begrenzte Fernsteuerung (Accessibility) mit Action-Allowlist
- [ ] Git-Tag `v0.2-content`

## Phase 3 — Skalierung & Veröffentlichung

- [ ] iOS (Flutter-Codebasis reicht strukturell)
- [ ] Multi-User/Mandanten für wenige Nutzer
- [ ] Store-Publishing-Pipeline (Play Store), Datenschutzerklärung, DPA
- [ ] Entscheidung Open Source für Hermes-Community
- [ ] Git-Tag `v1.0`

---

## Entscheidungshilfe: Flutter vs. nativ

| Kriterium | Flutter | Nativ (Kotlin) |
|---|---|---|
| Eine Codebasis für iOS später | ✔ | ✘ (getrennt) |
| Medien/Dedupe-Zugriff | gut (Plugins) | sehr direkt |
| Accessibility-Steuerung | über Plugin, mglw. Einschränkungen | voll direkt |
| Aufwand MVP | etwas mehr Abstraktion | direkt |

> **Tendenz:** Flutter wegen iOS-Perspektive; Accessibility-Fernsteuerung ggf. als natives Modul. Finale Entscheidung folgt nach Tooling-Check auf der Zielumgebung.