package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import be.drakarah.intonation.ui.theme.Spacing

/**
 * The thin, half-width, low-alpha divider that groups sections on the Results and Progress screens.
 * Self-centering (wraps the rule in a full-width Box) so it looks the same whether the parent Column
 * centers its children or not, and it owns its own [Spacing.SECTION_BREAK] margins top and bottom —
 * drop it *between* sections and remove the surrounding spacers. One divider style app-wide.
 */
@Composable
fun SectionDivider() {
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.5f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
}
