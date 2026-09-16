package com.gavinb8.askclaude

import android.content.Context
import android.content.SharedPreferences

/**
 * Resolves configuration in this order: runtime Settings screen override
 * (SharedPreferences) > secrets.properties/env var baked in at build time
 * (BuildConfig) > hardcoded fallback. This lets you change the model or
 * point at a bridge server without rebuilding the APK.
 */
class AppConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ask_claude_config", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.ANTHROPIC_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLAUDE_MODEL.ifBlank { "claude-sonnet-5" }
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** If non-blank, prompts are sent to this bridge server instead of Anthropic directly. */
    var bridgeServerUrl: String
        get() = prefs.getString(KEY_BRIDGE_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.BRIDGE_SERVER_URL
        set(value) = prefs.edit().putString(KEY_BRIDGE_URL, value).apply()

    /** Must match BRIDGE_SHARED_SECRET on the server, if it sets one. */
    var bridgeSecret: String
        get() = prefs.getString(KEY_BRIDGE_SECRET, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.BRIDGE_SHARED_SECRET
        set(value) = prefs.edit().putString(KEY_BRIDGE_SECRET, value).apply()

    val garminAppId: String get() = BuildConfig.GARMIN_APP_ID

    fun isConfigured(): Boolean = apiKey.isNotBlank() || bridgeServerUrl.isNotBlank()

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BRIDGE_URL = "bridge_url"
        private const val KEY_BRIDGE_SECRET = "bridge_secret"
    }
}
