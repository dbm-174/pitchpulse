
package de.dbmlab.pitchpulse.feature.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme
import kotlin.math.ceil
import kotlin.math.floor


@Preview
@Composable
private fun PreviewHistoryChart() {

    val history: List<Float> = listOf(70f, 71f, 72f, 70f, 70f, 70f, 70f, 70f, 70f, 70f, 70f, 70f)
    PitchPulseTheme (darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            HistoryChart(history)
        }
    }


}


@Composable
fun HistoryChart(
    history: List<Float>,
    modifier: Modifier = Modifier,
    windowSize: Float = 20f,
    updateLatencyMilisec: Int = 200
) {
    // Drawing parameters
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val lineColor = Color.Green
    val lineWidth = 9f
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nonKeyTickColor = MaterialTheme.colorScheme.onSurface
    val keyTickColor = MaterialTheme.colorScheme.onSurface
    // Maximum size is given by the 128 possible midi notes that we use here
    val maxVal = 127f
    val minVal = 0f

    // Later
    val mapper = NoteMapper()

    // --------------------
    // we do not show the full window but follow smoothely, this is done from here
    // Initial viewpoint center
    val initialCenter = 60f.coerceIn(windowSize/2, maxVal - windowSize/2)
    val center = remember { Animatable(initialCenter) }

    // Last valid measurement point
    val lastValid = remember(history) {
        history.indexOfLast { !it.isNaN() }
            .takeIf { it >= 0 }
            ?.let { history[it].coerceIn(minVal, maxVal) }
    }

    // smooth update here
    LaunchedEffect(lastValid, windowSize) {
        val margin = 2f // some margin
        lastValid?.let { v ->
            val curMin = center.value - windowSize/2
            val curMax = center.value + windowSize/2
            val inside = v in (curMin + margin)..(curMax - margin)
            if (!inside) {
                val targetCenter = v.coerceIn(windowSize/2, maxVal - windowSize/2)
                center.animateTo(
                    targetCenter,
                    animationSpec = tween(durationMillis = updateLatencyMilisec, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // end of smooth following
    // -------------------------

    Box(
        modifier
            .fillMaxWidth()
            //.height(400.dp) // volle Höhe nutzen
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height


            // current viewport
            val vpMin = (center.value - windowSize/2).coerceIn(minVal, maxVal - windowSize)
            val vpMax = (vpMin + windowSize).coerceAtMost(maxVal)

            // mapping of data to y-value (y increases with downwards movement)
            fun yFor(v: Float): Float {
                val t = ((v - vpMin) / (vpMax - vpMin)).coerceIn(0f, 1f)  // 0..1
                return (1f - t) * h
            }

            // small ticks for every note
            for (tick in ceil(vpMin).toInt()..floor(vpMax).toInt()) {
                val y = yFor(tick.toFloat())
                drawLine(nonKeyTickColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // Strong ticks and labels for notes within this key
            val paddingPx = 4.dp.toPx()
            for (tick in ceil(vpMin).toInt()..floor(vpMax).toInt()) {
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
                    val topLeft = Offset(paddingPx, y - layout.size.height )
                    drawText(layout, topLeft = topLeft)
                }
            }

            // History plot
            if (history.isNotEmpty()) {
                val stepX = w / (history.size - 1).coerceAtLeast(1)
                var last: Offset? = null
                history.forEachIndexed { i, v ->
                    val vv = v.takeIf { !it.isNaN() }?.coerceIn(minVal, maxVal)
                    val y = vv?.let { yFor(it) }
                    if (y != null) {
                        val p = Offset(i * stepX, y)
                        last?.let { prev -> drawLine(lineColor, prev, p, lineWidth) }
                        last = p
                    } else {
                        last = null // dont plot invalid values
                    }
                }
            }
        }
    }
}



