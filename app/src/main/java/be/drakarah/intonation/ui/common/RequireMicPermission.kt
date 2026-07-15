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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Gate for microphone screens: requests RECORD_AUDIO, keeps the screen awake while shown,
 * and only renders [content] (with a signal that the mic is available) once granted. */
@Composable
fun RequireMicPermission(content: @Composable () -> Unit) {
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
            Text("Intonation Trainer needs the microphone to hear your bass.")
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant microphone access")
            }
        }
    }
}
