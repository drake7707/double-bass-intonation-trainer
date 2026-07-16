package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StarRating(
    starCount: Int,
    color: Color,
    modifier: Modifier = Modifier,
    starSize: Dp = 32.dp,
) {
    Row(modifier = modifier) {
        repeat(3) { i ->
            Icon(
                imageVector = if (i < starCount) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(starSize)
            )
        }
    }
}
