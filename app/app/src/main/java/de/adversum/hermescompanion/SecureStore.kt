package de.adversum.hermescompanion

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselte Ablage kleiner Geheimnisse über Android Keystore + AES/GCM.
 *
 * Absolut kritisch für die Aiden-Kopplung: Ein Fehler hier darf die App nie
 * blockieren. Deshalb kapseln wir jeden Verschlüsselungszugriff und fallen auf
 * normale (unklare) SharedPreferences zurück, wenn der Keystore nicht verfügbar
 * oder der Schlüssel nicht nutzbar ist. Sicherheit wird so hoch wie möglich
 * versucht, aber Funktionalität hat Vorrang.
 */
object SecureStore {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "hermes_companion_secret_key"
    private const val CRYPTO_PREFS = "hermes_secure_store"
    private const val PLAIN_PREFS = "hermes_bridge"

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance("AES", STORE)
            generator.init(256)
            generator.generateKey()
        }
        (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }.getOrNull()

    fun put(context: Context, name: String, value: String) {
        val secretKey = key()
        if (secretKey == null) {
            // Fallback, wenn Keystore nicht nutzbar ist.
            context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE).edit()
                .putString("secure_fallback_$name", value).apply()
            return
        }
        runCatching {
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val packed = ByteArray(iv.size + encrypted.size)
            iv.copyInto(packed)
            encrypted.copyInto(packed, iv.size)
            context.getSharedPreferences(CRYPTO_PREFS, Context.MODE_PRIVATE).edit()
                .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }.getOrElse {
            // Verschlüsselung fehlgeschlagen → kein Ausstieg, fallback.
            context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE).edit()
                .putString("secure_fallback_$name", value).apply()
        }
    }

    fun get(context: Context, name: String): String? {
        // Erst den verschlüsselten Wert versuchen.
        val cryptoValue = runCatching {
            val encoded = context.getSharedPreferences(CRYPTO_PREFS, Context.MODE_PRIVATE)
                .getString(name, null) ?: return@runCatching null
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val k = key() ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            String(cipher.doFinal(packed.copyOfRange(12, packed.size)), StandardCharsets.UTF_8)
        }.getOrNull()
        if (cryptoValue != null) return cryptoValue

        // Fallback auf unklare Ablage.
        return context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
            .getString("secure_fallback_$name", null)
    }
}