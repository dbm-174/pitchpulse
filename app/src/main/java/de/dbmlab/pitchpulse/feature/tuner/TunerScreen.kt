package de.dbmlab.pitchpulse.feature.tuner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dbmlab.pitchpulse.core.permissions.LifecycleEffect
import de.dbmlab.pitchpulse.core.permissions.hasRecordAudioPermission
import de.dbmlab.pitchpulse.core.permissions.rememberPermissionLauncher
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import de.dbmlab.pitchpulse.feature.chart.HistoryChart
import de.dbmlab.pitchpulse.feature.settings.SettingsScreen
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme
import kotlin.math.roundToInt


@Preview
@Composable
fun TunerScreenPreview() {
    val context = LocalContext.current
    val vm = TunerViewModel(SettingsRepository(context))
    PitchPulseTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            TunerScreen(vm, onNavigateToSettings = {})
        }
    }
}

@Composable
fun TunerHost() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val vm: TunerViewModel = viewModel(factory = TunerViewModelFactory(settingsRepository))
    var hasPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val permissionLauncher = rememberPermissionLauncher { hasPermission = it }
    val activity = (LocalContext.current as? Activity)
    var showSettings by remember { mutableStateOf(false) }


    if (hasPermission) {
        if (showSettings) {
            SettingsScreen(onNavigateBack = { showSettings = false })
        } else {
            LifecycleEffect(
                onStarted = { vm.start() },
                onStopped = { vm.stop() }
            )
            PitchPulseTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    TunerScreen(vm, onNavigateToSettings = { showSettings = true })
                }
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
fun TunerScreen(vm: TunerViewModel, onNavigateToSettings: () -> Unit) {
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            NoteText(s.note)
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(16.dp))
        NeedleBar(cents = s.cents)
        Spacer(Modifier.height(16.dp))
        HistoryChart(
            history = s.history,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 200.dp)
        )
    }
}

@Composable
private fun NoteText(noteName: String = "-") {
    Text(noteName, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

}


@Composable
private fun NeedleBar(cents: Float) {
    val maxCents = 50f
    val clamped = cents.coerceIn(-maxCents, maxCents)
    val pos = (clamped + maxCents) / (2 * maxCents) // 0..1

    val needleColor = Color.Red
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurface
    val centerLineColor = textColor
    val centerBoxColor = MaterialTheme.colorScheme.surfaceVariant



    Box(
        Modifier.fillMaxWidth().height(80.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            fun drawBand(cent: Float, color: Color) {
                val x = (cent + maxCents) / (2 * maxCents) * w
                drawLine(color = color, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 2f)
            }

            val winStart = (maxCents - 10f) / (2 * maxCents) * w
            val winEnd = (maxCents + 10f) / (2 * maxCents) * w
            drawRect(color = centerBoxColor, topLeft = Offset(winStart, 0f), size = androidx.compose.ui.geometry.Size(winEnd - winStart, h))

            drawLine(centerLineColor, Offset(w / 2f, 0f), Offset(w / 2f, h), 2f)

            drawBand(-25f, tickColor)
            drawBand(+25f, tickColor)
            drawBand(-50f, tickColor)
            drawBand(+50f, tickColor)

            val xNeedle = pos * w
            drawLine(color = needleColor, start = Offset(xNeedle, 0f), end = Offset(xNeedle, h), strokeWidth = 5f)

            for (c in -50..50 step 10) {
                val x = (c + maxCents) / (2 * maxCents) * w
                val th = if (c % 25 == 0) 14f else 8f
                drawLine(tickColor, Offset(x, h - th), Offset(x, h), 2f)
            }
        }
        Text(
            text = "${clamped.roundToInt()} cents",
            color = textColor,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            fontSize = 14.sp
        )
    }
}