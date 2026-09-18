package de.adversum.hermescompanion

import android.content.Context
import java.io.File

/**
 * Schnittstelle für ein lokales on-device LLM (tokenfrei, offline).
 *
 * Aktuell vorbereitet für Google AI Edge LLM Inference (Gemma 3 1B).
 * Das Modell wird NICHT in der APK mitgeliefert — es liegt als `gemma-3-1b.task`
 * im internen App-Speicher und wird einmalig heruntergeladen.
 *
 * Wenn kein Modell vorhanden ist, liefern alle Methoden einen klaren Zustand,
 * ohne dass die App abstürzt. Dadurch bleibt der Rest der App (Scanner,
 * Doubletten, Suchfunktion) voll funktionsfähig, auch ohne LLM.
 */
class OnDeviceLlm private constructor(
    private val inference: Any?,
) {

    companion object {
        const val MODEL_FILE = "gemma-3-1b.task"

        /** Prüft, ob das on-device Modell im internen Speicher liegt. */
        fun isModelInstalled(context: Context): Boolean {
            return File(context.filesDir, MODEL_FILE).exists()
        }

        /**
         * Erstellt einen Provider, falls verfügbar. Gibt null zurück (statt zu
         * crashen), wenn das Modell fehlt oder die Initialisierung scheitert.
         */
        fun createIfAvailable(context: Context): OnDeviceLlm? {
            if (!isModelInstalled(context)) return null
            // LlmInference wird hier nur initialisiert, wenn das Modell wirklich da ist.
            // Leichtgewichtige, defensive Initialisierung; Fehlschlag fällt auf null zurück.
            return try {
                OnDeviceLlm(Unit)
            } catch (t: Throwable) {
                null
            }
        }

        /** Zeigt dem Nutzer, wo das Modell hinkommt. */
        fun modelTargetPath(context: Context): String =
            File(context.filesDir, MODEL_FILE).absolutePath
    }

    /**
     * Führt eine lokale, tokenfreie Anfrage aus.
     *
     * @return Kurzantwort oder einen klar lesbaren Hinweis, falls das Modell
     *         nicht einsatzbereit sein sollte.
     */
    suspend fun generate(prompt: String): String {
        if (inference == null) {
            return "Lokales Modell ist nicht bereit. (Modell-Download folgt)"
        }
        // Integration von LlmInference.generateResponseAsync erfolgt, sobald das
        // Modell bootstrap-mäßig hinterlegt ist. Der Slots ist bewusst isoliert,
        // damit die App ohne LLM voll funktionsfähig bleibt.
        return "Lokale KI in Vorbereitung. Anfrage: ${prompt(prompt)}"
    }

    private fun prompt(p: String) = p.take(80)
}