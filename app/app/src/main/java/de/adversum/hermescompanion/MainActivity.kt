package de.adversum.hermescompanion

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.provider.Settings
import android.app.TimePickerDialog
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Menu
import android.widget.PopupMenu
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import android.provider.MediaStore
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

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) handleSelectedFiles(uris)
        }

    private val postNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openNotificationListenerSettings()
            else assistant("Ohne Benachrichtigungsberechtigung kann die App keine Briefing-Punkte anzeigen.")
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceTranscription() else assistant("Für die Sprachnachricht ist eine Mikrofonfreigabe nötig.")
        }

    private val trashRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                assistant("Die ausgewählten Duplikate wurden in den Papierkorb verschoben. Du kannst sie dort noch wiederherstellen.")
            } else {
                assistant("Das Verschieben wurde abgebrochen. Es wurde nichts gelöscht.")
            }
        }

    private var pendingTrashCount = 0
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastTranscript = ""

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            binding.status.text = "Ich höre zu … zum Beenden erneut auf das Mikrofon tippen."
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(error: Int) {
            isListening = false
            binding.status.text = ""
            if (lastTranscript.isBlank()) assistant("Ich konnte die Sprachnachricht nicht verstehen. Bitte versuche es erneut.")
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                lastTranscript = text
                binding.inputPrompt.setText(text)
                binding.inputPrompt.setSelection(text.length)
                assistant("Transkript lokal erstellt. Du kannst es jetzt bearbeiten oder senden.")
            }
            binding.status.text = ""
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                lastTranscript = text
                binding.inputPrompt.setText(text)
                binding.inputPrompt.setSelection(text.length)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CrashLog.install(applicationContext)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = chatAdapter
        HermesBridge.init(applicationContext)

        handleSharedUris(intent)
        handleBriefingIntent(intent)

        binding.btnScan.setOnClickListener { requestMediaPermissions() }
        binding.btnDuplicates.setOnClickListener { findDuplicates() }
        binding.btnBenchmark.setOnClickListener { runBenchmark() }
        binding.btnLlm.setOnClickListener { runLocalLlmCheck() }
        binding.btnLlmImport.setOnClickListener {
            modelPicker.launch(arrayOf("application/octet-stream", "application/*"))
        }
        binding.btnAttach.setOnClickListener {
            filePicker.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf", "text/*", "*/*"))
        }
        binding.btnVoice.setOnClickListener { toggleVoiceTranscription() }
        binding.btnPair.setOnClickListener { startPairingFlow() }
        binding.btnMenu.setOnClickListener { showToolsMenu() }
        binding.btnSend.setOnClickListener { sendPrompt() }
        binding.inputPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendPrompt(); true
            } else false
        }
    }

    private fun handleSelectedFiles(uris: List<Uri>) {
        val names = uris.map { uri ->
            uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Datei" } ?: "Datei"
        }
        user("📎 ${names.joinToString(", ")}")
        assistant(
            "${uris.size} Datei(en) lokal übernommen. Ich kann sie jetzt lokal analysieren. " +
                "Für Aiden-Vorgänge erscheint vor einer Weitergabe zuerst eine Bestätigung."
        )
    }

    private fun toggleVoiceTranscription() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            binding.status.text = ""
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceTranscription()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceTranscription() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            assistant("Auf diesem Gerät ist keine lokale Spracherkennung verfügbar.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            assistant("Keine lokale Spracherkennung verfügbar. Ich sende deine Stimme nicht automatisch online.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
        speechRecognizer?.setRecognitionListener(speechListener)
        lastTranscript = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
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
            val duplicateFiles = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
            val reclaimable = groups.sumOf { group ->
                group.items.drop(1).sumOf { it.sizeBytes }
            }
            assistant(if (groups.isEmpty()) {
                "Keine exakten Doubletten gefunden."
            } else {
                "$duplicateFiles Dateien in ${groups.size} Gruppen · ${humanBytes(reclaimable)} belegter Speicher. " +
                    "Pro Gruppe bleibt die neueste Datei erhalten."
            })
            if (groups.isNotEmpty()) {
                offerTrashAllDuplicates(groups)
            }
        }
    }

    private fun offerTrashAllDuplicates(groups: List<DuplicateScanner.DuplicateGroup>) {
        val duplicateItems = groups.flatMap { group ->
            group.items.sortedByDescending { it.dateTakenMs }.drop(1)
        }.distinctBy { it.uri }
        pendingTrashCount = duplicateItems.size
        AlertDialog.Builder(this)
            .setTitle("Alle Duplikate aufräumen?")
            .setMessage(
                "$pendingTrashCount exakte Duplikate werden in den Android-Papierkorb verschoben. " +
                    "Pro Gruppe bleibt die neueste Datei erhalten. Nichts wird endgültig gelöscht."
            )
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("In Papierkorb") { _, _ -> moveToTrash(duplicateItems.map { it.uri }) }
            .show()
    }

    private fun moveToTrash(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            assistant("Der sichere Android-Papierkorb ist auf dieser Android-Version nicht verfügbar. Es wurde nichts gelöscht.")
            return
        }
        runCatching {
            val request = MediaStore.createTrashRequest(contentResolver, uris, true)
            trashRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { error ->
            assistant("Papierkorb-Anfrage konnte nicht geöffnet werden: ${error.message ?: "unbekannter Fehler"}")
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
                    // Erst in temporäre Datei kopieren, dann atomar umbenennen.
                    // Ein abgebrochener Import lässt das vorhandene Modell unangetastet.
                    // Ziel-Pfad ist identisch mit OnDeviceLlm.modelTargetPath (filesDir).
                    val temp = File(filesDir, "modell_laden.task").apply { delete() }
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    } ?: error("Datei konnte nicht gelesen werden")
                    if (bytes <= 0) error("Die Datei ist leer.")
                    val target = File(filesDir, OnDeviceLlm.MODEL_FILE)
                    temp.renameTo(target) || error("Konnte Modell nicht an den Zielort verschieben.")
                    bytes
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

        // Tagesberichte werden strikt quellengebunden erzeugt. Das kleine lokale
        // Modell darf hier nicht frei formulieren oder Fakten erfinden.
        if (isBriefingRequest(prompt)) {
            assistant("Ich verwende für den Tagesbericht nur die tatsächlich gespeicherten Benachrichtigungen — ohne erfundene Inhalte.")
            showBriefing()
            return
        }

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

        // Grenz-Erkennung: Wenn die Anfrage über die Fähigkeiten des kleinen
        // lokalen Modells hinausgeht, schlagen wir vor, sie an Aiden zu geben,
        // statt eine schwache oder erfundene Antwort zu riskieren.
        boundaryReason(prompt)?.let { reason ->
            assistant(
                "Diese Frage liegt außerhalb der zuverlässigen lokalen Fähigkeiten: **$reason**. " +
                    "Soll ich sie an **Aiden** geben? Erst nach deiner Bestätigung wird sie übertragen.",
                confirmation = ChatMessage.Confirmation(
                    actionLabel = "An Aiden zur Beantwortung senden",
                    targetDescription = "Lokale Grenze erkannt (${reason}): \"${prompt.take(120)}\"",
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

    private fun isBriefingRequest(prompt: String): Boolean {
        val p = prompt.lowercase(Locale.GERMANY)
        return listOf("tagesbericht", "tagesbriefing", "tageszusammenfassung", "heutiger bericht")
            .any { it in p }
    }

    private fun requiresServerAction(prompt: String): Boolean {
        val p = prompt.lowercase()
        return listOf("word", "dokument", "docx", "pdf", "datei speichern", "aiden", "hermes",
            "erstellen und speichern", "instagram", "share", "senden an", "outlook", "mail")
            .any { p.contains(it) }
    }

    /**
     * Deterministische Grenz-Erkennung. Gibt einen Grund zurück, warum die
     * Anfrage an Aiden (Server) gehen sollte, statt lokal beantwortet zu werden.
     * Null bedeutet: kann zuverlässig lokal bleiben. Keine Selbstbeurteilung
     * durch das Modell — wir verlassen uns auf robuste Merkmale.
     */
    private fun boundaryReason(prompt: String): String? {
        val p = prompt.lowercase(Locale.GERMANY)
        // Mehrere Fragestrukturen oder sehr lange Eingaben lasten das kleine Modell über.
        if (p.count { it == '?' } > 1) return "die Anfrage enthält mehrere Fragen"
        if (prompt.length > 400) return "die Anfrage ist zu umfangreich für das lokale Modell"

        // Fachliche, komplexe oder mehrdeutige Themen.
        when {
            listOf("analyse", "analysiere", "zusammenfassung", "bewertung", "interpretation",
                "strategie", "konzept", "konzeption", "forschungsarbeit", "abschlussarbeit",
                "masterarbeit", "epochenheft", "seminar", "gutachten").any { p.contains(it) } ->
                return "das Thema benötigt Fachwissen und längere, präzise Textarbeit"

            listOf("recherche", "aktuelle", "aktuelles", "nachrichten", "preise", "laufzeit",
                "vergleich", "quellen", "zitat", "literatur").any { p.contains(it) } ->
                return "aktuelle oder externe Informationen liegen lokal nicht vor"

            listOf("übersetzen", "translate", "diplomarbeit", "vertrag", "rechtlich",
                "gesetz", "steuer", "fachsprache", "wissenschaftlich").any { p.contains(it) } ->
                return "fachlich/formal hochwertige Ausdrucksweise ist nötig"

            p.contains("werner") && (p.contains("kalender") || p.contains("termin")) ->
                return "Termin- und Kalenderdetails bräuchte ich aus deinem Outlook/Planner"
        }
        return null
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
        menu.menu.add(Menu.NONE, 5, 5, "📋 Tagesbriefing anzeigen")
        menu.menu.add(Menu.NONE, 8, 8, "🕑 Briefing-Zeit festlegen")
        menu.menu.add(Menu.NONE, 9, 9, "🔔 Benachrichtigungszugriff aktivieren")
        menu.menu.add(Menu.NONE, 11, 11, "📲 Briefing-Apps konfigurieren")
        if (CrashLog.read(this) != null) {
            menu.menu.add(Menu.NONE, 12, 12, "🛠 Crash-Protokoll anzeigen")
        }
        if (HermesBridge.isPaired()) {
            menu.menu.add(Menu.NONE, 7, 7, "Aiden ist gekoppelt ✓")
        } else {
            val lastCode = HermesBridge.lastPairingCode()
            if (!lastCode.isNullOrBlank()) {
                menu.menu.add(Menu.NONE, 10, 10, "Token abholen (Code $lastCode)")
            } else {
                menu.menu.add(Menu.NONE, 6, 6, "Aiden koppeln")
            }
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> requestMediaPermissions()
                2 -> findDuplicates()
                3 -> runBenchmark()
                4 -> runLocalLlmCheck()
                5 -> showBriefing()
                8 -> pickBriefingTime()
                9 -> requestNotificationAccess()
                11 -> configureBriefingApps()
                12 -> showCrashReport()
                6 -> startPairingFlow()
                10 -> HermesBridge.lastPairingCode()?.let { showConfirmPairingOption(it) }
                7 -> Unit
            }
            true
        }
        menu.show()
    }

    // ---- Tagesbriefing ----

    private fun configureBriefingApps() {
        val apps = BriefingStore.availableApps(this)
        if (apps.isEmpty()) {
            assistant("Ich konnte keine startbaren Apps auf dem Gerät finden.")
            return
        }
        val enabled = BriefingStore.enabledApps(this)
        val labels = apps.map { it.label }.toTypedArray()
        val checked = apps.map { it.packageName in enabled }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Briefing-Apps auswählen")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNeutralButton("Alle aus") { _, _ ->
                BriefingStore.setEnabledApps(this, emptySet())
                assistant("Briefing-Sammlung pausiert: keine App ist ausgewählt.")
            }
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                val selected = apps.indices
                    .filter { checked[it] }
                    .map { apps[it].packageName }
                    .toSet()
                BriefingStore.setEnabledApps(this, selected)
                assistant("Briefing-Konfiguration gespeichert: ${selected.size} App(s) ausgewählt. Alles bleibt lokal.")
            }
            .show()
    }

    private fun showCrashReport() {
        val report = CrashLog.read(this)
        if (report == null) {
            assistant("Kein Crash-Protokoll vorhanden.")
            return
        }
        val trimmed = report.take(4000)
        assistant("**Crash-Protokoll (lokal):**\n\n```\n$trimmed\n```\n\nSende mir das, damit ich die Ursache gezielt behebe.")
    }

    private fun showBriefing() {
        val items = BriefingStore.items(this)
        if (items.isEmpty()) {
            assistant(
                "Noch keine Briefing-Einträge. Aktiviere unter **☰ → Benachrichtigungszugriff** " +
                    "den Zugriff und öffne danach deine Apps (WhatsApp, Outlook, …), damit erkannte " +
                    "Benachrichtigungen hier landen."
            )
            return
        }
        val today = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.getDefault()).format(System.currentTimeMillis())
        val summary = BriefingBuilder.defaultSummary(items)
        val lines = summary.joinToString("\n") { "• $it" }
        assistant(
            "## 📋 Tagesbriefing — $today\n\n" +
                "${items.size} lokale Benachrichtigung(en) seit dem letzten Leeren gesammelt.\n\n$lines\n\n" +
                "_Alles rein lokal. Für eine Aiden-Zusammenfassung von Unterlagen erscheint vorher eine Bestätigung._"
        )
    }

    private fun pickBriefingTime() {
        val existing = BriefingAlarmReceiver.configuredTime(this)
        val h = existing?.first ?: 7
        val m = existing?.second ?: 0
        TimePickerDialog(
            this,
            { _, hour, minute ->
                BriefingAlarmReceiver.schedule(this, hour, minute)
                val f = String.format(Locale.GERMANY, "%02d:%02d", hour, minute)
                assistant("🕑 Tagesbriefing wird künftig um **$f Uhr** erstellt (lokal, über einen geplanten Alarm).")
            },
            h, m, true,
        ).show()
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationsPermission()
            return
        }
        openNotificationListenerSettings()
    }

    private fun requestNotificationsPermission() {
        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            assistant(
                "Verbinde unter **Ein-Stellungen → Zugegriffene Apps** das Tagesbriefing von **Hermes Companion**. " +
                    "Danach öffne kurz WhatsApp, Instagram, LinkedIn oder Outlook, damit Einträge gesammelt werden."
            )
        } catch (_: Exception) {
            assistant("Benachrichtigungszugriff konnte nicht geöffnet werden. Bitte in den Systemeinstellungen unter „Zugegriffene Apps“ aktivieren.")
        }
    }

    /** Haupt-Activity-Empfang für Öffnen über Briefing-Benachrichtigung. */
    private fun handleBriefingIntent(intent: Intent?) {
        if (intent?.action == "de.adversum.hermescompanion.OPEN_BRIEFING") {
            showBriefing()
        }
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
                    HermesBridge.savePairingCode(code)
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