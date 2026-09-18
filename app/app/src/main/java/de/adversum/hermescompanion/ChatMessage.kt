package de.adversum.hermescompanion

/**
 * Eine Chat-Nachricht im Messenger-Verlauf.
 * direction: ROLE_USER = eingegeben (rechts), ROLE_ASSISTANT = KI (links).
 */
data class ChatMessage(
    val text: String,
    val role: Role,
) {
    enum class Role { USER, ASSISTANT }

    val isUser: Boolean get() = role == Role.USER
}