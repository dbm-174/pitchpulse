package de.dbmlab.pitchpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.dbmlab.pitchpulse.feature.tuner.TunerHost
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PitchPulseTheme (darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    TunerHost()

                    // Sichtbarer Debug-Overlay unten rechts
                    Text(
                        "UI läuft",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    )
                }
            }

        }
    }
}



