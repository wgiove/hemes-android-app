package de.adversum.hermescompanion

/** Erzeugt aus gesammelten Benachrichtigungen eine lokale, kurze Briefing-Zusammenfassung. */
object BriefingBuilder {

    fun defaultSummary(items: List<BriefingItem>, limit: Int = 6): List<String> {
        val sorted = items.sortedByDescending { it.time }.take(limit)
        return sorted.map { item ->
            val head = if (item.title.isNotBlank()) item.title else item.text
            val body = item.text.takeIf { it.isNotBlank() && item.title.isNotBlank() }
            if (body != null) "${item.app}: $head — $body" else "${item.app}: $head"
        }.distinct()
    }
}