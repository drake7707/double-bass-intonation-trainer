package be.drakarah.intonation.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.common.RoundSummaryScaffold
import be.drakarah.intonation.ui.common.exerciseLabel
import be.drakarah.intonation.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/** Read-only replay of a past round's results, rebuilt from Room. Hosts the shared
 * [RoundSummaryScaffold] in history mode (`live = null`): the playing itself, none of the
 * meta-game (no personal-best line, achievements, feedback prompt or "Let's go again"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = viewModel(factory = HistoryDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    val title = when (val s = state) {
        is HistoryDetailUiState.Loaded -> stringResource(
            R.string.history_detail_title,
            exerciseLabel(s.summary.exerciseType),
            dateFormat.format(Date(s.summary.startedAt)),
        )
        else -> stringResource(R.string.history_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cd_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
            // Top-aligned so every result has the same top margin regardless of how tall its content
            // is (vertical-centering pushed short rounds — e.g. Shifts — down inconsistently).
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                HistoryDetailUiState.Loading -> CircularProgressIndicator()
                HistoryDetailUiState.NotFound -> Text(
                    stringResource(R.string.history_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                is HistoryDetailUiState.Loaded -> RoundSummaryScaffold(
                    data = s.summary,
                    onExit = onBack,
                    live = null,
                    showDots = true,
                )
            }
        }
    }
}
