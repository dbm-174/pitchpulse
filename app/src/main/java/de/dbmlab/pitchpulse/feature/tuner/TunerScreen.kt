package de.dbmlab.pitchpulse.feature.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dbmlab.pitchpulse.core.permissions.PermissionedLifecycleGate
import de.dbmlab.pitchpulse.feature.chart.HistoryChart
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme
import kotlin.math.roundToInt


@Preview
@Composable
fun TunerScreenPreview() {
    val vm =  remember { TunerViewModel() }
    PitchPulseTheme (darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            TunerScreen(vm)
    }
}
}

@Composable
fun TunerHost() {
    val vm = remember { TunerViewModel() }
    PermissionedLifecycleGate(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onEnterWithPermission = { vm.start() },
        onLeave = { vm.stop() }
    )
    PitchPulseTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            TunerScreen(vm)
        }
    }
}



@Composable
fun TunerScreen(vm: TunerViewModel) {
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        NoteText(s.note)
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
        //Spacer(Modifier.height(16.dp))
        //RoundIconButtonsRow()
    }
}

@Composable
private fun NoteText(noteName : String = "-") {
    Text(noteName, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

}





@Composable
private fun NeedleBar(cents: Float) {
    val maxCents = 50f
    val clamped = cents.coerceIn(-maxCents, maxCents)
    val pos = (clamped + maxCents) / (2 * maxCents) // 0..1

    // Color scheme here
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

            // Background bands: ±50 (dark), ±25 (mid), ±10 (green window)
            fun drawBand(cent: Float, color: Color) {
                val x = (cent + maxCents) / (2 * maxCents) * w
                drawLine(color = color, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 2f)
            }
            // colored window
            val winStart = (maxCents - 10f) / (2 * maxCents) * w
            val winEnd = (maxCents + 10f) / (2 * maxCents) * w
            drawRect(color = centerBoxColor, topLeft = Offset(winStart, 0f), size = androidx.compose.ui.geometry.Size(winEnd - winStart, h))

            // center line
            drawLine(centerLineColor, Offset(w/2f, 0f), Offset(w/2f, h), 2f)
            // ±25/±50 markers
            drawBand(-25f, tickColor)
            drawBand(+25f, tickColor)
            drawBand(-50f, tickColor)
            drawBand(+50f, tickColor)

            // Needle
            val xNeedle = pos * w
            drawLine(color = needleColor, start = Offset(xNeedle, 0f), end = Offset(xNeedle, h), strokeWidth = 5f)

            // Scale ticks every 10 cents
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