package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceController private constructor(private val context: Context) : RecognitionListener {
    private var speechRecognizer: SpeechRecognizer? = null

    companion object {
        @Volatile
        private var instance: VoiceController? = null

        fun getInstance(context: Context): VoiceController {
            return instance ?: synchronized(this) {
                instance ?: VoiceController(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _finalResult = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val finalResult: kotlinx.coroutines.flow.SharedFlow<String> = _finalResult

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startListening() {
        _error.value = null
        _isListening.value = false

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _error.value = "Microphone permission is required. Please authorize it in the PERMS tab."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.value = "Speech recognition is not available in this environment. Please use direct text commands."
            Log.w("VoiceController", "SpeechRecognizer.isRecognitionAvailable returned false.")
            return
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            _recognizedText.value = "Listening..."
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceController)
                    }
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                try {
                    speechRecognizer?.startListening(intent)
                    _isListening.value = true
                } catch (e: Exception) {
                    Log.w("VoiceController", "Failed starting existing recognizer, recreating...", e)
                    try {
                        speechRecognizer?.setRecognitionListener(null)
                        speechRecognizer?.destroy()
                    } catch (ex: Exception) {}
                    
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceController)
                    }
                    speechRecognizer?.startListening(intent)
                    _isListening.value = true
                }
            } catch (t: Throwable) {
                _error.value = "Failed to start speech service: ${t.localizedMessage ?: "Unknown error"}"
                Log.e("VoiceController", "Error starting speech recognizer on Main Thread", t)
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isListening.value = false
        }
    }

    fun destroy() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                speechRecognizer?.setRecognitionListener(null)
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                speechRecognizer = null
            }
        }
    }

    // --- SpeechRecognizer Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) { Log.d("VoiceController", "Ready") }
    override fun onBeginningOfSpeech() { Log.d("VoiceController", "Speech started") }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { _isListening.value = false }
    override fun onError(error: Int) {
        val msg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Speech recognizer error"
        }
        _error.value = msg
        _isListening.value = false
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _recognizedText.value = matches[0]
            _finalResult.tryEmit(matches[0])
        }
        _isListening.value = false
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _recognizedText.value = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
