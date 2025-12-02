package de.dbmlab.pitchpulse.feature.melodygame

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.core.permissions.hasRecordAudioPermission
import de.dbmlab.pitchpulse.core.permissions.rememberPermissionLauncher
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme
import kotlin.math.ceil
import kotlin.math.floor

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Interactive note canvas
        NoteCanvas(
            notes = uiState.notes,
            currentPlaybackTime = if (uiState.isPlaying) uiState.currentPlaybackTime else null,
            currentPitch = uiState.currentPitch,
            onTap = { pitch, timePosition ->
                viewModel.handleCanvasTap(pitch, timePosition)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Game controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play button
            Button(
                onClick = { viewModel.playMelody() },
                enabled = !uiState.isPlaying && uiState.notes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isPlaying) "Playing..." else "Play melody")
            }

            // Listen / Check tone button (tuner-style listening)
            Button(
                onClick = { viewModel.toggleListening() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isListening) "Stop listening" else "Listen & check tone")
            }

            // Clear notes button
            Button(
                onClick = { viewModel.clearNotes() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.notes.isNotEmpty()
            ) {
                Text("Clear notes")
            }

            // Simple feedback line
            val feedback = when (uiState.lastHitGood) {
                null -> "Play or sing a note to start"
                true -> "Hit!"
                false -> "Miss"
            }
            Text(
                text = "Score: ${uiState.score}   •   $feedback",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun NoteCanvas(
    notes: List<Note>,
    currentPlaybackTime: Float? = null,
    currentPitch: Float? = null,
    onTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    windowSize: Float = 20f,
    maxTimeBeats: Float = 8f, // Maximum time range in beats
    quarterNoteBeats: Float = 1f // Quarter note duration in beats
) {
    // Drawing parameters
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val noteColor = Color.Green
    val noteHeight = 16f // Height of the note rectangle
    val noteCornerRadius = 4f // Corner radius for rounded rectangles
    val cursorColor = Color.Red
    val cursorWidth = 3f
    val currentPitchColor = Color.Cyan
    val currentPitchWidth = 3f
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nonKeyTickColor = MaterialTheme.colorScheme.onSurface
    val keyTickColor = MaterialTheme.colorScheme.onSurface
    
    // Maximum size is given by the 128 possible midi notes
    val maxVal = 127f
    val minVal = 0f
    
    val mapper = NoteMapper()

    // Initial viewpoint center
    val initialCenter = 60f.coerceIn(windowSize/2, maxVal - windowSize/2)

    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    var pitchCenter by remember { mutableStateOf(initialCenter) }
    var timeWindowStart by remember { mutableStateOf(0f) }

    val totalTimeSpan = remember(notes) {
        val lastNoteEnd = notes.maxOfOrNull { it.timePosition + quarterNoteBeats } ?: 0f
        maxTimeBeats.coerceAtLeast(lastNoteEnd + quarterNoteBeats)
    }
    val timeWindow = maxTimeBeats
    val maxTimeStart = (totalTimeSpan - timeWindow).coerceAtLeast(0f)

    Box(
        modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .onSizeChanged { canvasSize.value = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val h = canvasSize.value.height.coerceAtLeast(1)
                    val w = canvasSize.value.width.coerceAtLeast(1)

                    val pitchDelta = (dragAmount.y / h) * windowSize
                    val minCenter = windowSize / 2f
                    val maxCenter = maxVal - windowSize / 2f
                    pitchCenter = (pitchCenter + pitchDelta).coerceIn(minCenter, maxCenter)

                    val timeDelta = -(dragAmount.x / w) * timeWindow
                    timeWindowStart = (timeWindowStart + timeDelta).coerceIn(0f, maxTimeStart)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val w = size.width
                    val h = size.height

                    // Calculate viewport
                    val pitchVpMin = (pitchCenter - windowSize/2).coerceIn(minVal, maxVal - windowSize)
                    val pitchVpMax = (pitchVpMin + windowSize).coerceAtMost(maxVal)

                    // Convert y coordinate to pitch (MIDI note)
                    val yNormalized = 1f - (tapOffset.y / h).coerceIn(0f, 1f)
                    val pitch = pitchVpMin + yNormalized * (pitchVpMax - pitchVpMin)

                    // Convert x coordinate to time position (in beats)
                    val timePosition = (tapOffset.x / w) * timeWindow + timeWindowStart

                    onTap(pitch, timePosition)
                }
            }
    ) {
        val measurer = rememberTextMeasurer()
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height

            // Current viewport
            val pitchVpMin = (pitchCenter - windowSize/2).coerceIn(minVal, maxVal - windowSize)
            val pitchVpMax = (pitchVpMin + windowSize).coerceAtMost(maxVal)

            val timeVpStart = timeWindowStart.coerceIn(0f, maxTimeStart)
            val timeVpEnd = timeVpStart + timeWindow

            // Mapping of data to y-value (y increases with downwards movement)
            fun yFor(v: Float): Float {
                val t = ((v - pitchVpMin) / (pitchVpMax - pitchVpMin)).coerceIn(0f, 1f)  // 0..1
                return (1f - t) * h
            }

            // Mapping of time position to x-value
            fun xFor(timePos: Float): Float {
                val normalized = ((timePos - timeVpStart) / timeWindow).coerceIn(0f, 1f)
                return normalized * w
            }

            // Small ticks for every note
            for (tick in ceil(pitchVpMin).toInt()..floor(pitchVpMax).toInt()) {
                val y = yFor(tick.toFloat())
                drawLine(nonKeyTickColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Strong ticks and labels for notes within this key
            val paddingPx = 4.dp.toPx()
            for (tick in ceil(pitchVpMin).toInt()..floor(pitchVpMax).toInt()) {
                if (mapper.isInKey(tick)) {
                    val y = yFor(tick.toFloat())
                    drawLine(
                        keyTickColor,
                        Offset(0f, y),
                        Offset(w, y),
                        strokeWidth = 5f
                    )
                    val label = mapper.midiToName(tick)
                    val layout = measurer.measure(
                        AnnotatedString(label),
                        style = TextStyle(color = textColor, fontSize = 14.sp)
                    )
                    val topLeft = Offset(paddingPx, y - layout.size.height)
                    drawText(layout, topLeft = topLeft)
                }
            }

            // Draw time grid lines
            val startBeat = floor(timeVpStart).toInt()
            val endBeat = ceil(timeVpEnd).toInt()
            for (beat in startBeat..endBeat) {
                val x = xFor(beat.toFloat())
                drawLine(
                    nonKeyTickColor.copy(alpha = 0.5f),
                    Offset(x, 0f),
                    Offset(x, h),
                    strokeWidth = 1f
                )
            }

            // Draw notes as rounded rectangles
            notes.forEach { note ->
                val y = yFor(note.pitch)
                val x = xFor(note.timePosition)

                // Calculate note width based on duration (quarter note = 1 beat)
                val noteWidth = xFor(note.timePosition + quarterNoteBeats) - x

                // Draw rounded rectangle centered on the pitch line
                val rectTop = y - noteHeight / 2
                val rectLeft = x
                drawRoundRect(
                    color = noteColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(noteWidth, noteHeight),
                    cornerRadius = CornerRadius(noteCornerRadius, noteCornerRadius)
                )
            }

            // Draw playback cursor
            currentPlaybackTime?.let { playbackTime ->
                if (playbackTime in timeVpStart..timeVpEnd) {
                    val cursorX = xFor(playbackTime)
                    drawLine(
                        color = cursorColor,
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, h),
                        strokeWidth = cursorWidth
                    )
                }
            }

            // Draw current detected pitch as a horizontal line (tuner style)
            currentPitch?.let { pitch ->
                if (pitch in pitchVpMin..pitchVpMax) {
                    val y = yFor(pitch.coerceIn(minVal, maxVal))
                    drawLine(
                        color = currentPitchColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = currentPitchWidth
                    )
                }
            }
        }
    }
}
