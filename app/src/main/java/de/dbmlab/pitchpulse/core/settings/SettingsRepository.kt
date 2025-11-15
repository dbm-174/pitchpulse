package de.dbmlab.pitchpulse.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.dbmlab.pitchpulse.core.music.NoteMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val a4Hz: Float,
    val key: NoteMapper.AllKeys
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val A4_HZ = floatPreferencesKey("a4_hz")
        val KEY = stringPreferencesKey("key")
    }

    val appSettings: Flow<AppSettings> = context.dataStore.data.map {
        val a4Hz = it[Keys.A4_HZ] ?: 440f
        val key = it[Keys.KEY]?.let { keyName ->
            try {
                NoteMapper.AllKeys.valueOf(keyName)
            } catch (e: IllegalArgumentException) {
                NoteMapper.AllKeys.C // Default to C if stored key is invalid
            }
        } ?: NoteMapper.AllKeys.C

        AppSettings(a4Hz, key)
    }

    suspend fun setA4Hz(hz: Float) {
        context.dataStore.edit {
            it[Keys.A4_HZ] = hz
        }
    }

    suspend fun setKey(key: NoteMapper.AllKeys) {
        context.dataStore.edit {
            it[Keys.KEY] = key.name
        }
    }
}
