package de.adversum.hermescompanion

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.values.any { granted -> granted }) {
                scanAndList()
            } else {
                binding.status.text = getString(R.string.status_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnScan.setOnClickListener { requestMediaPermissions() }
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
            adapter.submit(items)
            val images = items.count { !it.isVideo }
            val videos = items.count { it.isVideo }
            binding.status.text = getString(R.string.status_result, images, videos)
        }
    }
}