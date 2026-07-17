package be.drakarah.intonation.data

import be.drakarah.intonation.metrics.AttemptOutcome
import be.drakarah.intonation.metrics.AttemptQuality
import be.drakarah.intonation.metrics.AttemptRecord
import be.drakarah.intonation.metrics.RoundRecord

/**
 * Attempt entity ↔ domain mapping in one place (moved out of RoomMetricsStore) so the round-trip
 * — live `AttemptRecord` → row → [toRecord] → identical record — is unit-testable on the JVM
 * (`RoundReconstructionTest`, same pattern as `BackupDtoMappingTest`).
 */
internal fun AttemptRecord.toEntity(sessionId: Long, round: RoundRecord, epochDay: Int) = AttemptEntity(
    sessionId = sessionId,
    promptIndex = promptIndex,
    timestamp = round.startedAt,
    exerciseType = round.exerciseType,
    targetMidi = targetMidi,
    targetFreqHz = targetFreqHz,
    startMidi = startMidi,
    stringMidi = stringMidi,
    positionId = positionId,
    playedFreqHz = playedFreqHz,
    centsError = centsError,
    reactionTimeMs = reactionTimeMs,
    timeToStableMs = timeToStableMs,
    score = score,
    stars = stars,
    quality = quality.name,
    epochDay = epochDay,
    outcome = outcome.name,
    energyLevel = energyLevel,
    retryCount = retryCount,
    sustainHeldMs = sustainHeldMs,
    sustainResets = sustainResets,
    steadinessCents = steadinessCents,
    captureWobbleCents = captureWobbleCents,
    extrasJson = extrasJson,
)

/** Rebuilds the domain record from a persisted row. The wrong-note/octave/timeout flags come from
 * the `outcome` column (source of truth since v4); rows with a null outcome (shouldn't survive
 * MIGRATION_3_4, but be lenient) fall back to the quality column, which classifies timeouts. */
internal fun AttemptEntity.toRecord(): AttemptRecord {
    val storedOutcome = outcome?.let { name ->
        AttemptOutcome.entries.firstOrNull { it.name == name }
    }
    return AttemptRecord(
        promptIndex = promptIndex,
        targetMidi = targetMidi,
        targetFreqHz = targetFreqHz,
        startMidi = startMidi,
        stringMidi = stringMidi,
        positionId = positionId,
        playedFreqHz = playedFreqHz,
        centsError = centsError,
        reactionTimeMs = reactionTimeMs,
        timeToStableMs = timeToStableMs,
        score = score,
        stars = stars,
        quality = AttemptQuality.entries.firstOrNull { it.name == quality } ?: AttemptQuality.CLEAN,
        wrongNote = storedOutcome == AttemptOutcome.WRONG_NOTE,
        wrongOctave = storedOutcome == AttemptOutcome.WRONG_OCTAVE,
        timedOut = storedOutcome == AttemptOutcome.TIMEOUT,
        energyLevel = energyLevel,
        retryCount = retryCount,
        sustainHeldMs = sustainHeldMs,
        sustainResets = sustainResets,
        steadinessCents = steadinessCents,
        captureWobbleCents = captureWobbleCents,
        extrasJson = extrasJson,
    )
}
