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

### Tune up
Pre-session tuner (the one deliberate exception to the no-needle rule): play each open
string, the app auto-detects which one, shows a live cents needle, and ticks the string
green after it's held in tune for a second. An out-of-tune instrument makes intonation
scores meaningless, so this comes first.

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
- **Same string**: the classic shift along one string (prefers ≥3-semitone shifts).
- **Cross string**: start and target on different strings — string crossing plus landing.

## Positions (Simandl)

Prompts are drawn only from positions the player selects (multi-select chips):
½, 1st, 2nd, 3rd, 4th, 5th — mapped per the Simandl fingering chart (semitones above the
open string: ½ = 1–3, 1st = 2–4, 2nd = 3–5, 3rd = 5–7, 4th = 7–9, 5th = 8–10).
Rounds are **balanced across the selected positions** (shuffled per-position decks drawn
round-robin), so a round can't cluster on the easiest position. Open strings are not
prompted (they test the bow, not finger placement). **Each exact position combination is
its own scoring category** — scores are only ever compared between identical setups.

## Scoring & gamification

- Per note: 100 points within ±5 cents, falling linearly to 0 at the difficulty limit
  (Relaxed ±75 / Standard ±50 / Strict ±30). Stars: 3★ ≤5c, 2★ ≤15c, 1★ ≤30c.
- **Personal bests** per configuration (exercise + arco/pizz + difficulty + round length +
  position set + variant); round summaries show "New personal best!" or points-to-beat.
- **Daily practice streak** (🔥 on the home screen; surviving until end of the next day).
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
  session history list. Every attempt is stored locally (Room) with target, cents error,
  timing and quality — the raw material for future insights.

## Settings

- **Note names: solfège (Do Ré Mi — default) or letters (C D E)**, applied everywhere.
- Concert pitch A4 (415–446 Hz, default 440).
- Difficulty (scoring tolerance), round length (5/10/20).
- Sound feedback and pitch-drift warning toggles.

## Utilities & diagnostics

- **Pitch debug screen**: live frequency/cents/noise/energy readout and a
  **"save last 8 s" button** that writes the raw audio (WAV) plus a per-window detection
  log (JSONL) — recordings of misbehavior become reproducible offline test cases.
- Screens stay awake while listening. Dark theme only. Offline; no accounts, no ads,
  no telemetry.

## Planned

- Chord-progression and walking-bass-line exercises (hand movement across the fingerboard).
- Guess First ear-training mode (play → predict sharp/flat/in-tune → reveal).
- Endless streak mode; drone mode.
- Auto-calibration wizard (searches mic source / analysis window for the most stable
  detection on this phone) — deferred until real-world detection demands it.
- Long-term insights: weakest notes/shifts heatmaps, tendencies per string and position.

## License

GPL-3.0-or-later. Pitch detection adapted from
[Tuner](https://codeberg.org/thetwom/Tuner) © Michael Moessner (GPL-3.0-or-later).
