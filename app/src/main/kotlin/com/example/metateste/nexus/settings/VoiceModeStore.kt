package com.example.metateste.nexus.settings

import android.content.Context

/** Whether voice capture waits for push-to-talk (hold Back, the default) or listens continuously and auto-detects speech. */
class VoiceModeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoSpeakEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    companion object {
        private const val PREFS_NAME = "nexus_settings"
        private const val KEY_AUTO_SPEAK = "auto_speak_enabled"
    }
}
