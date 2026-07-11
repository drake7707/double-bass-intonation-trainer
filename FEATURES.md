# Double Bass Intonation Trainer — Features

A gamified intonation trainer for double bass on Android (Kotlin + Jetpack Compose, GPLv3).
**It is deliberately not a tuner**: during exercises there is no live pitch needle. The app
detects the note onset, waits for the pitch to stabilize, freezes the *first stable pitch*,
and scores that — so it trains accurate first landings and ear-first playing instead of
watching-the-needle corrections.

## Pitch detection

- Detector adapted from [Tuner](https://codeberg.org/thetwom/Tuner) (© Michael Moessner,
  GPL-3.0-or-later): FFT-accelerated autocorrelation plus harmonic-series verification,
  reliable down to the open E string (41.2 Hz) on a phone microphone.
- **Octave-error correction** developed against recordings of the actual instrument:
  - *odd-harmonic proof* — a spectral peak at 1.5× the detected frequency proves the true
    note is an octave lower (fixes the bowed A string reading A2 when the mic loses the
    55 Hz fundamental);
  - *decay continuation* — a jump to exactly 2× the tracked pitch with falling energy is a
    decay artifact, not a new note (fixes plucked low E flipping to E2 as it rings out).
- Real recordings from the target instrument are a permanent regression test suite.
- Arco and pizzicato have separate capture tunings (attack skip, stability window, decay
  handling); every exercise can be played in either mode.

## Exercises

All exercises run as **fixed rounds** (5/10/20 prompts) with a total score to beat.
Prompts always show the note (large), the **position** and the **string** — readable at
arm's length while holding the instrument.

### Daily focus
The home screen leads with one highlighted card — a suggested session that rotates with
the calendar day (accuracy arco, same-string shifts, sustain, pizz accuracy, cross-string
shifts) with its personal best. One tap and practice starts: no configuring, no decision
fatigue.

### Tune up
Pre-session tuner (the one deliberate exception to the no-needle rule): play each open
string, the app auto-detects which one, shows a live cents needle, and ticks the string
green after it's held in tune for a second. An out-of-tune instrument makes intonation
scores meaningless, so this comes first.

### Drone (ear training)
A steady reference pitch to play against by ear — hold your note against it and listen for
the beats to disappear. Pure practice aid: **no scoring and no pitch detection**, so it
needs no microphone and never touches the capture machine. Pick the pitch by open-string
root (Mi/La/Ré/Sol) or any note by name; because phone speakers roll off in the bass, the
tone always sounds in an audible register (octave-equivalent to your choice). Optional
**"add fifth"** reinforces the root with a just (3:2) fifth for easier tuning. Its own
volume slider; the tone stops when you leave the screen or background the app.

### Note Accuracy
"Play Sol2." The first stable pitch is frozen and scored in cents. Correcting the finger
after landing does not help. Timeouts count as misses; a note ~a semitone off is labelled
"wrong note?" instead of pretending it was very flat.

### Sustain
Hold the prompted note inside a tolerance ring (tolerance follows difficulty: ±20/15/10
cents). The ring fills toward the goal (5 s); drifting out resets the timer (single bad
readings are debounced). While out of tolerance the app shows only a coarse
"too sharp / too flat" hint — never a needle. Stopping the note banks the best stretch;
a 20-second cap awards partial credit.

### Shift Trainer (two variants, scored separately)
Confirm the start note (wrong note → "that's not it" and it re-arms), hold it through a
randomized 0.5–1.5 s wait, then **GO**: shift to the target. Only the first stable landing
counts — glide samples are excluded, so sliding into the note scores where the pitch
*stops*; returning to the start note scores nothing. Landings under 1.2 s earn a
⚡ confident-shift bonus.
A shift always moves **between two positions** — two notes in the same position aren't a
shift — so both variants need at least two selected positions (the home cards are disabled,
with an explanation, when only one is selected).
- **Same string**: the classic shift along one string, between positions (prefers ≥3-semitone shifts).
- **Cross string**: start and target on different strings *and* different positions — string
  crossing plus shift plus landing.

## Position system (v1: Simandl)

Prompts are drawn only from positions the player selects (multi-select chips):
½, 1st, 2nd, 3rd, 4th, 5th — mapped per the Simandl fingering chart (semitones above the
open string: ½ = 1–3, 1st = 2–4, 2nd = 3–5, 3rd = 5–7, 4th = 7–9, 5th = 8–10).
Positions are plain data (name + semitone offsets), so other systems (French, Rabbath)
are a new table, not a rewrite; each system's positions would score separately.
Rounds are **balanced across the selected positions** (shuffled per-position decks drawn
round-robin), so a round can't cluster on the easiest position. Open strings are not
prompted (they test the bow, not finger placement). **Each exact position combination is
its own scoring category** — scores are only ever compared between identical setups.

## Scoring & gamification

- Per note: 100 points within ±5 cents, falling linearly to 0 at the difficulty limit
  (Relaxed ±75 / Standard ±50 / Strict ±30). Stars: 3★ ≤5c, 2★ ≤15c, 1★ ≤30c.
- **Personal bests** per configuration (exercise + arco/pizz + difficulty + round length +
  position set + variant); round summaries show "New personal best!" or points-to-beat.
- **Daily practice streak** (flame icon + "N day streak" label on the home screen;
  surviving until end of the next day).
- **Achievements** (12): first round, bullseye (≤2c), sharpshooter round, perfect round,
  all four strings, 100/1000 notes, 100 notes in a day, week/month streaks, perfect
  sustain round, sub-second three-star shift. Unlocks announced on the round summary;
  gallery on the Progress page.
- **Sound feedback** (toggle): rising chime on a good landing, soft blip when close, low
  buzz on a miss — practice without watching the screen.
- **Pitch-drift warning** (toggle): if everything landed recently trends consistently
  sharp or flat (≥8 cents median, 5 of the last 6 attempts same direction), the app warns
  visually and with a directional sound ("come down" / "come up") — better to reset the
  inner reference than to train wrong pitches.

## Progress

- **Progress page**: per-exercise score chart (percent per round, colored dots), stats
  (rounds played, best, average cents of the last 10 rounds), achievements gallery, and a
  session history list.
- **Improvement over points**: round summaries compare this round's average error against
  your previous week ("14.8 cents — last week 18.6 ⬇"), because improving matters more
  than the raw score.
- Every attempt is stored locally (Room) with target, cents error, **reaction time,
  time-to-stable and shift landing time**, and capture quality — so future insights
  (average shift speed, slow-but-accurate vs fast-but-wild) need no schema changes.

## Settings

- **Note names: solfège (Do Ré Mi — default) or letters (C D E)**, applied everywhere.
- Concert pitch A4 (415–446 Hz, default 440).
- **Player level (Beginner / Intermediate / Advanced / Expert, default Beginner)** — one
  friendly setting for time pressure across all games instead of raw timeout values: time
  to start a note after the prompt (20/13/8/5 s), reveal reading time, the Shift GO-cue
  window and the Sustain attempt cap all scale with it. Scoring is equally strict at
  every level and personal bests carry over when moving up — improving is never punished.
  Round summaries watch your actual reaction times: a round where every note came
  comfortably fast offers a one-tap "Switch to <next> pace" (progress worth celebrating);
  a round where prompts kept timing out offers more breathing room. Suggestions are
  per-round, so an unfamiliar position naturally holds them back.
- Difficulty (scoring tolerance), round length (5/10/20).
- Sound feedback and pitch-drift warning toggles.

## Utilities & diagnostics

- **Pitch debug screen**: live frequency/cents/noise/energy readout and a
  **"save last 8 s" button** that writes the raw audio (WAV) plus a per-window detection
  log (JSONL) — recordings of misbehavior become reproducible offline test cases.
- The debug screen also runs the **real game-capture machine** live (arco/pizz
  toggleable): it shows its state, stamps "✓ stable" with time-to-stable whenever a note
  freezes, and keeps a **note sweep checklist** — play chromatically through the whole
  range and every game-ready note turns green, exposing any notes that need tweaking.
- **Long capture**: besides the 8 s snippet, a start/stop recording mode keeps up to
  2 minutes — for systematic test recordings (chromatic sweeps) that grow the offline
  regression corpus.
- **Recordings manager**: lists all saved snippets/captures with date, duration and size;
  share (audio + detection log via any app) or delete, straight from the phone.
- **Tune-up reminder**: the app remembers your last complete tune-up; starting a game
  more than ~8 hours later asks "Tuned up?" first (dismissible per session).
- **Full calibration wizard** (Settings → "Full calibration (new phone or double
  bass)"): a ~2-minute guided setup that makes detection work on any phone. It measures
  the room while you keep quiet, has you bow open Mi once per available microphone mode
  and picks the most reliable one, then bows of the other open strings measure where the
  phone's mic loses low fundamentals (this bounds the octave-up correction per device),
  and a prompted high note (Do3 — the note most prone to false octave halving) verifies
  the whole chain. Because every prompted note's true pitch is known, the wizard replays
  the recordings through candidate settings offline and picks the ones that detect every
  note correctly — "turning the knobs" against ground truth. The summary shows a
  per-note verdict before anything is saved; a too-noisy room refuses to save at all.
- **Calibrate surroundings** (quick, per room): quiet phase + soft-playing phase set the
  noise gate; refuses on overlap. The full wizard includes this measurement, so it only
  needs re-running when the room changes.
- Screens stay awake while listening. Dark theme only. Offline; no accounts, no ads,
  no telemetry.

## Planned

- Chord-progression and walking-bass-line exercises (hand movement across the fingerboard).
- Guess First ear-training mode (play → predict sharp/flat/in-tune → reveal).
- Endless streak mode; drone mode.
- Long-term insights: weakest notes/shifts heatmaps, tendencies per string and position.

## License

GPL-3.0-or-later. Pitch detection adapted from
[Tuner](https://codeberg.org/thetwom/Tuner) © Michael Moessner (GPL-3.0-or-later).
