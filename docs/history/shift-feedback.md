# Shift Trainer — 3 levels, blended scoring, and shared detection

## Context

Sarah's two feedback items on the Shift Trainer (from TESTING.md, bottom):

1. **Difficulty levels.** Same-string shifts always jump finger 1 ↔ finger 4 (the pool prefers
   distance ≥ 3 semitones), so she never trains her 2nd finger or smaller shifts. She wants a
   3-level difficulty ladder from basic to hardest.
2. **Scoring + detection.**
   - Scoring only looks at the landing vs. target. "Start 20¢ off, land 20¢ off" is a *good shift
     with a bad start*, but scores as a 20¢ miss. She wants the shift *distance* credited and the
     start error surfaced in coaching.
   - **The start note of a shift often didn't register** (not a timeout — the note simply never
     captured), and it *doesn't* happen in Note Accuracy. She asked whether the two games share a
     detection flow. **They don't, and that's the bug.**

**Root cause of the "note didn't register" (confirmed from code, not guesswork):** Note Accuracy
arms every prompt `skipQuietGate=true, requireOnsetRise=true`
([NoteAccuracyViewModel.kt:409](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L409)) —
the DETECTION.md §3 legato fix. But Shift, on every prompt after the first, calls
`newCapture(..., skipQuiet = false)` ([ShiftViewModel.kt:298](app/src/main/java/be/drakarah/intonation/ui/shift/ShiftViewModel.kt#L298)),
which lands the start `AttemptCapture` in `AWAIT_QUIET` mode (because `requireOnsetRise` defaults to
`!skipQuietGate`, [AttemptCapture.kt:105](app/src/main/java/be/drakarah/intonation/game/AttemptCapture.kt#L105)).
Under legato/ring the room never goes quiet, so the start never onsets. Exactly the §3.1 bug,
fixed for Note Accuracy but never carried to Shift.

**Decisions taken with Sarah:**
- Levels: **Basic** = same string, finger 1↔4 only; **Intermediate** = same string, any fingers;
  **Advanced** = cross string, any fingers. Replaces the current same/cross split.
- Scoring: **blend** 0.7 × shift-distance accuracy + 0.3 × landing accuracy, with the results page
  decomposing start vs. shift vs. landing.
- Detection: unify the flow (her "one pipeline, avoid duplication"). She will record a fresh shift
  trace *with events* to confirm the fix.

---

## Workstream A — Fix "start note didn't register" (the core bug)

The start-note capture must arm like Note Accuracy / Arpeggio: **`skipQuietGate=true,
requireOnsetRise=true` on every prompt** (Listening immediately, but only a real attack onsets — so
no AWAIT_QUIET starvation *and* no ring-over grab). Once Workstream B lands, the shift start runs
through the shared `FilteredCapture`, so this arming is inherited, not re-specified — A and B are the
same fix seen from two angles (A = the behavior/root cause, B = the mechanism). A can be delivered
first as the minimal fix if we want it on her phone before the larger refactor.

- **`game/ShiftCapture.kt`**: thread `requireOnsetRise` into the start `AttemptCapture`. Arm the
  start capture (initial + the wrong-note re-arm at [ShiftCapture.kt:94](app/src/main/java/be/drakarah/intonation/game/ShiftCapture.kt#L94))
  with `skipQuietGate=true, requireOnsetRise=true`. Landing stays `skipQuietGate=true,
  requireOnsetRise=false` (mid-glide — correct as-is). Drop the now-pointless `skipQuietGate` ctor
  param path, or default it so start is always armed correctly.
- **`ui/shift/ShiftViewModel.kt`**: stop passing `skipQuiet = false` in `advance()`
  ([ShiftViewModel.kt:298](app/src/main/java/be/drakarah/intonation/ui/shift/ShiftViewModel.kt#L298));
  arm every prompt consistently.
- **`game/ShiftCaptureTest.kt`**: add a regression case — a legato/ringing stream (no silence gap)
  on a mid-round prompt must still confirm the start (currently would starve in AWAIT_QUIET).

## Workstream B — One detection pipeline for every game (no duplicated plumbing)

Today the detection flow is fragmented three ways: Note Accuracy inlines arm + discard-filter +
re-arm in its VM; `ArpeggioCapture` carries a **hand-copied** clone of that filter
([ArpeggioCapture.onFrozen](app/src/main/java/be/drakarah/intonation/game/ArpeggioCapture.kt#L82));
Shift has **no** filter at all. Sarah's directive: a **single** detection pipeline that every game
shares, with only genuinely game-specific behavior expressed as configuration — no copies.
DETECTION.md §4.2/§8 already anticipate this ("if the filter needs a third caller, extract it to one
shared pure function"); Shift is the third caller.

**What is genuinely common (→ the shared pipeline):** arm the capture (`skipQuietGate=true,
`requireOnsetRise=true`), run `AttemptCapture`, and when a pitch freezes apply the target-aware
discard filter (ring-over, too-soon, harmonic-artifact, unplayable, flimsy) and either **re-arm and
keep listening** or **surface the frozen candidate** — with the `MAX_DISCARDS` give-up bound. This
plumbing is identical for Note Accuracy prompts, Arpeggio tones, and the Shift start note.

**What is legitimately per-game (→ config/hooks, "if the game logic allows"):** the *accept-side*
decision after a candidate passes the filter — Note Accuracy scores it; Arpeggio enforces strict
order (wrong-root re-arms); Shift-start confirms it's within `startToleranceCents` (else re-arm).
Also which "previous pitch" ring-over compares against (previous *prompt* vs previous *tone* vs
previous *landing*) and whether too-soon applies (all pitches / root only / not at all). The Shift
*landing* is the one variant that arms `requireOnsetRise=false` + glide filter and applies only the
non-timing rules (harmonic/unplayable/flimsy) — expressible with the same config.

Design:
- **New `game/CaptureFilter.kt`** — one pure, Android-free function + `CaptureFilterConfig`
  (rule toggles + thresholds `wrongNoteMinLevel`/`lowestPlayableHz`/`minReadMs` + the universal
  `NON_OCTAVE_HARMONICS`/tolerance/near-target/ring-match constants). Input: frozen `CapturedPitch`,
  target Hz, previous-answer Hz, elapsed-since-prompt. Output: `Accept` / `Discard(reason)`. This is
  the single home of `isIntegerHarmonic` and the discard rules.
- **New `game/FilteredCapture.kt`** — a thin reusable state machine that owns the arm →
  `AttemptCapture` → `CaptureFilter` → re-arm loop (+ `MAX_DISCARDS`), emitting `Listening` /
  `Passed(CapturedPitch)` / `GaveUp`. Every game composes this; the accept-side rule stays in the
  caller. (If the accept side proves uniform enough, fold it in via a callback — but the loop itself
  is shared regardless.)
- **Delete both copies:** the discard logic inside
  [NoteAccuracyViewModel.onCaptured](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L276)
  and the entire private filter in `ArpeggioCapture` — both call the shared code instead.
- **Rewire callers:** `NoteAccuracyViewModel`, `ArpeggioCapture`, `ShiftCapture` (start + landing)
  all go through `FilteredCapture`/`CaptureFilter`. Note Accuracy behavior must stay
  **byte-identical** (locked by `FeedbackRegressionTest`, `NoiseRejectionTest`); Arpeggio's two
  specifics become config, not a copy (`ArpeggioCaptureTest` green unchanged). Shift landing gains
  the harmonic/flimsy leniency it currently lacks (a harmonic-artifact landing is re-armed, not
  mis-scored as a wrong note).
- Tests: add `CaptureFilterTest` (pure rules) and `FilteredCaptureTest` (the loop); existing
  regression suites must pass **without edits** — that's the proof the single pipeline preserves
  behavior.

## Workstream C — 3 difficulty levels

- **`game/Positions.kt`**: add `PromptSpec.finger(): Int` — offset within the position →
  `first→1, last→4, else→2` (every selectable position is exactly 3 semitones wide, so this is
  well-defined). Add a `ShiftLevel` enum (`BASIC`, `INTERMEDIATE`, `ADVANCED`) with id/label.
- **`game/ShiftPool.kt`**: replace `crossString: Boolean` with `level: ShiftLevel`:
  - `BASIC` — same string, different position, finger set == {1,4}. Prefer larger spreads; fall
    back to any {1,4} pair.
  - `INTERMEDIATE` — same string, different position, any fingers (current same-string behavior).
  - `ADVANCED` — different string, different position, any fingers (current cross behavior).
  Keep the anti-repeat draw and the `isEmpty` (needs ≥2 positions) contract — all three levels have
  material with any 2 positions.
- **UI/nav/persistence**: route param and daily-focus field `style → level`
  ([AppNav.kt:47](app/src/main/java/be/drakarah/intonation/ui/AppNav.kt#L47)); `configKey`
  `variant = level.id` ([ShiftViewModel.kt:327](app/src/main/java/be/drakarah/intonation/ui/shift/ShiftViewModel.kt#L327)) —
  each level is its own scoring category (old same/cross PBs simply don't collide with the new
  ones; no migration needed — and Sarah is fine wiping her 2–3 days of test data, so no
  back-compat for the old same/cross categories is required either).
  - **`ui/home/HomeScreen.kt` + `HomeViewModel.kt`**: replace the two cards
    ([HomeScreen.kt:299-316](app/src/main/java/be/drakarah/intonation/ui/home/HomeScreen.kt#L299))
    with one Shift Trainer entry + a 3-way level picker (chips), and replace
    `shiftBest`/`shiftCrossBest` with per-level bests. Keep the ≥2-positions gate.
  - Check `ui/progress/ProgressScreen.kt` and `DailyFocus` for `"same"/"cross"` literals and update
    to level ids.

## Workstream D — Blended scoring + start/landing coaching

- **`game/ShiftCapture.kt`**: add `confirmedStartHz` (already tracked internally) to `ShiftResult`
  so the VM can compute the interval actually travelled.
- **`game/ShiftCapture.kt` `scoreShift`**: take landing cents *and* start cents (or the ideal
  start Hz). Compute `shiftCents = landCents − startCents`, then
  `score = round(0.7·scoreAttempt(shiftCents) + 0.3·scoreAttempt(landCents))` + the existing 10%
  fast-landing bonus. Weights as named constants for easy tuning.
- **`ui/shift/ShiftViewModel.kt`**: compute `startCents` (confirmed start vs. ideal start),
  `landingCents` (vs. target, unchanged), `shiftCents`. `wrongNote` and drift stay on **landing**
  cents (what a listener hears). Persist `AttemptRecord.centsError = landing cents` (unchanged →
  **no DB migration**); the decomposition is live/in-memory only.
- **`ui/shift/ShiftAttemptUi`**: carry `shiftCents`, `startCents`, `landingCents`.
- **`ui/shift/ShiftScreen.kt`** `RevealContent`: headline the **shift-distance** error (the skill);
  add a coaching line, e.g. `started +20¢ · landed +18¢ · shift +2¢`, and when the start was
  notably off but the shift was tight, say so ("great shift — your start was 20¢ sharp"). Stars can
  key off the blended result. `DoneContent`/`ImprovementLine` keep using landing cents.

---

## Verification

1. **Unit/regression:** `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest` —
   `FeedbackRegressionTest`, `ArpeggioCaptureTest`, `ShiftCaptureTest`, `NoiseRejectionTest`,
   `ScoringTest` green; new `CaptureFilterTest` + the legato-start `ShiftCaptureTest` case pass.
2. **Build+install:** `.\gradlew.bat :app:assembleDebug` then
   `adb install -r app\build\outputs\apk\debug\app-debug.apk` (her Pixel 6a is connected now).
3. **Fresh event trace (Sarah):** Settings → Debug → "Record & trace games" ON, play a shift round
   (arco, legato — the case that failed), including the start-note-doesn't-register scenario. Pull
   the newest `game-trace-shift-*.jsonl` and confirm from the `event` lines that every prompt's
   start now confirms (no AWAIT_QUIET starvation), and use it to tune departure/glide only if the
   trace still shows a real miss.
4. **On-device check:** all 3 levels selectable and start with ≥2 positions; Basic only produces
   1↔4 shifts, Intermediate/Advanced include 2nd finger; reveal shows the start/shift/landing
   decomposition; a good shift off a slightly-off start now scores well.
5. **Docs:** update `FEATURES.md` (3 levels, blended scoring), `TESTING.md` (move these two items to
   Pending with today's date), and `docs/DETECTION.md` §4.2/§8 (filter extracted to the shared pure
   function; shift start now arms like Note Accuracy).
