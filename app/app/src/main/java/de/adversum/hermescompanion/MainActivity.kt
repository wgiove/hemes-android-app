package de.adversum.hermescompanion

import android.Manifest
import android.app.AlertDialog
import android.view.Menu
import android.widget.PopupMenu
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import java.io.File
import java.io.FileOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.adversum.hermescompanion.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Messenger-artige Hauptansicht:
 *  - Chat-Verlauf (User rechts, KI links)
 *  - lokales Modell (Gemma) für Antworten
 *  - Medien-Werkzeuge (Scan, Doubletten, Benchmark) als Unterhaltung
 *  - Modell-Import im Header
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatAdapter = ChatAdapter(
        onConfirm = { msg -> onConfirmedAction(msg) },
        onCancel = { msg -> onCancelledAction(msg) },
    )
    private val messages = mutableListOf<ChatMessage>()

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.values.any { granted -> granted }) {
                scanAndList()
            } else {
                assistant(getString(R.string.status_permission_denied))
            }
        }

    private val modelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importModel(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = chatAdapter
        HermesBridge.init(applicationContext)

        handleSharedUris(intent)

        binding.btnScan.setOnClickListener { requestMediaPermissions() }
        binding.btnDuplicates.setOnClickListener { findDuplicates() }
        binding.btnBenchmark.setOnClickListener { runBenchmark() }
        binding.btnLlm.setOnClickListener { runLocalLlmCheck() }
        binding.btnLlmImport.setOnClickListener {
            modelPicker.launch(arrayOf("application/octet-stream", "application/*"))
        }
        binding.btnPair.setOnClickListener { startPairingFlow() }
        binding.btnMenu.setOnClickListener { showToolsMenu() }
        binding.btnSend.setOnClickListener { sendPrompt() }
        binding.inputPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendPrompt(); true
            } else false
        }
    }

    // ---- Chat-Helfer ----

    private fun user(text: String) {
        messages += ChatMessage(text, ChatMessage.Role.USER)
        refreshChat()
    }

    private fun assistant(text: String, confirmation: ChatMessage.Confirmation? = null) {
        messages += ChatMessage(text, ChatMessage.Role.ASSISTANT, confirmation)
        binding.status.text = ""
        refreshChat()
    }

    private fun refreshChat() {
        chatAdapter.submitList(messages.toList())
        binding.recycler.scrollToPosition(messages.size - 1)
    }

    // ---- Medien ----

    private fun requestMediaPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        mediaPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun scanAndList() {
        assistant("Scanne deine Medien …")
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                MediaScanner.listMedia(applicationContext)
            }
            val images = items.count { !it.isVideo }
            val videos = items.count { it.isVideo }
            assistant("Gefunden: **$images Fotos** und **$videos Videos**.")
        }
    }

    private fun handleSharedUris(intent: Intent?) {
        intent ?: return
        val count = intent.getIntExtra("shared_count", 0)
        val uris = intent.getParcelableArrayListExtra<Uri>("shared_uris")
        if (count > 0 || !uris.isNullOrEmpty()) {
            assistant("$count Datei(en) geteilt — Empfang in Warteschlange.")
        }
    }

    private fun findDuplicates() {
        assistant("Suche lokale Doubletten (SHA-256, ohne Übertragung) …")
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { MediaScanner.listMedia(applicationContext) }
            val groups = withContext(Dispatchers.IO) {
                DuplicateScanner.findExactDuplicates(applicationContext, items)
            }
            val duplicateFiles = groups.sumOf { it.items.size }
            val reclaimable = groups.sumOf { group ->
                group.items.drop(1).sumOf { it.sizeBytes }
            }
            assistant(if (groups.isEmpty()) {
                "Keine exakten Doubletten gefunden."
            } else {
                "$duplicateFiles Doubletten in ${groups.size} Gruppen · ${humanBytes(reclaimable)} belegter Speicher."
            })
        }
    }

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun runBenchmark() {
        assistant("Leistungscheck läuft …")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PerformanceBenchmark.runBenchmark(applicationContext)
            }
            assistant(result.summary)
        }
    }

    private fun importModel(uri: Uri) {
        binding.status.text = "LLM-Modell wird importiert …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(filesDir, OnDeviceLlm.MODEL_FILE)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    } ?: error("Datei konnte nicht gelesen werden")
                    target.length()
                }
            }
            result.onSuccess { bytes ->
                binding.status.text = "Modell importiert ✓"
                assistant("Lokales Modell ist bereit (${bytes / 1_048_576} MB). Frag mich etwas!")
            }.onFailure { error ->
                binding.status.text = "Import fehlgeschlagen"
                assistant("Import fehlgeschlagen: ${error.message}")
            }
        }
    }

    private fun sendPrompt() {
        val prompt = binding.inputPrompt.text.toString().trim()
        if (prompt.isEmpty()) {
            binding.status.text = "Bitte zuerst eine Frage eingeben."
            return
        }
        if (!OnDeviceLlm.isModelInstalled(applicationContext)) {
            assistant("Es ist noch kein lokales Modell installiert — bitte oben auf **Import LLM** tippen.")
            binding.inputPrompt.setText("")
            return
        }
        user(prompt)
        binding.inputPrompt.setText("")

        // Erkennung serverlastiger Aufträge (Human-in-the-Loop).
        if (requiresServerAction(prompt)) {
            assistant(
                "Möchtest du, dass ich an Aiden schicke: **${describeServerAction(prompt)}**? " +
                    "Erst nach deiner Bestätigung wird der Auftrag übergeben — nichts passiert automatisch.",
                confirmation = ChatMessage.Confirmation(
                    actionLabel = describeServerAction(prompt),
                    targetDescription = "Aktion aus Prompt: \"${prompt.take(120)}\"",
                ),
            )
            return
        }

        binding.status.text = "Antwort wird erzeugt …"
        lifecycleScope.launch {
            val llm = OnDeviceLlm.createIfAvailable(applicationContext)
            val answer = withContext(Dispatchers.IO) {
                llm?.generate(prompt) ?: "Lokales Modell ist nicht einsatzbereit."
            }
            assistant(answer)
            withContext(Dispatchers.IO) { llm?.close() }
        }
    }

    private fun requiresServerAction(prompt: String): Boolean {
        val p = prompt.lowercase()
        return listOf("word", "dokument", "docx", "pdf", "datei speichern", "aiden", "hermes",
            "erstellen und speichern", "instagram", "share", "senden an", "outlook", "mail")
            .any { p.contains(it) }
    }

    private fun describeServerAction(prompt: String): String {
        return when {
            prompt.lowercase().contains("word") || prompt.lowercase().contains("docx") -> "Word-Dokument erstellen"
            prompt.lowercase().contains("pdf") -> "PDF erstellen"
            prompt.lowercase().contains("instagram") -> "Instagram-Post vorbereiten"
            prompt.lowercase().contains("mail") || prompt.lowercase().contains("outlook") -> "E-Mail vorbereiten"
            else -> "Aktion für Aiden vorbereiten"
        }
    }

    private fun showToolsMenu() {
        val menu = PopupMenu(this, binding.btnMenu)
        menu.menu.add(Menu.NONE, 1, 1, "Medien scannen")
        menu.menu.add(Menu.NONE, 2, 2, "Duplikate lokal finden")
        menu.menu.add(Menu.NONE, 3, 3, "Leistungscheck")
        menu.menu.add(Menu.NONE, 4, 4, "Lokales LLM testen")
        menu.menu.add(Menu.NONE, 5, 5, "Aiden koppeln")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> requestMediaPermissions()
                2 -> findDuplicates()
                3 -> runBenchmark()
                4 -> runLocalLlmCheck()
                5 -> startPairingFlow()
            }
            true
        }
        menu.show()
    }

    // ---- Pairing (Human-in-the-Loop: Code muss von Aiden/Werner bestätigt werden) ----

    private fun startPairingFlow() {
        val input = EditText(this)
        input.hint = HermesBridge.serverUrl().ifBlank { "https://SERVER-IP:8787" }

        AlertDialog.Builder(this)
            .setTitle("Aiden-Brücke koppeln")
            .setMessage("Serveradresse (aus build config oder manuell):")
            .setView(input)
            .setPositiveButton("Pairing starten") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) HermesBridge.setServerUrl(url)
                doPairing()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun doPairing() {
        assistant("Fordere Pairing-Code an …")
        lifecycleScope.launch {
            val code = HermesBridge.startPairing("realme 9 Pro+")
            when {
                code.startsWith("ERROR") -> assistant("Pairing fehlgeschlagen: $code")
                else -> {
                    assistant(
                        "Dein Pairing-Code: **$code**. Schicke diesen Code an Aiden. " +
                            "Sobald Aiden ihn bestätigt hat, tippe unten erneut auf **Pair** " +
                            "und wähle **Token abholen**."
                    )
                    pendingPairingCode = code
                    showConfirmPairingOption(code)
                }
            }
        }
    }

    private var pendingPairingCode: String? = null

    private fun showConfirmPairingOption(code: String) {
        AlertDialog.Builder(this)
            .setTitle("Pairing abschließen")
            .setMessage("Hast du den Code $code an Aiden gesendet und bestätigt?")
            .setPositiveButton("Token abholen") { _, _ ->
                lifecycleScope.launch {
                    val ok = HermesBridge.confirmPairing(code)
                    if (ok) {
                        assistant("Gerät erfolgreich gekoppelt ✓ Aktionen können jetzt an Aiden gesendet werden.")
                        pendingPairingCode = null
                    } else {
                        assistant("Token konnte nicht abgeholt werden — wurde der Code von Aiden bestätigt?")
                    }
                }
            }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun onConfirmedAction(msg: ChatMessage) {
        val conf = msg.confirmation ?: return
        assistant("Sende Auftrag an Aiden: ${conf.actionLabel} …")
        lifecycleScope.launch {
            val answer = HermesBridge.sendAction(
                conf.actionLabel,
                conf.targetDescription,
            )
            assistant(answer)
            // Nach der Bestätigung nicht erneut bestätigen lassen
            replaceMessageWithPlain(msg, answer)
        }
    }

    private fun onCancelledAction(msg: ChatMessage) {
        assistant("Auftrag abgebrochen. Nichts wurde an Aiden gesendet.")
        removeConfirmation(msg)
    }

    /** Ersetzt die bestätigte Vorschlags-Nachricht durch eine gewöhnliche KI-Blase. */
    private fun replaceMessageWithPlain(original: ChatMessage, text: String) {
        val idx = messages.indexOfFirst { it === original }
        if (idx >= 0) {
            messages[idx] = ChatMessage(text, ChatMessage.Role.ASSISTANT)
        }
        refreshChat()
    }

    private fun removeConfirmation(original: ChatMessage) {
        val idx = messages.indexOfFirst { it === original }
        if (idx >= 0 && original.confirmation != null) {
            messages[idx] = original.copy(
                confirmation = null
            )
        }
        refreshChat()
    }

    private fun runLocalLlmCheck() {
        if (OnDeviceLlm.isModelInstalled(applicationContext)) {
            binding.status.text = "Test läuft …"
            lifecycleScope.launch {
                val llm = OnDeviceLlm.createIfAvailable(applicationContext)
                val answer = withContext(Dispatchers.IO) {
                    llm?.generate("Hallo! Antworte kurz in Deutsch.")
                        ?: "Lokales Modell ist nicht einsatzbereit."
                }
                assistant(answer)
                withContext(Dispatchers.IO) { llm?.close() }
            }
        } else {
            assistant("Kein lokales Modell. Tippe oben auf **Import LLM**, um `gemma-3-1b.task` zu wählen.")
        }
    }
}