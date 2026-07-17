package be.drakarah.intonation

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import be.drakarah.intonation.ui.AppNav
import be.drakarah.intonation.ui.theme.IntonationTrainerTheme

class MainActivity : ComponentActivity() {
    /**
     * Apply the in-app language choice before the UI inflates. The framework's per-app locale is
     * API 33+ only; AppCompat stores the choice (see the autoStoreLocales service in the manifest)
     * and we apply it to the Activity's resources here so it also works on API 26–32. On 33+ this
     * just re-affirms what the framework already set. Note names (Do Ré Mi / C D E) are a separate
     * user setting, not a locale — untouched by this.
     */
    override fun attachBaseContext(newBase: Context) {
        val locales = AppCompatDelegate.getApplicationLocales()
        val base = if (locales.isEmpty) newBase else {
            val config = Configuration(newBase.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(base)
    }

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
