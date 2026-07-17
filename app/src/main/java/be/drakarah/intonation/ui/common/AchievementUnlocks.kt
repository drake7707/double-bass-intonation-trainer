package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.R
import be.drakarah.intonation.game.AchievementDef

/** "Achievement unlocked" lines on a round summary. */
@Composable
fun AchievementUnlocks(achievements: List<AchievementDef>) {
    if (achievements.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        achievements.forEach { def ->
            Text(
                "${def.emoji} " + stringResource(R.string.common_achievement_unlocked, def.displayTitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
