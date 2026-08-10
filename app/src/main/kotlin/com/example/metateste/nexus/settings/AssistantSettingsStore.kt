package com.example.metateste.nexus.settings

import android.content.Context

/** Whether the host must ask for a spoken "sim"/"não" before actually running an LLM-requested command. */
class AssistantSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var requireCommandConfirmation: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, value).apply()

    companion object {
        private const val PREFS_NAME = "nexus_settings"
        private const val KEY_REQUIRE_CONFIRMATION = "require_command_confirmation"
    }
}
