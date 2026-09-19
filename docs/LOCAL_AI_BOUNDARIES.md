# Grenzen der lokalen KI

## Darf sie
- Text im lokalen Chat verarbeiten.
- Das importierte lokale Gemma-Modell ausführen.
- Vom Nutzer ausgewählte Dateien lesen, solange die App den URI-Zugriff besitzt.
- Vom Nutzer gestartete Mikrofon-Transkription nutzen.
- Lokale Medien scannen, hashen und Duplikate melden.
- Sicherheitsinformationen des Android-Systems anzeigen, wenn Android sie ohne Spezialrechte bereitstellt.
- Einen Vorschlag für eine größere Aufgabe als strukturierten Auftrag formulieren.

## Darf sie nicht
- Ohne sichtbare Nutzeraktion Dateien auswählen, hochladen, teilen, löschen oder verschieben.
- Automatisch Nachrichten an Aiden, WhatsApp, Instagram oder andere Apps senden.
- WhatsApp-/Instagram-Inhalte überwachen.
- Bildschirm, Mikrofon oder Kamera heimlich dauerhaft überwachen.
- Andere Apps per Accessibility fernsteuern; diese Berechtigung wird nicht angefordert.
- Käufe, Überweisungen, Logins oder sicherheitskritische Einstellungen selbstständig ausführen.
- Den gesamten WLAN-, Bluetooth- oder NFC-Funkverkehr anderer Apps entschlüsseln oder mitschneiden.
- Root-Rechte erlangen oder Sicherheitsbeschränkungen umgehen.

## App-Steuerung
Eine spätere optionale Integration darf nur über explizit aktivierte Android-Mechanismen erfolgen:

- Share-Intent: Nutzer teilt konkret einen Inhalt.
- Notification Listener: nur nach separater Systemfreigabe und standardmäßig deaktiviert; keine Nutzung für heimliche Überwachung.
- Accessibility: vorerst nicht vorgesehen, weil damit Passwörter und private Bildschirminhalte sichtbar werden könnten.

Jede solche Erweiterung braucht eine eigene Freigabe, sichtbaren Status, Abschaltmöglichkeit und Audit-Eintrag.
