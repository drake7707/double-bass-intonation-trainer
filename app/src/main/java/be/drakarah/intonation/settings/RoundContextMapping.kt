package be.drakarah.intonation.settings

import be.drakarah.intonation.metrics.RoundContext

/** Builds the [RoundContext] captured with a completed round from the current settings snapshot.
 * Shared by all game ViewModels so context assembly lives in one place. */
fun AppSettings.toRoundContext(appVersionCode: Int, nowMs: Long): RoundContext {
    fun minsSince(at: Long): Long? = if (at > 0) (nowMs - at) / 60_000 else null
    return RoundContext(
        a4Hz = a4.toFloat(),
        micSensitivity = micSensitivity.toInt(),
        difficulty = difficulty.name,
        roundLength = roundLength,
        minsSinceTuneUp = minsSince(lastTunedAt),
        minsSinceCalibration = minsSince(lastCalibratedAt),
        appVersionCode = appVersionCode,
    )
}
