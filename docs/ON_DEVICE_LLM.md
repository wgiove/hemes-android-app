# On-device LLM auf Android

## Verifizierter Einstieg über Google AI Edge Gallery

Für den ersten Hardwaretest verwenden wir auf Werners realme 9 Pro+ (8 GB RAM) die offizielle App **Google AI Edge Gallery**. Sie lädt ein kompatibles Modell selbst herunter und führt die Inferenz danach lokal auf dem Android-Gerät aus.

### Installation

1. Google Play Store auf dem Android-Gerät öffnen.
2. Nach **AI Edge Gallery** suchen.
3. Die offizielle App von Google installieren.
4. In der App das Modell **Gemma 3 1B IT** auswählen.
5. Wenn mehrere Varianten angeboten werden, zunächst **INT4/4-bit** wählen.
6. Falls ein Backend auswählbar ist, zunächst **CPU** verwenden; GPU kann anschließend testweise verglichen werden.
7. Eine kurze Anfrage ausführen, zum Beispiel eine Zusammenfassung oder eine einfache Frage.

Das Modell läuft nach dem Download offline. Prompts und Antworten werden für diese lokale Ausführung nicht an Hermes, Google oder Qdrant übertragen. Ein Internetzugang wird nur für Installation, Modell-Download und eventuelle App-/Modell-Updates benötigt.

## Warum Gemma 3 1B IT?

- geeignete Größenordnung für das realme 9 Pro+ mit 8 GB RAM
- instruction-tuned (`IT`) und daher für direkte Fragen besser geeignet
- 4-bit-Variante spart Speicher und Rechenleistung
- geeignet für lokale Befehle, kurze Klassifikation, Zusammenfassungen und Vorverarbeitung
- nicht als Ersatz für Hermess komplexe Recherche oder große Dokumentaufgaben gedacht

Nicht als erste Variante verwenden:

- Gemma 3n E4B: deutlich höherer Speicher- und Rechenbedarf
- größere Modelle ab etwa 7B: für dieses Gerät nicht sinnvoll

## Testergebnis

Die erste lokale Modellantwort war sinngemäß:

> Das Modell kann nicht direkt auf das lokale Dateisystem zugreifen.

Das ist korrekt. Ein allgemeines LLM erhält von Android noch keine Dateiliste oder Medien-Tools. Genau diese kontrollierte Tool-Verbindung wird in Hermes Companion umgesetzt.

## Zielarchitektur für Hermes Companion

```text
Android MediaStore
    ↓ explizit freigegebene lokale Tool-Funktion
Medienliste / SHA-256-Duplikate / lokale Metadaten
    ↓ strukturierter Kontext
Gemma 3 1B IT auf dem Gerät
    ↓ optionales Ergebnis oder Hermes-Auftrag
Hermes-Server, nur wenn die Aufgabe es erfordert
```

Das lokale Modell soll nicht automatisch das Dateisystem durchsuchen. Die App stellt nur ausdrücklich erlaubte Ergebnisse bereit, zum Beispiel:

```json
{
  "tool": "list_media",
  "images": 1234,
  "videos": 56,
  "exact_duplicate_groups": 23,
  "potential_reclaimable_bytes": 1879048192
}
```

## Abgrenzung

- SHA-256-Duplikaterkennung: komplett lokal, keine Tokens, kein LLM
- Medienliste: komplett lokal nach Android-Berechtigung
- kleines LLM: optional lokal, keine Hermes-Tokens
- komplexe Caption, Recherche, Qdrant und große Materialien: Hermes-Server
- Löschen: nie automatisch endgültig; nur nach Nutzerbestätigung und zunächst über Android-Papierkorb

## Nächster technischer Schritt

Die Hermes-Companion-App erhält einen echten lokalen LLM-Provider. Dieser soll:

1. das vorhandene Gemma-Modell laden,
2. strukturierte lokale Tool-Ergebnisse in den Prompt einsetzen,
3. kurze Antworten lokal erzeugen,
4. bei komplexen Aufgaben einen klaren Hermes-Auftrag vorbereiten,
5. ohne installiertes Modell weiterhin vollständig funktionsfähig bleiben.

Der Modell-Import in die eigene App ist bereits als separater Pfad vorbereitet. Die Google-AI-Edge-Gallery-Installation dient zunächst als einfacher Hardware- und Laufzeittest.
