package be.drakarah.intonation.ui.tune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale
import kotlin.math.abs

/** Pre-session tuner: the instrument itself must be in tune or the games are meaningless.
 * Centered layout and large elements for readability from 2 meters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneUpScreen(
    onDone: () -> Unit,
    viewModel: TuneUpViewModel = viewModel(factory = TuneUpViewModel.Factory),
) {
    RequireMicPermission {
        LaunchedEffect(Unit) { viewModel.start() }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val noteStyle = rememberAppSettings().noteNameStyle

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_tune_up)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_cd_back),
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.tune_play_each),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.SECTION_BREAK))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
                ) {
                    // display order low to high: E A D G
                    BassTuning.openStrings.forEach { string ->
                        val done = state.inTune.contains(string)
                        val active = state.activeString == string
                        Card(Modifier.weight(1f)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.CARD_PADDING),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    string.displayName(noteStyle),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        done -> ResultColors.excellent
                                        active -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.height(Spacing.COMPONENT_SPACING))
                                // Fixed height container for the checkmark to prevent layout jumping
                                Box(
                                    Modifier.height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (done) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = stringResource(
                                                R.string.tune_cd_in_tune,
                                                string.displayName(noteStyle),
                                            ),
                                            tint = ResultColors.excellent,
                                            modifier = Modifier.fillMaxHeight(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.SECTION_BREAK))

                val cents = state.cents
                val technical = LocalTechnicalDetails.current
                if (cents != null) {
                    val color = when {
                        abs(cents) <= 5f -> ResultColors.excellent
                        abs(cents) <= 15f -> ResultColors.close
                        else -> ResultColors.off
                    }
                    if (technical) {
                        Text(
                            String.format(Locale.US, "%+.1f", cents),
                            fontSize = TextSizes.SCORE_DISPLAY,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Text(
                            stringResource(
                                if (cents > 0) R.string.tune_cents_sharp else R.string.tune_cents_flat
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = color,
                        )
                    } else {
                        Text(
                            stringResource(
                                when {
                                    abs(cents) <= 5f -> R.string.tune_in_tune
                                    abs(cents) <= 15f ->
                                        if (cents > 0) R.string.tune_little_sharp
                                        else R.string.tune_little_flat
                                    else -> if (cents > 0) R.string.tune_sharp else R.string.tune_flat
                                }
                            ),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                    }
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    CentsNeedle(cents = cents)
                } else {
                    Text(
                        "—",
                        fontSize = TextSizes.SCORE_DISPLAY,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.tune_play_prompt),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Spacing.SECTION_BREAK))
                if (state.allInTune) {
                    Text(
                        stringResource(R.string.tune_all_done),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ResultColors.excellent,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (state.allInTune) R.string.summary_done else R.string.tune_skip
                        ),
                        fontSize = 20.sp, modifier = Modifier.padding(4.dp),
                    )
                }
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
    val needleDescription = stringResource(R.string.tune_cd_needle)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = needleDescription }
    ) {
        val cx = size.width / 2f
        val scale = size.width / 100f // 100 cents full width
        drawLine(trackColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 8f)
        drawLine(
            zoneColor,
            Offset(cx - 5 * scale, size.height / 2),
            Offset(cx + 5 * scale, size.height / 2),
            32f,
            cap = StrokeCap.Round,
        )
        drawLine(trackColor, Offset(cx, 4f), Offset(cx, size.height - 4f), 6f)
        val x = cx + cents.coerceIn(-50f, 50f) * scale
        drawLine(needleColor, Offset(x, 0f), Offset(x, size.height), 12f, cap = StrokeCap.Round)
    }
}
