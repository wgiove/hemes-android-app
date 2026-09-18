# Architektur — Hermes Mobile Companion

Dieses Dokument beschreibt die Zielarchitektur der Android-Companion-App und der zugehörigen Server-Brücke. Es ist bewusst **skalierbar** ausgelegt: Android-First, aber mit Blick auf iOS, Multi-User und mehrere Hermes-Instanzen — ohne den Anfangskomplex zu überladen.

---

## 1. Systemübersicht

Zwei Komponenten, die über eine verschlüsselte (TLS) gesicherte Verbindung kommunizieren:

- **Android-App** (`app/`) — läuft auf dem Smartphone des Nutzers. Zuständig für lokale Ressourcen (Medien, Dateien) und für die *kontrollierte* Bedienung.
- **Server-Brücke** (`server/`) — läuft auf dem Hermes-Rechner. Zuständig für Authentifizierung, Befehlsverarbeitung, die Anbindung an den Hermes-Agenten und die Inhalts-Pipeline (Qdrant, OCR, Generierung).

Die schwere Arbeit (Bilderkennung, OCR, semantische Suche, Caption-Generierung) passiert **auf dem Server**, nicht auf dem Handy.

---

## 2. Komponenten im Detail

### 2.1 Android-App

| Schicht | Aufgabe |
|---|---|
| **UI** | Medien-Vorschau, Duplikat-Gruppen, Freigabe-Dialoge, Einstellungen, Kill-Switch |
| **Media-Repository** | Zugriff auf Fotos/Videos via MediaStore; Dateien via Storage Access Framework (SAF) |
| **Dedupe-Engine** | Exakte Duplikate per Hash (SHA-256); ähnliche Bilder per Perceptual Hash (Phase 2) |
| **Pairing/Auth** | Geräte-Kopplung per QR-Code / Einmalcode; Tokens im Android-Keystore |
| **Remote/Steuerung** | Begrenzte Bedienung (App öffnen, scrollen, tippen) — nur mit Freigabe und Allowlist |
| **Audit-Log** | lokale, verschlüsselte Protokollierung aller Aktionen |
| **Kill-Switch** | sofortige Sperre des Remote-Zugriffs; unabhängiger Schalter |

**Wichtige Android-Schnittstellen:**
- `MediaStore` — Bilder/Videos (mit Berechtigungs-Modellen seit Android 13: `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`)
- `SAF` (Storage Access Framework) — Zugriff auf beliebige Dateien
- `AccessibilityService` — nur für die *begrenzte* Fernsteuerung (Phase 2), mit enger Action-Allowlist
- `MediaProjection` — Screenshot nur nach explizitem, sichtbarem Start

### 2.2 Server-Brücke (Django)

| Schicht | Aufgabe |
|---|---|
| **Auth/Pairing** | One-Time-Pairing, verschlüsselte Tokens, Geräte-Registrierung |
| **Command-API** | Befehls-Endpunkte (suche, duplikate, sende, öffne …) mit Freigabe-Regeln |
| **Qdrant-Client** | Semantische Suche + Indexierung der übertragenen Inhalte |
| **Content-Pipeline** | Caption/Hashtag-Generierung (z. B. Instagram), Bildauswahl |
| **Mandantentrennung** | Daten strikt pro Nutzer/Gerät getrennt (auch bei nur 1 Nutzer schon angelegt) |
| **Audit/Logs** | serverseitige Protokollierung aller Requests |

---

## 3. Kommunikations- & Sicherheitsmodell

```
App  ──(Pairing: QR/Einmalcode)──►  Server
App  ──(TLS, Bearer-Token im Keystore)──►  Command-API
Server ──(Befehle, die der Nutzer freigibt)──►  Hermes-Agent / Qdrant
```

**Regeln:**
1. Jede ferngesteuerte Aktion ist entweder vom Nutzer bestätigt oder durch die Allowlist des Tools abgedeckt.
2. Löschen endet immer im Android-Papierkorb, nie sofort endgültig.
3. Freigaben sind granular: Medien-Lesen vs. Bedienung sind getrennt erlaubbar.
4. Audit-Log lokal + serverseitig; Kill-Switch unterbricht sofort.

---

## 4. Datenmodell (Server)

**Einheiten:**
- `User` / `Device`: ein Nutzer, mehrere gekoppelte Geräte
- `PairingSession`: einmalige Kopplung
- `AccessGrant`: welche Freigaben (Medien, Steuerung) für ein Gerät bestehen
- `CommandLog`: jede ausgeführte Aktion
- `MediaIndex`: Metadaten/Zusammenfassungen übertragener Medien (Originale bleiben auf dem Gerät)
- `ContentJob`: Auftrag zur Content-Vorbereitung (z. B. Instagram-Post)

> **Datensparsamkeit:** Es werden keine Original-Mediendateien dauerhaft dupliziert — nur Metadaten, Hashes, Thumbnails und (falls gewollt) Autorinformationen.

---

## 5. Technologien (Entscheidungskandidaten)

| Bereich | Optionen | Empfehlung |
|---|---|---|
| App-UI | Flutter / React Native / nativer Kotlin | **Flutter** (eine Codebasis, später iOS) — finale Entscheidung folgt mit Tooling-Check |
| Server | Django + DRF (wie Epochenhefte-Projekt) | **Django + DRF** — bekannte, mandantenfähige Basis |
| Suche | Qdrant (bestehende Instanz) | bestehende Qdrant-Infrastruktur |
| Auth | Django Token / JWT / Pairing-Code | Pairing-Code + verschlüsselte Tokens |

---

## 6. Entwicklungsreihenfolge (empfohlen)

1. **Server-Brücke** zuerst bauen (API, Pairing, Qdrant-Client, Brain) — testbar per curl.
2. **App-Skeleton** (Flutter) mit Pairing-Screen und Medien-Scanner.
3. **Dedupe-Grundfunktion** (Hash) mit sicherem Papierkorb-Ablauf.
4. **Übertragung an Hermes → Qdrant** durchverdrahten.
5. Dann Phase-2-Features (Ähnlich, Instagram, Fernsteuerung) schrittweise.