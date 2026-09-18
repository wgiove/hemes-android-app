package de.adversum.hermescompanion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
 * Einstiegspfad: Pairing + Medien-Scanner.
 *
 * Phase-1-Scaffold:
 *  - fragt Medien-Berechtigungen (Fotos + Videos) an
 *  - zählt lokale Medien über MediaStore und zeigt aus, dass der Zugriff funktioniert
 *
 * Datenschutz-Konzept: nur das Nötigste, alles kontrolliert, keine Fernzugriffe ohne
 * explizite Freigabe (siehe docs/DATENSCHUTZ.md).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = MediaAdapter()
    private var scannedItems: List<MediaScanner.MediaItem> = emptyList()

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.values.any { granted -> granted }) {
                scanAndList()
            } else {
                binding.status.text = getString(R.string.status_permission_denied)
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
        binding.recycler.adapter = adapter

        binding.btnScan.setOnClickListener { requestMediaPermissions() }
        binding.btnDuplicates.setOnClickListener { findDuplicates() }
        binding.btnBenchmark.setOnClickListener { runBenchmark() }
        binding.btnLlm.setOnClickListener { runLocalLlmCheck() }
        binding.btnLlmImport.setOnClickListener {
            modelPicker.launch(arrayOf("application/octet-stream", "application/*"))
        }
    }

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
        binding.status.text = getString(R.string.status_scanning)
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                MediaScanner.listMedia(applicationContext)
            }
            scannedItems = items
            adapter.submit(items)
            val images = items.count { !it.isVideo }
            val videos = items.count { it.isVideo }
            binding.status.text = getString(R.string.status_result, images, videos)
        }
    }

    private fun findDuplicates() {
        if (scannedItems.isEmpty()) {
            binding.status.text = "Bitte zuerst Medien scannen."
            return
        }
        binding.status.text = "Doubletten werden lokal gesucht …"
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                DuplicateScanner.findExactDuplicates(applicationContext, scannedItems)
            }
            val duplicateFiles = groups.sumOf { it.items.size }
            val reclaimable = groups.sumOf { group ->
                group.items.drop(1).sumOf { it.sizeBytes }
            }
            binding.status.text = if (groups.isEmpty()) {
                "Keine exakten Doubletten gefunden. (Nur lokal geprüft)"
            } else {
                "%d Doubletten in %d Gruppen · %s belegter Speicher"
                    .format(duplicateFiles, groups.size, humanBytes(reclaimable))
            }
        }
    }

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun runBenchmark() {
        binding.status.text = "Leistungscheck läuft …"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PerformanceBenchmark.runBenchmark(applicationContext)
            }
            binding.status.text = result.summary
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
                binding.status.text = "LLM-Modell importiert: ${bytes / 1_048_576} MB"
            }.onFailure { error ->
                binding.status.text = "LLM-Import fehlgeschlagen: ${error.message}"
            }
        }
    }

    private fun runLocalLlmCheck() {
        if (OnDeviceLlm.isModelInstalled(applicationContext)) {
            binding.status.text = "Lokales LLM: Modell ist installiert. Test läuft …"
            lifecycleScope.launch {
                val llm = OnDeviceLlm.createIfAvailable(applicationContext)
                val answer = llm?.generate("Hallo von Hermes Companion!")
                    ?: "Lokales Modell ist nicht einsatzbereit."
                binding.status.text = "Lokal: $answer"
            }
        } else {
            binding.status.text = "Lokales LLM: kein Modell installiert. Pfad: " +
                OnDeviceLlm.modelTargetPath(applicationContext)
        }
    }
}