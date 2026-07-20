# Second detection pipeline — per-rig template matching (research plan)

**Status: research sidestep, NOT committed.** This plan captures a direction to *explore later*; nothing
here ships or touches the current pipeline until a spike proves it worthwhile. Written 2026-07-20 after
the per-window octave discriminators (`acf2x`, `ampF2/ampF`) were refuted on real data — see
DETECTION.md §12.6 / §12.6.1.

## 1. The idea in one line
Run a second, per-rig **template-matching** detector *alongside* the existing pitch pipeline to
**resolve/verify octave (and eventually note) decisions in realtime**, using an instrument profile built
during calibration. MIDI export of a clean segmented note stream is a downstream bonus, not the goal.

## 2. Why (the problem the current pipeline can't solve)
The octave errors (§12: fingered E2→E1; §12.6: Si1→B2; §12.6.1: Fa1→F2) are a **polyphonic signal forced
through a monophonic estimator**: when you finger E2 and the open E1 rings sympathetically, two pitches
really are sounding, and the single-pitch estimator sometimes picks the wrong octave. Every per-window
fix we tried (odd-harmonic proof; `acf2x`; `ampF2/ampF`) makes a **binary** decision from one feature and
is defeated by the sympathetic sub-octave energy — worsened by the mic roll-off, which weakens a real low
fundamental *more* than a ringing open-string sub-octave (§12.6.1). This is the wall.

## 3. Why this is NOT the general polyphonic problem (Sarah's key point)
This is single-source **template matching against a known profile**, not blind source separation:
- **Tiny known dictionary** — ~26–40 pitches (12-TET across her range) + 4 fixed open-string resonance
  signatures. Not a continuous unknown space.
- **One instrument, one rig** — templates are measured through *her* mic in calibration, so they bake in
  the roll-off and body/mic coloration. The very rig-specific coloration that *corrupts* the monophonic
  estimator becomes the *matching key* here.
- **Strong physics priors** — usually one played note at a time, plus a few *weak* open-string
  activations at *fixed* pitches; a well-conditioned, tiny fit.
- **Onset segmentation already exists** — Pipeline 1's attack detection can gate/segment, so the new
  module can classify per-onset rather than blindly per-frame.

**Crux — decomposition beats a binary test.** The old rule asked "is there energy at 1.5×f? → halve," and
sympathetic ring fooled it. A decomposition instead *explains* the frame: played E2 + ringing open E1 →
a **strong** E2-template activation (accounts for 82/164 Hz) + a **weak** E1-template activation (accounts
for residual 41/123 Hz). The **ratio** of activations is the answer. That relative-strength readout is
strictly more information than any single-peak test — which is why it can work where §12 failed.

## 4. The instrument profile (built in calibration)
Per-rig, stored in `AppSettings` alongside the existing calibration outputs:
- **Per-note harmonic template** — relative partial amplitudes (f, 2f, 3f, …) as *her mic* hears them,
  per playable note, **separately for arco and pizz** (combs differ).
- **Open-string resonance signatures** — the 4 open strings' combs, so their sympathetic energy can be
  "explained away" into a separate activation instead of corrupting the played note.
- **Roll-off curve** — to interpolate templates for notes not directly played in calibration.

**We already record most of this:** calibration captures open strings (arco + pizz), stopped notes, Do3,
the high note, and estimates the roll-off knee. Extension = store the relative comb per recorded note +
derive the roll-off curve for interpolation. (See `calibration/CalibrationAnalysis`, `WizardViewModel`.)

## 5. Candidate methods (accuracy spike vs realtime deployability)
The spike judges **accuracy** offline in Python; the **product** target is realtime Kotlin, so weight by
whether a method can run per-frame (~23 ms) on-device.

| Method | Polyphonic | Octave-robust | Per-rig | Realtime on Android | License | How to spike |
|---|---|---|---|---|---|---|
| **A. pYIN + note HMM** | ✗ mono | ✓✓ (temporal HMM) | partial | ✓ (fixed-lag Viterbi) | GPL | Python baseline: `librosa.pyin()` |
| **B. Supervised NMF / template NNLS** | ✓ | ✓✓✓ (full comb + decomposition) | ✓✓ (learn from calib) | ✓ (small matrix solve/frame) | our code | **Kotlin, dsp test** (reuses real FFT — §8) |
| **C. CREPE** | ✗ mono | ✓✓✓ | ✗ | ~ (TFLite, heavier) | MIT | Python baseline: `crepe` |
| **D. Basic Pitch** | ✓✓ | ✓✓ | ✗ | ✗–~ (batch-oriented NN) | Apache-2.0 | Python baseline: `basic-pitch` → MIDI |

Avoid MELODIA (Salamon) — patent-encumbered, incompatible with Play-store plans.

**Working favourite: B (template NNLS).** Best philosophical fit (per-rig, inspectable, no model files),
octave-robust *by construction* (matches the whole comb + decomposes the resonance into its own
activation), and **realtime-friendly** (a non-negative least-squares over a ~30-column dictionary per
frame is cheap). A and C are the strong monophonic baselines to beat; D is the batteries-included polyphonic
fallback if classical underperforms.

## 6. Realtime integration sketch (if it graduates)
It **augments**, not replaces, the current pipeline. `PitchGate` already computes the per-frame FFT
(`FrequencyDetectionCollectedResults.frequencySpectrum`) — the template module would consume that same
spectrum (no new audio front-end), and either:
- **(verify mode)** confirm/deny PitchGate's pitch and correct only the octave (lowest-risk augmentation,
  directly targets §12), or
- **(propose mode)** emit its own note estimate that a fusion step reconciles with PitchGate.

Latency budget: one frame (~23 ms) for the NNLS; the note-HMM (if used) adds a small fixed lag. Runs on
the same Default-dispatcher coroutine as detection.

## 7. Evaluation — reuse the corpus we already have (no new offline recording)
- **Profile source:** the calibration traces from the 2026-07-20 full calibration
  (`calibration-*-*.jsonl/.wav` in the snippets dir / `.trace-incoming`).
- **Acid-test set (known octave-ups):**
  - `capture-20260720-123048` — Fa1↔Do2; every 82–88 Hz read is a *known* Fa1 octave-up (§12.6.1).
  - `game-trace-shift-basic-pizz-20260719-212817` — the Si1→B2 shift landing (§12.6).
  - `shift-pizz-octavedown-20260719` (corpus clip) — fingered Mi2/Sol2 that must NOT be halved (§12 false-down).
- **Genuine-note controls:** the scale captures `122425` (slow) / `122438` (fast), `081805`, `074413`.
- **Metric:** per known octave-up, does the method report the true octave? Per genuine note (incl. La2 with
  open-A resonance), does it leave the octave alone? Target: resolve the Fa1→F2 / Si1→B2 cases that the
  per-window features could not, with zero false halving on the §12 Mi2/Sol2.

## 8. The spike (later — offline evaluation, primary path in Kotlin)
**Do Method B in Kotlin, in the dsp test harness — not Python.** Reason: B must consume the *same*
per-frame FFT the product uses (the collector's `frequencySpectrum`). A Python spike would use librosa's
different FFT/windowing, so a positive result wouldn't transfer cleanly — whereas a Kotlin spike on the
real spectrum has **zero translation gap** (if it works in the test, it works in the pipeline) and reuses
`WavFile` + the collector + the calibration WAVs directly. NNLS/NMF is ~15–40 lines (multiplicative
updates, or Lawson-Hanson) — no external solver needed. (Python is only *easier* for the generic
baselines below, which don't use the profile anyway; and Python still can't reproduce the *current* Kotlin
detector — DETECTION.md §11.0 — though that doesn't matter for a brand-new algorithm.)

Primary spike (Kotlin, dsp test, offline replay — evaluation only, not realtime yet):
1. Build per-note templates from the calibration WAVs: run each take through the collector, take the
   median normalized magnitude spectrum → one dictionary column per note (arco + pizz).
2. Per analysis frame of an acid-test WAV, solve non-negative activations over the dictionary
   {playable notes} ∪ {open-string resonances}; read the dominant activation as the note, the
   open-string activations as explained-away resonance.
3. Score against §7 — especially Fa1→F2 (`123048`) and Si1→B2 (`…212817`), with zero §12 Mi2/Sol2 false
   halving.

Optional baselines (Python one-liners, only if B underwhelms — checks whether a fundamentally different
approach wins): `librosa.pyin` (temporal HMM), `crepe` (NN mono), `basic-pitch` (NN polyphonic → MIDI).

**Decision gate:** if B cleanly resolves Fa1→F2 and Si1→B2 with no §12 false-halving, it's worth a
realtime Kotlin build (§6); if nothing separates the bottom-octave cases, shelve it and keep
`ignoreWrongOctave` ON as the accepted mitigation.

## 9. Risks / open questions
- **Bottom-octave roll-off degeneracy** — when a note's true fundamental (≈41 Hz) is below the mic's
  usable floor, E1 vs E2 templates differ mainly in the 123-Hz partial, which resonance muddies. The
  decomposition should still beat the binary rule, but this is the residual hard case; the spike must
  probe it explicitly (it's exactly the Fa1/E1 region in `123048`).
- **Template variation** with dynamics / vibrato / bow position / pluck point — combs aren't perfectly
  constant. Mitigate with amplitude normalization, relative-comb matching, maybe 2 templates/note.
- **Same pitch, different string** — timbre differs; may need per-(string,position) templates or accept
  per-pitch.
- **Template interpolation** for un-calibrated notes — physical harmonic model scaled by the measured
  roll-off curve, or interpolate between measured anchors.
- **Realtime cost** on the low end of devices — profile the NNLS; cap dictionary to the plausible
  range per prompt if needed.

## 10. What this is NOT (scope guard)
Not a general-purpose transcriber, not multi-instrument separation, not a replacement for Pipeline 1, and
not something that ships without the §8 decision gate passing. The current pipeline stays as-is and
`ignoreWrongOctave` stays ON until/unless this proves out.
