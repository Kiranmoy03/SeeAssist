package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "seeassist_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_ESP32_IP = stringPreferencesKey("esp32_ip")
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")

        const val DEFAULT_ESP32_IP = "192.168.0.119"
        const val DEFAULT_BACKEND_URL = "https://81adfc0cb8924119f5.gradio.live"
    }

    val esp32IpFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ESP32_IP] ?: DEFAULT_ESP32_IP
    }

    val backendUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL
    }

    suspend fun saveEsp32Ip(ip: String) {
        val cleanIp = ip.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_ESP32_IP] = cleanIp
        }
    }

    suspend fun saveBackendUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKEND_URL] = cleanUrl
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences[KEY_ESP32_IP] = DEFAULT_ESP32_IP
            preferences[KEY_BACKEND_URL] = DEFAULT_BACKEND_URL
        }
    }
}
