package de.dbmlab.pitchpulse.feature.melodygame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.dbmlab.pitchpulse.core.settings.SettingsRepository

class MelodyGameViewModelFactory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MelodyGameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MelodyGameViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
