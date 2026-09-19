# Sicherheits- und Produktivplan

## Ziel
Die Companion-App wird zur lokalen Hauptoberfläche. Serverzugriffe erfolgen nur sichtbar, nach Bestätigung und über die HTTPS-Brücke.

## Phase 1 — umgesetzt
- Tokenablage mit Android Keystore + AES/GCM statt Klartext-SharedPreferences.
- Migration vorhandener Token beim ersten App-Start.
- Android-Backup deaktiviert, damit Pairing-Token nicht über Gerätesicherungen kopiert werden.
- HTTPS verpflichtend; Cleartext bleibt deaktiviert.
- Keine Accessibility-, Notification-Listener-, VPN-, Root- oder Hintergrundüberwachungs-Berechtigung.
- Dateien, Mikrofon und Share-Intents bleiben nutzerinitiierte Aktionen.
- Human-in-the-Loop vor jeder Aiden-Aktion.

## Phase 2 — als Nächstes
- Produktive Release-Signatur und reproduzierbarer Release-Build.
- Zertifikats-Pinning für `aiden.adversum-business.de` mit kontrolliertem Backup-Pin.
- Server: Token-Widerruf, Ablaufzeit, Rate-Limits, Geräteübersicht und Audit-Export.
- Keine sensiblen Datei-/Transkriptinhalte in Standard-Logs.
- Lokale verschlüsselte Ablage für importierte Arbeitsdateien mit Löschfunktion.

## Tagesbriefing (neu)
- Notification Listener sammelt lokal Benachrichtigungen ausgewählter Apps (Standardeinstellung:
  WhatsApp, Instagram, LinkedIn, Outlook, Microsoft To Do, Gmail, Kalender).
- Eventueller kurzer Text + Titel werden lokal gespeichert, nichts wird weitergeschickt.
- Morgen-Alarm (AlarmManager, einstellbare Uhrzeit) stößt die Zusammenfassung an und zeigt eine
  System-Benachrichtigung. Das Briefing ist im Menü „Tagesbriefing anzeigen“ aufrufbar.
- Benachrichtigungszugriff und Benachrichtigungsfreigabe sind separat zu aktivieren (Menü).
- Eigene App-Konfiguration im Menü: installierte startbare Apps als Checkbox-Liste, lokal gespeichert.
- „Alle aus“ pausiert die Sammlung sofort.
- Kein Import der Nachrichten in die Aiden-Brücke ohne sichtbare Bestätigung.

## Phase 3 — Sicherheitskonsole
- Read-only PuTTY-Stil für lokale Diagnose.
- WLAN: aktive Verbindung, Transport, Linkdaten und vom System erlaubte Scaninformationen.
- Bluetooth: Status, eigene Berechtigungen, gekoppelte Geräte; kein heimliches Scannen.
- NFC: Adapterstatus, unterstützte Features und App-Berechtigungen.
- Berechtigungsbericht: Was darf diese App aktuell — und was nicht?
- Jede aktive Messung mit sichtbarem Start/Stop und ohne automatische Serverweitergabe.

## Abnahmekriterien
- Keine unerklärlichen Berechtigungen im Manifest.
- Jeder externe Auftrag ist im Chat sichtbar und bestätigungspflichtig.
- Token ist im App-Sandbox-Speicher verschlüsselt.
- Release-APK wird auf dem realme installiert und Pairing/Command erneut getestet.
