package de.dbmlab.pitchpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.dbmlab.pitchpulse.feature.tuner.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview


/*
old call
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

*/
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TunerHost() }
    }
}




@Preview
@Composable
fun TunerHost() {
    val vm =  remember { TunerViewModel() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0C0F)) {
            TunerScreen(vm)
        }
    }
}




/*
Old application, don'need?
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var running by remember { mutableStateOf(false) }
    var frames by remember { mutableIntStateOf(0) }
    var rms by remember { mutableFloatStateOf(0f) }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> granted = isGranted }
    )

    val scope = rememberCoroutineScope()
    // AudioInput einmal erstellen und behalten
    val audioInput = remember {
        AudioInput(
            context = context,
            sampleRate = 44100,
            frameSize = 2048
        ) { frame ->
            frames++
            var sum = 0f
            for (v in frame) sum += v * v
            rms = kotlin.math.sqrt(sum / frame.size)
        }
    }


    MaterialTheme() {
        Surface(modifier = Modifier.fillMaxSize()) {


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
                                val ok = audioInput.start()
                                running = ok
                                if (!ok && !granted) {
                                    // Safety: falls Berechtigung entzogen wurde
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
    }
}*/



