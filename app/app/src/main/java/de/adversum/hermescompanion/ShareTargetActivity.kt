package de.adversum.hermescompanion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Empfängt von anderen Apps geteilte Dateien (ACTION_SEND / SEND_MULTIPLE),
 * wenn der Nutzer Hermes Companion im Teilen-Menü auswählt.
 *
 * Die URIs sind per Grant an dieser Activity lesbar. Wir zeigen sie als kurze
 * Liste an und übertragen noch nichts. Ein späterer Schritt kann diese
 * Dateien an den Server oder in eine lokale Warteschlange übergeben.
 */
class ShareTargetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val received = extractUris(intent)
        finish()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("shared_uris", received.toTypedArray())
                .putExtra("shared_count", received.size)
        )
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when {
            intent.clipData != null ->
                (0 until intent.clipData!!.itemCount).map { intent.clipData!!.getItemAt(it).uri }
            intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            }
            else -> {
                @Suppress("DEPRECATION")
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
            }
        }
    }
}