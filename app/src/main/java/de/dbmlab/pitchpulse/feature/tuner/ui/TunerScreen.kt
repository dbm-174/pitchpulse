package de.dbmlab.pitchpulse.feature.tuner.ui

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import de.dbmlab.pitchpulse.core.audio.AudioInput
import de.dbmlab.pitchpulse.core.permissions.hasRecordAudioPermission

@Composable
fun TunerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission-State
    var granted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var running by remember { mutableStateOf(false) }
    var frames by remember { mutableIntStateOf(0) }
    var rms by remember { mutableFloatStateOf(0f) }

    // AudioInput exakt so erzeugen, wie es die Klasse verlangt:
    val audioInput = remember {
        AudioInput(
            context = context,
            sampleRate = 44100,
            frameSize = 2048
        ) { frame ->
            // onFrame: hier z. B. RMS berechnen und Frame-Zähler erhöhen
            frames++
            var sum = 0f
            for (v in frame) sum += v * v
            rms = kotlin.math.sqrt(sum / frame.size)
        }
    }

    // Optional: Permission-Launcher direkt im Screen
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> granted = isGranted }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pitch Pulse", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (!granted) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !granted
            ) { Text("Mikro erlauben") }

            Button(
                onClick = {
                    scope.launch {
                        val ok = audioInput.start()   // keine Argumente!
                        running = ok
                        if (!ok && !granted) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                enabled = granted && !running
            ) { Text("Start") }

            Button(
                onClick = {
                    scope.launch {
                        audioInput.stop()
                        running = false
                    }
                },
                enabled = running
            ) { Text("Stop") }
        }

        Divider()

        Text("Status: " + when {
            running -> "Audio läuft"
            granted -> "bereit (Berechtigung erteilt)"
            else -> "Berechtigung fehlt"
        })

        Text("Frames: $frames")
        Text("RMS: ${"%.4f".format(rms)}")
    }
}
