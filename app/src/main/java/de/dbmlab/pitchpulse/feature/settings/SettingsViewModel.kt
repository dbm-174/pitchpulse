package de.dbmlab.pitchpulse.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings = repository.appSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setA4Hz(hz: Float) {
        viewModelScope.launch {
            repository.setA4Hz(hz)
        }
    }

    fun setKey(key: NoteMapper.AllKeys) {
        viewModelScope.launch {
            repository.setKey(key)
        }
    }
}
