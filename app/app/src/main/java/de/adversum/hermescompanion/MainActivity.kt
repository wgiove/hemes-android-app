package de.adversum.hermescompanion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private val chatAdapter = ChatAdapter()
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

        handleSharedUris(intent)

        binding.btnScan.setOnClickListener { requestMediaPermissions() }
        binding.btnDuplicates.setOnClickListener { findDuplicates() }
        binding.btnBenchmark.setOnClickListener { runBenchmark() }
        binding.btnLlm.setOnClickListener { runLocalLlmCheck() }
        binding.btnLlmImport.setOnClickListener {
            modelPicker.launch(arrayOf("application/octet-stream", "application/*"))
        }
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

    private fun assistant(text: String) {
        messages += ChatMessage(text, ChatMessage.Role.ASSISTANT)
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