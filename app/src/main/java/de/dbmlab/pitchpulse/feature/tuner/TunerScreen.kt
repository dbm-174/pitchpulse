package de.dbmlab.pitchpulse.feature.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dbmlab.pitchpulse.feature.tuner.TunerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Preview
@Composable
fun TunerScreen(vm: TunerViewModel) {
    val s by vm.state.collectAsState()
    val accent = if (s.inTuneWindow) Color(0xFF2ECC71) else Color(0xFFE67E22)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: Note + Hz
        Text(s.note, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(if (s.hz > 0) "${"%.2f".format(s.hz)} Hz" else "— Hz",
            fontSize = 18.sp, color = Color(0xFFB0B0B0))

        Spacer(Modifier.height(16.dp))

        // Needle Bar ±50 cents with colored bands
        NeedleBar(cents = s.cents, accent = accent)

        Spacer(Modifier.height(16.dp))

        // History graph (2–5 s)
        HistoryChart(history = s.history)

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.start() }, enabled = !s.running) { Text("Start") }
            OutlinedButton(onClick = { vm.stop() }, enabled = s.running) { Text("Stop") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Conf: ${"%.2f".format(s.confidence)} • ${if (s.inTuneWindow) "in tune" else "off"}",
            color = Color(0xFF9FB0BF), fontSize = 14.sp)
    }
}

@Composable
private fun NeedleBar(cents: Float, accent: Color) {
    val maxCents = 50f
    val clamped = cents.coerceIn(-maxCents, maxCents)
    val pos = (clamped + maxCents) / (2 * maxCents) // 0..1

    Box(
        Modifier.fillMaxWidth().height(80.dp)
            .background(Color(0xFF101216), RoundedCornerShape(12.dp)).padding(12.dp)
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
            drawRect(color = Color(0x142ECC71), topLeft = Offset(winStart, 0f), size = androidx.compose.ui.geometry.Size(winEnd - winStart, h))

            // center line
            drawLine(Color(0x66FFFFFF), Offset(w/2f, 0f), Offset(w/2f, h), 2f)
            // ±25/±50 markers
            drawBand(-25f, Color(0x3340A0FF))
            drawBand(+25f, Color(0x3340A0FF))
            drawBand(-50f, Color(0x22808080))
            drawBand(+50f, Color(0x22808080))

            // Needle
            val xNeedle = pos * w
            drawLine(color = accent, start = Offset(xNeedle, 0f), end = Offset(xNeedle, h), strokeWidth = 5f)

            // Scale ticks every 10 cents
            for (c in -50..50 step 10) {
                val x = (c + maxCents) / (2 * maxCents) * w
                val th = if (c % 25 == 0) 14f else 8f
                drawLine(Color(0x55FFFFFF), Offset(x, h - th), Offset(x, h), 2f)
            }
        }
        Text(
            text = "${clamped.roundToInt()} cents",
            color = accent,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HistoryChart(history: List<Float>) {
    val bg = Color(0xFF0B0D10)
    Box(
        Modifier.fillMaxWidth().height(160.dp)
            .background(bg, RoundedCornerShape(12.dp)).padding(8.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val maxCents = 50f
            // bands
            fun yForCent(c: Float) = (1f - ((c + maxCents) / (2 * maxCents))) * h
            // ±50 band bg
            drawRect(Color(0x1116A085), topLeft = Offset(0f, yForCent(+50f)), size = androidx.compose.ui.geometry.Size(w, yForCent(-50f)-yForCent(+50f)))
            // ±25 lines
            drawLine(Color(0x3340A0FF), Offset(0f, yForCent(+25f)), Offset(w, yForCent(+25f)), 2f)
            drawLine(Color(0x3340A0FF), Offset(0f, yForCent(-25f)), Offset(w, yForCent(-25f)), 2f)
            // 0 cent center
            drawLine(Color(0x66FFFFFF), Offset(0f, yForCent(0f)), Offset(w, yForCent(0f)), 2f)

            if (history.isNotEmpty()) {
                val stepX = w / (history.size - 1).coerceAtLeast(1)
                var last: Offset? = null
                history.forEachIndexed { i, v ->
                    val y = if (v.isNaN()) null else yForCent(v.coerceIn(-maxCents, +maxCents))
                    if (y != null) {
                        val p = Offset(i * stepX, y)
                        last?.let { prev -> drawLine(Color(0xFF52D273), prev, p, 3f) }
                        last = p
                    } else {
                        last = null // gap for unvoiced
                    }
                }
            }
        }
    }
}
