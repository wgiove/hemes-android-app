package de.adversum.hermescompanion

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Verschlüsselte Ablage kleiner Geheimnisse mit Android Keystore + AES/GCM. */
object SecureStore {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "hermes_companion_secret_key"
    private const val PREFS = "hermes_secure_store"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance("AES", STORE)
            generator.init(256)
            generator.generateKey()
        }
        return (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun put(context: Context, name: String, value: String) {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(iv.size + encrypted.size)
        iv.copyInto(packed)
        encrypted.copyInto(packed, iv.size)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(context: Context, name: String): String? = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(name, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)), StandardCharsets.UTF_8)
    }.getOrNull()
}
