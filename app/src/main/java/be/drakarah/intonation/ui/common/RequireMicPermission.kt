package be.drakarah.intonation.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import be.drakarah.intonation.R

/** Gate for microphone screens: requests RECORD_AUDIO, keeps the screen awake while shown,
 * and only renders [content] (with a signal that the mic is available) once granted.
 *
 * [onStop] is invoked when the app leaves the foreground (or this screen leaves
 * composition) so the caller can stop its pitch engine — without this, the mic stays
 * open and the AudioRecord read-loop keeps spinning indefinitely in the background.
 * [onStart] is invoked when the app returns to the foreground, mirroring the caller's
 * own initial `start()` call, so listening resumes instead of leaving the screen frozen. */
@Composable
fun RequireMicPermission(
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
        onDispose { view.keepScreenOn = false }
    }

    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnStop by rememberUpdatedState(onStop)
    val hasPermissionState by rememberUpdatedState(hasPermission)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Guarded by hasPermissionState: registering this observer while the
                // lifecycle is already STARTED replays a synthetic ON_START immediately,
                // before permission may have been granted — starting then would crash.
                Lifecycle.Event.ON_START -> if (hasPermissionState) currentOnStart()
                Lifecycle.Event.ON_STOP -> currentOnStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentOnStop()
        }
    }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(
                    R.string.common_mic_rationale,
                    stringResource(R.string.app_name),
                )
            )
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.common_mic_grant))
            }
        }
    }
}
