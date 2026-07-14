package be.drakarah.intonation.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.ui.theme.Spacing

data class DotInfo(
    val color: Color,
    val contentDescription: String,
    val icon: ImageVector? = null,
)

@Composable
fun ProgressDotsCommon(
    dots: List<DotInfo>,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.PROGRESS_DOT_SIZE,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.PROGRESS_DOT_SPACING),
        verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
        modifier = modifier.fillMaxWidth()
    ) {
        dots.forEach { info ->
            Box(
                modifier = Modifier
                    .size(size)
                    .semantics { contentDescription = info.contentDescription }
                    .background(info.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (info.icon != null) {
                    Icon(
                        info.icon,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.65f), // Scale with dot size
                        tint = Color.White
                    )
                }
            }
        }
    }
}
