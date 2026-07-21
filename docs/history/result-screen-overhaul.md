# Results-screen overhaul + History (from Sarah's 2026-07-17 feedback batch)

## Context

Sarah's real-gameplay feedback on the round-results screens (TESTING.md bottom, discussed 2026-07-17).
Code reading confirmed every item and found root causes:

- **No scrolling**: the summary is a plain `Column` centered in a fixed `Box(weight(1f))` — the
  trace-feedback box overflows off-screen with no way to reach Save. (Bug.)
- **"More in tune than last week" is wrong three ways**: the window is a *trailing 7 days ending
  yesterday* (fires 2 days after install), it ignores mode (arco+pizz pooled), and it renders
  **twice** via two divergent code paths (coach sentence `IMPROVED` + `ImprovementLine`) with
  different thresholds. The ↓ arrow means "cents went down" but reads as decline. (Bugs.)
- **"Solid" felt wrong**: "How in tune" averages only the *scored* notes — a round with 9/20
  outright misses can still read Solid. Also two competing scales exist: stars (≤5/≤15/≤30¢) vs
  mastery bands (≤10/≤25¢).
- **Score shown twice**: in-round HUD "1712 / 2000" stays visible in the Done phase under the big
  1712 + "of 2000".
- **Yellow spans 5–30¢** (1★ and 2★ share a color) — why she wants an orange step.
- **Fonts**: one screen mixes 88/32/24 sp hardcoded with 5 Material roles.
- **No chart on Shift/Chords** results despite per-attempt cents being persisted/available.
- **History**: she wants Progress → history icon → list of past rounds → tap to reopen its results
  screen (also her tool for iterating on the screen without playing full rounds).

## Decisions made with Sarah (2026-07-17, via plan-mode Q&A — her calls)

1. **Trend = true previous 7-day block** `[startedAt−14d, startedAt−7d)`, filtered by exerciseType
   **and mode**; silent when no prior data. One render path only. (Her pick: honest, accepts silent first week.)
2. **Verdict capped by hit rate**, and the wording must make clear it covers only the landed notes.
3. **Orange = 1★** using existing star thresholds (green 3★ ≤5¢ / yellow 2★ ≤15¢ / orange 1★ ≤30¢ /
   red 0★ or missed), centralized once. Plus: **document the scale in-app** (legend; cents shown in
   expert mode) and extend the *word* scale to match (new "Quite flat/sharp" step between
   "a bit" and "too"). Consistency app-wide.
4. **History ships in this same pass** ("everything in one pass").
5. Also agreed in discussion: drop the ↓/↑ arrow; hide the HUD score line in the Done phase (keep
   the dots row — it carries wrong-note/timeout info the chart doesn't); typography hierarchy first,
   plus thin low-emphasis dividers between major sections.

---

## Part A — Shared data model + reconstruction (foundation)

**New `metrics/RoundSummary.kt`** (pure Kotlin, unit-testable):

```kotlin
data class SummaryChartPoint(signedCents: Float?, stars: Int, missed: Boolean, wrongOctave: Boolean)
data class RoundTrend(thisRoundAvgAbsCents: Float, previousBlockAvgAbsCents: Float)
data class RoundSummaryData(
    exerciseType, mode, configKey, startedAt, endedAt, totalScore, maxScore, roundLength,
    avgAbsCents: Float?, chartPoints: List<SummaryChartPoint>,   // empty for Sustain
    starsEarned: Int, starsPossible: Int,
    band: MasteryBand?, hitRatePct: Int?, scoredCount: Int, attemptCount: Int,
    verdict: RoundCoachVerdict?, sustainVerdict: SustainCoachVerdict?,
    trend: RoundTrend?, shiftStartFlagged: Boolean?,             // null = unknown (old shift rows)
)
fun buildRoundSummary(round: RoundRecord, previousBlockAvgCents: Float?): RoundSummaryData
fun cappedMasteryBand(avgAbsCents, scoredCount, attemptCount, thresholds): MasteryBand?
fun previousBlockWindow(startedAtMs, zone): Pair<Int, Int>       // epochDay [−14d, −7d)
fun masteryThresholdsFor(exerciseType): MasteryThresholds        // moved from ProgressViewModel (private today)
```

- **Hit-rate cap** (provisional, tune with her): band from cents as today, then capped —
  scored/attempts < 0.9 → at most SOLID; < 0.8 → at most DEVELOPING. Lives in `cappedMasteryBand`.
- **Live path**: each VM builds its `RoundRecord` at phase→Done, sets `uiState.summary =
  buildRoundSummary(record, null)` synchronously; after `RoundRecorder.record()` returns, copies in
  the trend. Trend is the only recorder-dependent field → no loading flash.
- **Trend plumbing**: `MetricsStore.averageAbsCentsBetween` gains `mode`; `RoundRecorder` calls it
  with `previousBlockWindow(round.startedAt)`; `RoundOutcome.lastWeekAvgCents` →
  `previousBlockAvgCents`. Remove the `IMPROVED` member from `RoundCoachVerdict` (and its string) —
  the single `ImprovementLine` is the one trend surface; the coach line keeps
  lean/time-pressure/band sentences.
- **Shift persistence fix (forward-only, DB stays v4)**: `ShiftViewModel.persistRound` also writes
  `wrongNote` and `extrasJson = ShiftAttemptExtras(startCents, shiftCents)` (@Serializable). Old
  rows → `verdict = null`, `shiftStartFlagged = null`, UI omits those lines.
  `shiftStartPushedLandingOff(...)` moves out of ShiftScreen.kt:339 as a pure fn.

**New `data/RoundReconstruction.kt` + `data/AttemptMapping.kt`**:
`reconstructRoundRecord(session, attempts): RoundRecord`; flags from the `outcome` column
(quality-TIMEOUT fallback); `contextJson`-null → synthesized context. Move the private
`AttemptRecord.toEntity` from `RoomMetricsStore.kt:75` into `AttemptMapping.kt` so the round-trip is
testable (pattern: `BackupDtoMappingTest`). History reuses the same `buildRoundSummary` — one build path.

## Part B — Results UI rework (all four games)

**`ui/common/RoundSummaryScaffold.kt` — rewritten signature**:
```kotlin
RoundSummaryScaffold(data: RoundSummaryData, onExit, live: LiveSummaryActions? = null, footerExtras = {})
class LiveSummaryActions(outcome: RoundOutcome?, showTraceFeedback, onTraceFeedback, onPlayAgain)
```
- **Scrollable** (`verticalScroll`) — fixes the unreachable feedback box in all games at once.
- Internal `RoundSummaryBreakdown(data)` switches on exercise: NOTE_ACCURACY = band headline +
  caption + chart + stars; SHIFT = optional check-start row + chart; CHORDS = chart + stars;
  SUSTAIN = nothing. Then ONE `ImprovementLine(data.trend)`. Live-only block (PB line,
  achievements, footerExtras, trace prompt, "Let's go again"), then "Done" always.
- Replaces the four private summaries (`NoteAccuracySummary`, three `DoneContent`s) — each game's
  Done phase becomes one scaffold call. **Hide the HUD "X / Y" score text in Done phase** (keep dots).
- **Typography scale** (fix "all over the place"): hero score keeps 88sp (celebratory); everything
  else Material roles — verdict headline `headlineSmall`, section labels/stars `titleLarge`, body
  `titleMedium`/`bodyLarge`, captions `bodyMedium` + onSurfaceVariant. The 32/24sp `TextSizes.REVEAL_*`
  stay ONLY in mid-round reveal (sized for playing distance); the summary is phone-in-hand.
- **Sections + dividers**: group into score block / intonation block / meta block with consistent
  spacing and thin `HorizontalDivider`s (her request).
- **Verdict honesty wording**: headline stays "How in tune: X"; when any prompts missed, a subline
  "based on the %1$d of %2$d notes you landed" (from scoredCount/attemptCount).
- **ImprovementLine**: takes `RoundTrend`, **no arrow icons** (words + color only); strings stay
  "more in tune than last week" (now actually true); technical variant keeps both averages.

**`ui/common/SummaryCentsChart.kt` (new)**: the Canvas from NoteAccuracyScreen.kt:332 generalized to
`List<SummaryChartPoint>`. Fed by Note Accuracy (per-prompt cents), Shift (landing cents), Chords
(per-tone cents). Sustain excluded (hold-based game). Per-game caption strings.

**Colors + words, centralized (`ui/theme` + `ui/common`)**:
- `starColor(stars: Int, missed: Boolean): Color` — 3★ green / 2★ yellow / **1★ orange (new
  `ResultColors.almost`)** / 0★+missed red. Replace the ~5 duplicated `when` blocks (dots rows ×4,
  reveal color ×4, chart) with it.
- `centsRevealWord` gains the orange step: 3★ "Spot on!", 2★ "Close — a bit sharp/flat",
  **1★ "Quite sharp/flat" (new strings)**, 0★ "Too sharp/flat".
- **Legend** (new small composable rendered under the chart and on the reveal? — chart only for now):
  four dots + word labels ("spot on / a bit off / quite off / off or missed"); expert mode
  (`LocalTechnicalDetails`) appends the cent ranges (≤5¢ / ≤15¢ / ≤30¢ / >30¢). This is the
  "document the scale" home; About stays untouched.

**Previews**: `ui/common/RoundSummaryPreviews.kt` — @Preview per exercise, live + history variants,
hand-built fake `RoundSummaryData` (her "iterate without playing rounds" need).

## Part C — History feature

- **Nav** (`ui/AppNav.kt`): `Routes.HISTORY = "history"`, `HISTORY_DETAIL = "history/{sessionId}"`
  (Long arg). `ProgressScreen` TopAppBar gains a History `IconButton` (top-right, her placement) →
  `onOpenHistory`.
- **`ui/history/HistoryScreen.kt` + `HistoryViewModel`**: list from `recentSessions(500)` filtered
  in the VM (same pattern as ProgressViewModel; no new list DAO). Rows are sessions-row-only:
  date/time, exercise label, arco/pizz, score/max, band word (from `session.avgAbsCents`; omitted
  for sustain). Grouped by day with headers. Factory pattern as elsewhere; no AppContainer changes.
- **`ui/history/HistoryDetailScreen.kt` + `HistoryDetailViewModel`**: Loading | NotFound |
  Loaded(RoundSummaryData). Loads session + attempts → `reconstructRoundRecord` →
  `previousBlockWindow(session.startedAt)` → mode-filtered avg query → `buildRoundSummary` →
  `RoundSummaryScaffold(data, live = null, onExit = back)` under a TopAppBar (exercise + date).
- **History shows what you played, not the meta-game**: score/chart/stars/band/verdict/trend
  (all recomputed exactly); **omits** PB line, achievements, trace prompt, pace suggestion,
  "Let's go again". (Trend is exact because its window is anchored to the session's own start.)
- **DAO additions** (no schema change, DB stays v4): `sessionById(id)`;
  `avgAbsCentsByDayRangeForMode(exerciseType, mode, fromDay, untilDay)` — this one query also backs
  the live trend. `SessionRepository`: `sessionWithAttempts(id)`, `avgAbsCentsInWindow(...)`.

## Strings / i18n

New: `reveal_quite_sharp/flat`, verdict subline (`note_band_scope` "based on the %1$d of %2$d notes
you landed"), legend labels (+ cents variants), shift/chords chart captions, `history_title`,
`history_empty`, history row bits. Removed: `coach_round_improved`, `improvement_cd_improved/worse`
(arrow gone). All new strings get EN + **draft NL/FR** (flag drafts for her/teacher sign-off like
the rest of phase 6b/6c). Base files: `strings_common.xml`, `strings_games.xml`, new
`strings_history.xml`.

## Tests

- `metrics/RoundSummaryTest.kt` — builder per exercise; **hit-rate cap** (tight cents + 55% hit
  rate → Developing); chart-point mapping incl. timeout/wrong-note/wrong-octave; trend silence.
- `metrics/TrendWindowTest.kt` — `previousBlockWindow` boundaries.
- `data/RoundReconstructionTest.kt` — round-trip: RoundRecord → entities → reconstruct →
  buildRoundSummary equals live-built, all 4 exercises; null-`outcome`/null-`contextJson` fallbacks.
- Extend `metrics/RoundRecorderTest.kt` — asserts mode-filtered `[−14d, −7d)` trend call.

## Docs (repo discipline)

- **TESTING.md**: move her feedback items into a dated Pending block with phone-verification steps
  (scroll to feedback box + submit; no trend line on fresh exercise/mode; capped verdict wording on
  a sloppy round; orange dots + legend incl. expert mode; shift/chords chart; history list + reopen;
  old shift rounds in history show chart but no coach line).
- **FEATURES.md**: history screen, 4-step color/word scale + legend, per-game charts, trend semantics.

## Sequencing

1. `metrics/RoundSummary.kt` + cap + window (+tests) → 2. trend query/mode change through recorder
(+tests) → 3. colors/words/legend centralization → 4. scaffold rewrite + shared chart + port 4 games
(+ typography/dividers/score-dedupe) → 5. reconstruction mapper (+round-trip tests) → 6. history
screens/nav/strings → 7. previews → 8. NL/FR drafts → 9. docs.

## Verification

- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then
  `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`.
- Emulator (`Medium_Phone_API_36.1`) smoke: play a short round per game → summary scrolls, one trend
  line max, legend renders, history opens a played round (emulator OK here since we verify within
  one boot; real persistence verification is her phone per repo notes).
- Install on her Pixel via wireless adb; she verifies from the TESTING.md Pending block.

## Commit notes

Her ideas to credit (user feedback/design/request): orange step, in-app color-scale documentation,
"quite flat" word step, history screen + top-right placement, honest verdict scope wording,
true-previous-week choice, dividers, everything-in-one-pass scope. Mine: star↔color unification,
data-driven summary refactor enabling previews, "history shows what you played not the meta-game".
