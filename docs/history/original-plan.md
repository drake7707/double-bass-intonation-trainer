# Double Bass Intonation Trainer — Android App Plan

## Context

Sarah plays double bass and practices intonation against a plain tuner app
([thetwom/Tuner](https://codeberg.org/thetwom/Tuner)), which works but is boring. Goal: a
**gamified intonation trainer** — exercises that produce a score, persisted across days/weeks,
enticing to beat. Core UX principle: **not a tuner**. No live pitch needle during an attempt;
the app detects onset, waits for stability, freezes the *first stable pitch*, and scores that —
training accurate first landings and ear-first playing.

### Locked decisions (from requirements interview)

| Decision | Choice |
|---|---|
| Stack | Kotlin + Jetpack Compose, native Android, single activity |
| Instrument | 4-string orchestral tuning E1 (41.2 Hz)–A1–D2–G2, neck positions, exercises up to ~D4 |
| Concert pitch | A4 = 440 Hz default, configurable |
| v1 exercises | Note Accuracy, Sustain Challenge, Shift Trainer + score history & progress graphs |
| Game loop | Fixed rounds of N prompts → total score → beat-your-best (endless streak mode = v2) |
| Playing styles | Arco AND pizz from v1 (separate capture parameter sets) |
| Calibration | Full auto-calibration wizard in v1 |
| Gamification v1 | Personal bests + daily practice streak + ~10 achievements (no XP/levels) |
| First v2 feature | Guess First ear-training mode |
| DSP | **Adapt thetwom/Tuner's detector → app is GPLv3** (user accepted) |
| Constraints | Offline-first, no accounts/ads/telemetry, dark mode only in v1 |

### DSP research summary (two research passes, reports in session transcript)

- **thetwom/Tuner detector (chosen)**: FFT-accelerated autocorrelation for candidate period +
  harmonic-series verification over the spectrum (rejects harmonic sets with common divisors →
  kills octave errors) + phase-vocoder sub-bin accuracy + amplitude-weighted fundamental. Fast
  path tracks decaying notes (ACF cosine-similarity > 0.95). Proven on the user's Pixel 6a + bass.
  DSP is a cleanly separable pure-Kotlin package. 44.1 kHz, window 4096 (≈93 ms, min freq
  ~21.5 Hz — E1 fine), 75% overlap → result every ~23 ms.
- **TarsosDSP (rejected)**: defaults can't see below 43 Hz, MPM broken below E2 (issue #42),
  Android glue dropped from release 2.5, also GPL → no advantage.
- Repo clone for reference:
  `C:\Users\DRAKE7~1\AppData\Local\Temp\claude\s--Documenten-Programming-Own-Java-Projects-intonation-trainer\0d7fb73e-e412-48f0-80e2-c83580f03d45\scratchpad\tuner-repo`
  (re-clone from codeberg if scratchpad is gone).
- Audio source: **user confirms Tuner's defaults (including plain `MIC` source) work fine on her
  Pixel 6a** — so our defaults mirror Tuner's proven config exactly, `MIC` included. The
  literature warns `MIC` can apply AGC + high-pass below ~100 Hz on some devices, and the
  detector survives that by reconstructing the fundamental from harmonics; `UNPROCESSED` and
  `VOICE_RECOGNITION` are therefore *calibration candidates* the wizard measures empirically,
  not assumed-better defaults.

## 1. Project setup

- **Working name** BassPitch; package `be.drakarah.intonation`; license GPLv3. `git init` first.
- **Toolchain**: current stable — AGP 9.1.x, Kotlin 2.3.x, KSP, Compose BOM 2026.03+,
  compile/targetSdk 36, **minSdk 26**, coroutines 1.10.x, Room 2.8.x, DataStore, Navigation
  Compose, kotlinx-serialization. Version catalog `gradle/libs.versions.toml` (crib structure from
  tuner-repo's catalog and prune).
- **Modules**: `:dsp` (Android library — all GPL-lifted code, clearly attributed, own public API;
  pure-Kotlin math → fast JVM tests) + `:app` (game, data, UI).
- **DI**: manual `AppContainer` in Application + small ViewModelProvider.Factory. No Hilt (~8
  objects in the graph; retrofit later if needed).
- **Charts**: Vico 2.x (`compose-m3`) for history graphs, confined to `ui/history/`. Hand-drawn
  Canvas only for the cents-error reveal dial.
- `:app` package layout: `ui/`, `game/`, `data/`, `calibration/`, `settings/`, `music/`, `di/`.
- RECORD_AUDIO via plain `rememberLauncherForActivityResult`; `FLAG_KEEP_SCREEN_ON` on round screen.

## 2. DSP adaptation layer (`:dsp`)

**Lift verbatim** (keep GPL headers + "modified" notice; repackage to
`be.drakarah.intonation.dsp.detection`), from `tuner-repo/app/src/main/java/de/moekadu/tuner/`:
`notedetection/`: FFT, AutoCorrelation, Correlation, CorrelationBasedFrequency, FrequencySpectrum,
TimeSeries, WindowingFunctions, Harmonics, HarmonicPredictor, HarmonicStatistics,
AccurateSpectrumPeakFrequency, MaximumOfPolynomialFit, SampleData,
FrequencyDetectionCollectedResults, OutlierRemovingSmoother, AcousticWeighting, Inharmonicity;
`misc/`: MemoryPool, UpdatableStatistics, WaveWriter. Inline FREQUENCY_MIN/MAX into own
`DspDefaults.kt`.

**Lift with modification**:
- `SoundSource.kt` → add `audioSource` param (MIC default / UNPROCESSED / VOICE_RECOGNITION;
  capability check `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` before offering
  UNPROCESSED); keep `testFunction` hook; add WAV-stream provider hook for regression tests;
  keep WaveWriter tap and extend it into a **rolling ring buffer of the last ~8 s of raw audio**
  that can be snapshotted to a WAV file on demand (see §9 snippet debugging).
- `FrequencyEvaluatorSimple.kt` → basis for our `PitchGate`, extended to expose gating diagnostics
  (noise, harmonic energy, level 0–100). Do **not** lift `FrequencyEvaluator.kt` (drags in their
  MusicalScale2/Instrument/note-name model).

**Do not lift**: MusicalScale2, notenames/, instruments/, TuningTarget*, TargetNoteAutoDetection,
TuningState, temperaments, UI/preferences/Hilt.

**Own note model** (`:app` `music/`, 12-TET only): `NoteSpec(midi)` with `frequency(a4)`, name
formatting; `centsBetween(f, ref)`; `nearestNote(f, a4)`; `BassTuning` (open strings E1/A1/D2/G2,
range E1..D4).

**Public API**:
```kotlin
PitchEngineConfig(sampleRate=44100, windowSize=4096, overlap=0.75f,
  audioSource=MIC,                       // Tuner's proven default on this phone
  maxNoise=0.1f, minHarmonicEnergyContent=0.1f, sensitivity=90f, numMovingAverage=5,
  maxNumFaultyValues=3, frequencyMin=35f, frequencyMax=1800f)
PitchSample(timestampMs, framePosition, frequencyHz, smoothedHz, accepted,
  noise, harmonicEnergyRelative, energyLevel)
class PitchEngine(config, waveWriter?=null) { fun samples(scope): Flow<PitchSample> }
// + wavSamples(wavData): Flow<PitchSample> for tests/regression/replay (no AudioRecord)
```
Wiring blueprint: `tuner-repo/.../tuner/Tuner.kt` (SoundSource on IO → collector on Default →
Channel(2, DROP_OLDEST) → gate → Flow; restart-on-settings pattern). Detector constants: Tuner's
proven values (subharmonicsTolerance 0.1, subharmonicsPeakRatio 0.75, harmonicTolerance 0.11,
minimumFactorOverLocalMean 3, maxGapBetweenHarmonics 5, Tophat window). Cents conversion happens
in `:app` (needs A4 setting) — `:dsp` stays music-agnostic.

## 3. Attempt-capture state machine (`game/AttemptCapture.kt`) — the heart

Pure function of `PitchSample` stream → events; zero Android deps; fully unit-testable.

```
AWAIT_QUIET → LISTENING → ONSET → STABILIZING → FROZEN (emits CapturedPitch)
                   └──────────────────────────→ TIMEOUT
```
- **AWAIT_QUIET**: energyLevel < 30 or !accepted for ≥200 ms before arming (bass rings long;
  prevents ring-over triggering next prompt). Skipped for first prompt.
- **Onset**: accepted for 2 consecutive samples (≈46 ms) AND energyLevel ≥ noise-floor EMA
  (α=0.02, tracked during LISTENING) + 15 levels. Onset timestamp → reaction time.
- **Attack skip**: discard 120 ms (arco) / 60 ms (pizz) after onset.
- **Stability**: last K samples spanning 250 ms (arco) / 150 ms (pizz) within a 10-cent band
  measured vs the window *median* (target-independent — wrong notes must also freeze). Frozen
  pitch = window median. Bridge ≤2 consecutive rejected samples; more → pizz decayed: if ≥4
  buffered samples, freeze median of last 4 as `quality=SHAKY`, else back to LISTENING.
- **Timeouts**: no onset in 8 s → miss (0 pts). Onset but never stable within 3 s arco / 1.5 s
  pizz → fallback-freeze most-stable sub-window, SHAKY.
- Arco/pizz is a per-round toggle switching the parameter set (single `CaptureParams` data class,
  persisted, calibration can override). Pizz scores the *speaking* pitch (60–500 ms post-attack)
  — bass pizz drifts flat as it decays; detector's decay fast path keeps lock.

**Exercise drivers** on top:
- **Note Accuracy**: prompt → one AttemptCapture → score cents vs target → reveal 1200 ms
  (green ≤5c / yellow ≤15c / red) → AWAIT_QUIET → next. |cents| > 450 labeled "wrong note?" not
  "very flat".
- **Shift Trainer**: CONFIRM_START (capture start note, ±50c) → CUE at randomized 500–1500 ms
  delay → DEPARTURE (3 consecutive samples > 80c from start, OR ≥300 ms silence — covers pizz and
  lifted finger) → LANDING: fresh capture where "in motion" samples (|Δcents| > 25/hop) are
  discarded as glide; the stability window can only fill after motion stops → **first FROZEN is
  the score, machine goes dead** (no credit for corrections). Landing must differ from start by
  >80c else "returned to start". Stores landingTimeMs.
- **Sustain**: ONSET → 300 ms grace → TRACKING: in-tolerance (default ±15c, range 5–25) accrues
  heldMs toward goal (3/5/10 s); out-of-tolerance >2 samples → reset (ring flashes red);
  !accepted >400 ms → note died, reset + relisten; 20 s attempt cap → partial credit on best
  heldMs. UI: filling ring + coarse high/low arrow only while out of tolerance.

## 4. Scoring

- Per attempt: `e=|cents|`; 100 pts if e≤5, else `100·(1−(e−5)/(zeroAt−5))` clamped, zeroAt by
  difficulty: Relaxed 75 / Standard 50 / Strict 30. Timeout=0; SHAKY scores normally.
- Shift: +10% if landingTime <1200 ms, cap 100. Sustain: 100 (0 resets) / 85 (1) / 70 (2) /
  else `60·bestHeld/goal`.
- Stars: 3★ ≤5c, 2★ ≤15c, 1★ ≤30c (sustain: by resets).
- Round = sum over N prompts (5/10/20, default 10), shown as score/1000 + avg |cents|.
- **Personal bests keyed by `configHash`** = hash of canonical JSON (exerciseType, arco|pizz,
  difficulty, N, note pool, sustain params) — changing config starts a fresh leaderboard.
- ~10 achievements via `AchievementEngine` (First Round, Bullseye ≤2c, Sharpshooter avg ≤10c,
  Deca-streak 10×≥2★, Marathon 100/day, Week & Month streaks, All-Strings round, 10 s sustain,
  fast 3★ shift). Progress computed from data, not counters.

## 5. Data model (Room `data/IntonationDatabase.kt` v1)

- `sessions(id, startedAt, endedAt, exerciseType, mode, configJson, configHash, totalScore,
  maxScore, avgAbsCents, completed, calibrationProfileId)`
- `attempts(id, sessionId FK, promptIndex, timestamp, exerciseType, targetMidi, targetFreqHz,
  startMidi?, playedFreqHz?, centsError? /*signed = sharp/flat*/, reactionTimeMs?,
  timeToStableMs?, landingTimeMs?, heldMs?, resets?, score, stars, quality CLEAN|SHAKY|TIMEOUT)`
- `personal_bests(configHash PK, sessionId, score, maxScore, achievedAt)`
- `achievements(achievementId PK, unlockedAt)`
- `calibration_profiles(id, name, createdAt, isActive, audioSource, windowSize, sensitivity,
  maxNoise, minHarmonicEnergy, captureParamsJson, reportJson)`
- Streaks/graph aggregates **derived** in `StatsRepository` (DISTINCT dates, GROUP BY day/week).
- DataStore settings: a4, default mode, difficulty, roundLength, activeProfileId, haptics,
  debugScreenEnabled.

## 6. Calibration wizard (`calibration/`)

Staged, ~3–4 min, re-runnable, named profiles ("Living room"); first launch forces it (skippable
→ sane defaults = Tuner's: MIC / 4096 / sensitivity 90):
1. **Ambient** (3 s silence): noise-floor distribution → `sensitivity = clamp(100−(p95+10), 60, 95)`.
2. **Source** (open E only — hardest signal): for each of {MIC, UNPROCESSED if supported,
   VOICE_RECOGNITION}: bow 3×, pluck 3×. Engine restarted per source.
3. **Window**: winning source, E1+A1, windowSize 4096 vs 8192.
4. **Confirmation**: all 4 open strings, shows lock time + pitch (doubles as "is my bass in tune").

Candidate score: `100·lockRate − 0.02·medianTimeToStable − 2·pitchStdCents − 40·octaveErrors`
(octave error = frozen freq within ±60c of 2×/0.5×/3× median). Ties → prefer MIC (proven default),
then 4096 (latency). Wizard stages also snapshot their audio to WAVs (with consent prompt),
feeding the regression corpus for free.

## 7. Screens (Navigation Compose, single activity)

`home` (streak flame, exercise cards with PBs, arco/pizz toggle) · `round/{type}` (huge target
note + string hint, progress dots, "listen…" pulse → frozen signed-cents reveal + points, haptic
tick; sustain ring variant; **no pitch needle ever**) · `summary/{sessionId}` (score, per-attempt
dot strip, sharp/flat bias, PB banner, achievement toasts) · `history` (Vico progress line
week/month, session list, achievements grid) · `settings` · `calibration` (stepper) · `about`
(GPLv3 text + **"Pitch detection adapted from Tuner © Michael Moessner, GPLv3"** + source links)
· `debug` (live freq/cents/noise/energy/state + **"save last 8 s" snippet button** — built first,
kept behind settings toggle as the field-diagnostics tool).

## 8. Milestones (each ends runnable on the Pixel 6a)

| # | Milestone | Size |
|---|---|---|
| M0 | Skeleton: git init, catalog, `:app`+`:dsp`, dark theme, nav shell | S |
| M1 | DSP lift + PitchEngine + permission flow + **debug pitch screen incl. snippet recording** | M |
| **M1.5** | **DSP validation on the real bass** — gate: E1 locks reliably arco+pizz before any game UI; tune thresholds now; record initial WAV corpus | S |
| M2 | AttemptCapture (pure Kotlin + full unit tests) + Note Accuracy round, in-memory | L |
| M3 | Room persistence, summary screen, real home with PBs, settings | M |
| M4 | Sustain + Shift drivers and screens | M |
| M5 | Calibration wizard + profiles | M |
| M6 | History graphs (Vico), streaks, achievements | M |
| M7 | Polish: about/licenses, icon, haptics, empty states → **v1** | S |

v2 seams already in place: drivers are plugins over AttemptCapture (Guess First = hide result +
prediction buttons before reveal; endless streak = alternate RoundRunner policy); drone =
independent audio-out module; insights = queries over `attempts`.

## 9. Verification

- **Snippet debugging (user-requested, first-class)**: the DSP pipeline keeps a rolling ~8 s raw
  audio ring buffer. The debug screen has a "save snippet" button that writes WAV + a JSON
  detection log (per-window freq/noise/energy/accepted + state-machine transitions) to
  app-external storage. During rounds, a long-press on a suspicious result saves the same bundle.
  A dev-side **replay path** (`PitchEngine.wavSamples`) runs any saved WAV through the exact
  pipeline off-device, so a mis-detection captured in the practice room becomes a reproducible
  JVM test case. Good snippets get promoted into the regression corpus below.
- **Unit tests** (`game/`, JVM): synthetic PitchSample scripts — clean arco settle, pizz
  attack-sharp→settle→decay, slide-into-note (must not freeze mid-glide), Shift
  wrong-note-then-correct (first landing scores), noise burst (no false onset), sustain
  single-outlier (no reset). Table-driven scoring/configHash/streak tests.
- **DSP regression** (`:dsp`, JVM): synthetic harmonic stacks at E1/A1/D2/G2/D4 ±20c through
  `testFunction` hook (assert ±2c, no octave errors) + **WAV corpus** recorded on-device at M1.5
  (and grown via snippet saves + calibration snapshots), committed to
  `dsp/src/test/resources/wav/` with expected-pitch JSON, streamed via `wavSamples`.
- **On-device protocol** (M1.5, M2, M4, M5, real bass): per string bow 5× + pluck 5× (≥4/5
  clean freezes, spread ≤5c); stopped notes F#1/D3/D4; deliberate ±20c mistuning (sign check);
  slow slide G2→D3 (Shift must score landing not slide); background talking (no false onsets);
  5 s arco sustain on A1 (no spurious resets).
- Thin Compose tests: round-screen state rendering + nav smoke.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Mic AGC/high-pass kills 41 Hz | MIC is empirically proven on this phone (Tuner defaults); detector reconstructs fundamental from harmonics; calibration measures UNPROCESSED/VOICE_RECOGNITION as alternatives; M1.5 gate |
| E-string octave errors | Tuner's common-divisor harmonic rejection (why this detector); frequencyMin 35 Hz; calibration penalizes octave errors ×40; WAV regression corpus + snippet replay |
| Pizz decay loses lock | 150 ms stability window, 60 ms attack skip, dropout bridging, SHAKY fallback, detector decay fast path; if flaky at M1.5 → window 8192 |
| Room noise / false onsets | Dual onset condition (gate accept AND rise over adaptive floor), AWAIT_QUIET, per-room calibration profiles |
| Ring-over triggers next prompt | AWAIT_QUIET is designed for exactly this (tunable quietMs) |
| GPL slip | Headers intact from M1; About screen M7; source public before distribution |
| Tuner-creep | No continuous pitch readout outside `debug` route — enforced structurally |
