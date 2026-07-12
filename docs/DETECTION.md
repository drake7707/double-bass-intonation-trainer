# Capture & detection — the definitive reference

**Read this before touching anything in `game/AttemptCapture.kt`, `game/SustainCapture.kt`,
`dsp/PitchGate.kt`, `ui/round/RoundViewModel.kt`, or the calibration wizard.** It records the
problem, every design decision, what worked and what didn't, and how we got there — so after a
context reset we can be current again in one read.

Last updated: 2026-07-11 (evening), after the "instant wrong note" saga was fully resolved and
the calibration wizard was extended to own the detection thresholds.

---

## 0. Core philosophy (never violate)

This is **not a tuner**. During an exercise there is **no live pitch readout**. The model is:

> detect onset → wait for stability → freeze the FIRST stable pitch → score it.

Correcting your finger *after* the note is frozen must not change the result. Live needles exist
only on the Tune-up and Pitch-debug screens. Everything below serves this model.

---

## 1. The two gating layers (do not confuse them)

Detection happens in two stages, and it matters which layer a change belongs in:

### Layer 1 — `dsp/PitchGate` — target-AGNOSTIC, per analysis window
Lifted/adapted from Tuner. Decides, for each ~23 ms window, whether it's an acceptable pitch:
noise gate (periodicity), harmonic-energy content, absolute level vs the sensitivity threshold,
plus **octave-UP correction** (halving a detected octave error). It does **not** know what note
the user was asked to play. Shared by every screen. Emits a `PitchSample` per window.

### Layer 2 — the capture state machines — consume `PitchSample`s
- `game/AttemptCapture` — Note Accuracy & Shift. Freezes the first stable pitch.
- `game/SustainCapture` — Sustain. Tracks how long a target is held in tune.

### Layer 3 — `ui/round/RoundViewModel.onCaptured` — target-AWARE game rule
This is the only place that knows the prompted note. It decides whether a frozen pitch is
*really her attempt* or should be discarded (see §4). Target-aware logic lives here, **not** in
the target-agnostic machine — that separation is deliberate and keeps the machine reusable by
the Shift Trainer and the debug screen.

---

## 2. `AttemptCapture` — the capture state machine

`AWAIT_QUIET → LISTENING → CAPTURING → FROZEN | TIMED_OUT`. Pure state machine; all timing is on
the audio clock (sample timestamps), so it's deterministic and unit-testable with synthetic
`PitchSample` scripts. Terminal states are sticky.

Two independent arming flags (this decoupling is the crux of the whole saga — see §3):

- **`skipQuietGate`** — start in `LISTENING` immediately instead of waiting for the room to go
  quiet first. Avoids waiting for a silence that legato bowing never provides.
- **`requireOnsetRise`** — the onset must be a genuine **attack**: energy rising above the
  tracked ambient floor, not merely *any* sounding pitch. A decaying/sustained ring has no
  rising edge, so it never onsets. **This is what distinguishes "she played a note" from "a
  previous note is still ringing."**

They were originally coupled (`requireOnsetRise = !skipQuietGate`). They are now **independent**:

| Caller | skipQuietGate | requireOnsetRise | Why |
|---|---|---|---|
| **Game prompt** (Note Accuracy) | true | **true** | no silence wait (legato-friendly) AND won't grab ring-over |
| **Shift landing** | true | false | mid-glide, there is no attack to wait for — the sounding string IS the floor |
| default / legacy | false | true (=`!skipQuietGate`) | preserved for old callers |

The ambient floor is tracked from **every** sample (fast down, slow up). That's why, after a
loud note decays, the floor falls and a fresh attack can clear it, but a note held loud forever
never produces a rise.

`CapturedPitch` carries `frequencyHz`, `reactionTimeMs`, `timeToStableMs`, `quality`
(CLEAN/SHAKY), and **`energyLevel`** (median energy of the frozen window — added so Layer 3 can
reject faint captures).

---

## 3. The saga: how the "instant wrong note" bug was found and fixed

This is the part to re-read. Every step was driven by **real recordings**, not guesswork.

### 3.1 Symptom 1 — "Fa2/Fa#2 arco: no note detected" and "Do#2 sustain won't lock"
Her first hands-on gameplay feedback. Isolated debug snippets (she alternated the notes in the
Pitch-debug screen) replayed offline (`FeedbackSnippetAnalysis`) showed: **the engine detected
the notes perfectly, but the capture machine never fired.** Root cause: mid-round prompts armed
via an `AWAIT_QUIET` gate that needs the room to drop below level 30 for 200 ms. When she bows
legato, that silence never comes, so the machine sits in `AWAIT_QUIET` forever. Pizz worked
because plucks decay to silence.

### 3.2 Fix attempt #1 (WRONG, regressed) — `skipQuietGate=true`
Arm each prompt immediately, no silence wait. Fixed the "no note" — but **caused instant false
"wrong notes."** Because arming instantly with `requireOnsetRise` also off (they were coupled),
the machine froze whatever was already sounding: **the previous note still ringing.**

### 3.3 The instrument that cracked it — the game-trace tool (her idea)
Isolated debug snippets couldn't show the *between-prompt* dynamics. So we built
`audio/GameTrace` (Settings → Debug → "Record & trace games"): records the **whole game** — full
per-sample detection stream + game events (prompt shown w/ timestamp, each freeze, each discard
with its reason) + the raw audio. Replaying the audio through `PitchEngine.wavSamples`
reconstructs detection exactly; the event log lines game decisions up against it.

**This is the workflow for any future detection issue: turn trace on, play a real round, pull
the newest `game-trace-*` off the phone, analyse events + samples, fix, re-run.**

### 3.4 What the traces proved
- Trace 1 (`…194019`): 10 prompts. You play E2 well on prompt 8 (+10¢); that **E2 keeps ringing
  at level 65–100 through prompts 9 AND 10** and is frozen both times (+289¢, then −610¢
  "wrong"). The false captures landed **0.35–0.8 s** after the prompt; your *genuine* correct
  plays measured **2.4–5.0 s**. Ring-over, confirmed.

### 3.5 Fix attempt #2 (partial) — ring-over + "too soon" rejection
In `onCaptured`: discard a capture that (a) matches the **previous** answer's pitch and isn't
near the current target (ring-over), or (b) arrives sooner than she could physically read the
new note and play it (**her physical-impossibility insight** — an off-target capture in a
fraction of a second is never her attempt). The read-time floor is per-player (see §5).
Traces 2/3 showed this cleared the fast cases but a **loud ring/decay past the floor** (the open
A string resonating at level 100, "I didn't even play, just let it ring") still slipped: it
wasn't near the previous pitch (decay had shifted it) and wasn't too-soon (>1 s).

### 3.6 Fix attempt #3 (THE fix) — require a genuine attack
Decoupled `requireOnsetRise` from `skipQuietGate` and set **both true** for game prompts. The
distinguishing fact was never timing or pitch — it's that **a ring has no new attack. She wasn't
playing.** With `requireOnsetRise=true`, a decaying/sustained ring produces no rising edge, so it
never onsets and never captures; only a real attack does.

**Verified** on a full mixed run: 10 prompts, correct notes scored, deliberate wrong notes
flagged ("wrong note?" / "right note, wrong octave"), and letting a note ring produced **zero
captures** (the trace showed zero discard events — the ring never even onset). Commits `3f34e0c`
(ring-over/too-soon) and `f65497b` (attack requirement).

### 3.7 What did NOT work, and why (so we never re-try these)
- **`AWAIT_QUIET` silence gate** for mid-round prompts → starves under legato (no silence).
- **`skipQuietGate` with the rise requirement off** → grabs the previous note's ring-over.
- **A read-time floor alone** → a loud ring outlasts any fixed floor.
- **Ring-over pitch-match alone** → decay shifts the pitch out of the match window.
- **The winning signal is attack detection** (energy rising edge). The others are useful backups
  but not sufficient alone.

---

## 4. `onCaptured` — the wrong-note filter (Layer 3, Note Accuracy)

A frozen pitch is discarded (and the machine keeps listening within the prompt) when it is
clearly not the note she meant. In priority of concept:

1. **Ring-over** — matches the previous prompt's answer pitch (`RING_MATCH_CENTS`=60) and isn't
   near the current target.
2. **Too soon** — arrived before `minReadMs` (she couldn't have read + played yet). Applies to
   ANY pitch, near-target included (that gap once let a semitone-away ring score).
3. **Harmonic artifact** (her idea) — a **non-octave integer overtone** of the target (×3, ×5,
   ×6, ×7, ×9, ×10). She aims at the target, so an overtone reading is the detector latching a
   harmonic, not a note anyone plays by mistake. **Octaves (×2, ×4) are the exception** — a wrong
   octave is a plausible misread, reported as **"right note, wrong octave"** (`wrongOctave`).
4. **Unplayable** — below the lowest string (`lowestPlayableHz`) — a subharmonic/correction
   artifact.
5. **Flimsy** — faint (`energyLevel < wrongNoteMinLevel`) or SHAKY quality.

A confidently-played, on-time, non-artifact wrong note **is** reported ("wrong note?") — we must
never swallow a genuine mistake. If an artifact/ring persists past `MAX_DISCARDS` (25), report
"no note detected" rather than the artifact. Every discard is logged to the trace with its
reason.

**With the §3.6 attack requirement in place, most of these rarely fire** — the ring simply never
onsets. They remain as defence-in-depth.

### 4.1 Same filter, reused by the Chords (arpeggio) game

`game/ArpeggioCapture` plays a triad tone-by-tone by composing one `AttemptCapture` per tone
(each armed `skipQuietGate=true, requireOnsetRise=true`, exactly like Note Accuracy) — the same
way `ShiftCapture` composes its sub-captures. It carries a **copy of the §4 discard filter**
(ring-over/too-soon/harmonic/unplayable/flimsy) as a pure, parameterized function inside the
machine (thresholds passed in by the ViewModel from the same calibration/player sources), so it
stays Android-free and unit-tested (`ArpeggioCaptureTest`). Two arpeggio-specific rules:
- **Ring-over is against the *previous tone of the same arpeggio*** (not the previous prompt) —
  this is the dominant risk here because the tone she just played is still sounding when the
  next tone arms. `too-soon` (`minReadMs`) applies to the **root only**; later tones follow
  immediately.
- Strict ascending order: a genuine wrong **root** re-arms ("that's not it", like the shift
  start); a genuine wrong **third/fifth** is scored as a miss and advances (never stuck).

These thresholds are **provisional** — reused from Note Accuracy, not yet retuned against a real
arpeggio game-trace. Get one via Settings → Debug "Record & trace games" (tag `chords-*`) and
replay offline before trusting them. If the filter ever needs a third caller, that's the trigger
to extract it to one shared pure function (see §8) rather than keep a third copy.

---

## 5. Threshold ownership — who sets what (settled WITH the user)

The guiding principle she set: **calibrate what depends on the device/room/player; hard-code only
true universals.** Three homes:

### A. Detection thresholds → the calibration wizard (per phone / room / instrument)
Persisted in `AppSettings`, applied via `settings.applying(config)` (the single settings→config
point). Measured by the full calibration wizard from prompted notes (ground truth known):
- **noise gate** (`micSensitivity`) — from room-noise ceiling vs playing floor.
- **`wrongNoteMinLevel`** — energy floor for the "flimsy" rule. Sits **halfway** between measured
  noise and playing (the gate sits ⅓ up, favouring hearing soft notes; calling something a
  *wrong note* demands clearer energy). `CalibrationAnalysis.wrongNoteFloor(noiseCeil, playingFloor)`.
- **`lowestPlayableHz`** — a semitone below the lowest open string's known pitch, so it tracks her
  A4 / tuning. `CalibrationAnalysis.lowestPlayableHz(lowestOpenStringHz)`.
- **mic source** (Standard/Voice/Unprocessed), **roll-off knee** (`missingFundamentalMaxHz`),
  **octave-correction odd-harmonic thresholds** — as before.

Defaults in `AppSettings` are the reference-Pixel-6a values; the wizard overrides per device.

### B. Player-facing timing → `PlayerLevel` (auto-tuned by `LevelAdvisor`)
- **`minReadMs`** — the read-time floor used by "too soon". It's her **reading speed**, not a mic
  property, so it belongs to the player level, NOT the detection wizard. Beginner 1000 / Int 800 /
  Adv 600 / Expert 450 ms. Her genuine reads measured 2.4 s+, so there is wide margin.
  `LevelAdvisor` already suggests a level from measured reaction times.
- prompt/reveal/shift/sustain timeouts, reveal factor — same rationale.

### C. Universal musical constants → hard-coded, NOT calibrated
- `NON_OCTAVE_HARMONICS = {3,5,6,7,9,10}` — an overtone is an overtone on every phone.
- `RING_MATCH_CENTS`=60, `NEAR_TARGET_CENTS`=150 — a semitone is a semitone everywhere.
- Calibrating these per-device would be meaningless (pushback stands).

---

## 6. `SustainCapture` — hold-in-tune machine

Separate machine. Requires onset-rise already (a ring won't start it). Key behaviour:
- **Bow-reversal forgiveness** (her "classifier" idea, heuristic form): an out-of-tolerance
  excursion that **returns** within `outGraceMs` (250 ms) does not reset the hold timer; a
  **sustained** departure does. A bow reversal briefly scoops the pitch then returns to the same
  note; genuine finger drift persists. (Energy-dip refinement is available later from a sustain
  trace if needed.)
- The Sustain screen shows a **tune-up-style in-tune bar** that greys out below the noise gate.

---

## 7. The calibration wizard — measure, validate, test-run, then save

`ui/calibrate/WizardViewModel` + pure core `calibration/CalibrationAnalysis`. Flow (~2 min arco):
quiet room → gate; open Mi once per mic source → best source; open La/Ré/Sol → playing floor +
roll-off knee; Do3 → verify + refit octave thresholds only if it halves. Every prompted note's
true pitch is known, so takes are **replayed offline through candidate configs** and scored
against ground truth ("turning the knobs against known notes").

Robustness (her requirements — reject bad data, don't bake in a one-off, validate with a test
run):
- **`isUsableTake`** — a take is accepted only if it has enough signal AND actually contains the
  asked-for note (at some octave — low strings may read octave-up on a rolled-off mic).
  Rejects wrong-note / wrong-string / noise takes → **asks her to play it again** (the retry path
  IS "repeat the action" until the data is clean).
- **Test-run save guard** — before saving, every take is replayed under the **final** config and
  verified; if any **core open string** fails to detect, the wizard **refuses to save** and asks
  her to re-run. Bad data can never become saved settings. (The high note is allowed to be
  unreliable; that's surfaced separately, not blocking.)
- A too-noisy room (gate OVERLAP) already refuses to save.

Not yet done (candidate future work): explicit N-take **consensus/median** per note (record each
2–3× and aggregate) for extra one-off protection — deferred as a UX/time trade-off; the 5 s takes
already pool ~170 windows each and the retry+guard cover the main risk.

---

## 8. Test coverage (the safety net)

- `app` `AttemptCaptureTest` — the state machine incl. the **attack-requirement** cases (a ring
  with no attack must not freeze; a genuine attack must; an attack after a ring decays must).
- `app` `FeedbackRegressionTest` — replays her Sol#1/Fa2 snippets; guards the harmonic/unplayable
  /flimsy filters and legato arming.
- `app` `SustainCaptureTest` — incl. `briefBowReversalScoopDoesNotReset`.
- `app` `NoiseRejectionTest` — desk/bird noise must never produce a capture.
- `app` `calibration/WizardCorpusTest` — grounds the wizard's decisions in the corpus: roll-off
  knee ≈ 63 Hz, default octave thresholds win on the reference phone, `wrongNoteFloor` lands above
  the gate in a sane band, `lowestPlayableHz` ≈ a semitone under E1, `isUsableTake` accepts a real
  open string and rejects the wrong note.
- `dsp` `RealBassRegressionTest`, `OctaveCorrectionEvidence`, etc. — the octave-correction corpus.

Corpus lives in `dsp/src/test/resources/wav/` (WAV float32 + JSONL). The `:app` tests read it via
sourceSets. Full-round game traces are large; they live locally in `.trace-incoming/` (untracked)
and can become a round-replay regression if the `onCaptured` decision is extracted to a pure fn.

---

## 9. Diagnosing a future detection problem (the drill)

1. Ask her to reproduce with **trace on** (Settings → Debug → Record & trace games), play a round,
   note which prompts misbehaved.
2. Pull the newest `game-trace-*.jsonl` (+`.wav`) from
   `/sdcard/Android/data/be.drakarah.intonation/files/snippets/` via adb.
3. Read the `event` lines: `prompt` (t, target midi, previous pitch), `result` (cents, wrong,
   timeout), `discard` (hz, quality, level, elapsed-ms, ring/soon/harm flags). Correlate her
   read→play timing against captures.
4. If a DSP change is needed, replay the `.wav` through `PitchEngine.wavSamples` under candidate
   configs (that's exactly what the wizard and corpus tests do).
5. Fix, add a regression test from the recording, re-run on device, confirm from a fresh trace.

**The signal hierarchy that actually worked, in order:** genuine attack (energy rise) > ring-over
pitch-match > read-time floor > energy/harmonic/unplayable artifact checks. Reach for attack
detection first.
