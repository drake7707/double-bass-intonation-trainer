package be.drakarah.intonation.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.Spacing

/**
 * The app's UI language. This is a per-app locale, applied via [AppCompatDelegate] — separate from
 * the note-name style (Do Ré Mi vs C D E), which is a musical-notation preference, not a language.
 * [SYSTEM] follows the phone's language; the others force one. English is the base, NL/FR are
 * shipped translations (values-nl / values-fr). The endonyms are intentionally the same in every
 * language so a lost user can always spot their own.
 */
enum class AppLanguage(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    DUTCH("nl", R.string.language_dutch),
    FRENCH("fr", R.string.language_french);

    companion object {
        /** The currently applied language, from AppCompat's stored per-app locale. */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .takeIf { !it.isEmpty }?.get(0)?.language ?: return SYSTEM
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }

        /** Apply a language app-wide. AppCompat recreates the activity and persists the choice. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag)
            )
        }
    }
}

/** Horizontally-scrolling chips for choosing the app language (onboarding + Settings). */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    // AppCompat recreates the activity on change, so reading current() at composition is enough;
    // the local mirror only keeps the chip highlighted for the instant before recreation.
    var selected by remember { mutableStateOf(AppLanguage.current()) }
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
    ) {
        AppLanguage.entries.forEach { language ->
            FilterChip(
                selected = selected == language,
                onClick = {
                    selected = language
                    AppLanguage.apply(language)
                },
                label = { Text(stringResource(language.labelRes)) },
            )
        }
    }
}
