package be.drakarah.intonation.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.game.ACHIEVEMENTS
import be.drakarah.intonation.game.AchievementDef
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.displayDescription
import be.drakarah.intonation.ui.common.displayTitle
import be.drakarah.intonation.ui.theme.Spacing

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel(factory = AchievementsViewModel.Factory),
) {
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
        ) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            Text(stringResource(R.string.ach_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            Text(
                stringResource(R.string.ach_unlocked_count, unlocked.size, ACHIEVEMENTS.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            LinearProgressIndicator(
                progress = {
                    if (ACHIEVEMENTS.isEmpty()) 0f else unlocked.size.toFloat() / ACHIEVEMENTS.size
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.SECTION_BREAK))

            // Unlocked first so a full-looking board rewards the collector; locked ones stay
            // visible as goals. Definition order is preserved within each group (stable sort).
            val ordered = ACHIEVEMENTS.sortedByDescending { it.id in unlocked }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
                verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
                contentPadding = PaddingValues(bottom = Spacing.FINE_SPACING),
            ) {
                items(ordered, key = { it.id }) { def ->
                    AchievementCell(def, def.id in unlocked)
                }
            }
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ach_back))
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

@Composable
private fun AchievementCell(def: AchievementDef, isUnlocked: Boolean) {
    Card(
        // A fixed height keeps every tile in a row identical — otherwise each cell sizes to its
        // own text and the grid looks ragged. Content is centered and clamped to fit.
        modifier = Modifier.height(160.dp),
        colors = if (isUnlocked) CardDefaults.cardColors()
                 else CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                 ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(Spacing.CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isUnlocked) {
                Text(
                    def.emoji,
                    style = MaterialTheme.typography.headlineLarge,
                )
            } else {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.ach_cd_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(40.dp),
                )
            }
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            Text(
                def.displayTitle,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.COMPONENT_SPACING))
            Text(
                def.displayDescription(LocalTechnicalDetails.current),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
