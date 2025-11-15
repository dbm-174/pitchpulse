package de.dbmlab.pitchpulse.feature.melodygame

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dbmlab.pitchpulse.core.permissions.hasRecordAudioPermission
import de.dbmlab.pitchpulse.core.permissions.rememberPermissionLauncher
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import de.dbmlab.pitchpulse.feature.chart.HistoryChart
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme

@Preview
@Composable
fun MelodyGameScreenPreview() {
    MelodyGameScreen()
}

@Composable
fun MelodyGameHost() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val permissionLauncher = rememberPermissionLauncher { hasPermission = it }
    val activity = (LocalContext.current as? Activity)

    if (hasPermission) {
        PitchPulseTheme(darkTheme = true) {
            Surface(Modifier.fillMaxSize()) {
                MelodyGameScreen()
            }
        }
    } else {
        PermissionScreen(
            onGrantPermission = { permissionLauncher() },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            },
            shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
        )
    }
}

@Composable
fun PermissionScreen(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    shouldShowRationale: Boolean
) {
    PitchPulseTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Microphone Permission",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (shouldShowRationale) {
                        "We need your permission to access the microphone to analyze the pitch of your instrument. Please grant the permission to continue."
                    } else {
                        "We need microphone access. Please grant the permission in the app settings."
                    },
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = if (shouldShowRationale) onGrantPermission else onOpenSettings) {
                    Text(if (shouldShowRationale) "Grant Permission" else "Open Settings")
                }
            }
        }
    }
}


@Composable
fun MelodyGameScreen() {

    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val viewModel: MelodyGameViewModel = viewModel(factory = MelodyGameViewModelFactory(settingsRepository))

    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HistoryChart(history = uiState.melody)
        HistoryChart(history = uiState.userMelody)
        Button(onClick = { viewModel.startGame() }) {
            Text(if (uiState.isPlaying) "Playing..." else "Start Game")
        }
    }
}
