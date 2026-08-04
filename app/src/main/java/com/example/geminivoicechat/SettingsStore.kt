package com.example.geminivoicechat

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One DataStore instance for the whole app, backed by a small preferences file on disk.
val Context.dataStore by preferencesDataStore(name = "gemini_voice_settings")

/**
 * Simple wrapper around Jetpack DataStore for the handful of settings this app needs:
 * the user's Gemini API key, chosen voice, and response language hint.
 */
class SettingsStore(private val context: Context) {

    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")

        // A reasonable default voice offered by Gemini Live API.
        const val DEFAULT_VOICE = "Puck"
        const val DEFAULT_LANGUAGE = "en-US"
    }

    val apiKeyFlow: Flow<String> =
        context.dataStore.data.map { it[API_KEY] ?: "" }

    val voiceNameFlow: Flow<String> =
        context.dataStore.data.map { it[VOICE_NAME] ?: DEFAULT_VOICE }

    val languageCodeFlow: Flow<String> =
        context.dataStore.data.map { it[LANGUAGE_CODE] ?: DEFAULT_LANGUAGE }

    val systemPromptFlow: Flow<String> =
        context.dataStore.data.map { it[SYSTEM_PROMPT] ?: "" }

    suspend fun getApiKey(): String = apiKeyFlow.first()
    suspend fun getVoiceName(): String = voiceNameFlow.first()
    suspend fun getLanguageCode(): String = languageCodeFlow.first()
    suspend fun getSystemPrompt(): String = systemPromptFlow.first()

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key.trim() }
    }

    suspend fun saveVoiceName(voice: String) {
        context.dataStore.edit { it[VOICE_NAME] = voice }
    }

    suspend fun saveLanguageCode(lang: String) {
        context.dataStore.edit { it[LANGUAGE_CODE] = lang }
    }

    suspend fun saveSystemPrompt(prompt: String) {
        context.dataStore.edit { it[SYSTEM_PROMPT] = prompt }
    }
}
