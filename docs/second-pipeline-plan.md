# Second detection pipeline — per-rig template matching (research plan)

**Status: EXPLORED and SHELVED (2026-07-22). Negative result — see §11.** The rule-3 template
octave-verifier described below was built and evaluated against a fresh chromatic corpus and does not
work: a genuine open-string-resonant note is spectrally identical to a true octave-up, so a
target-agnostic per-window discriminator cannot separate them (full write-up: §11 + DETECTION.md
§12.6.2). Nothing shipped; `ignoreWrongOctave` stays the mitigation. The design below (§1–§10) is kept
as the record of what was tried. Originally written 2026-07-20 after the per-window discriminators
(`acf2x`, `ampF2/ampF`) were refuted (DETECTION.md §12.6 / §12.6.1).

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

## 6. Realtime integration — rule 3 in `PitchGate.correctOctaveUp` (the concrete design)
It **augments**, not replaces, the current pipeline — it slots in as a **third octave-correction rule**
inside `PitchGate.correctOctaveUp`, next to rule 1 (odd-harmonic proof) and rule 2 (decay-continuation).
Not a parallel pipeline.

### 6.1 Why here (the insertion point matters)
`PitchGate.evaluate()` per window does: `frequency = correctOctaveUp(results, frequency, …)` **then**
`smoothed = smoother(frequency)`. Two consequences make this the right seam:
- **PitchGate already holds the spectrum** (`results.frequencySpectrum`) — rule 3 reuses it: no second
  FFT, no second audio path. This is what makes it "an additional discriminator," not a second pipeline.
- **It runs BEFORE the smoother.** The §12.6 Si1/Fa1 bug was the *smoother locking the 2nd harmonic*
  because the per-window reads were octave-up. Correcting each window's octave *before* smoothing fixes
  the root cause; a post-freeze verifier (Layer 3) would only patch the final value, not the smoother
  lock. Rule 3 is also target-agnostic (best-matching template, no prompt needed) — PitchGate's contract.

### 6.2 What it computes (focused candidates, not the full dictionary)
For the discriminator role we don't identify the note — we resolve the octave of the pitch PitchGate
already estimated. Decompose the frame over just the **candidate octaves in playable range** — `{f/2, f}`
(add `2f` to also catch octave-down misreads) — and read the activation ratio:

```
rule3(results, f):
  if f/2 < lowestPlayableHz || f/2 > missingFundamentalMaxHz: return f   // same guards as rules 1/2
  (a_lo, a_hi) = nnls(frameSpectrum, [template(f/2), template(f)])       // ~2 cols × ~90 bins, ~80 iters
  if a_lo / a_hi > octaveRatioThreshold: return f/2                      // decisive → halve
  return f                                                               // else keep PitchGate's read
```

A 2-column NNLS over ~90 bins is a few thousand flops per window — realtime-trivial, on the existing
detection coroutine, and only invoked when an octave decision is actually in range (else skipped). Two
candidates (not 23) is cheaper *and* less ambiguous, and the 2-source fit is what **decomposes a genuine
note + ringing open string** (the polyphonic win) rather than a binary test.

### 6.3 Conservative by design (the safety property)
Override PitchGate only when the ratio is **decisive**; otherwise leave its estimate. This routes cases
correctly on their own confidence: Fa1→F2 (spike 93% low) and La2-with-open-A-ring (72% vs 2%) are
lopsided → corrected/left right; the shaky roll-off case §12 Mi2 (51/27) is near-1 → **rule 3 declines,
falls back to today's behaviour** (and `ignoreWrongOctave` still covers it in-game). So it can only help
where confident and is a no-op where not — the right risk posture for touching detection.

### 6.4 Pizz-only initially; arco unchanged (Sarah, 2026-07-20)
Rule 3 is validated on **pizz only** (that's all the spike tested, and pizz robustness was the original
goal). It maps straight onto the existing `pizz` flag (`settings.applying(config, pizz)`):

| mode | rule 1 odd-harmonic | rule 2 decay | rule 3 template |
|---|---|---|---|
| **arco** | ON (unchanged) | ON | **OFF** (untested — future work) |
| **pizz** | OFF (§12, false-fires) | ON | **ON (new)** |

This is clean: §12 disabled rule 1 for pizz because it false-fired on sympathetic resonance, leaving pizz
with only rule 2 (which misses onset-octave-ups — §12.6). **Rule 3 is the pizz-appropriate stateless
octave proof that fills exactly that gap.** Arco keeps rule 1 (it genuinely needs it for the bowed open-A
missing-fundamental case, §12.3) and leaves rule 3 off. Extending rule 3 to arco is separate future work:
it needs arco calibration templates + an arco octave-error test set, and would only be worth it if it lets
rule 1 retire (it won't be *needed* for arco as long as rule 1 works).

### 6.5 Calibration ownership (your rule)
Everything device/instrument-specific comes from the wizard, stored in `AppSettings`, passed via
`PitchEngineConfig` — same pattern as every other knob:
- **template dictionary** — built from the calibration takes (the profile), pizz templates for pizz rule 3;
- **`octaveRatioThreshold`** — fit + graded GOOD/TIGHT/OVERLAP from the labeled takes; **refuse to arm on
  OVERLAP** → falls back to rules 1+2, exactly like the noise gate and play-style split;
- **playable-range floor** — from the open-E measurement (already `lowestPlayableHz`).

### 6.6 What still needs doing before rule 3 is real (from the spike, §8.1)
- **Per-window noise** — the spike's per-frame argmax was leaky (Mi2). Rule 3 should aggregate a short
  rolling window of frames before flipping, or lean on the smoother + a strict ratio threshold (a little
  state, like rule 2's `lastValidHz`).
- **Measured / envelope-modelled templates for every pitch** — never naive octave-shift (the Re2 artifact,
  fixed once the dictionary is floored at the playable range).
- Guard with the corpus (`RealBassRegressionTest`, `PizzOctaveDownFalsePositiveTest`) + new Fa1/Si1
  regression clips; keep `ignoreWrongOctave` on as belt-and-suspenders.

### 6.7 (Later, for MIDI export / pitch-on-a-piece) propose mode
The same templates can run in **propose mode** — emit an independent note stream a fusion step reconciles
with PitchGate — for the downstream transcription goal. Out of scope for the octave-verifier; noted so the
template investment is known to serve both.

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

## 8.1 Spike result — 2026-07-20 (gate PASSED, with caveats) — `TemplateDecompositionSpike.kt`
Built per-rig templates from the 8 pizz calibration takes (E1/G1/A1/C2/D2/F2/G2/Bb2), pitch-shift-filled
the rest of MIDI 26–50, and ran a fixed-dictionary non-negative decomposition (NMF multiplicative update)
over the collector's real per-frame magnitude spectrum. Per-frame argmax note, aggregated over each
acid-test region:

| test region | decomposition picks | verdict |
|---|---|---|
| **Fa1 octave-up** (Fa1 read ~82–88, the bug) | **Mi1 76% + Fa1 17% = 93% LOW note** | ✅ **recovers the octave** |
| genuine low Fa1 (~41) | Mi1 80% + Fa1 14% = 94% low | ✅ stays low |
| genuine Do2 (~65) | Do2 73% | ✅ |
| §12 Sol2 (must NOT halve) | **Sol2 84%**, Sol1 2% | ✅ not halved |
| §12 Mi2 (must NOT halve) | **Mi2 54%**, Mi1 25% | ⚠️ majority-correct, leaky |

**Result: the decomposition recovers the Fa1→F2 octave-up (93%) that the odd-harmonic rule, `acf2x`, and
`ampF2/ampF` all failed on** — by matching the full comb (Fa1's un-rolled-off 3rd harmonic ≈131 Hz, which
F2's series lacks) instead of the rolled-off fundamental. §12 Sol2 stays clean; the current pipeline is
NOT worse anywhere tested.

Caveats / next refinements before this is production-grade:
- **Mi2 leak (25%→Mi1):** the residual roll-off-region weakness — partly the decomposition *correctly*
  explaining the ringing open-E as a separate source (the polyphonic win, hidden by per-frame argmax). A
  note-level decision (sum/median over the note's frames) still lands on Mi2. Should tighten with a real
  Mi2 template (spike only had F2, a semitone off) and finer frequency resolution.
- **±1 semitone pitch fuzz** (Mi1 vs Fa1): the dictionary is coarse (8 measured + shift-synthesis, 10.77
  Hz bins). It nails the octave (the goal) but not exact pitch — fine for an octave-*verifier* augmenting
  Pipeline 1; needs work for standalone transcription.
- Per-frame, offline, one rig, small test set — a promising signal, not a proof.

**Fast Do-scale stress test (`122438`, heavy ring-over — where `acf2x` failed).** All degrees genuine,
so nothing should be halved. **La2 (its sub-octave is the ringing open A) stayed La2 72% vs La1 2%** — the
exact resonance confound that defeated `acf2x`, handled. Do2 80%, Do3 90%, Sol2/Si2 octave-correct. Two
weak spots, both the already-known refinements, not new failures: **Re2→Re1** and **±1 semitone pitch
fuzz** (octave always right; coarse 8-note/10.77 Hz dictionary).

**Re2→Re1 was fixed for free by flooring the dictionary at the playable range** (2026-07-20): Re1/D1
(37 Hz) is below open E1 (41 Hz) and *unplayable on a standard 4-string bass* (Sarah), so it should never
be a candidate. Setting `loMidi = E1` (mirrors the pipeline's `lowestPlayableHz` guard, which calibration
already measures from the open-E) → **Re2 now reads Re2 75%** (was Re1 62%), Do2 tightened 80%→86%, and
Fa1→93% / La2→72% / Sol2→84% all held. **Design note: the second pipeline should inherit the calibrated
playable-range floor** — a cheap, principled constraint that removes a whole class of false octave-downs.
Remaining: §12 Mi2 (51% Mi2 / 27% Mi1) — genuine roll-off-region hard case (82 Hz fundamental just above
the knee, open-E1 ringing, and only a *synthesized* Mi2 template a semitone off F2). Confirms the gate
result; a real Mi2 template + note-level aggregation should firm it up.

**Recommendation:** worth pursuing. Next, in priority order: (1) **measured or harmonic-envelope-modelled
templates for every pitch** — do NOT naive-shift (the Re2 artifact); (2) note-level aggregation
(sum/median over a note's frames, not per-frame argmax); (3) finer frequency resolution for exact pitch;
(4) re-test incl. Si1→B2 and a wider genuine set. Then prototype the realtime "octave-verifier"
integration (§6, verify mode).

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

## 11. Outcome — decision gate FAILED, shelved (2026-07-22)
Built the rule-3 octave-verifier (`HarmonicTemplateDictionary` + `TemplateOctaveResolver` + the
`TemplateDecompositionSpike` eval, all in the **dsp test** source set) and ran the §8 gate against a
fresh, purpose-recorded corpus. **It fails, conclusively.**

**Method as built** (not the §6.2 draft): the {f/2, f} 2-candidate ratio collapses (template(f/2)
subsumes template(f)'s partials), so the resolver decomposes the frame over a **full local octave**
`[f/2, f]` (13 semitone candidates) via NNLS and votes on which cluster the strongest activation lands
in, with rolling-window aggregation. Dictionary = **measured** combs from a gapped chromatic pizz take
(2026-07-22, E1→B2), labelled by the trace's octave-correct `smoothedHz`.

**Result** (sub-octave vote per region; want octave-ups high, genuine notes low):

| note | truth | vote | wanted |
|---|---|---|---|
| Fa1→F2 | octave-up | 48% | high |
| Si1→B2 | octave-up | 41% | high |
| Mi2 (E2) | genuine | 25% | low |
| **La2** (open-A rings) | **genuine** | **63%** | low |
| Sol2 | genuine | 10% | low |

A genuine **La2 votes sub-octave harder than the real Fa1→F2 octave-up** — no threshold separates them.
Confirmed across three metrics (argmax / activation-ratio / odd-even), sparse vs dense dictionaries, a
16384-point FFT (worse — pizz decay + longer window averages in more open-string ring), and thresholds
up to unanimity.

**Root cause:** a genuine note whose sub-octave is a hard-ringing open string (Mi2/open-E, La2/open-A) is
**spectrally identical** to a true octave-up — the open string genuinely sounds an octave down (real
polyphony in the roll-off knee). Measured open-string templates match the ring *better*, worsening it.

**Conclusion:** the octave decision for low pizz is **not solvable by a target-agnostic per-window
discriminator**. The only separating signal is the *prompted note* (context), already exploited by
`ignoreWrongOctave` / the game's octave-fold at the target-aware layer. Rule 3 shelved; code kept as a
guarded documented dead-end (like the acf2x write-up). Full trace: DETECTION.md §12.6.2.

**The temporal axis was probed too, and also fails** (`TemplateDecompositionSpike.temporalProbe`,
2026-07-22). Idea: a true octave-up's odd harmonic (1.5·f) is a real partial present at the pluck (rise
lag ≈ 0), while a genuine note's 1.5·f is a sympathetic ring that builds up *after* (lag > 0). The data
refute it — genuine La2's odd energy rises *with* its fundamental (lag 0), the Fa1 octave-up's builds up
*late* (+255 ms), no consistent sign otherwise. Reason: sympathetic coupling on the bass is
**near-instant** vs the 23 ms frame — the open string rings inside the first window, so there is no onset
lag to exploit. A note-level HMM would only help the *bistable* Si1 flip, not the steady-La2 tie.

**Net:** per-window spectral, odd/even split, onset timing, and note continuity have all been tried and
fail on the same physical ambiguity (a strong sympathetic ring is the *same vibration* as playing that
open string). The only untried signal-only avenue is a full learned polyphonic-over-time model — and its
justification is the **transcription** goal (§6.7 / scoring a played piece), where melodic context does
the octave disambiguation, NOT octave-error mitigation (which `ignoreWrongOctave` already handles). If
polyphony is ever pursued, scope it around transcription, not this bug.
