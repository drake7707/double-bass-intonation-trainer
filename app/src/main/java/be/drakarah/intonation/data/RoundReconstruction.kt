package be.drakarah.intonation.data

import be.drakarah.intonation.metrics.RoundContext
import be.drakarah.intonation.metrics.RoundRecord
import kotlinx.serialization.json.Json

/**
 * Rebuilds a [RoundRecord] from a persisted session + its attempts, so the History screen can feed
 * the exact same `buildRoundSummary` the live games use — one build path, no second rendering
 * logic. Pure entity → domain mapping; unit-tested round-trip in `RoundReconstructionTest`.
 */
fun reconstructRoundRecord(session: SessionEntity, attempts: List<AttemptEntity>): RoundRecord =
    RoundRecord(
        exerciseType = session.exerciseType,
        mode = session.mode,
        configKey = session.configKey,
        startedAt = session.startedAt,
        endedAt = session.endedAt ?: session.startedAt,
        totalScore = session.totalScore,
        maxScore = session.maxScore,
        context = parseRoundContext(session.contextJson)
            // Oldest rows may predate contextJson: synthesize the one field the summary shows.
            ?: RoundContext(
                a4Hz = 440f,
                micSensitivity = 0,
                difficulty = "",
                roundLength = (attempts.maxOfOrNull { it.promptIndex } ?: -1) + 1,
            ),
        attempts = attempts.map { it.toRecord() },
    )

private val lenientJson = Json { ignoreUnknownKeys = true }

internal fun parseRoundContext(json: String?): RoundContext? = json?.let {
    runCatching { lenientJson.decodeFromString<RoundContext>(it) }.getOrNull()
}
