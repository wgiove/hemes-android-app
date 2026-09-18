# Datenschutz- & Sicherheitskonzept — Hermes Mobile Companion

This document (German) defines the privacy and security model. It is designed so the project can be used for private use, and later — with adjustments like a privacy policy and DPA — published for other Hermes users.

---

## 1. Grundsatz

**Die App übernimmt nie die Kontrolle über das Gerät oder die Daten.** Sie ist eine kontrollierte Schnittstelle zwischen dem Smartphone und dem Hermes-Agenten des Nutzers. Zweck: dem Nutzer Arbeit im Umgang mit den eigenen lokalen Medien abnehmen — nichts anderes.

## 2. Datenverarbeitung im Überblick

| Daten | Verarbeitung | Verbleib |
|---|---|---|
| Lokale Fotos/Videos/Dateien | nur auf Anforderung eingelesen; auf dem Gerät, das Original bleibt auf dem Gerät | Gerät |
| Metadaten + Hash + Thumbnail | beim Senden an Hermes übertragen | Server (Qdrant/metadat) |
| Eventuelle extrahierte Inhalte (OCR/KI) | Server-seitig, nur für ausgewählte Dateien | Server |
| Caption/Texte | nur auf Anfrage generiert, aus ausgewählten Medien | Server |
| Audit-Log (Aktionen) | lokal + serverseitig | Gerät + Server |

## 3. Berechtigungen & Freigaben (granular)

Die App fragt **nur die nötigsten Rechte** ab und trennt sie klar:

1. **Medien lesen** (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`) — für Suche/Duplikate (Phase 1)
2. **Dateien lesen** (Storage Access Framework) — beliebige Dateien, nutzergesteuert
3. **Bedienung** (`AccessibilityService`) — optional, Phase 2, nur mit enger Allowlist
4. **Screenshot** (`MediaProjection`) — optional, nur nach sichtbarem Start

Jede Freigabe ist einzeln erteil- und widerrufbar. **Keine** permanent heimlichen Rechte; kein Zugriff auf Nachrichten anderer Apps; keine Installation fremder Apps; keine eigenständigen Transaktionen.

## 4. Löschen

- Finale Löschungen laufen **nie automatisch**. Duplikate werden zunächst in den **Android-Papierkorb** verschoben.
- Eine endgültige Löschung erfolgt erst nach erneuter Bestätigung durch den Nutzer.
- Geschützte Neuigkeiten (Favoriten, RAW, definierte Ordner) werden nie gelöscht.

## 5. Pairing & Authentifizierung

- Erstkopplung per **QR-Code / Einmalcode**, der gültig nur kurz ist.
- Danach **verschlüsselte Bearer-Tokens**, abgelegt im Android-**Keystore** (nicht im Klartext im Dateisystem).
- **Credentials werden niemals im Klartext protokolliert** (weder in Chat noch Logs).

## 6. Kommunikation

- Transport ausschließlich **TLS**.
- Kein Umweg über fremde Drittanbieter-Server; direkte Verbindung zwischen App und eigener Hermes-Instanz.

## 7. Audit-Log & Kill-Switch

- **Audit-Log**: jede Aktion wird lokal (verschlüsselt) und serverseitig protokolliert (Zeitstempel, Aktion, Ziel).
- **Kill-Switch**: der Nutzer kann den Remote-Zugriff jederzeit **sofort** sperren (unabhängiger Schalter in der UI; unterbricht Verbindung und ungültigt Tokens).

## 8. Mandantentrennung

Daten werden **strikt pro Nutzer/Gerät getrennt** — bereits im Design, auch wenn aktuell nur 1 Nutzer aktiv ist. So bleibt der Weg zu weiterer Nutzung (z. B. Adventure Steam: mehrere Nutzer/Kunden) offen, ohne Architektur-Anpassungen.

## 9. Veröffentlichung / Open Source (später)

Vor einer Veröffentlichung (z. B. für die Hermes-Community) werden nötige Dokumente ergänzt: Datenschutzerklärung, DPA-Baustein, Lizenz, Contribution-Guide. Solange **Private** – Nutzung nur durch den Eigentümer.

## 10. Verantwortlicher

Dieses Projekt wird im Rahmen von **Adversum GmbH** entwickelt und gepflegt. Für private Nutzung durch den Auftragnehmer.