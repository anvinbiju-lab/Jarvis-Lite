package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibility.ActionResult
import com.example.accessibility.JarvisAccessibilityManager
import com.example.actions.*
import com.example.data.AppDatabase
import com.example.data.CommandLog
import com.example.data.TodoItem
import com.example.data.JarvisPreferences
import android.content.Intent
import kotlinx.coroutines.flow.first
import com.example.parser.CommandParser
import com.example.parser.GeminiParser
import com.example.parser.RuleBasedParser
import com.example.voice.VoiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val logDao = db.commandLogDao()
    
    val prefs = JarvisPreferences(context)
    val voiceController = VoiceController.getInstance(context)

    // Command logs flow from database
    val commandLogs: StateFlow<List<CommandLog>> = logDao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current parser (rule-based or LLM/Gemini)
    private val ruleBasedParser = RuleBasedParser()
    private val geminiParser = GeminiParser(
        apiKeyProvider = { prefs.geminiApiKey.ifEmpty { "PLACEHOLDER_NOT_SET" } },
        fallbackParser = ruleBasedParser
    )

    private val activeParser: CommandParser
        get() = if (prefs.useLlmParser) geminiParser else ruleBasedParser

    // Connection status of Accessibility Service
    val isAccessibilityConnected: StateFlow<Boolean> = JarvisAccessibilityManager.isServiceConnected
    val activePipelineStatus: StateFlow<String> = JarvisAccessibilityManager.currentStatus

    // UI state for active command inputs
    val currentCommandText = MutableStateFlow("")
    val executionLog = MutableStateFlow<String>("")

    // Confirmation Mode Dialog flow
    val showConfirmationDialog = MutableStateFlow(false)
    val confirmationMessage = MutableStateFlow("")
    private var pendingAction: JarvisAction? = null
    private var pendingRawCommand: String = ""

    init {
        // Collect recognized text from voice controller and auto-populate the input field
        viewModelScope.launch {
            voiceController.recognizedText.collect { text ->
                if (text.isNotEmpty() && text != "Listening...") {
                    currentCommandText.value = text
                }
            }
        }

        viewModelScope.launch {
            voiceController.finalResult.collect { text ->
                if (text.isNotEmpty()) {
                    executeCommand(text)
                }
            }
        }
    }

    fun triggerMic() {
        triggerHapticFeedback()
        if (voiceController.isListening.value) {
            voiceController.stopListening()
            executeCommand(currentCommandText.value)
        } else {
            voiceController.startListening()
        }
    }

    fun simulateCommand(simulatedText: String) {
        currentCommandText.value = simulatedText
        executeCommand(simulatedText)
    }

    fun executeCommand(rawCommandText: String) {
        if (rawCommandText.trim().isEmpty() || rawCommandText == "Listening...") return

        viewModelScope.launch {
            executionLog.value = "Parsing command..."
            JarvisAccessibilityManager.updateStatus("Parsing: '$rawCommandText'")
            
            try {
                var action = activeParser.parse(rawCommandText)
                
                // Fallback to Gemini Q&A if the user asked a question but rule-based failed
                if (action is JarvisAction.Unknown && !prefs.useLlmParser && prefs.geminiApiKey.isNotEmpty()) {
                    action = geminiParser.parse(rawCommandText)
                }

                Log.d("JarvisVM", "Parsed action: $action")
                
                // Risk / confirmation handling
                if (prefs.confirmationMode && action.isRisky) {
                    pendingAction = action
                    pendingRawCommand = rawCommandText
                    confirmationMessage.value = getConfirmationPrompt(action)
                    showConfirmationDialog.value = true
                    executionLog.value = "Awaiting safety confirmation."
                    JarvisAccessibilityManager.updateStatus("Awaiting confirmation")
                    triggerHapticFeedback()
                } else {
                    executeParsedActionDirectly(action, rawCommandText)
                }
            } catch (e: Exception) {
                val errorMsg = "Parse failed: ${e.message}"
                executionLog.value = errorMsg
                saveLog(rawCommandText, "Error", false, errorMsg)
            }
        }
    }

    fun confirmPendingAction() {
        showConfirmationDialog.value = false
        val action = pendingAction ?: return
        val rawText = pendingRawCommand
        pendingAction = null
        pendingRawCommand = ""

        viewModelScope.launch {
            executeParsedActionDirectly(action, rawText)
        }
    }

    fun cancelPendingAction() {
        showConfirmationDialog.value = false
        pendingAction = null
        pendingRawCommand = ""
        executionLog.value = "Action cancelled by user."
        JarvisAccessibilityManager.updateStatus("Action cancelled")
    }

    private suspend fun executeParsedActionDirectly(action: JarvisAction, rawCommandText: String) {
        executionLog.value = "Executing: ${action.javaClass.simpleName}"
        
        // Handle System controls (Volume and Brightness adjustment, Bluetooth, WiFi) locally in App bounds
        if (action is JarvisAction.SystemControl) {
            val result = executeSystemControl(action)
            saveLog(rawCommandText, action.toString(), result.success, result.message)
            executionLog.value = result.message
            return
        }

        // Handle To-Do actions locally
        if (action is JarvisAction.AddTodo) {
            try {
                db.todoDao().insertTodo(TodoItem(title = action.title))
                val feedback = "I've added '${action.title}' to your to-do list."
                JarvisAccessibilityManager.speak(feedback)
                saveLog(rawCommandText, action.toString(), true, feedback)
                executionLog.value = feedback
            } catch (e: Exception) {
                val errorMsg = "Failed to add todo: ${e.localizedMessage}"
                saveLog(rawCommandText, action.toString(), false, errorMsg)
                executionLog.value = errorMsg
            }
            return
        }

        if (action is JarvisAction.ListTodos) {
            try {
                val list = db.todoDao().getActiveTodos().first()
                val feedback = if (list.isEmpty()) {
                    "Your to-do list is empty. You're all caught up for today!"
                } else {
                    val tasks = list.mapIndexed { index, item -> "${index + 1}: ${item.title}" }.joinToString(", ")
                    "Today's to-do list has ${list.size} item${if (list.size > 1) "s" else ""}: $tasks."
                }
                JarvisAccessibilityManager.speak(feedback)
                saveLog(rawCommandText, action.toString(), true, feedback)
                executionLog.value = feedback
            } catch (e: Exception) {
                val errorMsg = "Failed to list todos: ${e.localizedMessage}"
                saveLog(rawCommandText, action.toString(), false, errorMsg)
                executionLog.value = errorMsg
            }
            return
        }

        if (action is JarvisAction.CompleteTodo) {
            try {
                val rowsAffected = db.todoDao().completeTodoByTitle(action.title)
                val feedback = if (rowsAffected > 0) {
                    "I've marked '${action.title}' as done."
                } else {
                    "I couldn't find '${action.title}' on your active to-do list."
                }
                JarvisAccessibilityManager.speak(feedback)
                saveLog(rawCommandText, action.toString(), rowsAffected > 0, feedback)
                executionLog.value = feedback
            } catch (e: Exception) {
                val errorMsg = "Failed to complete todo: ${e.localizedMessage}"
                saveLog(rawCommandText, action.toString(), false, errorMsg)
                executionLog.value = errorMsg
            }
            return
        }

        // Handle File searches locally
        if (action is JarvisAction.SearchFiles) {
            val result = executeFileSearch(action.query)
            saveLog(rawCommandText, action.toString(), result.success, result.message)
            executionLog.value = result.message
            return
        }

        // Delegate accessibility-related actions directly to the active Accessibility Service
        val result = JarvisAccessibilityManager.executeAction(action)
        saveLog(rawCommandText, action.toString(), result.success, result.message)
        executionLog.value = result.message
    }

    private fun executeSystemControl(action: JarvisAction.SystemControl): ActionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (action.controlType) {
                SystemControlType.VOLUME -> {
                    val direction = if (action.adjustment == AdjustmentType.INCREASE || action.adjustment == AdjustmentType.ENABLE) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "Adjusted music stream volume.")
                }
                SystemControlType.BRIGHTNESS -> {
                    // Changing brightness system-wide requires WRITE_SETTINGS.
                    // Instead, we prompt an instructional layout / open Settings safely
                    val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    JarvisAccessibilityManager.speak("Opening Display settings to adjust screen brightness.")
                    ActionResult(true, "Opening Device Brightness Control screen.")
                }
                SystemControlType.BLUETOOTH -> {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                    val adapter = bluetoothManager?.adapter ?: @Suppress("DEPRECATION") android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    if (adapter == null) {
                        ActionResult(false, "Bluetooth is not supported on this device.")
                    } else {
                        val enable = action.adjustment == AdjustmentType.ENABLE || action.adjustment == AdjustmentType.INCREASE
                        if (enable) {
                            if (!adapter.isEnabled) {
                                @Suppress("DEPRECATION")
                                val success = adapter.enable()
                                if (success) {
                                    JarvisAccessibilityManager.speak("Bluetooth turned on.")
                                    ActionResult(true, "Bluetooth enabled.")
                                } else {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    JarvisAccessibilityManager.speak("Opening Bluetooth settings to enable.")
                                    ActionResult(true, "Opening Bluetooth settings.")
                                }
                            } else {
                                JarvisAccessibilityManager.speak("Bluetooth is already on.")
                                ActionResult(true, "Bluetooth already enabled.")
                            }
                        } else {
                            if (adapter.isEnabled) {
                                @Suppress("DEPRECATION")
                                val success = adapter.disable()
                                if (success) {
                                    JarvisAccessibilityManager.speak("Bluetooth turned off.")
                                    ActionResult(true, "Bluetooth disabled.")
                                } else {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    JarvisAccessibilityManager.speak("Opening Bluetooth settings to disable.")
                                    ActionResult(true, "Opening Bluetooth settings.")
                                }
                            } else {
                                JarvisAccessibilityManager.speak("Bluetooth is already off.")
                                ActionResult(true, "Bluetooth already disabled.")
                            }
                        }
                    }
                }
                SystemControlType.WIFI -> {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    if (wifiManager == null) {
                        ActionResult(false, "Wi-Fi is not supported on this device.")
                    } else {
                        val enable = action.adjustment == AdjustmentType.ENABLE || action.adjustment == AdjustmentType.INCREASE
                        try {
                            @Suppress("DEPRECATION")
                            val success = wifiManager.setWifiEnabled(enable)
                            if (success || wifiManager.isWifiEnabled == enable) {
                                val stateWord = if (enable) "on" else "off"
                                JarvisAccessibilityManager.speak("Wi-Fi turned $stateWord.")
                                ActionResult(true, "Wi-Fi toggled.")
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                JarvisAccessibilityManager.speak("Opening Wi-Fi settings.")
                                ActionResult(true, "Opening Wi-Fi settings.")
                            }
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            JarvisAccessibilityManager.speak("Opening Wi-Fi settings.")
                            ActionResult(true, "Opening Wi-Fi settings.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ActionResult(false, "System control error: ${e.localizedMessage}")
        }
    }

    private fun executeFileSearch(query: String): ActionResult {
        val isStorageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!isStorageGranted) {
            val message = "File Search permission is required. Please authorize 'Media Storage' in the PERMS tab."
            JarvisAccessibilityManager.speak(message)
            return ActionResult(false, message)
        }

        return try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns._ID,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                android.provider.MediaStore.Files.FileColumns.SIZE
            )
            val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            val sortOrder = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE)
                
                val results = mutableListOf<String>()
                var matchedUri: android.net.Uri? = null
                var matchedName = ""
                
                while (cursor.moveToNext() && results.size < 3) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val sizeKb = size / 1024
                    results.add("$name ($sizeKb KB)")
                    
                    if (matchedUri == null) {
                        matchedName = name
                        matchedUri = android.content.ContentUris.withAppendedId(uri, id)
                    }
                }
                
                if (results.isEmpty()) {
                    val message = "I checked the file manager, but couldn't find any file named '$query'."
                    JarvisAccessibilityManager.speak(message)
                    ActionResult(false, message)
                } else {
                    val summary = "I found matches for '$query': ${results.joinToString(", ")}."
                    JarvisAccessibilityManager.speak("I found ${results.size} matches. Opening the latest one: $matchedName.")
                    
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(matchedUri, "*/*")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val filesIntent = Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(filesIntent)
                    }
                    ActionResult(true, summary)
                }
            } ?: run {
                ActionResult(false, "Could not access File MediaStore provider.")
            }
        } catch (e: Exception) {
            val errorMsg = "File search error: ${e.localizedMessage}"
            JarvisAccessibilityManager.speak("There was an error searching for your files.")
            ActionResult(false, errorMsg)
        }
    }

    private fun getConfirmationPrompt(action: JarvisAction): String {
        return when (action) {
            is JarvisAction.TypeText -> "Jarvis detected you want to input/type: \"${action.text}\". Proceed?"
            is JarvisAction.OpenChatWithUser -> "Jarvis is about to search and open DM chat with \"${action.userName}\". Select chat?"
            is JarvisAction.GoToChats -> "Jarvis is about to access chats/direct message screen. Open DMs?"
            else -> "Are you sure you want to execute this safety restricted action?"
        }
    }

    private suspend fun saveLog(rawText: String, parsedStr: String, success: Boolean, msg: String) {
        val log = CommandLog(
            rawText = rawText,
            parsedIntent = parsedStr,
            success = success,
            feedbackMessage = msg
        )
        logDao.insertLog(log)
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            logDao.clearLogs()
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            Log.e("JarvisVM", "Could not perform haptic vibrate", e)
        }
    }

    override fun onCleared() {
        voiceController.destroy()
        super.onCleared()
    }
}
