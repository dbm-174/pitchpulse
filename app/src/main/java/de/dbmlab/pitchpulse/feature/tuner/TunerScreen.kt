package de.dbmlab.pitchpulse.feature.tuner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dbmlab.pitchpulse.core.music.NoteMapper
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import de.dbmlab.pitchpulse.core.permissions.*


@Preview
@Composable
fun TunerScreenPreview() {
    val vm =  remember { TunerViewModel() }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0C0F)) {
                TunerScreen(vm)
        }
    }
}

@Composable
fun TunerHost() {
    val vm =  remember { TunerViewModel() }
    PermissionedLifecycleGate(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onEnterWithPermission = { vm.start() },
        onLeave = { vm.stop() }
    )
    TunerScreen(vm)
}



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
        /*
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.start() }, enabled = !s.running) { Text("Start") }
            OutlinedButton(onClick = { vm.stop() }, enabled = s.running) { Text("Stop") }
        }*/
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
fun HistoryChart(
    history: List<Float>,
    modifier: Modifier = Modifier,
    windowSize: Float = 20f,            // sichtbarer Ausschnitt in "Steps"
    animateMs: Int = 400               // Dauer des Nachführens


) {
    val bg = Color(0xFF0B0D10)
    val maxVal = 127f
    val minVal = 0f
    val mapper = NoteMapper()

    // Viewport als center-basierte Darstellung, damit wir sauber clampen können
    val initialCenter = 60f.coerceIn(windowSize/2, maxVal - windowSize/2)
    val center = remember { Animatable(initialCenter) }

    // Letzten gültigen Messwert ermitteln (für Nachführung)
    val lastValid = remember(history) {
        history.indexOfLast { !it.isNaN() }
            .takeIf { it >= 0 }
            ?.let { history[it].coerceIn(minVal, maxVal) }
    }

    // Sanftes Nachführen, wenn der Wert den Viewport verlässt (mit kleinem Innenpuffer)
    LaunchedEffect(lastValid, windowSize) {
        val margin = 2f // etwas Puffer, damit nicht bei jeder kleinen Berührung gescrollt wird
        lastValid?.let { v ->
            val curMin = center.value - windowSize/2
            val curMax = center.value + windowSize/2
            val inside = v in (curMin + margin)..(curMax - margin)
            if (!inside) {
                val targetCenter = v.coerceIn(windowSize/2, maxVal - windowSize/2)
                center.animateTo(
                    targetCenter,
                    animationSpec = tween(durationMillis = animateMs, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(400.dp) // volle Höhe nutzen
            .background(bg, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Aktueller Viewport (unten/oben in Datenkoordinaten)
            val vpMin = (center.value - windowSize/2).coerceIn(minVal, maxVal - windowSize)
            val vpMax = (vpMin + windowSize).coerceAtMost(maxVal)

            // Mapping: Datenwert -> y-Pixel (oben kleiner y)
            fun yFor(v: Float): Float {
                val t = ((v - vpMin) / (vpMax - vpMin)).coerceIn(0f, 1f)  // 0..1
                return (1f - t) * h
            }

            // Einfache Hintergrundbänder / Raster: jede 2 Einheiten eine feine Linie
            val tickStep = 2
            for (tick in ceil(vpMin).toInt()..floor(vpMax).toInt()) {
                if (tick % tickStep == 0) {
                    val y = yFor(tick.toFloat())
                    drawLine(Color(0x2230A0FF), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
            }

            // Stärkere Linie auf ganzzahligen Noten (optional)
            for (tick in ceil(vpMin).toInt()..floor(vpMax).toInt()) {
                val y = yFor(tick.toFloat())
                drawLine(Color(0x33444444), Offset(0f, y), Offset(w, y), strokeWidth = if (tick % 12 == 0) 2f else 1f)
            }

            // y-Achsen-Labels links (alle 4 Schritte)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = 10.dp.toPx()
                }
                val labelStep = 4
                val paddingPx = 4.dp.toPx()
                for (tick in ceil(vpMin).toInt()..floor(vpMax).toInt()) {
                    if (tick % labelStep == 0) {
                        val y = yFor(tick.toFloat())
                        val label = mapper.midiToName(tick)
                        canvas.nativeCanvas.drawText(
                            label,
                            paddingPx,
                            y - 2f,
                            paint
                        )
                    }
                }
            }

            // Verlaufslinie zeichnen, wenn Daten vorhanden
            if (history.isNotEmpty()) {
                val stepX = w / (history.size - 1).coerceAtLeast(1)
                var last: Offset? = null
                history.forEachIndexed { i, v ->
                    val vv = v.takeIf { !it.isNaN() }?.coerceIn(minVal, maxVal)
                    val y = vv?.let { yFor(it) }
                    if (y != null) {
                        val p = Offset(i * stepX, y)
                        last?.let { prev -> drawLine(Color(0xFF52D273), prev, p, 3f) }
                        last = p
                    } else {
                        last = null // Lücke für ungültige Werte
                    }
                }
            }
        }
    }
}

