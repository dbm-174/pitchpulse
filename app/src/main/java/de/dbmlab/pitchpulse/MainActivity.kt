package de.dbmlab.pitchpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dbmlab.pitchpulse.feature.tuner.TunerHost
import de.dbmlab.pitchpulse.feature.level.LevelHost
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme







enum class Screen { MENU, LEVEL, TUNER, VOICE, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PitchPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PitchPulseApp()
                }
            }
        }
    }
}


// App preview function
@Preview(showBackground = true)
@Composable
fun PitchPulseAppPreview() {
    PitchPulseTheme {
        PitchPulseApp()
    }
}


@Composable
fun PitchPulseApp(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf(Screen.MENU) }


    BackHandler(enabled = screen != Screen.MENU) {
        screen = Screen.MENU
    }
    when (screen) {
        Screen.MENU -> MainMenu(
            modifier = modifier,
            onOpenLevel = { screen = Screen.LEVEL },
            onOpenTuner = { screen = Screen.TUNER },
            onOpenVoice = { screen = Screen.VOICE },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.LEVEL -> LevelScreenWrapper(onBackToMenu = { screen = Screen.MENU })
        Screen.TUNER -> TunerScreenWrapper(onBackToMenu = { screen = Screen.MENU })
        Screen.VOICE -> VoiceScreenWrapper(onBackToMenu = { screen = Screen.MENU })
        Screen.SETTINGS -> SettingsScreenWrapper(onBackToMenu = { screen = Screen.MENU })
    }
}

@Composable
fun MainMenu(
    modifier : Modifier = Modifier,
    onOpenLevel: () -> Unit,
    onOpenTuner: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier.statusBarsPadding()
            .safeDrawingPadding()
            .padding(24.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MenuButton(text = stringResource(R.string.level), onClick = onOpenLevel, enabled = true)
        Spacer(Modifier.height(12.dp))
        MenuButton(text = stringResource(R.string.tuner), onClick = onOpenTuner, enabled = true)
        Spacer(Modifier.height(12.dp))
        MenuButton(text = stringResource(R.string.voice), onClick = onOpenVoice, enabled = true)
        Spacer(Modifier.height(12.dp))
        MenuButton(text = stringResource(R.string.settings), onClick = onOpenSettings, enabled = true)
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text(text) }
}



// ---------------- my 4 sub pages  -------------------- ///

@Composable
fun LevelScreenWrapper(onBackToMenu: () -> Unit) {
    ScreenWithBottomMainMenu(
        title = stringResource(R.string.level),
        onBackToMenu = onBackToMenu
    ) {
        LevelHost()
    }
}

@Composable
fun TunerScreenWrapper(onBackToMenu: () -> Unit) {
    ScreenWithBottomMainMenu(
        title = stringResource(R.string.tuner),
        onBackToMenu = onBackToMenu
    ) {
        TunerHost() // deine bestehende Composable
    }
}

@Composable
fun VoiceScreenWrapper(onBackToMenu: () -> Unit) {
    ScreenWithBottomMainMenu(
        title = stringResource(R.string.voice),
        onBackToMenu = onBackToMenu
    ) {
        // TODO: Dein Voice-UI hier
        Text("Voice Coming Soon")
    }
}

@Composable
fun SettingsScreenWrapper(onBackToMenu: () -> Unit) {
    ScreenWithBottomMainMenu(
        title = stringResource(R.string.settings),
        onBackToMenu = onBackToMenu
    ) {
        // TODO: Deine Settings hier (Theme, Sprache, etc.)
        Text("Settings Coming Soon")
    }
}

// joint layout for all sub-pages

@Composable
fun ScreenWithBottomMainMenu(
    title: String,
    onBackToMenu: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .safeDrawingPadding()
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Hauptinhalt der Seite
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            content = content
        )

        // Button unten
        Button(
            onClick = onBackToMenu,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.back_to_main))
        }
    }
}

