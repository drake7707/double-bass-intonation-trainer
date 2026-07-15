# Double Bass Intonation Trainer (working name BassPitch)

Gamified intonation trainer for double bass. Kotlin + Jetpack Compose, GPL-3.0-or-later,
package `be.drakarah.intonation`. User: Sarah — double bassist, thinks in **fixed-do
solfège** (Do Ré Mi; "la string" = A string), plays arco + pizz, learns from Simandl,
currently knows up to 2nd position. She gives design feedback constantly and **wants
pushback when an idea is weak** — don't blindly implement.

**Core philosophy (never violate):** this is NOT a tuner. During exercises there is no
live pitch readout. Detect onset → wait for stability → freeze the FIRST stable pitch →
score it. Live needles exist only on the Tune-up screen and Pitch debug screen.

## Key documents

- `docs/DETECTION.md` — **the definitive capture/detection reference.** The problem history,
  every design decision, what worked/didn't, the trace-driven fix, threshold ownership, and the
  drill for diagnosing detection issues. Read it before editing `AttemptCapture`, `SustainCapture`,
  `PitchGate`, `NoteAccuracyViewModel.onCaptured`, or the calibration wizard.
- `FEATURES.md` — complete user-facing feature description. Keep in sync with changes.
- `TESTING.md` — her hands-on verification checklist. **Discipline: every change that
  needs bass/phone verification gets a Pending item; move to Verified (dated) when she
  confirms.** She asks "give me the checklist".
- Plan (approved, historical): `C:\Users\Drake7707\.claude\plans\iterate-with-me-ask-bubbly-sparkle.md`
- Auto-memory has project status, her preferences, build quirks.

## Architecture

Two modules:

### `:dsp` — detection (GPL-lifted from thetwom/Tuner)
- `dsp/detection/` + `dsp/misc/` = **lifted nearly verbatim from
  https://codeberg.org/thetwom/Tuner (© Michael Moessner, GPL-3.0-or-later)** with
  attribution headers. Don't edit casually; keep headers; diffs vs upstream should stay
  reviewable. Algorithm: FFT autocorrelation + harmonic-series verification.
- `dsp/PitchEngine.kt` — the public API. `samples(): Flow<PitchSample>` (live mic),
  `wavSamples(pcm): Flow<PitchSample>` (offline replay — identical path, used by tests).
  `PitchEngineConfig` defaults are Tuner's proven values EXCEPT `sensitivity = 55`
  (see noise gate below).
- `dsp/PitchGate.kt` — accept/reject gating + **octave-up correction** (two rules:
  odd-harmonic proof at 1.5×f; decay continuation with non-rising energy). This fixed
  bowed-A-reads-A2 and pizz-E-decays-to-E2. Guarded by `RealBassRegressionTest`.

### `:app`
- `game/` — pure-Kotlin state machines, all heavily unit-tested with synthetic
  `PitchSample` scripts:
  - `AttemptCapture.kt` — the heart: AWAIT_QUIET → LISTENING → CAPTURING → FROZEN/TIMEOUT.
    Ambient floor tracked from ALL samples; onset-rise check skipped when
    `skipQuietGate=true` (mid-sound captures like shift landings — breaking this breaks
    the Shift Trainer).
  - `SustainCapture.kt`, `ShiftCapture.kt` (confirm start → random cue → departure →
    glide-filtered landing; return-to-start re-arms), `ShiftPool.kt` (same/cross-string),
    `NotePool.kt` (balanced round-robin across selected positions),
    `Positions.kt` (**Simandl mapping confirmed from her chart**: ½=1–3, 1st=2–4, 2nd=3–5,
    3rd=5–7, 4th=7–9, 5th=8–10 semitones above open; positions are plain data),
    `Scoring.kt`, `Achievements.kt` (12 defs), `DriftDetector.kt`.
- `data/` — Room (db v2: sessions/attempts/personal_bests/achievements; migration 1→2
  shipped). `configKey(...)` = scoring-category identity (exercise|mode|difficulty|
  length|positions|variant) — anything affecting score comparability must be in it.
  `SessionRepository.recordCompletedRound` persists + PB + achievements + week-trend.
- `settings/SettingsRepository.kt` — DataStore: noteNameStyle (SOLFEGE default), a4,
  difficulty, roundLength, positions (multi-select), soundFeedback, driftWarning,
  micSensitivity, lastTunedAt, lastCalibratedAt.
- `music/NoteSpec.kt` — 12-TET midi note model, solfège/letter display names.
- `audio/GameSounds.kt` — generated chime/blip/buzz/drift tones (AudioTrack).
- `ui/` — one package per screen: home (daily focus, position chips, pre-game
  "Ready to play?" gate for stale tune-up/calibration), round (Note Accuracy),
  sustain, shift, tune (TuneUp), calibrate (two-phase surroundings calibration),
  progress (hand-drawn chart + achievements gallery), recordings (play/share/delete),
  settings, about (GPL attribution — required), debug (see below), common/.
  Manual DI via `di/AppContainer.kt`; every ViewModel has a `Factory` companion.

### Pitch debug screen = the field-diagnostics tool
Live diagnostics, gate-aware level bar, the REAL game-capture machine running live
(arco/pizz toggle, "✓ stable" stamps), note-sweep checklist (midi 28..53 turn green when
game-captured), 8 s snippet save, 2 min long capture, recordings manager entry.

## The noise gate (important recent work)

`energyLevel` is 0..100 log scale; gate = `100 - sensitivity`. Tuner's default
(sensitivity 90 → gate 10) accepted 25–37% of ambient windows (typing/birds measured
level 14–23; they froze false sweep notes). Her real playing measures 63–100 incl. full
pizz decay. Default now sensitivity 55 (gate 45). `NoiseRejectionTest` asserts zero
captures on her noise recordings. "Calibrate surroundings" (her design): quiet phase +
SOFT-playing phase → separation verdict GOOD/TIGHT/OVERLAP; refuses to set a gate on
overlap. Don't put device-specific numbers in shipped UI text (Play-store ambitions).

## Test corpus workflow (the superpower — use it)

Real recordings live in `dsp/src/test/resources/wav/` (WAV float32 + JSONL detection
logs): open-string arco/pizz, desk noise, birdsong. When she reports detection
weirdness: she saves a snippet → pull via adb (or she Shares it) → add to corpus →
replay offline (`SnippetReplayAnalysis`, `OctaveDiagnosis` in dsp tests write reports to
`dsp/build/reports/`) → fix → add a regression test. `:app` tests also read the corpus
(sourceSets points at `../dsp/src/test/resources`).

## Build / device (Windows box quirks in auto-memory too)

- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every gradlew.
- Build+test: `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`
- adb: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` (not on PATH). Her Pixel 6a
  connects via **wireless debugging** (mDNS auto-connects when she enables it; ask her to
  toggle it if no device). Install:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- Emulator AVD `Medium_Phone_API_36.1` — note its quickboot snapshot reverts userdata
  between boots (don't trust it for persistence/migration verification; verify on her
  phone via logcat instead).
- Git Bash mangles `/sdcard/...` paths → prefix `MSYS_NO_PATHCONV=1`. Screenshots:
  `adb exec-out screencap -p > f.png` from Bash only (PowerShell corrupts binary).
  Never round-trip UTF-8 files through PS5.1 Get/Set-Content (mojibake) — use Write/Edit.
- Commits: descriptive, note which ideas were hers ("user feedback/design/request").

## Status snapshot (2026-07-11)

Everything through M7 + calibration wizard + drone mode is DONE and installed. Latest
(2026-07-11 evening): her **first real-gameplay feedback batch** (bottom of TESTING.md)
diagnosed from 5 pulled snippets and fixed — see the "2026-07-11 afternoon feedback fixes"
Pending block in TESTING.md and `FeedbackRegressionTest`. Key changes now in the tree
(UNCOMMITTED — left for her review): legato arming (mid-round prompts re-arm
`skipQuietGate=true`; fixed Fa2/Fa#2 "no note" + Do#2 sustain "won't lock"), a
ViewModel-level **wrong-note filter** (see below), sustain bow-reversal grace, visual
count-in + "Let's go again" on all games, audible buzz, sustain in-tune bar, and the
**game-trace tool**. Provisional thresholds await a real full-round trace to retune.
Backlog: chord progressions, walking bass lines, Guess First ear training, endless streak,
insights/heatmaps, decay-relative pizz hold (maybe).

### Two capture-gating layers (don't confuse them)
- **`dsp/PitchGate`** — target-AGNOSTIC, per-sample: noise/harmonic/energy gate + octave-UP
  correction. Shared by everything.
- **`NoteAccuracyViewModel.onCaptured`** — target-AWARE game rule (Note Accuracy only): a frozen
  *wrong* note is discarded and listening continues when it's flimsy (SHAKY or
  `energyLevel < 55`), unplayable (`< 40 Hz`, below open E1), or a **non-octave integer
  harmonic of the target** (her "harmonic leniency": overtones are detector artifacts, not
  notes she'd mistakenly play). Octaves are exempt → shown as "right note, wrong octave".
  `CapturedPitch.energyLevel` (median of the frozen window) feeds this. Provisional.

### Game trace (`audio/GameTrace.kt`) — debug replay of a whole game
Settings→Debug "Record & trace games" (`traceGames`). When on, the 3 game VMs pass its
`waveWriter` to PitchEngine and feed it every sample + game events; on round completion it
saves `game-trace-*.wav` + `.jsonl` to the snippets dir (Recordings lists/shares them).
Replay the WAV via `PitchEngine.wavSamples` to reconstruct detection; the event log lines
up game decisions. This is how to get real inter-prompt audio for tuning arming/thresholds.
