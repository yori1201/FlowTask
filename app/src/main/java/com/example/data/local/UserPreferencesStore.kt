package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taskflow_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ID,
    val largeText: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val dailySummaryEnabled: Boolean = true,
    val deviceId: String = "android-device"
)

class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] }
                ?: ThemeMode.SYSTEM,
            language = AppLanguage.entries.firstOrNull { it.name == prefs[Keys.LANGUAGE] }
                ?: AppLanguage.ID,
            largeText = prefs[Keys.LARGE_TEXT] ?: false,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
            quietEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
            dailySummaryEnabled = prefs[Keys.DAILY_SUMMARY_ENABLED] ?: true,
            deviceId = prefs[Keys.DEVICE_ID] ?: "android-device"
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "android-${UUID.randomUUID().toString().take(8)}"
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setLargeText(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LARGE_TEXT] = enabled }
    }

    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[Keys.QUIET_HOURS_ENABLED] = enabled
            it[Keys.QUIET_START_HOUR] = startHour.coerceIn(0, 23)
            it[Keys.QUIET_END_HOUR] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DAILY_SUMMARY_ENABLED] = enabled }
    }
}
