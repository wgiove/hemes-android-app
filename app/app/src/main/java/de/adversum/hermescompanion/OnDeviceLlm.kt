package de.adversum.hermescompanion

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import java.io.File

/**
 * Lokales, tokenfreies on-device LLM über Google AI Edge ($tasks-genai).
 *
 * Modell: Gemma 3 1B IT als `.task`-Datei im internen App-Speicher.
 * Wird NICHT in der APK mitgeliefert — der Nutzer importiert sie einmalig
 * über den Datei-Dialog. Ohne Modell bleibt die App voll funktionsfähig.
 *
 * Inferenz-Aufrufe sind synchron [`LlmInference.generateResponse`] und müssen
 * vom Aufrufer auf einen IO-/Hintergrund-Thread gestellt werden.
 */
class OnDeviceLlm private constructor(
    private val inference: LlmInference,
) : AutoCloseable {

    companion object {
        const val MODEL_FILE = "gemma-3-1b.task"
        private const val PREFS = "hermes_llm"
        private const val KEY_MODEL_NAME = "model_name"

        fun saveModelName(context: Context, fileName: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODEL_NAME, fileName)
                .apply()
        }

        fun displayName(context: Context): String {
            if (!isModelInstalled(context)) return "LLM importieren"
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODEL_NAME, null)
                ?.trim()
                ?.removeSuffix(".task")
                ?.takeIf { it.isNotBlank() }
                ?: return "LLM aktiv ✓"
            val lower = stored.lowercase()
            val shortName = when {
                "gemma3" in lower && "1b" in lower -> "Gemma 3 1B"
                "gemma2" in lower && "2b" in lower -> "Gemma 2 2B"
                "gemma3" in lower && "4b" in lower -> "Gemma 3 4B"
                else -> stored.replace("-", " ").replace("_", " ").take(14)
            }
            return "$shortName ✓"
        }

        fun isModelInstalled(context: Context): Boolean =
            File(context.filesDir, MODEL_FILE).exists()

        fun modelTargetPath(context: Context): String =
            File(context.filesDir, MODEL_FILE).absolutePath

        /**
         * Baut den Provider. Null, wenn kein Modell installiert ist oder die
         * Initialisierung scheitert (z. B. Modell nicht kompatibel).
         */
        fun createIfAvailable(context: Context, backend: Backend = Backend.CPU): OnDeviceLlm? {
            val path = modelTargetPath(context)
            if (!File(path).exists()) return null
            return runCatching {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(512)
                    // Kleineres Top-K reduziert sprachliches Driften und Fantasieausgaben.
                    .setMaxTopK(20)
                    .setPreferredBackend(backend)
                    .build()
                OnDeviceLlm(LlmInference.createFromOptions(context, options))
            }.getOrNull()
        }
    }

    /** Führt eine lokal generierte Antwort aus. Muss auf IO-Thread laufen. */
    fun generate(prompt: String): String = runCatching {
        val guardedPrompt = """
            Du bist ein lokaler deutscher Assistent auf einem Android-Handy.
            Antworte ausschließlich auf Deutsch und in natürlicher, klarer Alltagssprache.
            Beantworte nur die konkrete Nutzerfrage.
            Erfinde keine Namen, Termine, Quellen, Orte oder Fakten.
            Wenn dir Informationen fehlen, sage genau das kurz und ehrlich.
            Keine Fantasiegeschichte, keine Rollenfigur und keine englischen Floskeln,
            außer der Nutzer bittet ausdrücklich darum.

            Nutzerfrage:
            $prompt

            Deutsche Antwort:
        """.trimIndent()
        inference.generateResponse(guardedPrompt)
    }.getOrElse { throwable ->
        "Lokale Generierung fehlgeschlagen: ${throwable.message}"
    }

    override fun close() {
        runCatching { inference.close() }
    }
}