package de.dbmlab.pitchpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.dbmlab.pitchpulse.feature.melodygame.MelodyGameScreen
import de.dbmlab.pitchpulse.feature.settings.SettingsScreen
import de.dbmlab.pitchpulse.feature.tuner.TunerHost
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme

sealed class Screen(val route: String, val titleResId: Int, val iconResId: Int)

object TunerScreen : Screen("tuner", R.string.tuner, R.drawable.ic_tuner)
object MelodyGameScreen : Screen("melody_game", R.string.melody_game, R.drawable.ic_melody_game)
object SettingsScreen : Screen("settings", R.string.settings, R.drawable.ic_settings)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PitchPulseTheme(darkTheme = true) {
                val navController = rememberNavController()
                val screens = listOf(
                    TunerScreen,
                    MelodyGameScreen,
                    SettingsScreen
                )
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            screens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(painterResource(id = screen.iconResId), contentDescription = null) },
                                    label = { Text(stringResource(screen.titleResId)) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController,
                        startDestination = TunerScreen.route,
                        Modifier.padding(innerPadding)
                    ) {
                        composable(TunerScreen.route) { TunerHost() }
                        composable(MelodyGameScreen.route) { MelodyGameScreen() }
                        composable(SettingsScreen.route) {
                            SettingsScreen(onNavigateBack = { navController.navigateUp() })
                        }
                    }
                }
            }
        }
    }
}