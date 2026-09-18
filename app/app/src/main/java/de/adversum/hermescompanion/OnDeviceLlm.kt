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
                    .setMaxTokens(1024)
                    .setPreferredBackend(backend)
                    .build()
                OnDeviceLlm(LlmInference.createFromOptions(context, options))
            }.getOrNull()
        }
    }

    /** Führt eine lokal generierte Antwort aus. Muss auf IO-Thread laufen. */
    fun generate(prompt: String): String = runCatching {
        inference.generateResponse(prompt)
    }.getOrElse { throwable ->
        "Lokale Generierung fehlgeschlagen: ${throwable.message}"
    }

    override fun close() {
        runCatching { inference.close() }
    }
}