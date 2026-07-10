package be.drakarah.intonation.ui.tune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale
import kotlin.math.abs

/** Pre-session tuner: the instrument itself must be in tune or the games are meaningless.
 * This is deliberately the only screen (besides debug) with a live needle. */
@Composable
fun TuneUpScreen(
    onDone: () -> Unit,
    viewModel: TuneUpViewModel = viewModel(factory = TuneUpViewModel.Factory),
) {
    RequireMicPermission {
        LaunchedEffect(Unit) { viewModel.start() }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val noteStyle = rememberAppSettings().noteNameStyle

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Text("Tune up", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Play each open string and tune until it turns green.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // display order low to high: E A D G
                    BassTuning.openStrings.forEach { string ->
                        val done = state.inTune.contains(string)
                        val active = state.activeString == string
                        Card(Modifier.weight(1f)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    string.displayName(noteStyle),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        done -> ResultColors.excellent
                                        active -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    if (done) "✓" else " ",
                                    color = ResultColors.excellent,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                val cents = state.cents
                if (cents != null) {
                    val color = when {
                        abs(cents) <= 5f -> ResultColors.excellent
                        abs(cents) <= 15f -> ResultColors.close
                        else -> ResultColors.off
                    }
                    Text(
                        String.format(Locale.US, "%+.1f", cents),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        "cents ${if (cents > 0) "sharp" else "flat"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                    Spacer(Modifier.height(16.dp))
                    CentsNeedle(cents = cents)
                } else {
                    Text(
                        "—",
                        fontSize = 64.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "play an open string",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.weight(1f))
                if (state.allInTune) {
                    Text(
                        "All strings in tune — go play!",
                        style = MaterialTheme.typography.titleMedium,
                        color = ResultColors.excellent,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.allInTune) "Done" else "Skip")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Horizontal needle: +-50 cents full scale, center line at 0, green zone +-5 cents. */
@Composable
private fun CentsNeedle(cents: Float) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val zoneColor = ResultColors.excellent.copy(alpha = 0.35f)
    val needleColor = when {
        abs(cents) <= 5f -> ResultColors.excellent
        abs(cents) <= 15f -> ResultColors.close
        else -> ResultColors.off
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val cx = size.width / 2f
        val scale = size.width / 100f // 100 cents full width
        drawLine(trackColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 6f)
        drawLine(
            zoneColor,
            Offset(cx - 5 * scale, size.height / 2),
            Offset(cx + 5 * scale, size.height / 2),
            24f,
            cap = StrokeCap.Round,
        )
        drawLine(trackColor, Offset(cx, 4f), Offset(cx, size.height - 4f), 4f)
        val x = cx + cents.coerceIn(-50f, 50f) * scale
        drawLine(needleColor, Offset(x, 0f), Offset(x, size.height), 10f, cap = StrokeCap.Round)
    }
}
