package be.drakarah.intonation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.settings.AppSettings

/** Current app settings as observable state, for screens without a ViewModel of their own. */
@Composable
fun rememberAppSettings(): AppSettings {
    val app = LocalContext.current.applicationContext as IntonationApplication
    val settings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle(AppSettings())
    return settings
}
