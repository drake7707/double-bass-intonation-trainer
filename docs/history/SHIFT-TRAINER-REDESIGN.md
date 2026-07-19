# Shift Trainer redesign — levels, blended scoring, unified detection

Status: **implemented 2026-07-15, awaiting hands-on verification on the bass** (see TESTING.md).
Tracks Sarah's two Shift-Trainer feedback items and the detection unification they surfaced. See
also `docs/DETECTION.md` (the capture/detection reference) and `FEATURES.md`.

**What shipped:** A — shift start now arms like Note Accuracy (fixes "start didn't register"),
wrong-start re-arm stays permissive for legato correction. B — one shared discard filter
(`game/CaptureFilter.kt`) + the Note Accuracy pipeline moved into the domain
(`game/NoteAttemptCapture.kt`); ViewModel is a thin adapter; both old copies deleted; new
`CaptureFilterTest` + `NoteAttemptCaptureTest` are the coverage that was missing. C — 3 levels
(`ShiftLevel`, `PromptSpec.finger()`, `ShiftPool` + Home/nav wiring). D — blended scoring
(0.7·shift-distance + 0.3·landing) with start/landing coaching in the reveal.

**Decisions taken during implementation:**
- **Shared loop (`FilteredCapture`): declined.** The filter is the duplicated part and is now
  unified; the per-machine arm→filter→re-arm loop has genuinely different accept-side logic (score /
  strict-order+wrong-root / start-tolerance), so a shared wrapper would leak more via callbacks than
  it saves. Documented in DETECTION.md §4.2.
- **`captureFilter` on the shift *landing*: deferred.** The landing has no filter today (so no
  duplication to remove), and adding harmonic/flimsy leniency there isn't a reported problem; it's
  optional future work, gated on a real shift event-trace.
- **Coverage audit finding:** the Note Accuracy filter had *no* direct test (it lived in the UI;
  `FeedbackRegressionTest` re-implements the rules rather than calling them). The domain move is what
  made it testable — that's why B is safe.

## Why

Two feedback items from real gameplay:

1. **Difficulty levels.** Same-string shifts always jump finger 1 ↔ finger 4 (the pool prefers
   spread ≥ 3 semitones), so she never trains her 2nd finger or smaller shifts. She wants a 3-level
   ladder from basic to hardest — and likes that the 2nd finger becomes an *optional* added
   difficulty rather than a surprise.
2. **Scoring + detection.**
   - Scoring only compares the landing to the target. "Start 20¢ off, land 20¢ off" is a *good shift
     with a bad start*, yet scores as a 20¢ miss. The shift *distance* should be credited and the
     start error surfaced in coaching.
   - **The start note of a shift often didn't register** — not a timeout; the note simply never
     captured. It does **not** happen in Note Accuracy. She asked whether the two games share a
     detection flow.

### Root cause of "start note didn't register" (confirmed from code)

Note Accuracy arms every prompt `skipQuietGate=true, requireOnsetRise=true`
(`NoteAccuracyViewModel`, the DETECTION.md §3 legato fix). The Shift trainer, on every prompt after
the first, calls `newCapture(..., skipQuiet = false)` (`ShiftViewModel.advance`), which lands the
start `AttemptCapture` in `AWAIT_QUIET` (because `requireOnsetRise` defaults to `!skipQuietGate`).
Under legato bowing or a ringing string the room never goes quiet, so the start never onsets. This
is exactly the §3.1 bug that was fixed for Note Accuracy but never carried to Shift — the two games
did **not** share the fixed flow.

## Decisions (settled with Sarah)

- **Levels** (replace the same/cross split; each level is its own scoring category):
  - **Basic** — same string, finger 1 ↔ 4 only.
  - **Intermediate** — same string, any fingers (adds 2nd finger + smaller shifts).
  - **Advanced** — cross string, any fingers.
- **Scoring** — blend `0.7 × shift-distance accuracy + 0.3 × landing accuracy`, results page
  decomposes start vs. shift vs. landing.
- **Detection** — one shared pipeline for every game, no duplicated plumbing (her directive). She
  can wipe her 2–3 days of test data, so no back-compat for old scoring categories is needed.

## Workstreams

### A — Fix the start-note capture (core bug)
Arm the shift start like Note Accuracy/Arpeggio: `skipQuietGate=true, requireOnsetRise=true` on
**every** prompt (Listening immediately, but only a real attack onsets — no AWAIT_QUIET starvation,
no ring-over grab). Delivered through the shared pipeline (B); can also ship as a minimal standalone
fix first. Files: `game/ShiftCapture.kt`, `ui/shift/ShiftViewModel.kt`; regression case in
`game/ShiftCaptureTest.kt` (a legato/ringing mid-round prompt must still confirm the start).

### B — One detection pipeline, living in the domain (⚠ regression-prone — stage carefully)
Today: Note Accuracy inlines arm + classification + filter + re-arm **in its ViewModel**;
`ArpeggioCapture` (already domain) carries a **copied** clone of the filter; Shift has none. Two
directives from Sarah: (1) a single shared pipeline, per-game behavior only as config; (2) the
pipeline must live **entirely in the domain** (`game/`), not the UI — same move as the metrics
refactor. The ViewModels become thin adapters.

**Coverage reality (audited 2026-07-15) — why the domain move is also the safety net.** The
NoteAccuracy filter (`onCaptured`) has **no direct test**: it can't be unit-tested while it lives in
an Android ViewModel. `FeedbackRegressionTest` *re-implements* the rules inline and asserts against
real snippets, so it guards thresholds + recordings, **not** the production code path — it would pass
regardless of how `onCaptured` is refactored. `NoiseRejectionTest` covers only `AttemptCapture`.
`ArpeggioCaptureTest` covers orchestration but not the harmonic/unplayable/flimsy rules. So existing
coverage is **insufficient** to refactor the NoteAccuracy path safely. Moving the pipeline into the
domain is what makes it testable; the new domain tests are the net that lets B proceed.

Pieces:
- **`game/CaptureFilter.kt`** (new) — the pure, Android-free discard filter + `CaptureFilterConfig`.
  Rules: ring-over, too-soon, harmonic-artifact, unplayable, flimsy. Sole home of `isIntegerHarmonic`
  and the universal constants (`NON_OCTAVE_HARMONICS`, tolerances, `NEAR_TARGET_CENTS`,
  `RING_MATCH_CENTS`, `OCTAVE_TOLERANCE_CENTS`, `MAX_DISCARDS`). Returns the individual flags so
  callers can log the trace exactly as before.
- **`game/NoteAttemptCapture.kt`** (new) — the NoteAccuracy capture pipeline as a domain state
  machine (mirrors `ArpeggioCapture`/`ShiftCapture`): owns the target-aware classification (the
  `resultFor` octave-fold + wrongNote/wrongOctave/score/stars), the discard filter + re-arm loop, and
  cross-prompt ring-over state. Emits a domain `NoteAttempt` result; no Android imports.
- **`game/FilteredCapture.kt`** (optional) — shared arm → `AttemptCapture` → filter → re-arm loop, if
  it removes real duplication across the three machines without contorting their accept-side rules.
- **Delete both copies**: `NoteAccuracyViewModel.onCaptured`/`resultFor` filter+classification and the
  private filter in `ArpeggioCapture` — both use the shared domain code.
- **Per-game config** ("if the game logic allows"): which previous pitch ring-over compares against
  (prompt vs tone vs landing); whether too-soon applies (all / root-only / never); Shift landing arms
  `requireOnsetRise=false` + glide and runs only harmonic/unplayable/flimsy.

**Staging (each step gated on green tests; existing suites must pass WITHOUT edits):**
1. `captureFilter()` + `CaptureFilterTest` (independent hand-computed expectations, all 5 rules +
   boundaries + OR-combination) — the foundational guard.
2. Rewire `ArpeggioCapture` → `ArpeggioCaptureTest` + `CaptureFilterTest` green.
3. Extract `NoteAttemptCapture` + `NoteAttemptCaptureTest` (classification + discard + octave-fold +
   ring-over + too-soon) — the guard that's missing today.
4. Migrate `NoteAccuracyViewModel` to a thin adapter → `FeedbackRegressionTest` +
   `NoiseRejectionTest` green.
5. Route Shift start + landing through the domain pipeline.

Corpus/traces per `docs/DETECTION.md` §9.

### C — 3 difficulty levels
- `game/Positions.kt` — `PromptSpec.finger()` (offset within position → `first→1, last→4, else→2`;
  every selectable position is exactly 3 semitones wide) and a `ShiftLevel` enum.
- `game/ShiftPool.kt` — replace `crossString: Boolean` with `level: ShiftLevel`; Basic filters to
  finger set {1,4} same-string (prefer larger spreads, fall back to any {1,4}), Intermediate =
  same-string any fingers, Advanced = cross-string any fingers. Keep anti-repeat + ≥2-positions
  contract.
- Wiring — `AppNav` route param `style → level`; `configKey` `variant = level.id`; `HomeScreen` +
  `HomeViewModel` replace two cards with one entry + a 3-way level picker and per-level bests;
  `DailyFocus.style` and `ProgressScreen` literals updated.

### D — Blended scoring + coaching
- `game/ShiftCapture.kt` — add `confirmedStartHz` to `ShiftResult`; `scoreShift` blends
  `0.7·scoreAttempt(shiftCents) + 0.3·scoreAttempt(landCents)` (+ existing fast-landing bonus),
  where `shiftCents = landCents − startCents`. Weights as named constants.
- `ui/shift/ShiftViewModel.kt` — compute `startCents`/`landingCents`/`shiftCents`; `wrongNote` and
  drift stay on landing cents; persist `AttemptRecord.centsError = landing` (no DB migration).
- `ui/shift/ShiftScreen.kt` — reveal headlines the shift-distance error plus a
  `started · landed · shift` decomposition line; call out "great shift, bad start" cases.

## Verification
- `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest` — all existing suites green
  (unedited), plus new `CaptureFilterTest`, `FilteredCaptureTest`, and the legato-start
  `ShiftCaptureTest` case.
- `.\gradlew.bat :app:assembleDebug` + install on the Pixel 6a.
- Fresh **event** trace: Settings → Debug → "Record & trace games", play a legato shift round;
  confirm every start now registers.
- On device: 3 levels selectable with ≥2 positions; Basic only 1↔4; reveal shows the decomposition;
  a good shift off a slightly-off start scores well.
