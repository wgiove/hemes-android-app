# Human-in-the-Loop

## Verbindliche Regel

Hermes Companion führt externe oder folgenreiche Aktionen niemals ohne sichtbare Bestätigung von Werner aus.

Das lokale LLM darf analysieren, klassifizieren, Entwürfe erstellen und einen Vorschlag für den nächsten Schritt formulieren. Es darf aber nicht selbstständig eine Serveraktion auslösen.

## Ablauf

```text
Prompt des Nutzers
    ↓
lokale Analyse / Entwurf
    ↓
strukturierter Aktionsvorschlag in der App
    ↓
explizite Bestätigung oder Änderung durch den Nutzer
    ↓
Hermes/Aiden führt die bestätigte Aktion aus
    ↓
Ergebnis, Datei oder Fehler zurück an die App
```

## Bestätigungspflichtige Aktionen

Eine Bestätigung ist erforderlich für:

- Übertragung von Dateien oder Medien an Hermes, Qdrant oder einen anderen Server
- Erzeugung und Speicherung von Word-, PDF- oder anderen Dokumentdateien
- Teilen oder Versenden von Dateien
- Verschieben oder Löschen von Dateien
- Aktionen in anderen Apps
- Fernsteuerung des Geräts
- externe Kommunikation, Veröffentlichung oder Versand
- jede Aktion mit Kosten, Zugangsdaten oder rechtlichen Folgen

## Lokale Aktionen ohne Serverbestätigung

Reine read-only- und lokale Analyse darf ohne zusätzliche Bestätigung laufen:

- MediaStore-Liste lesen, nachdem die Android-Berechtigung erteilt wurde
- exakte Doubletten per SHA-256 ermitteln
- lokale Modellantwort erzeugen
- lokale OCR oder Klassifikation vorbereiten
- einen Entwurf anzeigen

Sobald aus einem Entwurf eine Dateiänderung, Übertragung oder externe Aktion wird, erscheint eine Bestätigungsansicht.

## Beispiel: Word-Dokument

```text
Nutzer: „Erstelle aus diesem Text ein Word-Dokument.“

Lokal:
- erstellt einen Textentwurf
- erkennt: DOCX-Erzeugung durch Hermes erforderlich
- zeigt den geplanten Auftrag

App:
„Ich würde folgenden Auftrag an Aiden senden:
Word-Dokument aus dem Entwurf erzeugen und als Datei zurückgeben.
[Abbrechen] [Ändern] [An Aiden senden]“

Erst nach „An Aiden senden“ wird die Serveraktion ausgeführt.
```

## Struktur des Vorschlags

```json
{
  "route": "hermes",
  "requires_confirmation": true,
  "reason": "word_document_required",
  "steps": [
    "text_entwurf_lokal",
    "docx_erzeugen_durch_hermes",
    "datei_zur_app_zurueckgeben"
  ]
}
```

Der Nutzer sieht vor der Bestätigung eine verständliche deutsche Beschreibung. Die JSON-Struktur dient nur der technischen Verarbeitung und ersetzt nicht die sichtbare Nutzerfreigabe.
