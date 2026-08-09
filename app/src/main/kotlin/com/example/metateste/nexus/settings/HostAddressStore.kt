package com.example.metateste.nexus.settings

import android.content.Context

/** Stores the PC host's IP:port, entered manually by the user in the UI. */
class HostAddressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    companion object {
        private const val PREFS_NAME = "nexus_settings"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val DEFAULT_HOST = "192.168.0.100"
        const val DEFAULT_PORT = 7890
    }
}
