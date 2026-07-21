# Progress screen redesign — coach, don't grade

## Context

The Progress screen is meant to make Sarah feel real improvement and stay motivated. Right now it
does the opposite. Two concrete problems, confirmed against her real imported data (pulled from the
Pixel, 39 rounds over 5 days):

1. **A correctness bug produces the "0%" headline.** The screen reads two different data sources.
   The position breakdown reads the `daily_stats` rollup (SCORED-only, clean — pizz 1st = 24.7¢).
   But *"avg accuracy (last 10)"*, the session rows, and the cents shown per row all read raw
   `sessions.avgAbsCents`, which is **wrong-note-polluted** on older rounds (stored as 138¢, 149¢,
   up to 529¢ because confident wrong notes were folded in). Her last-10 pizz rounds are really
   `[20,21,35,18,68,21,22,23,139,135]¢`; two polluted rounds drag the mean to 50.2¢ → **0%**. The
   rollup already solved this; the screen just never got repointed at it.

2. **The cents→% scale is pedagogically wrong and demotivating.** `centsToAccuracy` is linear with
   `50¢ = 0%`, so 25¢ reads "49%". On fretless bass ~25¢ is normal learning territory (her SCORED
   median is 15.9¢). Worse, the position *bar* uses a different scale again (`30¢ = empty`), so the
   bar looks near-empty while the label says 49% — two mismatched punishing scales on one row.

Rich signals we already capture in `daily_stats` are shown nowhere: **signed bias** (her arco 1st
runs −22¢ flat while pizz 1st is −0.1¢ dead-on — actionable coaching), first-try rate, clean rate,
consistency (`sumSqAbsCents`), settle-time, hold-time/steadiness (sustain), streak.

**Decisions locked with Sarah:**
- Intonation shown as **actual cents + a mastery word** (Locked / Solid / Developing), bar fills by
  musical tiers — **no misleading %**.
- The per-round game list is **replaced by a "teacher's notebook" coaching summary**.
- Keep the trending chart (she likes it).

**Goal:** honest but encouraging framing, sourced entirely from the SCORED-only rollup, surfacing
the coaching signals we already collect — "you vs your past self", never a failing grade.

## Approach

All new *logic* lives in the pure `metrics/` domain (no `android.*`), unit-tested like `game/` — per
the layering rule in `docs/metrics-plan.md`. The `data/` layer only gains windowed rollup queries.
The ViewModel maps rows → pure functions → UI state. This is the start of the `CoachingRepository`
that metrics-plan deferred, kept minimal.

### 1. New pure domain: `metrics/Coaching.kt` (unit-tested)

Single source of truth for how intonation is *described* (kills the dual-scale bug):

- `enum MasteryBand { LOCKED, SOLID, DEVELOPING }` + `MasteryBand.of(cents)` with provisional
  thresholds **≤10¢ Locked / ≤25¢ Solid / >25¢ Developing** (align conceptually with the existing
  star tiers in [Scoring.kt](app/src/main/java/be/drakarah/intonation/game/Scoring.kt); provisional,
  Sarah tunes). Each band carries a display label + which `ResultColors` swatch it uses.
- `masteryFraction(cents): Float` — piecewise anchors so crossing a tier feels rewarding, not a
  near-empty bar: `≤5¢→1.0`, `10¢→0.8`, `25¢→0.5`, `≥45¢→0.05` (min floor, never 0). The bar and
  the word now come from the *same* function.
- `biasLabel(signedCents): Bias` — `|bias|<6¢` → "centered"; else "runs N¢ flat/sharp" + a
  direction arrow. (Her arco 1st = −22¢ flat is the flagship example.)
- `weekTrend(thisWeekAvg, lastWeekAvg): WeekTrend` → signed delta + word ("2¢ tighter" / "steady" /
  "looser"), gracefully null when there isn't a prior week yet (she only has 5 days of data).
- `selectInsight(positions, summary): String?` — the one "watch this" line (rendered with a
  lightbulb/tip icon, not an emoji). Ordered rule:
  biggest actionable bias (|signed|≥15¢) → else celebrate an improving trend → else name her most
  secure position. Provisional pedagogy; unit-tested for selection order.
- Value types `PositionMastery`, `CoachingSummary` (rounds this week, streak, weekTrend,
  firstTryPct, cleanPct, insight, and for sustain: avg/best hold-ms, steadiness).

### 2. Data layer: windowed reads on the existing rollup

[Daos.kt](app/src/main/java/be/drakarah/intonation/data/Daos.kt) — `DailyStatsDao`:
- Extend `positionAccuracy` to also `SUM(sumSignedCents)` and `SUM(sumSqAbsCents)`; add those to
  `PositionAccuracyAgg`.
- Add `windowAgg(exerciseType, fromDay, untilDay): Flow<WindowAgg>` — one row of the sums needed for
  the summary (scored/attempt/firstTry/clean/timeout/wrongNote counts, sumAbsCents, sumHeldMs,
  sumResets, sumSteadiness). Uses the existing `(exerciseType, epochDay)` index.
- Add `roundsInWindow(exerciseType, fromDay, untilDay): Flow<Int>` on `sessions` (indexed) — the
  honest round count (rollup `sessionCount` double-counts multi-position rounds, so don't use it here).

[SessionRepository.kt](app/src/main/java/be/drakarah/intonation/data/SessionRepository.kt): extend
`positionAccuracy` mapping to carry signed + variance; add `coachingWindow(...)` flows. Stays a thin
wrapper (no logic — logic is in `metrics/`).

### 3. ViewModel: [ProgressViewModel.kt](app/src/main/java/be/drakarah/intonation/ui/progress/ProgressViewModel.kt)

- Compute today's epochDay once; build `this week` = last 7 days, `last week` = prior 7.
- `combine` position mastery + this-week/last-week window aggs + streak (`practiceStreakDays()`) +
  rounds count → map through the pure `metrics/Coaching.kt` functions into a new `ProgressUiState`.
- **Delete `recentAvgCents`** and its dependence on `sessions.avgAbsCents` (the bug source). Chart
  keeps using `scorePercents` (score/maxScore) — that's already SCORED-safe and unaffected.
- Per-exercise shape: Note Accuracy / Shift / Chords → intonation mastery + bias. **Sustain →**
  hold-time/steadiness/resets variant (it produces no scored cents — all TIMEOUT in her data — so
  intonation bands and "avg deviation" are meaningless for it today).

### 4. UI: [ProgressScreen.kt](app/src/main/java/be/drakarah/intonation/ui/progress/ProgressScreen.kt)

- Keep: tabs, `ScoreChart`, top stat trio — but make the trio motivating and non-punishing:
  **rounds · best % · streak** (drop the "avg accuracy %" stat entirely).
- **Delete** `centsToAccuracy`, the `showCents` toggle (cents are always shown now), `SessionRow`,
  and the session `LazyColumn` list.
- **Coaching summary card** (teacher's notebook) below the chart: rounds & streak, intonation
  `24¢ → 21¢ vs last week` (trend shown with a Material trending-up/down/flat icon), first-try % ·
  clean %, and the insight line. Empty/first-week state degrades to encouragement ("keep playing to
  unlock your weekly trend").

**Design language — no emoji; use Material icons + existing tokens for UX consistency.** Streak →
`Icons.Filled.LocalFireDepartment` (or `Whatshot`); trend → `Icons.Filled.TrendingUp/Down/Flat`;
insight → `Icons.Outlined.Lightbulb` (or `TipsAndUpdates`) — matching the app's existing icon usage
(`EmojiEvents`, `PlayCircleOutline`, `ArrowBack`). Reuse `Spacing`, `ResultColors`, `MaterialTheme`
typography, and the existing `Card`/`PositionPill` patterns already in `ProgressScreen.kt`; introduce
no new visual style.
- **Mastery-by-position** section replacing `PositionBreakdown`: per position → word + actual ¢ +
  tiered bar (`masteryFraction`, band color) + bias sub-line ("runs 22¢ flat ↓"). Cents-based
  exercises only.
- **Sustain variant** of both sections: best/avg hold time, steadiness, resets; no cents, no bias.

### 5. Tests

- `metrics/CoachingTest.kt` (pure, no Android): band boundaries, `masteryFraction` anchors/monotonic,
  `biasLabel` thresholds/sign, `weekTrend` incl. null prior week, `selectInsight` ordering. This is
  the safety net for the provisional pedagogy thresholds.
- Repository/DAO test (in-memory Room, corpus style) for `windowAgg` / `roundsInWindow` day bounds
  and SCORED-only sums — reuse the seeding pattern in existing `:app` metrics tests.
- Remove/adjust any test asserting the old `centsToAccuracy` behaviour (grep shows it's used only in
  `ProgressScreen.kt`, so no test currently depends on it).

## Files

- **New:** `app/src/main/java/be/drakarah/intonation/metrics/Coaching.kt`;
  `app/src/test/java/be/drakarah/intonation/metrics/CoachingTest.kt`
- **Edit:** `data/Daos.kt`, `data/SessionRepository.kt`, `ui/progress/ProgressViewModel.kt`,
  `ui/progress/ProgressScreen.kt`
- **Docs:** `FEATURES.md` (progress-screen description), `docs/metrics-plan.md` (tick the
  CoachingRepository/read-surface item), `TESTING.md` (add Pending on-phone checks).

No schema/migration change — `daily_stats` already carries every signal this uses.

## Verification

- `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`;
  `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`.
- Install on the Pixel and open Progress with her imported data. Confirm on real numbers:
  - Accuracy tab no longer shows 0%; pizz 1st reads ~25¢ "Solid", not "49%".
  - Arco 1st shows the flat-bias callout (−22¢). Bar and word agree (no near-empty "49%" bar).
  - Coaching summary populates (rounds, streak, first-try/clean %); weekly-trend degrades cleanly
    with her sparse history.
  - Sustain tab shows hold-time/steadiness, not "avg deviation".
- Add the above as **Pending** items in TESTING.md; move to Verified (dated) when she confirms.
- Optional: re-run the scratchpad `analyze.py` numbers as the expected on-screen values to eyeball
  against the device.
