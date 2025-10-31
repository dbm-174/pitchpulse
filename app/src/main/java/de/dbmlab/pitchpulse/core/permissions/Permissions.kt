package de.dbmlab.pitchpulse.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/**
 * Checks if the app has permission to record audio.
 */
fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * A composable that returns a lambda to launch the permission request.
 */
@Composable
fun rememberPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult
    )
    return { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}

/**
 * A composable that executes callbacks when the lifecycle enters and leaves the STARTED state.
 */
@Composable
fun LifecycleEffect(
    onStarted: () -> Unit,
    onStopped: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            onStarted()
            try {
                awaitCancellation()
            } finally {
                onStopped()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onStopped() }
    }
}
