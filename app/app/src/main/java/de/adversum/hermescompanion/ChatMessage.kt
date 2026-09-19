package de.adversum.hermescompanion

/**
 * Eine Chat-Nachricht im Messenger-Verlauf.
 *
 * [role] USER = eingegeben (rechts), ASSISTANT = KI (links).
 * [confirmation] wenn gesetzt: die Nachricht ist ein Vorschlag/Entwurf, der
 * erst nach Bestätigung (An Aiden senden) als Auftrag ausgeführt werden darf.
 * Aktivierung des Human-in-the-Loop: extern folgenreiche Aktionen erfordern
 * eine explizite Bestätigung.
 */
data class ChatMessage(
    val text: String,
    val role: Role,
    val confirmation: Confirmation? = null,
) {
    enum class Role { USER, ASSISTANT }

    val isUser: Boolean get() = role == Role.USER
    val needsConfirmation: Boolean get() = confirmation != null

    /**
     * Bestätigungs-Metadaten: was genau passieren würde, wenn `Aiden` den
     * Auftrag ausführt. Dies wird angezeigt, bevor die Serverbrücke aufgerufen
     * wird.
     */
    data class Confirmation(
        val actionLabel: String,
        val targetDescription: String,
        val confirmLabel: String = "An Aiden senden",
        val cancelLabel: String = "Abbrechen",
    )
}