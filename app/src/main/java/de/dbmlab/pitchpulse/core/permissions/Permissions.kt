package de.dbmlab.pitchpulse.core.permissions

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable


fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun PermissionedLifecycleGate(
    permission: String,
    onEnterWithPermission: () -> Unit,
    onLeave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Launcher für die Laufzeit-Berechtigung
    val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onEnterWithPermission()
        }

    // Beim ersten Anzeigen prüfen/anfragen und ggf. starten
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) onEnterWithPermission()
        else permissionLauncher.launch(permission)
    }

    // Beim Verlassen/Stoppen aufräumen
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                onLeave()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Falls das Composable komplett wegdisponiert wird (Navigation), ebenfalls aufräumen
            onLeave()
        }
    }
}