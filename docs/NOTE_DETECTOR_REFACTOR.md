# Proposal: a single `NoteDetector` box (future refactor — not yet built)

Status: **proposal / not implemented.** Written 2026-07-15 after the shift-trace investigation.
Sarah's call: this deserves a dedicated, well-thought-out plan rather than being rushed in alongside
a bug-fix — "if we can't make a clean refactor then we're just adding more fabrication classes /
hoops to jump through." This doc records the reasoning so the future effort starts from it.

## Why this exists

The 2026-07-15 shift bugs (traces truncated; false "wrong note when it wasn't"; pizz landing froze on
the ringing start note's harmonic) all had **one root shape**: the Shift capture machine re-implemented
the detect loop *without* the reliability logic Note Accuracy had. Fixing it meant hand-patching Shift
to (a) apply the calibrated pizz timing and (b) run the shared `captureFilter` on its start and
landing — i.e. re-deriving, per game, something that should exist once.

Sarah's framing (correct): *"Why didn't we make a NoteDetection box you configure with parameters,
with an `onNoteDetected` event, and use it in every game?"*

## What is already shared vs. what is duplicated

Shared, and genuinely reusable (the "primitives"):
- `dsp/PitchGate` — per-window noise/harmonic gate + octave-up correction (Layer 1).
- `game/AttemptCapture` — params in (`attackSkip`, `stabilityWindow`, `skipQuietGate`,
  `requireOnsetRise`, `glide`) → `CaptureState.Frozen(CapturedPitch)` out (Layer 2). Already a box.
- `game/CaptureFilter.kt` — the pure target-aware discard filter (Layer 3). Already shared.
- `settings.CaptureParams.applying(settings, pizz)` — calibrated timing, one source of truth.

**Duplicated — and this is the problem:** the *loop* that ties them together —
`arm → freeze → captureFilter → re-arm on artifact (cap at MAX_DISCARDS) → emit the accepted note` —
is hand-rolled in **four** places:
1. `NoteAttemptCapture` (Note Accuracy),
2. `ArpeggioCapture` (Chords, once per tone),
3. `ShiftCapture` start confirmation,
4. `ShiftCapture` landing.

Each copy can (and did) drift: Shift's copies had neither the filter nor the calibrated timing until
2026-07-15. The current DETECTION.md §4.2 note that the loop is "deliberately not factored into a
shared wrapper" is the decision this proposal overturns — with four call sites it now pays off.

## Proposed shape

A `NoteDetector` domain box that OWNS the loop:

```
class NoteDetector(
    params: CaptureParams,                 // already calibrated via CaptureParams.applying
    filterConfig: CaptureFilterConfig,
    targetHz: Double,                      // reference for the filter (0 = no target filtering)
    previousAnswerHz: Float = 0f,          // ring-over source
    tooSoon: TooSoonPolicy = Disabled,     // NA: from prompt; chords: root only; shift: disabled
    skipQuietGate: Boolean, requireOnsetRise: Boolean,
    onDiscard: ((CapturedPitch, CaptureFilterResult) -> Unit)? = null,
) {
    fun process(sample): Detection   // Listening | Detected(CapturedPitch) | Nothing(timeout/gaveup)
}
```

It emits **accepted, non-artifact** notes only; games never see the loop. Games keep the parts that
are genuinely theirs:
- **Note Accuracy** = one `NoteDetector` + classify/octave-fold/score.
- **Chords** = a `NoteDetector` per tone + strict ascending order / wrong-root re-arm.
- **Shift** = a `NoteDetector` for the **start** + one for the **landing**, wrapped in the shift
  **orchestration** (random cue, departure detection, glide exclusion, return-to-start). The departure
  and glide are *motion* detection around the note-detects, not note events themselves — they stay in
  `ShiftCapture`.

## Two things the box deliberately does NOT swallow (pushback on "one box does everything")

1. **Sustain stays its own machine.** `SustainCapture` is *hold-in-tune over time*, not
   detect-a-note: no freeze, no discrete onset to filter. It rejects the same artifacts structurally
   (grace window + `holdBandCents`/`statsClampCents` exclude off-pitch glitches; median stats absorb a
   sharp attack). Forcing it to emit `onNoteDetected` ~40×/s and rebuild a hold on top is strictly
   worse. It is the documented exception, not a gap.
2. **Game orchestration is not "flags."** Shift's cue/departure/glide handshake and Chords' strict
   order are real per-game logic. The box unifies *detection*; games still *sequence* it.

## Why it needs a careful plan (not a quick extraction)

- The **accept-side differs** per game (classify+fold+score / strict-order+wrong-root /
  start-tolerance+quiet-rearm / landing+return-to-start). A naive shared loop with callbacks for all
  of these risks becoming callback-hell — the exact "more hoops to jump through" Sarah warned against.
  The extraction is only worth doing if the box's surface stays small and the games get *simpler*, not
  more indirected. If it can't be clean, don't do it.
- The safety net exists: `NoteAccuracy* / ArpeggioCapture* / ShiftCaptureTest` pin current behavior
  with synthetic streams, and the corpus replay tests pin real detection. The refactor should be
  **behavior-preserving** — green tests before and after, no threshold changes in the same pass.
- Consider whether `NoteAttemptCapture` simply *becomes* `NoteDetector` (it is already the closest
  thing: arm + filter + re-arm) with classification lifted out, rather than a new class beside it.

## Non-goals
- No threshold/tuning changes — pure restructure.
- Not touching Sustain.
- Not merging the shift motion-detection (departure/glide) into the box.

## Pointers
- Loop copies to unify: `game/NoteAttemptCapture.kt`, `game/ArpeggioCapture.kt`, `game/ShiftCapture.kt`.
- Shared units already in place: `game/AttemptCapture.kt`, `game/CaptureFilter.kt`,
  `settings/SettingsRepository.kt` (`CaptureParams.applying`).
- Background: `docs/DETECTION.md` §2–§4, `docs/architecture.md` §2–§3.
