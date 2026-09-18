# Hermes Mobile Companion (Android)

**Schlanke Android-Companion-App für [Hermes Agent](https://hermes-agent.nousresearch.com/docs).**
Verbinde dein Smartphone mit deinem Hermes-Agenten, damit dieser auf deine **lokalen Medien** (Fotos, Videos, Dateien) zugreifen, sie ordnen und daraus **Content vorbereiten** kann — während die schwere Verarbeitung (Bilderkennung, OCR, Indexierung, Text-Generierung) auf deinem Server läuft.

> Ziel: **kontrollierter, begrenzter Zugriff** auf deine eigenen Dateien durch deinen Hermes-Agenten — unter deiner expliziten Freigabe, mit Audit-Log und Not-Aus.

---

## Projektziel

Lokale Dateien auf dem Smartphone zu finden und zu verwalten ist aufwändig. Diese App macht deinen Hermes-Agenten zum Assistenten für deine Medienbibliothek:

- **Medien suchen** — Fotos, Videos und Dateien auf dem Gerät nach Inhalt und Metadaten finden
- **Duplikate erkennen** — exakte Kopien (Hash) und ähnliche Bilder/Videos gruppieren
- **Inhalt an Hermes senden** — ausgewählte Dateien zur Analyse/Indexierung übertragen
- **Content vorbereiten** — aus Handybildern z. B. einen Instagram-Post (Bildwahl, Reihenfolge, Caption, Hashtags) vorbereiten
- **Kontrolliertes Fernsteuern** — begrenzte Bedienung (App öffnen, scrollen, tippen), nur mit Freigabe

## Sicherheitsprinzipien (verbindlich)

1. **Nichts läuft ohne explizite Freigabe** — jede Aktion ist vom Nutzer bestätigt oder durch eine Allowlist des Tools gedeckt.
2. **Endgültige Löschungen gibt es nie automatisch** — Duplikate wandern zunächst nur in den Android-Papierkorb.
3. **Audit-Log** — jede durchgeführte Aktion wird lokal protokolliert.
4. **Not-Aus / Kill-Switch** — der Nutzer kann den Remote-Zugriff jederzeit sofort sperren.
5. **Kein Cloud-Umweg** — Kommunikation verschlüsselt direkt zwischen App und Hermes-Instanz; keine fremden Server in der Leitung.
6. **Credentials niemals im Klartext** — Pairing per QR-Code/Einmalcode, Tokens verschlüsselt im Android-Keystore.

> **Die App übernimmt NIEMALS die Kontrolle über das Gerät oder die Daten** — sie ist eine kontrollierte Schnittstelle, die dem Nutzer Arbeit abnimmt.

---

## Architektur (Überblick)

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  Android-App  (diese App)   │        │  Hermes-Server  (Backend)    │
│                             │  TLS   │                              │
│  • Medien-Scanner           │ ◄────► │  • Hermes-Agent / API        │
│  • Duplikat-Erkennung       │        │  • Qdrant (Wissen/Index)     │
│  • Freigabe-Prompts         │        │  • Verarbeitung (OCR, KI)    │
│  • Audit-Log                │        │  • Pairing/Auth             │
│  • Kill-Switch              │        │                              │
└─────────────────────────────┘        └──────────────────────────────┘
         │  (MediaStore/SAF, verschl.)          │
         ▼                                       ▼
   dein Gerät (Fotos, Dateien)          Qdrant-Collection / Dateien
```

**Zwei Elemente:**
- **App** (`app/`): Android-Client, schlank — sucht, zeigt, sendet, protokolliert.
- **Server** (`server/`): Django-basierte Hermes-Brücke mit Pairing, Mandantentrennung, Qdrant-Anbindung und Content-Pipeline.

---

## Feature-Map (Phasen)

| Feature | Status | Phase |
|---|---|---|
| Projektgerüst + Doku | ✔ start | 1 |
| Medien-Scanner (Fotos/Videos) | kommend | 1 |
| Exakte Duplikaterkennung (Hash) | kommend | 1 |
| Ähnliche Bilder gruppieren | kommend | 2 |
| Auswahl + Übertragung an Hermes | kommend | 1 |
| Instagram-Content-Vorbereitung | kommend | 2 |
| Begrenzte Fernsteuerung (Accessibility) | kommend | 2 |
| Audit-Log + Kill-Switch | kommend | 1 |
| iOS-Version | später | 3 |

---

## Struktur

```
projekte_hermes_android/
├── app/        # Android-App (Flutter/nativ — Entscheidung offen)
├── server/     # Django-Hermes-Brücke (Pairing, Qdrant, Content)
├── docs/       # Architektur, Datenschutz, Sicherheit
├── scripts/    # Entwicklungs-/Build-Helfer
└── README.md
```

Details siehe [`docs/ARCHITEKTUR.md`](docs/ARCHITEKTUR.md) und [`docs/DATENSCHUTZ.md`](docs/DATENSCHUTZ.md).

---

## Roadmap

**Phase 1 (MVP, Android-First, privat):**
- Pairing App ↔ Server (QR-Code)
- Medien-Scanner (Fotos/Videos)
- Exakte Duplikaterkennung (Hash) + Papierkorb sicher
- Auswahl & Übertragung an Hermes → Qdrant
- Audit-Log + Kill-Switch

**Phase 2:**
- Ähnliche Bilder/videos gruppieren (Perceptual Hash)
- Instagram-Content-Vorbereitung
- Begrenzte Fernsteuerung mit Allowlist

**Phase 3:**
- iOS, Multi-User/Mandantenfähigkeit für wenige Nutzer, Store-Publishing

---

## Lizenz & Nutzung

**Privatnutzung** solange nicht anders entschieden. Veröffentlichung (z. B. Open Source für die Hermes-Community) erst nach Freigabe.