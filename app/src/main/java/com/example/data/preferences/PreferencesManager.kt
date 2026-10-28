package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fresh_rss_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var isDemoMode: Boolean
        get() = prefs.getBoolean(KEY_IS_DEMO_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_DEMO_MODE, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TS, value).apply()

    var readerFontSizeSp: Int
        get() = prefs.getInt(KEY_READER_FONT_SIZE, 16)
        set(value) = prefs.edit().putInt(KEY_READER_FONT_SIZE, value).apply()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_DEMO_MODE = "is_demo_mode"
        private const val KEY_LAST_SYNC_TS = "last_sync_ts"
        private const val KEY_READER_FONT_SIZE = "reader_font_size"
    }
}
