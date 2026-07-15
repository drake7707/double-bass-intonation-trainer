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
    decay artifact, not a new note (fixes plucked low E flipping to E2 as it rings out);
  - *pizz octave-settle* — a plucked note whose attack momentarily reads an octave high (the
    detector latching the 2nd harmonic before the low fundamental develops) is held briefly
    until the fundamental settles, so it scores the played note, not the wrong octave. The
    settle window is measured per rig by the calibration wizard (a phone with no such artifact
    uses none), and it only ever resolves downward when a real lower octave actually appears.
  - *pizz settle timing* — a plucked note's pitch reads slightly sharp on the attack and settles
    flatter as the string tension equalises, so the capture waits until the pitch has settled
    before scoring, rather than freezing the sharp attack. How long to wait is measured per rig by
    the calibration wizard (from plucked open **and fingered** notes); a phone whose plucks settle
    instantly adds no wait. Bowed notes settle gradually and need no such per-rig timing.
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

Every exercise begins with a short visual count-in ("Get ready / pick up your bass") so
you can put the phone down and get the bow ready before listening starts. Each score
screen offers **Let's go again** to replay the same exercise immediately.

### Note Accuracy
"Play Sol2." The first stable pitch is frozen and scored in cents. Correcting the finger
after landing does not help. Timeouts count as misses. A genuinely-held wrong note is
labelled "wrong note?"; the right pitch class an octave off is labelled "right note, wrong
octave". Stray artifacts never score against you: a faint finger-lift/adjacent-string ring,
an impossible sub-range reading, or a harmonic overtone of the target is ignored and the
app keeps listening for the note you meant.

### Sustain
Hold the prompted note steady for the goal (5 s). The ring fills while you're holding the
note — a generous ±40-cent band, so it keeps filling through a bow-direction scoop; only a
sustained departure to a different note (>0.5 s out) resets it. A tune-up-style bar shows how
sharp/flat you are (it greys out when you're not playing, and goes green inside the
difficulty tolerance ±20/15/10 cents). While out of tolerance the big hint stays coarse
("too sharp / too flat") — never a needle. Stopping the note banks the best stretch; a
20-second cap awards partial credit.

A completed hold is scored on **two things** (50/50): **accuracy** — how close the held pitch
centre sat to the target (median cents), and **steadiness** — how little the pitch wandered
(consistent bow speed; measured as the median-absolute-deviation of cents). Both use robust
statistics, so an unavoidable bow-reversal scoop barely dents the score. The results screen
breaks the two apart — "In tune: …" and "Steady: …", greening whichever you nailed — and
coaches one focus, e.g. *"Steady bow — but sitting sharp. Place the note a hair lower."* or
*"Good pitch — but the note wandered. Even out your bow speed."* Sustain accuracy also feeds
the Progress cents average.

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

### Chords (arpeggios)
The bass is monophonic, so a chord is played as an **arpeggio**: the app names a triad ("Ré
Majeur") and you play its tones one at a time, ascending — root, then third, then fifth — each
a separate attack (bow stroke or pluck). Every tone's first stable pitch is frozen and scored in
cents, so it trains whether your **third and fifth are in tune against the root**, plus the
string-crossing and hand shape of the chord across the neck. Major and minor triads (the
major-3rd vs minor-3rd ear is the point). Play in order: a wrong first note re-arms ("that's not
it"); the tone you just played ringing on is ignored, not scored as a wrong note. Chords are
drawn only from triads fully reachable within your selected positions, balanced across them (so
the game is disabled, with an explanation, when the selection can't form a full triad). **Open
strings are real chord tones** and are played as such, but — like anywhere else — an open
string's fixed pitch isn't scored for intonation; it's shown as "open". Because the same note
lives on several strings/positions, a **Chord fingering** setting chooses how each tone is
placed: *Natural* (closest hand shape, how you'd play it in a piece — the default), *Prefer
fingered* (finger notes in your positions for practice; open strings only when unavoidable), or
*Prefer open* (use open strings wherever a tone sits on one).

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
- **Achievements** (25): first round, bullseye (≤2c), triple bullseye (3 notes ≤2c),
  sharpshooter round (avg ≤10c), sniper round (avg ≤5c), tight group (every note ≤5c),
  perfect round, all four strings, position explorer (≥4 positions in a round),
  100/500/1000 notes, 100 notes in a day, week/month streaks, new personal best,
  early bird (before 7 a.m.) / night owl (after 11 p.m.), pizzicato precision (pizz round
  avg ≤12c), per-technique perfection — perfect sustain / shift / chords rounds — and a
  sub-second three-star shift. Unlocks announced on the round summary; full grid gallery
  reached from the Progress page.
- **Sound feedback** (toggle): rising chime on a good landing, soft blip when close, low
  buzz on a miss — practice without watching the screen.
- **Pitch-drift warning** (toggle): if everything landed recently trends consistently
  sharp or flat (≥8 cents median, 5 of the last 6 attempts same direction), the app warns
  visually and with a directional sound ("come down" / "come up") — better to reset the
  inner reference than to train wrong pitches.

## Progress

- **Progress page**: per-exercise score chart (percent per round, colored dots), stats
  (rounds played, best, average cents of the last 10 rounds), an achievements summary card
  (recent badges + unlocked count, tap for the full gallery), and a session history list.
  Each history row shows the **positions practiced** in that round.
- **Achievements gallery**: a dedicated grid page (its own screen, reached from Progress)
  showing every badge, unlocked-first, with an unlock-progress bar; locked ones show 🔒.
- **Accuracy by position**: a breakdown of your average intonation (cents) in each
  position across all your rounds — a fuller/greener bar means a more secure position, so
  you can see at a glance which positions still feel unfamiliar. (Per exercise tab; Sustain
  now contributes too, using the held-pitch accuracy. Fills in from rounds played after each
  exercise started recording cents.)
- **Improvement over points**: round summaries compare this round's average error against
  your previous week ("14.8 cents — last week 18.6 ⬇"), because improving matters more
  than the raw score.
- Every attempt is stored locally (Room) with target, cents error, **reaction time,
  time-to-stable and shift landing time**, and capture quality — so future insights
  (average shift speed, slow-but-accurate vs fast-but-wild) need no schema changes.

## Settings

- **Note names: solfège (Do Ré Mi — default) or letters (C D E)**, applied everywhere.
  Accidentals use the proper musical glyphs ♯ / ♭ (not the ASCII "#").
- **Mix sharps & flats (off by default)** — a note-naming aid: in the single-note games
  (Note Accuracy, Sustain, Shift) a black-key note shows sometimes as a sharp (La♯) and
  sometimes spelled flat (Si♭), randomly per prompt, so the same note is seen both ways and
  you learn both names of a position's notes. The reveal always matches what the prompt asked.
  Natural notes are never respelled (never "Si♯" for Do). Off keeps everything in sharps; the
  app is about intonation, not note-naming, so this is opt-in.
- **Chords are always spelled by their definition** (independent of the mix setting): a triad
  reads the way music theory demands — Ré major → Ré–Fa♯–La, B♭ major → Si♭–Ré–Fa (never
  "A♯"), G♯ minor → Sol♯–Si–Ré♯. Each pitch class uses the clean enharmonic spelling that
  avoids out-of-scope names (no Mi♯/E♯, Fa♭, or double-flats). The one arbitrary choice is
  F♯-vs-G♭ major, set to G♭. See `chordToneSpellings`.
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
- **Chord fingering** (Chords game): how a tone playable several ways is placed — Natural
  (closest hand shape, default) / Prefer fingered / Prefer open.
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
  the whole chain. A final **pizz phase** has you pluck the open strings and a few fingered
  notes so the wizard can measure how plucked notes behave on your rig: the octave-settle
  window, the octave-down thresholds, and the **capture timing** (how long to wait for a
  pluck's pitch to settle before scoring, so a plucked note isn't scored on its slightly-sharp
  attack). The bowed and plucked phases are clearly marked with a full-width banner so the
  switch to pizzicato is impossible to miss. Because every prompted note's true pitch is known,
  the wizard replays the recordings through candidate settings offline and picks the ones that
  detect every note correctly — "turning the knobs" against ground truth. Each prompt
  **auto-starts recording after a short countdown** so you never have to put the bass down, and
  the text is large enough to read from playing distance. The summary shows a per-note verdict before anything is saved;
  a too-noisy room refuses to save at all.
- **Calibrate surroundings** (quick, per room): quiet phase + soft-playing phase set the
  noise gate; refuses on overlap. The full wizard includes this measurement, so it only
  needs re-running when the room changes.
- **Record & trace games** (Settings → Debug, off by default): records a whole game — the
  raw audio plus the full detection stream and game events — into Recordings. The entire
  round can then be replayed offline to diagnose and tune detection thresholds against real
  playing, not isolated snippets. For normal play, leave it off.
- Screens stay awake while listening. Dark theme only. Offline; no accounts, no ads,
  no telemetry.

## Planned

- Chord extensions: descending / up-and-down arpeggios, chord progressions (I–IV–V), and
  walking-bass lines; more qualities (diminished, dominant 7th); a just-intonation mode that
  scores the third and fifth against the root's 5:4 and 3:2 rather than equal temperament.
- Guess First ear-training mode (play → predict sharp/flat/in-tune → reveal).
- Endless streak mode; drone mode.
- Long-term insights: weakest notes/shifts heatmaps, tendencies per string and position.

## License

GPL-3.0-or-later. Pitch detection adapted from
[Tuner](https://codeberg.org/thetwom/Tuner) © Michael Moessner (GPL-3.0-or-later).
