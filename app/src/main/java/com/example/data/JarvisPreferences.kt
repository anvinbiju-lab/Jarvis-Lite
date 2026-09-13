package com.example.data

import android.content.Context
import android.content.SharedPreferences

class JarvisPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_CONFIRMATION_MODE = "confirmation_mode"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_WAKE_BUTTON_ENABLED = "wake_button"
        private const val KEY_USE_LLM_PARSER = "use_llm_parser"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var confirmationMode: Boolean
        get() = prefs.getBoolean(KEY_CONFIRMATION_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRMATION_MODE, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var wakeButtonEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_BUTTON_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_BUTTON_ENABLED, value).apply()

    var useLlmParser: Boolean
        get() = prefs.getBoolean(KEY_USE_LLM_PARSER, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_LLM_PARSER, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
}
