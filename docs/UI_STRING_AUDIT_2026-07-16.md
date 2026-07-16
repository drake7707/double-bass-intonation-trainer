# UI String & Jargon Audit — 2026-07-16

**Source:** code-level audit of the `:app` UI layer (no emulator screenshots), baseline = working
tree of 2026-07-16 (HEAD `e22bfdc` + uncommitted coaching work).
**Purpose:** foundation for `docs/UX_OVERHAUL_PLAN_2026-07-16.md` — the i18n string-extraction work
queue and the plain-language rewrite targets. Line numbers will drift; treat them as pointers, not
anchors.

---

## 0. Executive summary

- **String resources vs hardcoded:** There is effectively **no i18n**. `app/src/main/res/values/strings.xml` contains exactly **one** string (`app_name` = "Intonation Trainer"). A repo-wide search for `stringResource` / `R.string.` / `getString(` returns **zero hits** in `:app`. **~99% of all user-facing text is hardcoded** as Kotlin string literals inside Composables and enum/data definitions. Every string quoted below is a hardcoded literal.
- **Approximate distinct user-facing string count:** roughly **340–380** distinct strings (screens ~230; content descriptions ~55; achievements 25×2 names+descriptions = 50; enum labels/blurbs ~30). This is an estimate; the count is dominated by SettingsScreen, the calibration Wizard, DebugPitchScreen, and the four game screens.
- **Expert-mode support:** The `expertMode` setting is honored **only in ProgressScreen** (via `ProgressViewModel.expertMode`). **No other screen reads it.** All four game reveal screens, TuneUp, Drone, and the round-summary common components show raw `cents` / `Hz` regardless of expert mode. This is the single biggest finding for the plain-language rewrite: the jargon a beginner sees during normal gameplay is **not** gated.
- **App-name inconsistency (Play Store risk):** The app is called three different things: **"Intonation Trainer"** (`strings.xml`, email subject), **"Double bass\nintonation trainer"** (Home header), and **"Bass Pitch"** (Onboarding welcome — `OnboardingScreen.kt:81`). Must be unified before launch.
- **No notification text** exists (no `NotificationCompat` in `:app`; the "channel" grep hits are DSP audio-source config, not user notifications). No TTS/audio-prompt text — `GameSounds.kt` plays tones, not speech.

---

## 1. Home screen — `ui/home/HomeScreen.kt` (+ `HomeViewModel.kt`)
**Purpose:** Landing hub — daily focus, mode/position pickers, and cards launching every exercise and tool.

### Hardcoded strings
- Pre-game gate dialog (`HomeScreen.kt:76-108`):
  - Title "Ready to play?" (:76)
  - "• App not calibrated — pitch detection has not been tuned to this phone's microphone and your bass yet. Accuracy will be poor." (:80)
  - "• No recent tune-up — an out-of-tune bass makes every score meaningless." (:83)
  - "• Surroundings not calibrated — room noise might interfere with detection." (:86)
  - Buttons: "Run full calibration wizard" (:95), "Tune up first" (:98), "Calibrate surroundings first" (:103), "Start anyway" (:108)
- Header "Double bass\nintonation trainer" (:138); streak "1 day streak" / "$streak day streak" (:148,152, also content descriptions)
- Content descriptions "Progress" (:160), "Settings" (:163)
- Focus card: "Today's focus" (:206); disabled reasons "Select at least two positions below to shift between." (:180), "Select positions below that can form a full chord." (:181); "${focus.subtitle}  ·  PB ${score}/${maxScore}" (:219)
- Section headers (uppercased at render): "Tuning & ear training" (:228), "Practice" (:242), "Tools" (:322)
- Cards: "Tune up" / "Check your open strings before you play." (:230-231); "Drone mode" / "A steady reference pitch to tune against by ear." (:236-237)
- Mode segmented buttons "Arco" (:250) / "Pizz" (:255)
- "Positions to practice (each combination scores separately)" (:259)
- "Note Accuracy" / "Land the note. First stable pitch counts." / "Best: {score} / {maxScore}" (:279-281)
- "Sustain" / "Arco only — a plucked note dies before a hold means anything." / "Hold it in tune. Don't let the ring reset." (:285-291)
- "Shift Trainer — ${level.shortLabel}" / "Select at least two positions to shift between." (:301-303)
- "Chords" / "Select positions that can form a full chord." / "Arpeggiate a triad — root, third, fifth, one note at a time." (:312-316)
- "Calibrate surroundings" / "Measure room noise and your soft playing so detection stays reliable." (:324-325)
- "Pitch Analyzer" / "See how the app hears your notes and explore your instrument's range." (:330-331)
- **Daily focus titles/subtitles** (`HomeViewModel.kt:38-51`): "Note Accuracy · arco" / "Land clean first notes with the bow.", "Shift · one string" / "Confident shifts along one string.", "Sustain · arco" / "Steady bow, steady pitch.", "Note Accuracy · pizz" / "First landings, plucked.", "Shift · across strings" / "String crossings that land in tune.", "Chords · arco" / "Arpeggiate a triad, tone by tone."

### Jargon (normal gameplay-facing)
- **"PB"** (:219) — abbreviation a 12-year-old may not parse.
- **"pitch detection", "calibrated", "microphone"** in the gate dialog — developer framing of an internal mechanism. "detection" (:325).
- **"Arco" / "Pizz"** are correct musical terms (acceptable for a bass student) but abbreviations "Pizz" and the mode `pizz`/`arco` string appear inconsistently capitalized elsewhere.

### Developer-oriented text
- "First stable pitch counts." (:281) and "Don't let the ring reset." (:291) leak the internal capture mechanism (first-stable-pitch freeze, ring-over) rather than describing the goal to a student.

### Inconsistencies
- Card is titled **"Note Accuracy"** here, **"Accuracy"** in Progress tabs (`ProgressScreen.kt:77`), and internally `EXERCISE_NOTE_ACCURACY`.
- **"Tune up"** (card) vs **"Drone mode"** (card) vs **"Drone"** (screen header) vs **"Pitch Analyzer"** (card) which opens a screen also titled "Pitch Analyzer" but internally the `debug` route.
- **"round"** used throughout ("Quit round", "Round complete") vs **"game"** ("Play a game", "trace games", "game capture") vs **"session"** ("today's suggested session" comment, Settings copy). Three words for the same concept.
- Capitalization is mixed: "Note Accuracy" (Title Case) vs "Tune up" / "Drone mode" (sentence case) vs "Shift Trainer".

### Expert mode: **not respected.**

---

## 2. Note Accuracy game — `ui/noteaccuracy/NoteAccuracyScreen.kt`
**Purpose:** Core game — read prompt (note + position), play it, first stable pitch is scored in cents/stars.

### Hardcoded strings
- Dot content descriptions "Note ${i+1}: next prompt / pending / missed / perfect / close" (:78-100) — repeated verbatim in Sustain, Shift, Chords.
- Drift banner "TRENDING SHARP\ncome down" / "TRENDING FLAT\ncome up" (:125)
- CountIn: "Get ready" (:165), "pick up your bass" (:176)
- Listening: "Play" (:194), "listening…" (:213)
- Reveal: "no note detected" (:239), "right note,\nwrong octave" (:245), "wrong note?" (:251), `"%+.1f cents"` (:256), "+${score}" (:265)
- Summary: "Round complete" (:284), "of ${maxScore}" (:293), `"average %.1f cents off"` (:300), "cents off per note" (:311), "${sum} of ${roundLength*3} stars" (:319)
- Personal-best lines: "New personal best! (was ${previousBest})" (:326), "First round on this setup — that's your best to beat." (:331), "Best: ${previousBest} — ${points} points to beat" (:336)
- Level suggestion: "You found every note with time to spare — that's progress!" (:347), "Several prompts ran out of time — more breathing room keeps it fun." (:348), "Switch to ${suggested.label} pace" (:354)
- Buttons "Quit round" (:150), "Let's go again" (:363), "Done" (:367)

### Jargon (normal gameplay)
- **"cents"** appears three times on the reveal/summary (:256, :300, :311) with **no expert-mode gate**. "TRENDING SHARP/FLAT" is drift-detector language.
- "wrong octave" / "first stable pitch" concepts.

### Inconsistencies
- "no note detected" (here) vs "no shift detected" (Shift) vs "—" (Chords) for the same timeout outcome.
- Reveal uses lowercase phrases ("no note detected", "wrong note?") but "Round complete" is capitalized.

### Expert mode: **not respected** — raw cents shown to all users.

---

## 3. Sustain game — `ui/sustain/SustainScreen.kt`
**Purpose:** Hold a note in tune and steady for a target duration; scored on intonation + steadiness.

### Hardcoded strings
- Same dot descriptions as §2.
- "Hold" (:139), `"play and hold %.0f s…"` (:168), "▼ too sharp" / "▲ too flat" (:174), `"%.1f s"` (:180)
- Reveal: "held!" (:270), `"best %.1f s"` (:271), "+${score}" (:278)
- Metric labels "In tune" / "Steady" (:286,288); `accuracyLabel`: "spot on", `"%.0f¢ sharp"`, `"%.0f¢ flat"` (:323-325); `steadinessLabel`: "rock steady", `"±%.0f¢"`, `"±%.0f¢ wobble"` (:332-334); "—" (:320,330)
- **Coaching lines** (`coachingText`, :339-349): "Rock steady and in tune.", "Steady bow — but sitting sharp. Place the note a hair lower.", "Steady bow — but sitting flat. Place the note a hair higher.", "Good pitch — but the note wandered. Even out your bow speed.", "Settle the pitch on a slow, even bow.", "Keep the note ringing longer — hold it steady the whole time."
- CountIn / best-lines / buttons identical to §2 ("Get ready", "pick up your bass", "Round complete", "of …", "New personal best!…", "First round on this setup…", "Best: …", "Quit round", "Let's go again", "Done").

### Jargon (normal gameplay)
- **"¢"** (cents symbol) in `accuracyLabel`/`steadinessLabel` — **not expert-gated**. The plain phrases ("spot on", "rock steady", "wobble") are good; but the numeric `±12¢ wobble` variant still exposes the symbol.
- "median" concept surfaces indirectly (`medianCents`), phrased acceptably as "In tune".

### Expert mode: **not respected.**

---

## 4. Shift Trainer — `ui/shift/ShiftScreen.kt` (+ `ShiftPool.kt`, `Positions.kt` ShiftLevel)
**Purpose:** Shift between two positions on cue; scored on shift-distance accuracy and landing.

### Hardcoded strings
- Dot descriptions + drift banner + CountIn + best-lines + buttons — all shared with §2.
- "then GO to" (:189), headers "that's not it — start on" / "Start on" (:201), "hold…" (:211), "GO —" (:221)
- Reveal: "no shift detected" (:249), "wrong note?" (:254), `"%+.1f cents"` (:261), **"shift distance"** (:267), "confident shift" (:286)
- Breakdown: `"start %+.0f¢  ·  "` + `"landed %+.0f¢"` (:312-313), "great shift — your start was sharp/flat" (:322)
- **ShiftLevel labels** (`Positions.kt:80-83`): "Basic — one string, 1↔4" / "Basic"; "Intermediate — one string, any finger" / "Intermediate"; "Advanced — across strings" / "Advanced".

### Jargon (normal gameplay)
- **"cents"** / **"¢"** (:261, :312-313) — **not expert-gated**.
- **"shift distance"** (:267) — semi-technical; combined with signed cents it is opaque to a beginner.
- ShiftLevel labels use "1↔4" (finger numbers) — fine for a bass student but terse.

### Inconsistencies
- "confident shift" (bonus) vs "fastBonus" concept; the Home card calls it "Shift Trainer" while the screen shows only "GO —"/note. Level names are "Basic/Intermediate/Advanced" here but PlayerLevel (Settings) also has "Beginner/Intermediate/Advanced/Expert" — two overlapping-but-different difficulty ladders using the word "Intermediate"/"Advanced", a real confusion risk.

### Expert mode: **not respected.**

---

## 5. Chords game — `ui/chords/ChordsScreen.kt` (+ `ChordPool.kt`)
**Purpose:** Arpeggiate a triad (root/third/fifth), one tone at a time; fingered tones scored, open strings shown but not scored.

### Hardcoded strings
- Dot descriptions + drift banner + CountIn + best-lines + buttons — shared with §2.
- "arpeggio — one note at a time" (:210), "that's not it —\nstart on ${note}" (:219), "play" (:227), "open string" (:238) / "open" (tone strip :185 and reveal :296), "listening…" (:246)
- Reveal per-tone: "—" (:297, timeout), "wrong?" (:298), `"%+.0f"` cents (:300); "+${score}" (:269)
- **Chord names** (`ChordPool.kt chordName`, :53-59): "Majeur" / "mineur" (solfège) and "major" / "minor" (letters), e.g. "Ré Majeur", "D major".

### Jargon (normal gameplay)
- **"arpeggio"**, **"triad"**, **"root, third, fifth"** (Home subtitle) — legitimate music theory, but on the edge for a 12-year-old beginner; worth a tooltip/onboarding.
- Signed cents `%+.0f` per tone (:300) — **not expert-gated**.

### Inconsistencies
- "open string" (:238) vs "open" (:185, :296) for the same concept within one screen.
- Note-of-triad shows `pitchClassName` (no octave) while other games show `displayName` (with octave) — inconsistent note formatting across games.

### Expert mode: **not respected.**

---

## 6. Tune Up — `ui/tune/TuneUpScreen.kt`
**Purpose:** Pre-session open-string tuner (E A D G) with a cents needle.

### Hardcoded strings
- Top bar "Tune up" (:68); back content desc "Back" (:71)
- "Play each open string" (:87); check content desc "${string} in tune" (:128)
- `"%+.1f"` (:149), "cents sharp" / "cents flat" (:155), "—" (:163), "play an open string" (:168)
- "All strings in tune — go play!" (:177); button "Done" / "Skip" (:186)
- Needle content desc "Tuning needle" (:207)

### Jargon (normal gameplay)
- **"cents sharp" / "cents flat"** (:155) shown to everyone — **not expert-gated**. This is a tuner readout; for a beginner "sharp/flat" is fine but the "cents" number is jargon.

### Expert mode: **not respected** (arguably acceptable for a tuner, but flagged for consistency).

---

## 7. Drone — `ui/drone/DroneScreen.kt`
**Purpose:** Play a steady reference pitch (by string root or any pitch class) to tune against by ear; no scoring, no mic.

### Hardcoded strings
- "Drone" (:85), "Reference pitch for ear training — no scoring" (:87)
- "sounding at ${note}" + " · with fifth" (:103-104)
- "Playing" / "Stopped" (:117-124, also content descriptions), "Stop"/"Play" content desc + labels (:139-142)
- "Add fifth" (:150), "Open strings" (:156), "Any note" (:177), "Volume" (:205), `"%.0f%%"` (:210), "Back" (:222)

### Jargon (normal gameplay)
- **"sounding at ${MIDI note name}"** (:103) — exposes the sounding octave (e.g. "Sol3"); the phrase "sounding at" is engineer-speak for "you'll hear it as".
- **"with fifth" / "Add fifth"** — music theory term; acceptable but undefined for a beginner.
- "pitch class" appears only in comments, not UI (good).

### Expert mode: **not respected.**

---

## 8. Calibrate surroundings — `ui/calibrate/CalibrateScreen.kt`
**Purpose:** Two-step noise-floor calibration (measure room noise, then soft playing) to set a mic gate.

### Hardcoded strings
- Top bar "Calibrate surroundings" (:65), "Back" (:68)
- Idle: "Verifying your room's noise floor against your softest playing to ensure reliable pitch detection." (:88), "Step 1: Room noise" (:95), "Don't play. Leave the room sounding as it normally does." (:101), "Start" (:109)
- Measuring: "Step 1: room noise" / "listening…" (:114-116), "Step 2: soft playing" / "play softly…" (:146-148)
- Transition: "Get ready to play SOFTLY" (:123), "Next: soft bowing or gentle plucks" (:131)
- Results: "Calibration Results" (:220); verdicts "Clear separation" / "Tight separation" / "No separation" (:232-234); `"noise up to %.0f · soft playing from %.0f"` (:256); explanations `"Gate set to %.0f — room noise is successfully ignored."` (:264), `"Gate set to %.0f, but room noise is high — very quiet notes may be missed."` (:265), "Your soft playing can't be told apart from the room's noise. Please practice somewhere quieter." (:266)
- "Saved." (:283), buttons "Use this gate" (:289), "Measure again" (:292), "Try again (quieter room)" (:297), "Cancel" (:161)

### Jargon (reachable in normal UI — this tool is on the Home "Tools" section and the pre-game gate)
- **"noise floor"** (:88), **"pitch detection"** (:88), **"gate" / "Gate set to"** (:264-265, :289) — all developer/DSP terms shown to a beginner.
- **"separation"** (verdict labels :232-234) — abstract; a student won't know noise-vs-signal separation.
- Bare numeric `noise up to 42 · soft playing from 58` (:256) is a raw level readout with no unit or explanation.

### Developer-oriented text
- "Use this gate" (:289) — asks the student to accept an internal parameter by its engineering name.

### Expert mode: **not respected** (and this content is arguably too technical even for experts to be unexplained).

---

## 9. Full calibration Wizard — `ui/calibrate/WizardScreen.kt`
**Purpose:** Per-phone detection setup: quiet measurement + bowed then plucked prompted notes; produces a per-device detection profile. Reachable from Onboarding, Settings, and the pre-game gate ("Run full calibration wizard").

### Hardcoded strings (high density — ~40 strings)
- Top bar "Full calibration" (:76), "Back" (:79)
- Intro: "Sets up pitch detection for this phone. First a quiet moment to measure the room, then two phases: BOWED, then PIZZ (plucked). Takes about two minutes." (:100), "Have your bass ready and tuned. Each note starts recording automatically after a short countdown, so you never have to put the bass down." (:108), "Start" (:117)
- Quiet: "Keep quiet…" (:122), "Measuring the room background." (:125)
- AwaitPlay: "Didn't catch that — let's try again." (:145), "PLUCK — " / "BOW — " + stringHint (:163), "Get ready… ${secsLeft}" (:172), "Start now" (:181)
- Recording: "Pluck & let it ring…" / "Keep bowing…" (:193), "hearing ${note}" / "listening…" (:211-212)
- PizzTransition: "Now it's time for pizz" (:221), "Put your bow away and get ready" (:228)
- Analyzing: "Analyzing…" (:250)
- Summary (`SummaryContent`, :306-448): "Microphone" (:309), "Room" (:313); verdicts "clear of noise" / "tight — soft notes may drop" / "too noisy to set a gate" (:331-333); per-note "detected" / "unreliable" (:357); "Pizz (plucked) — octave-settle ${ms} ms" / "Pizz (plucked) — no octave drift, no settle needed" (:368-370); pizz-check "correct octave" / "octave drift" / "not detected" / "off pitch" (:377-383); "${note} pizz" (:386); "Pizz lock timing" / "wait ${ms} ms, hold ${ms} ms" (:407-409)
  - Long warnings: "Plucked notes still occasionally read an octave high on this phone… save a pizz snippet from the Pitch debug screen." (:416), "Plucked notes settle slowly on this phone — the lock waits as long as it can… best fit measured." (:425), "Octave handling was adjusted for this phone's microphone." (:433), "High notes may occasionally read an octave low on this phone… save a snippet from the Pitch debug screen." (:441)
  - "The room was too noisy to finish — nothing was saved. Try again somewhere quieter." (:454)
  - "Saved — all games now use these settings." (:466), buttons "Save calibration" (:473), "Discard" (:476), "Close" (:282,:460), "Cancel" (:290)
- Failed: "Calibration failed" (:269) + `s.reason` (dynamic)

### Jargon (reachable from Onboarding + Settings + gate)
- Extremely heavy: **"octave-settle", "octave drift", "off pitch", "Pizz lock timing", "wait/hold ms", "octave high/low", "settle", "gate", "detection"** (:368-441). This entire Summary block reads like a DSP diagnostic report.
- **"Pitch debug screen"** referenced by name (:416, :441) — points the student at a debug screen; and it's actually labeled "Pitch Analyzer" in the UI, so the reference name is also wrong.
- "BOWED / PIZZ (plucked)" — the app oscillates between "pizz", "PIZZ", "Pizz", "plucked", "PLUCK".

### Developer-oriented text
- The whole Summary is written for the developer diagnosing a device, not for a child completing setup. Notably the "save a pizz snippet from the Pitch debug screen" instructions.

### Expert mode: **not respected.** This is first-run onboarding content that no beginner can parse.

---

## 10. Onboarding / first-run — `ui/onboarding/OnboardingScreen.kt` (+ `AppNav.kt` routing)
**Purpose:** First-launch welcome; routes into the full calibration Wizard or lets the user skip. Gated on `settings.onboardingCompleted` (`AppNav.kt:73`).

### Hardcoded strings
- Skip dialog: "Skip calibration?" (:46), "The app works best when calibrated to your specific phone and room. Without it, pitch detection may be unreliable.\n\nYou can always find the full calibration wizard later in Settings." (:48), "Skip anyway" (:52), "Cancel" (:57)
- **"Welcome to Bass Pitch"** (:81) ← **wrong/third app name**
- "A specialized trainer for double bass intonation. To give you accurate feedback, the app needs to understand your instrument and your room." (:90)
- "Room calibration" / "We'll measure the background noise so it doesn't interfere with your playing." (:100-101)
- "Instrument setup" / "You'll play a few notes so we can tune the detection specifically for your bass." (:106-108)
- Buttons "Get Started" (:118), "Skip for now" (:127)

### Jargon (first-run, beginner-critical)
- **"calibrated", "pitch detection", "the detection"** (:48, :90, :108) — the very first screen a new student sees uses developer terms.
- **"specialized trainer"** (:90) — dry, adult tone.

### Inconsistencies
- **"Bass Pitch"** (title) ≠ "Intonation Trainer" (app name) ≠ "Double bass intonation trainer" (Home).
- Button style mixed: "Get Started" (Title Case) vs "Skip for now" (sentence case). Elsewhere primary buttons are "Start"/"Done".
- The onboarding then launches the Wizard (§9) whose summary is deeply technical — a jarring first-run experience.

---

## 11. Settings — `ui/settings/SettingsScreen.kt`
**Purpose:** All app configuration + backup/restore + debug trace toggle. Section headers: DISPLAY, NOTATION & TUNING, GAMEPLAY, FEEDBACK, DETECTION & CALIBRATION, YOUR DATA, DEBUG.

### Every setting (label — description — beginner-appropriateness)
1. **Expert mode** (:191-193) — "Show the technical detail — exact cents, percentages and deviations — across the app. Off keeps things in plain language for younger or newer players." → *Ironic:* claims to be "across the app" but is honored only in Progress. **Overpromises.**
2. **Note names** (:209) — "How notes are written throughout the app." choices "Do Ré Mi" / "C D E". → Beginner-OK.
3. **Mix sharps & flats** (:218-223) — long paragraph mentioning "enharmonic", "black-key note", "La♯", "Si♭", "Naturals never change", "intonation one". → **Too technical / too long** ("enharmonic" concept, "intonation").
4. **Concert pitch (A4)** (:238-240) — "The reference your ensemble tunes to. 440 Hz is standard; some orchestras use 442." control shows "${a4} Hz". → **"A4", "Hz", "concert pitch"** = jargon. Reasonable for advanced, opaque for a 12-year-old.
5. **Player level** (:257-260) — "How much time you get to read the prompt and find the note…" chips "Beginner/Intermediate/Advanced/Expert"; "Up to ${s} s to start each note." → OK, but see ladder clash with ShiftLevel (§4).
6. **Difficulty** (:283) — "How forgiving the scoring is about cents off target." chips Relaxed/Standard/Strict (from `Scoring.kt` enum, rendered capitalized); "Points reach zero at ±${n} cents." → **"cents"** exposed to all.
7. **Round length** (:302) — "Notes per round. Scores only compare within the same length." 5/10/20. → OK ("round" term).
8. **Chord fingering** (:314-317) — "In the Chords game, a note can often be played several ways…"; chips + blurbs from `ChordFingering` enum (`ChordPool.kt:21-41`): "Natural" / "Closest hand shape — how you'd play it in a piece; open strings where they fall.", "Prefer fingered" / "Finger notes in your positions for practice; open strings only when unavoidable.", "Prefer open" / "Use open strings wherever a tone sits on one (open strings aren't scored)." → Reasonable but advanced.
9. **Sound feedback** (:341) — "Chime when you land a note, buzz when you miss…"; "Volume (release to hear it)" (:359); mute warning "Your phone's media volume is muted — game sounds stay silent no matter what this slider says." (:387) → Beginner-OK (good tone).
10. **Pitch drift warning** (:396) — "Warns when everything you land is consistently sharp or flat — a sign to reset your inner reference instead of learning wrong pitches." → OK, slightly wordy.
11. **Count right note, wrong octave as correct** (:411-416) — long paragraph: "…detected the right pitch but a whole octave off… Plucked low notes sometimes read an octave high (a weak string fundamental the mic loses while its overtone rings on); this keeps that detector quirk from counting against you." → **Deeply technical / developer-facing** ("fundamental", "overtone", "detector quirk", "the mic").
12. **Noise gate** (:430-433) — "Sound below this level is ignored as room noise. Calibrate measures your room and your soft playing, and sets it automatically." control text "gate at level ${n} / 100"; buttons "Calibrate surroundings" (:446), "Full calibration (new phone or double bass)" (:450). → **"Noise gate", "gate at level"** = jargon.
13. **Backup & restore** (:455-458) — "Export your whole practice history to a file… Nothing is stored in the cloud — this file is your only copy." buttons "Export backup" (:464) / "Import backup" (:469). → Beginner-OK.
14. **Record & trace games** (DEBUG, :477-481) — "Records the whole game — audio + detection + game events — so a real round can be replayed offline to diagnose detection. Files appear in Recordings tagged \"game-trace\"; share them for analysis. Leave off for normal play." button "View traces" (:499). → **Developer-only**, but it sits in the normal Settings screen under a "Debug" header (reachable by anyone). Contains "detection", "game events", "trace", "game-trace".
- Backup dialogs: "Import backup" (:140), "Merge keeps what's on this phone and adds any rounds from the backup that aren't already here. Replace wipes this phone's history first." (:143), "Merge"/"Replace…"/"Cancel" (:148-157); "Replace all data?" (:166), "This permanently deletes all history, personal bests and achievements… This can't be undone." (:167), "Replace everything" (:169); status strings "Imported ${n} rounds…" (:93), "Import failed: …"/"Export failed: …" (:96,:131), "Backup created — save it somewhere safe." (:128); "Share backup" chooser (:125).
- Footer "Settings" title (:187), "About & licenses" (:506), "Back" (:509).

### Inconsistencies
- Section header "Detection & calibration" mixes a student concept (calibration) with a dev concept (detection). "Debug" section header shown in a normal user's settings.
- **"cents"** used in Difficulty description with no expert gate, while Expert mode purports to control exactly that.
- Difficulty chips are Relaxed/Standard/Strict but rendered via `d.name.lowercase().replaceFirstChar{uppercase}` — style differs from the hardcoded PlayerLevel chip labels.

### Expert mode: This screen **reads** `expertMode` only to render the toggle itself; none of its other descriptions adapt.

---

## 12. Progress — `ui/progress/ProgressScreen.kt` (+ `ProgressViewModel`, `metrics/Coaching.kt`)
**Purpose:** Per-exercise history: score chart, rounds/best/streak, weekly coaching summary, per-position mastery / bow-control metrics. **The one screen that respects expert mode.**

### Hardcoded strings
- Top bar "Progress" (:106), "Back" content desc (:109), achievements action "${unlocked}/${total}" + content desc "Achievements" (:115,:123)
- Exercise tabs (:76-81): "Accuracy", "Sustain", "Shift", "Chords"
- Empty state: "No rounds yet" (:202), "Play a game to see your progress here." (:207)
- Stat trio labels "rounds" (:222), "best" (:223), "day streak" (:225); "—" placeholders
- Coaching card "This week" (:310)
- **Plain summary** (beginner): "You practiced ${n} time(s) this week." (:323-324), "Try to hold each note long and steady — your bow control is below." (:326), "Play a few more rounds this week to see how in tune you're playing." (:331), "You found the right note ${p}% of the time." (:334); intonation sentences (:371-375): "Your notes are landing right in tune — amazing work!", "Your notes are landing nicely in tune.", "Your notes are getting closer to in tune — keep practising!"
- **Trend lines** (:378-400): "Keep playing to see how you improve each week.", "That's more in tune than last week!", "A little off from last week — you'll get it back.", "About the same as last week."
- **Expert summary** (:340-362): "${n} rounds", "Not enough scored notes this week for a verdict (need ${MIN}+).", "Intonation ${c}¢ (last wk ${c}¢, ${d}¢ looser/tighter)", "Intonation ${c}¢ this week", "${p}% notes landed", "${p}% clean"
- Mastery: "Accuracy by position" (:426), band labels from `MasteryBand` ("Locked in" / "Solid" / "Developing", `Coaching.kt:31-33`), "keep playing" (:480), bias labels from `Bias` ("centered" / "a bit flat" / "a bit sharp"; expert: "runs ${n}¢ flat/sharp", `Coaching.kt:69-81`), expert detail "${c}¢ · ${count}" (:473)
- Sustain metrics: "Bow control" (:516), "Average hold" + `"%.1f s"` (:518), "Steadiness" + `${c}¢` (:519), "Bow changes / note" + `"%.1f"` (:520)
- **Insight lines** (`Coaching.kt selectInsight`, :196-219): "Your ${mode} ${pos} position lands a little flat — try aiming a touch higher.", "…lands a little sharp — try aiming a touch lower.", "You're getting more in tune than last week — keep it going!", "Your ${mode} ${pos} position is your anchor — nicely in tune."
- "Done" (:175)

### Jargon
- **Beginner (non-expert) path is genuinely plain-language** — this is the model the rest of the app should follow. Only leak: "¢" appears in `SustainMetrics.Steadiness` (:519) even in non-expert view (Sustain metrics aren't gated), and mode names "arco"/"pizz" appear in insight lines.
- Expert path intentionally uses "¢", "Intonation", "tighter/looser", "clean" — acceptable (gated).

### Inconsistencies
- Tab label **"Accuracy"** vs Home card **"Note Accuracy"**.
- MasteryBand "Locked in / Solid / Developing" is a *fourth* vocabulary for quality, distinct from the games' star ratings and from "excellent/close/off" result colors.

### Expert mode: **respected** (the reference implementation).

---

## 13. Achievements — `ui/achievements/AchievementsScreen.kt` (+ `game/Achievements.kt`)
**Purpose:** Grid of 25 achievements (unlocked emoji vs locked padlock) with progress bar.

### Screen strings
- "Achievements" (:52), "${n} of ${total} unlocked" (:55), "Locked achievement" content desc (:117), "Back" (:83)

### Achievement names + descriptions (`Achievements.kt:38-153`) — all hardcoded, 25 pairs
"First round"/"Complete your first round."; "Bullseye"/"Land a note within 2 cents."; "Sharpshooter"/"Finish a round averaging within 10 cents."; "Perfect round"/"Three stars on every note of a round."; "All four strings"/"Score on all four strings in one round."; "A hundred notes"/"Play 100 scored notes in total."; "A thousand notes"/"Play 1000 scored notes in total."; "Marathon"/"100 notes in a single day."; "Week streak"/"Practice seven days in a row."; "Month streak"/"Practice thirty days in a row."; "Steady hand"/"A Sustain round with every hold succeeded."; "Lightning shift"/"A three-star shift landed in under a second."; "Triads in tune"/"A Chords round with every scored tone in the stars."; "Sniper"/"Finish a round averaging within 5 cents."; "Tight group"/"Every note in a round within 5 cents."; "Triple bullseye"/"Three notes within 2 cents in a single round."; "New record"/"Beat one of your own personal bests."; "Early bird"/"Practise before 7 in the morning."; "Night owl"/"Practise at 11 at night or later."; "Pizzicato precision"/"Finish a pizz round averaging within 12 cents."; "Arpeggio ace"/"A Chords round with three stars on every tone."; "Unwavering"/"A Sustain round with three stars on every hold."; "Sure-footed"/"A Shift round with three stars on every landing."; "Position explorer"/"Score across four or more positions in one round."; "Five hundred notes"/"Play 500 scored notes in total."

### Jargon
- **"cents"** in 6 achievement descriptions (Bullseye, Sharpshooter, Sniper, Tight group, Triple bullseye, Pizzicato precision) — **not expert-gated**; a beginner reading their achievement goals meets "within 2 cents".
- "triad"/"arpeggio"/"pizz"/"pizzicato" — music terms.

### Inconsistencies
- Mixed spelling **"Practice"** (Week/Month streak) vs **"Practise"** (Early bird, Night owl) — US vs UK within the same list.
- Descriptions mix imperative ("Complete…", "Land…") and noun-phrase ("A Sustain round…") styles.

---

## 14. About — `ui/about/AboutScreen.kt`
**Purpose:** App description + GPL license + attribution, with expandable full license text.

### Strings
- "About" (:43), "Double bass intonation trainer — a deliberate-practice game, not a tuner. It freezes the first stable pitch of every note so you train accurate landings instead of correcting after the fact." (:46)
- "License" (:55) + GPL blurb (:57), "Attribution" (:63) + "Pitch detection adapted from Tuner, © Michael Moessner, GPL-3.0-or-later.\nhttps://codeberg.org/thetwom/Tuner" (:65)
- "Show full license text" / "Hide license text" (:74), "Back" (:89)

### Jargon
- "freezes the first stable pitch" (:46) — again exposes internal capture mechanism to justify the app; developer framing.
- License/attribution content is appropriately technical (expected on an About screen).

---

## 15. Recordings / Game traces — `ui/recordings/RecordingsScreen.kt`
**Purpose:** List/play/delete/email saved WAV+log recordings. Two modes: "Recordings" (from Debug) and "Game traces" (from Settings → trace toggle).

### Strings
- Titles "Game traces" / "Recordings" (:247); subtitles "Full game or calibration traces for analysis." / "Snippets and long captures. The envelope zips the audio plus its detection log to send." (:249-251)
- Empty "No traces saved yet." / "Nothing recorded yet — use the buttons on the Pitch Analyzer screen." (:257-259)
- **Config summary** `"window $window · gate $n · src $n"` (:83-94) — raw DSP config
- Row metadata `"%s · %d s · %.1f MB"` (:280); content descs "Stop"/"Play" (:307), "Send to developer" (:312), "Delete" (:317)
- Delete dialog "Delete ${name}?" / "Removes the audio and its detection log from the phone." (:199-204)
- Email dialog "Send to developer?" (:219), "This zips ${name} (the full microphone audio plus its detection log) and opens your email app addressed to $FEEDBACK_EMAIL. It contains everything the mic picked up while recording, not just your playing." (:221), "Send"/"Cancel"
- Email subject "Intonation Trainer trace: ${name}" (:357), body "Attached: ${name}.zip (audio + detection log).\n\nWhat happened:\n" (:360), chooser "Send trace to developer" (:365)
- "Back" (:330)

### Jargon (mostly debug-reachable, but "Game traces" mode is reachable from Settings)
- **"window", "gate", "src", "detection log", "WAV", "trace", "MB"** — heavy DSP/dev vocabulary. Acceptable for a debug surface, but "Game traces" is reachable by any user who toggles the Settings "Record & trace games" switch.
- Hardcoded developer email `feedback@drakarah.be` (:61).

---

## 16. Debug / Pitch Analyzer — `ui/debug/DebugPitchScreen.kt` (+ `DebugViewModel`)
**Purpose:** Live pitch diagnostics, capture freeze inspection, note-sweep coverage grid, snippet/long-capture recording. **Reachable from Home "Tools" → "Pitch Analyzer".**

### Strings (very high jargon density — expected for debug, but note reachability)
- "Pitch Analyzer" (:153), "Microphone permission is required." / "Grant permission" (:156-158)
- Live readout `"%+.1f cents"` (:178), "—"/"listening…" (:185-194)
- Diagnostic rows: "raw", "smoothed", "accepted", "noise", "harmonic energy" (:203-213), each `"%.2f Hz"` / `"%.3f"`; "level ${n} / 100 · noise gate ${n}  (ignored as noise)" (:220)
- "game capture: $captureLabel" (:242), content desc "Capture stable" (:253), freeze `"%s  %+.1fc"` (:260), `"%.2f Hz · stable in %d ms · %s"` (:270), "no stable note captured yet" (:277)
- "Note sweep" (:289), engine config "window $windowSize @ $sampleRate Hz, overlap $overlap, source $audioSource, sensitivity ${n}" (:293)
- "Save last 8 s (WAV + log)" (:303), "Stop & save long capture" / "Long capture (up to 2 min) — for test recordings" (:320-321), content descs "Start/Stop long capture" (:314); "Manage recordings" (:325), "Back" (:331)
- SweepView: "Note sweep" (:439), "${n} / $total" + "notes game-ready" (:449-456), banner "Wait for quiet"/"Capturing…"/"Play a note" (:474-476), "Reset"/"Exit sweep" (:534-535); ModeToggle content descs "Switch arco / pizz" (:357), mode text "arco"/"pizz"
- Snackbar messages come from `DebugViewModel.snippetMessage` (dynamic).

### Jargon
- Essentially every string: **"cents", "Hz", "raw/smoothed", "harmonic energy", "noise gate", "level", "window", "sample rate", "overlap", "source", "sensitivity", "WAV", "log", "stable in ms", "capture"**. This is fine to remain technical (it is a diagnostic screen) — **BUT** it is reachable from the normal Home screen under the friendly name "Pitch Analyzer" with the friendly subtitle "See how the app hears your notes and explore your instrument's range." A curious 12-year-old will land here and see full DSP output.

### Recommendation flag
- The Wizard summary and this screen cross-reference each other ("save a snippet from the Pitch debug screen"), but the label is "Pitch Analyzer" — naming mismatch. (Resolved in the overhaul plan: stays prominent during beta, DSP numbers collapse into a "Technical details" expander.)

### Expert mode: not respected (acceptable for debug, but reachability is the issue).

---

## 17. Common components (shared text)
- **`TraceFeedbackPrompt.kt`** (round summaries when tracing on): "Trace recorded — how did that round go?" (:41), "Went well" (:58), "Had issues" (:71), "What happened?" (:78), "Save note" (:83). → **"Trace"** shown to any user who enabled tracing.
- **`AchievementUnlocks.kt`**: "${emoji} ${title} unlocked!" (:22).
- **`ImprovementLine.kt`** (on Accuracy/Shift/Chords summaries): `"%.1f cents average — last week %.1f"` (:31) + content descs "Improved"/"Worse". → **"cents"**, **not expert-gated**.
- **`ProgressDots.kt` / `StarRating.kt` / `RequireMicPermission.kt`** — check `RequireMicPermission` for a permission-rationale string (not read in full during this audit; likely contains user-facing mic-permission copy — extract during i18n).

---

## Cross-cutting findings for the rewrite plan

**A. i18n readiness:** Start from zero — no string externalization exists. Every quoted string above must move to `strings.xml` (with plurals for "time/times", "day streak", "rounds", "stars"). Many use `String.format`/interpolation that will need `<string>` placeholders and quantity strings.

**B. Expert-mode gap (highest priority for plain language):** Raw `cents`/`¢`/`Hz` appear during **normal gameplay** in NoteAccuracy, Sustain, Shift, Chords, TuneUp, Drone, the `ImprovementLine` and `SustainMetrics` common views, **and** in 6 Achievement descriptions and the Difficulty setting — none gated by `expertMode`. Only Progress adapts. The plain-language rewrite should either (a) route all these through an expert-mode check like Progress does, or (b) replace numeric cents with the plain-word vocabulary already proven in `Coaching.kt` ("spot on", "a bit sharp", "Locked in").

**C. Terminology unification needed:**
- App name: **Bass Pitch / Intonation Trainer / Double bass intonation trainer** → pick one.
- Activity unit: **round / game / session** → pick one.
- Exercise name: **Note Accuracy / Accuracy** → pick one.
- Tool name: **Pitch Analyzer / Pitch debug screen** → pick one.
- Plucked mode: **pizz / Pizz / PIZZ / pizzicato / plucked** → standardize.
- Quality vocabularies proliferate: star counts, result colors "excellent/close/off", MasteryBand "Locked in/Solid/Developing", verdicts "Clear/Tight/No separation", check states "detected/unreliable".
- Difficulty ladders collide: PlayerLevel (Beginner/**Intermediate/Advanced**/Expert) vs ShiftLevel (Basic/**Intermediate/Advanced**).
- "open string" vs "open" within Chords; "no note/shift detected" vs "—" across games.
- US/UK spelling: "Practice" vs "Practise" (Achievements); "calibration"/"calibrate".

**D. Developer-facing copy leaking into user UI (rewrite targets):**
- Settings "wrong octave" description ("fundamental", "overtone", "detector quirk", "the mic").
- Entire Wizard Summary block (octave-settle, drift, lock timing, ms).
- Calibrate/Settings "noise gate", "noise floor", "separation", "pitch detection".
- Home gate dialog ("pitch detection has not been tuned to this phone's microphone").
- Onboarding ("pitch detection may be unreliable", "the detection").
- Recurring "first stable pitch" mechanism-explanations (Home, About).

**E. Debug reachability:** "Pitch Analyzer" (fully technical) is on the default Home "Tools" section; "Game traces" + `TraceFeedbackPrompt` become visible to any user who flips the Settings "Record & trace games" switch (which lives under a "Debug" header inside normal Settings). (Resolution per plan §2/§5H: stays visible during beta, reframed.)

**Key files for the i18n/rewrite effort:** all files under `app/src/main/java/be/drakarah/intonation/ui/**`, plus `game/Achievements.kt`, `game/Positions.kt` (ShiftLevel), `game/PlayerLevel.kt`, `game/Scoring.kt` (Difficulty), `game/ChordPool.kt` (ChordFingering, chordName), `metrics/Coaching.kt` (MasteryBand/Bias/WeekTrend labels), `music/NoteSpec.kt` (note-name tables), and `ui/home/HomeViewModel.kt` (DailyFocus rotation). Target resource file: `app/src/main/res/values/strings.xml` (currently 1 string).
