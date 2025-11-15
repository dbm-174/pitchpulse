package de.dbmlab.pitchpulse.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.ui.theme.PitchPulseTheme

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()

    var a4Hz by remember(settings) { mutableStateOf(settings?.a4Hz?.toString() ?: "440.0") }
    var key by remember(settings) { mutableStateOf(settings?.key ?: NoteMapper.AllKeys.C) }
    var keyDropdownExpanded by remember { mutableStateOf(false) }

    PitchPulseTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = a4Hz,
                    onValueChange = { a4Hz = it },
                    label = { Text("Reference Tone (A4) Hz") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keyDropdownExpanded = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Key: ${key.name}")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select key")
                    }
                    DropdownMenu(
                        expanded = keyDropdownExpanded,
                        onDismissRequest = { keyDropdownExpanded = false }
                    ) {
                        NoteMapper.AllKeys.entries.forEach { keyEntry ->
                            DropdownMenuItem(text = { Text(keyEntry.name) }, onClick = {
                                key = keyEntry
                                keyDropdownExpanded = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Corrected version in SettingsScreen.kt
                Button(onClick = {
                    a4Hz.toFloatOrNull()?.let { vm.setA4Hz(it) }
                    vm.setKey(key)
                    onNavigateBack()
                }) {
                    Text("Save")
                }

            }
        }
    }

