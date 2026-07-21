# Plan — Chords (Arpeggio) game

## Context

FEATURES.md lists "chord-progression and walking-bass-line exercises (hand movement across
the fingerboard)" as post-v1. The double bass is **monophonic** — you can't sound a triad —
so a "chords game" must be an **arpeggio**: the app names a chord, the player plays its tones
one at a time, and each tone is frozen and scored. The pedagogical value is real and distinct
from Note Accuracy: it trains whether your **3rd and 5th are in tune relative to the root**,
and the string-crossing/hand-shape of the chord across the neck.

**Decisions locked with Sarah (2026-07-12):**
- **Shape:** ascending triad arpeggio — root → 3rd → 5th, one note at a time.
- **Qualities:** major and minor triads only (both in the pool; the major-3rd vs minor-3rd
  ear is the point).
- **Neck model:** a chord appears only if **all three tones are reachable within her selected
  positions** (½/1st/2nd… chips) plus open strings. Fits the existing position-scoring model.
- **Order:** strict ascending; a wrong first note (root) re-arms like Shift's "that's not it".
- **Asserted (detection-driven, tell Sarah):** each tone is a **separate attack** (fresh bow
  stroke / pluck), not a let-ring roll. The detector is monophonic; `requireOnsetRise` segments
  clean attacks reliably, whereas overlapping ringing strings wreck capture.

This mirrors the existing game pattern almost entirely. The **only genuinely new logic** is a
multi-tone sequence capture (`ArpeggioCapture`, modeled on `ShiftCapture`) and a chord pool
with reachability/fingering (`ChordPool`). Everything else — scoring, persistence, PBs,
achievements, home card, nav, trace — reuses current plumbing.

## Design

### A chord prompt = ordered list of `PromptSpec`
A `ChordSpec` is `root: NoteSpec`, `quality`, and `tones: List<PromptSpec>` (3 tones ascending,
each with a concrete `string` + `position` placement — reuses the existing prompt display so
each tone shows note + string + position exactly like Note Accuracy).

### Reachability & fingering (`game/ChordPool.kt`) — the crux
`promptsFor(positions)` gives every **fingered** placement in the selected positions but
**deliberately excludes open strings** ([Positions.kt:57-64](app/src/main/java/be/drakarah/intonation/game/Positions.kt#L57-L64)).
For chords an open string is a legitimate tone, so `ChordPool` builds its own placement map:

- **Reachable placements** = `promptsFor(positions)` (fingered) **∪** open-string placements
  (`PromptSpec(target=open, string=open, position=OPEN_STRINGS)`, built directly since
  `promptsOf` skips them). Map: tone MIDI → list of placements.
- **Candidate roots** = fingered notes in the selected positions (roots stay fingered so the
  chord "belongs" to a position for balancing). Balanced **round-robin across positions**,
  reusing NotePool's deck idea ([NotePool.kt](app/src/main/java/be/drakarah/intonation/game/NotePool.kt)).
- For each root × quality (major `+0,+4,+7`; minor `+0,+3,+7`): compute the 3 tone MIDIs; each
  must have ≥1 reachable placement, else the chord is dropped. Pick one **canonical fingering**
  per tone with a simple heuristic (prefer the placement on the string nearest the root's
  string, then lowest offset; open string allowed). Heuristic is tunable — expect Sarah to
  refine fingerings after playing (TESTING.md item), but any *playable* fingering is correct.
- `isEmpty` guard (mirror [ShiftPool.kt:50-51](app/src/main/java/be/drakarah/intonation/game/ShiftPool.kt#L50-L51)):
  when only one sparse position is selected, few/no triads fit → home card disabled with an
  explanation, exactly like Shift needing ≥2 positions.
- `draw(count)` round-robins roots across positions, expands to available qualities, shuffles,
  avoids an immediate repeat root (mirror `ShiftPool.draw`).

### Capture (`game/ArpeggioCapture.kt`) — modeled on `ShiftCapture`
A pure, unit-testable state machine (no Android/settings deps — thresholds passed as params),
composing one `AttemptCapture` per tone, exactly as `ShiftCapture` composes its start/landing
captures ([ShiftCapture.kt:84-103](app/src/main/java/be/drakarah/intonation/game/ShiftCapture.kt#L84-L103)).

- Ctor: `ArpeggioCapture(targetsHz: List<Double>, captureParams, thresholds…, skipQuietGate)`.
- States: `Capturing(toneIndex)` → terminal `Finished(tones: List<ToneResult>)`
  (`ToneResult` = frozen Hz / quality / timed-out per tone).
- Per tone: arm `AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)`
  — same arming as Note Accuracy ([RoundViewModel.kt:216](app/src/main/java/be/drakarah/intonation/ui/round/RoundViewModel.kt#L216)):
  don't wait for silence (string is already sounding mid-arpeggio) but require a genuine attack.
- On `Frozen`, run the **ring-over/artifact discard filter** ported from
  `RoundViewModel.onCaptured` ([RoundViewModel.kt:233-267](app/src/main/java/be/drakarah/intonation/ui/round/RoundViewModel.kt#L233-L267)):
  discard-and-keep-listening when the frozen pitch is **ring-over of the previous tone**
  (the #1 risk here — the tone she just played is still sounding), a harmonic artifact,
  unplayable, or flimsy. This is the single most important reuse: the "instant wrong note"
  machinery from DETECTION.md was built for exactly this.
- Wrong-first-note (root): re-arm and expose a `wrongRoot` flag → UI "that's not it / listening
  for {root}", mirroring `ShiftState.ConfirmStart(wrongNote)`.
- Genuinely-held wrong 3rd/5th: **scored as a miss and advance** (don't get stuck) — only
  artifacts are discarded.
- `minReadMs` (player-level "too soon" guard) applies to **tone 0 only**; tones 1–2 follow
  immediately mid-arpeggio.
- To avoid duplicating the filter, factor the discard predicate into a small pure helper both
  `ArpeggioCapture` and (optionally, later) `RoundViewModel` can call — but **do not refactor
  RoundViewModel in this change** (DETECTION.md guards it); ship the helper used by arpeggio
  only, note the future consolidation.

### Scoring
Reuse `scoreAttempt(cents, difficulty)` + `stars(cents)` per tone
([Scoring.kt](app/src/main/java/be/drakarah/intonation/game/Scoring.kt)). Chord score = sum of
its 3 tone scores (max 300). Round `maxScore = roundLength * 300`. No new scoring math.

### ViewModel + Screen (`ui/chords/`)
Mirror `ShiftViewModel`/`ShiftScreen` verbatim (the multi-step-per-prompt template):
- `EXERCISE_CHORDS = "CHORDS"`; `ChordsAttemptUi`, `ChordsPhase`
  (`CountIn → Playing(toneIndex, wrongRoot) → Reveal → Done`), `ChordsUiState`, `Factory`.
- `start()`: read settings, `CaptureParams.pizz()/arco()` by `mode`, apply `PlayerLevel`
  timeouts, `prompts = ChordPool(positions).draw(roundLength)`, count-in, then
  `engine.samples().collect { … arpeggio.process(sample) }` mapping states to phases.
- `GameTrace` tag `"chords-$mode"` (reused wholesale); `GameSounds` per-tone feedback.
- `persistRound`: **one `AttemptEntity` per tone** (targetMidi = tone, `startMidi` = chord root
  to group, string/position = that tone's placement) — no schema change; gives rich per-tone
  data for future insights. Note: this makes note-count achievements count individual tones
  (3× per chord) — that is accurate (they *are* notes played); flag for Sarah, easy to switch
  to one aggregate row per chord if she prefers "chords played" semantics.
- `configKey(EXERCISE_CHORDS, mode, difficulty, roundLength, positions, variant = null)` — one
  scoring category (major+minor mixed). PBs / streak / ImprovementLine / RoundOutcome all work
  for free once `persistRound` calls `sessionRepository.recordCompletedRound`.

### Wiring
- [ui/AppNav.kt](app/src/main/java/be/drakarah/intonation/ui/AppNav.kt): `Routes.CHORDS =
  "chords/{mode}"` + builder + `composable { ChordsScreen(onExit = …) }` + `onStartChords`.
- [ui/home/](app/src/main/java/be/drakarah/intonation/ui/home/): a Practice `ExerciseCard`,
  `chordsBest = bestFor(EXERCISE_CHORDS)`, enabled-gate on `ChordPool(positions).isEmpty`
  (with an explanation like Shift's), arco **and** pizz both allowed. Optional `FOCUS_ROTATION`
  entry for daily focus.
- Optional [Achievements.kt](app/src/main/java/be/drakarah/intonation/game/Achievements.kt):
  1–2 chord defs guarded on `exerciseType == "CHORDS"` (e.g. "Perfect arpeggio" = all 3 tones
  3★; "Chord master" = perfect chords round).
- **No changes** to `AppContainer`, Room schema, `GameSounds`, `GameTrace`, `configKey`,
  `recordCompletedRound`.

### Explicitly out of scope (backlog / future variants)
- Chord progressions (I–IV–V) and up-and-down arpeggios — the ascending single-triad shape is
  the v-first primitive; both are natural `variant`s later.
- Diminished / dominant-7th qualities.
- **Just-intonation mode** (score the 3rd against 5:4 and 5th against 3:2 from the root instead
  of 12-TET) — genuinely compelling for a chord trainer and worth flagging to Sarah; v1 stays
  12-TET for consistency with every other game.

## Files

**New (pure `game/`, unit-tested):**
- `game/ChordPool.kt` — `ChordSpec`, `ChordQuality`, reachability + fingering + balanced draw.
- `game/ArpeggioCapture.kt` — sequence machine + ported discard filter; chord scoring helper.

**New (`ui/chords/`):**
- `ui/chords/ChordsViewModel.kt`, `ui/chords/ChordsScreen.kt`.

**Touched:**
- `ui/AppNav.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`.
- `game/Achievements.kt` (optional).
- `FEATURES.md` (move chords out of Planned into Exercises), `TESTING.md` (Pending items),
  `docs/DETECTION.md` (note arpeggio ring-over reuse + provisional thresholds).

## Verification

- **Unit tests** (the superpower — synthetic `PitchSample` scripts, mirror existing `*Test.kt`):
  - `ChordPoolTest`: only fully-reachable chords appear; open strings allowed as tones; roots
    balanced round-robin across positions; `isEmpty` true for a single sparse position;
    major & minor both present.
  - `ArpeggioCaptureTest`: clean ascending arpeggio freezes 3 tones in order; **ring-over of the
    previous tone is discarded** (keeps listening); wrong root re-arms; genuinely-wrong 3rd is
    scored and advances; per-tone timeout → `Finished` with timed-out tones.
- **Build + tests:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then
  `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`.
- **On-device (Sarah, arco + pizz):** install, select ½/1st/2nd, play through a chords round;
  confirm each tone captures once (no ring-over false notes), the arpeggio advances in order,
  wrong root re-arms, and the summary/PB/achievements render. Add TESTING.md Pending items;
  verify fingering choices feel natural (expect a tuning pass on the fingering heuristic).
- **Trace-driven threshold tuning:** turn on Settings→Debug "Record & trace games", play a real
  chords round, pull the `chords-*.wav/.jsonl`, replay offline to tune the ring-over/discard
  thresholds against real inter-tone audio — provisional until a real trace confirms them
  (same discipline as the current games; note in DETECTION.md).
