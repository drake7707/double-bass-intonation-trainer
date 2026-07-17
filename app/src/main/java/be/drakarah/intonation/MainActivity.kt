package be.drakarah.intonation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import be.drakarah.intonation.ui.AppNav
import be.drakarah.intonation.ui.theme.IntonationTrainerTheme

/**
 * AppCompatActivity (not a plain ComponentActivity) so the in-app language switch works:
 * AppCompatDelegate.setApplicationLocales only applies and persists a per-app locale when there is a
 * live AppCompat delegate to drive it. AppCompat's delegate also applies the stored locale to this
 * activity's resources in its own attachBaseContext (framework per-app locale on API 33+, the
 * autoStoreLocales backport on 26–32 — see the service in the manifest), so no manual override is
 * needed here. Note names (Do Ré Mi / C D E) are a separate user setting, not a locale.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IntonationTrainerTheme {
                AppNav()
            }
        }
    }
}
