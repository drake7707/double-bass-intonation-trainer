# Things to verify on the real bass

Running checklist of everything waiting for a hands-on check. Items get moved to *Verified*
with the date once confirmed. Ask Claude for "the checklist" anytime.

## Pending

### 2026-07-15 Pizz scored sharp on the attack: per-rig capture timing (your VERIFY)
You asked whether pizz Note Accuracy was "too quick on the trigger to lock before the pitch
stabilised." **Verified from your full game traces: yes, partly — and it's not you.** A plucked
attack reads sharp and settles flatter; the shipped lock (skip 60 ms / hold 150 ms) could freeze
that transient and score a correctly-played pizz note **~10–20 ¢ sharp** (worst on short/loud
attacks; long-held notes were already accurate). Arco was checked the same way and is fine (bowed
onset is gradual; scored within ~3 ¢ of where notes settle) — so this is pizz-only.
- [ ] **Pizz capture timing is now calibration-owned.** The wizard's pizz phase measures the
      smallest attack-skip / hold that lands the freeze on where the note *settles*, per rig. On the
      reference-rig takes it picks **200/200** (worst freeze error 10–18 ¢ → ~3 ¢). **Re-run the full
      calibration** to pick it up (defaults stay at the old 60/150 until you do). Then play a pizz
      round and confirm pizz notes no longer read sharper than they feel — especially firm, short
      plucks. Save a trace if any still do.
- [ ] **Calibration pizz phase now records stopped (fingered) notes too** (Sol1, Do2, Fa2, Si♭2,
      plucked) — open strings alone don't represent a fingered pluck's attack. The summary shows the
      chosen "Pizz lock timing (wait X ms, hold Y ms)". Confirm the fingered prompts are clear.
- [ ] **Arco/pizz phase switch is now unmissable** (your feedback that testers missed it): each play
      prompt shows a full-width colour-coded banner — "ARCO — BOWED" vs "PIZZICATO — PLUCKED, put the
      bow down". Confirm the switch is obvious mid-wizard.
      Guarded by `WizardCorpusTest` (`settledPitchHz` tracks the sustain not the attack;
      `choosePizzTiming` never regresses the reference freeze error). See DETECTION.md §2.2.
      (Addresses your [REVIEW/EVALUATE] "expand the calibration corpus beyond open strings —
      include representative stopped notes" — done for the pizz timing/settle fit.)

### 2026-07-13 Octave misdetection: relief valve + calibration/repro fixes (your feedback)
From testing: pizz still occasionally scores "right note, wrong octave" — and we found *why*.
Your second calibration's single Mi pluck read clean (Mi first, before other strings ring) so it
set **0 ms** (guard off). And the real culprit is **sympathetic resonance**: once the other
open strings ring, the low note's 2nd harmonic is boosted and the detector latches the octave
(Mi2) — the fundamental is essentially gone, so the time-based settle can't help. Your priority
order, addressed:
- [ ] **"Count right note, wrong octave as correct"** — new setting (Settings → Detection &
      calibration), **ON by default**. Play the right note, get the wrong octave from the mic, and
      it now **scores your intonation** instead of a miss. Confirm a pizz round no longer punishes
      you for octave misdetection; toggle it off to see the old "right note, wrong octave" behaviour.
- [ ] **Calibration pizz phase reordered** to pluck the strings **high→low (Sol, Ré, La, Mi)** and
      asks you to *keep the other strings ringing* — so it measures the low strings under the
      resonance that actually causes the problem, instead of the lucky first pluck. Re-run the full
      calibration and check the pizz summary now reflects a realistic window (likely non-zero).
- [x] Not user-visible, but relevant: **every recording's JSONL now stores the full detection
      config** (all octave-correction knobs, not just gate). So the next snippet you save can be
      replayed offline *exactly* as your rig ran it — which is what we need to tune the real
      octave discriminator (see below) without guessing.

- [ ] **Calibration trace (to feed the real fix).** Turn on Settings → Debug → "Record & trace
      games", then re-run the full calibration. It now saves every take (arco strings, high note,
      and the pizz strings under resonance) as `calibration-*.wav`/`.jsonl` in Recordings, each
      with the known target note and your exact config. **Share those** — they're the per-rig,
      ground-truth data to *fit and validate* the octave-down discriminator (below) without
      hard-coding anyone's rig. (Reproduction already confirmed: replaying your 07:14 snippet
      under its recorded config reproduces the 28% Mi2 exactly.)

- [ ] **Separate pizz octave-down correction (the real fix — LANDED).** From your calibration
      trace: pizz now has its **own, looser** octave-down thresholds (arco stays strict so Do3/Ré3
      are never halved). Fit per-rig by the wizard's pizz phase and validated against your own
      ground-truth takes — on your rig the sustained Mi2 read drops from ~28% to ~0 with no
      genuine note halved. **Re-run the full calibration** (so the pizz knobs are fit), then a pizz
      round: the octave misdetection should now be rare *at the source*, not just forgiven. If you
      still hit it, save a pizz snippet — its header carries the full config so it reproduces
      exactly.
      Guarded by `PizzOctaveDownTest` (your resonance snippet: arco knobs leave the octave, pizz
      knobs collapse it) + `WizardCorpusTest` (the per-rig fit logic).

### 2026-07-12 Pizz octave fix + calibration wizard pizz phase & auto-start (your feedback)
Two things from your 2026-07-12 batch. **(1)** The occasional pizz "right note, wrong octave"
(your Fa#1 snippet): every pluck's attack reads an octave high for a moment, then settles onto
the fundamental — the capture used to freeze the octave. New **octave-settle** guard (pizz only)
waits out that transient and takes the fundamental. It only ever folds down, and only when a real
octave-below appears, so a genuinely high note is never dragged down. **(2)** The wizard was
arco-only and made you set the bass down each prompt. It now has a short **pizz phase** that
*measures* the octave-settle window for your rig (not a hard-coded number — a rig with no
artifact gets none) and every prompt **auto-starts after a countdown**. Diagnosed + regression-
tested offline from your snippet (`PizzOctaveSettleTest`); needs your hands-on confirmation.
- [ ] **Re-run the full calibration.** Text is bigger — readable while holding the bass at ~2 m?
- [ ] **No button-tapping between prompts** — each note shows a "recording starts in 4…3…2…1"
      countdown then starts on its own. "Start now" works if you're ready early. (Applies to the
      bowed prompts AND the new pizz prompts.)
- [ ] **New pizz phase at the end**: prompts to *pluck* each open string (Mi, La, Ré, Sol),
      count-in then records. Summary shows "Pizz — octave-settle NNN ms" (or "no octave drift")
      and a ✓/⚠ per string.
- [ ] **Then play a pizz Note-Accuracy round on Fa#1 (and low notes generally):** the octave
      error should be gone — it scores Fa#1, not "wrong octave". (If you still see it, save a pizz
      snippet from Pitch debug so we can re-measure.)
- [ ] **Arco is unaffected** — bowed rounds behave exactly as before (no new latency, no octave
      weirdness).

### 2026-07-12 More achievements + a dedicated gallery grid (your request)
Grew the set from 13 → 25 badges and moved the gallery off the Progress feed onto its own
grid screen. All the unlock rules are pure logic and unit-tested (`AchievementsTest`), so this
is mostly a "do they actually fire in real play and does the screen look right" check.
- [ ] **Progress page shows a summary card**, not the old scrolling strip: "Achievements
      N/25 ›" with a row of your most recent badge emoji. Tapping it opens the grid.
- [ ] **Grid gallery.** Adaptive columns, unlocked badges first, a progress bar up top, locked
      ones show 🔒 with their title/description still readable. Back button returns to Progress.
- [ ] **New badges fire in real rounds** (spot-check a few you can trigger):
      - *Sniper* (round avg ≤5c) and *Tight group* (every note ≤5c) on a clean Note-Accuracy round.
      - *Triple bullseye* — three notes within 2c in one round.
      - *New record* — beat a stored PB (play a config you've played before and score higher).
      - *Position explorer* — select 4+ positions and finish a round.
      - *Perfect sustain / shift / chords* (*Unwavering* / *Sure-footed* / *Arpeggio ace*) —
        all-three-star round of that exercise.
      - *Pizzicato precision* — a pizz round averaging within 12c.
      - *Early bird / Night owl* — if you happen to practise before 7 a.m. or after 11 p.m.
- [ ] **No spurious unlocks** — e.g. the shift/sustain/chords perfection badges don't fire on the
      wrong exercise, and *New record* stays quiet on a first-ever round for a config.

### 2026-07-12 Mix sharps & flats — new setting (your request)
Settings → Notation & tuning → **"Mix sharps & flats"** (off by default). When on, the
single-note games (Note Accuracy, Sustain, Shift) spell each black-key prompt as a sharp or
flat at random, so the same note appears both ways over a session (La♯ / Si♭). Naturals never
change (no "Si♯" for Do — out of scope). Chords deliberately excluded (their tones have a
context-correct spelling). Proper ♯/♭ glyphs now used everywhere, not the ASCII "#". Pure
logic unit-tested (`EnharmonicSpellingTest`, `MixedSpellingTest`). Verify on the phone:
- [ ] Toggle on → over a Note-Accuracy round, black-key prompts show a mix of ♯ and ♭, and
      each reveal matches the name its prompt showed.
- [ ] Naturals (Do Ré Mi Fa Sol La Si) always read normally — never "Si♯" / "Mi♯".
- [ ] Same in Sustain and Shift (start → target both respell).
- [ ] ♯ and ♭ glyphs render cleanly in both solfège and letters mode (no tofu boxes).
- [ ] Toggle off → single-note games back to sharps, as before.
- [ ] **Chords read by their definition** (this is always on, not tied to the toggle): a flat
      chord shows Si♭ / Mi♭ etc. and its name reads "Si♭ Majeur", never "La♯". Spot-check a
      few: B♭ major (Si♭ Ré Fa), G♯ minor (Sol♯ Si Ré♯), E♭ major (Mi♭ Sol Si♭). Locked by
      `ChordSpellingTest`, but eyeball the ones your positions actually generate.

### 2026-07-12 Chords (arpeggio) game — new
Post-v1 chords game: play a named triad as an ascending arpeggio (root → third → fifth), one
note at a time, each tone frozen and scored like Note Accuracy. Major + minor. Pure logic is
unit-tested (`ChordPoolTest`, `ArpeggioCaptureTest`); thresholds reuse the Note-Accuracy
ring-over/artifact filter and are **provisional until a real game-trace of an arpeggio confirms
them** (turn on Settings → Debug "Record & trace games", play a round, share the `chords-*`
files). Arco and pizz both allowed.
- [ ] **Home card + gate.** "Chords" card under Practice shows its PB; it's disabled with an
      explanation when the selected positions can't form a full triad (try a single position).
      Daily focus rotates a "Chords · arco" card too.
- [ ] **The arpeggio flows.** Each tone captures once and the strip advances root → third →
      fifth; the note you *just* played ringing on does NOT get scored as the next tone (the
      dominant risk — watch for it especially in pizz where strings ring).
- [ ] **Wrong root re-arms.** Start on the wrong note → "that's not it — start on {root}" and it
      waits; a wrong third/fifth is scored as a miss and moves on (never gets stuck).
- [ ] **Open strings.** A chord whose tone is an open string shows it as "open" and does NOT
      score its intonation (only fingered tones score); the "x / y" total reflects that.
- [ ] **Fingering feels natural.** The string/position chosen for each tone should be a sensible,
      close hand shape — flag any that feel awkward (the fingering heuristic is tunable).
- [ ] **Chord fingering setting.** Settings → Gameplay → "Chord fingering" (Natural / Prefer
      fingered / Prefer open). *Natural* = closest hand shape (default). *Prefer fingered* should
      make you finger open-pitch tones (A/D/G) in your positions when possible — more scored
      notes. *Prefer open* should use the open strings (shown "open", unscored). Check the
      arpeggios change accordingly and that Natural feels like how you'd actually play it.
- [ ] **Summary/PB/achievements.** Round summary, personal best per exact position set, and the
      "🎼 Triads in tune" achievement (a round with every scored tone ≥1★) all appear.

### 2026-07-12 Chords tab on Progress (your feedback)
The Progress page only had Accuracy / Sustain / Shift tabs — the Chords game recorded rounds
but there was no way to see them. Added a **Chords** filter chip. Chord rounds already store
score, `avgAbsCents`, and per-note `positionId`, so the score chart, stats, and "Accuracy by
position" all populate for it with no data changes. The tab row is now a `FlowRow` so four
chips wrap instead of clipping on a narrow screen.
- [ ] **Chords tab.** Play a Chords round, open Progress → a "Chords" chip appears; selecting
      it shows that round in the score chart and history, with its avg-cents and position bars
- [ ] **Tab wrapping.** Confirm all four chips (Accuracy / Sustain / Shift / Chords) are fully
      visible — they should wrap to a second line rather than get cut off

### 2026-07-12 progress-by-position (verify on the bass)
Two Progress-page additions from your feedback. Attempts now store which position each note
belonged to (DB v2→v3 migration `MIGRATION_2_3`); the per-position breakdown only fills in
from rounds played *after* installing this build — older history has no position data.
Regression: `positionsFromConfigKey` round-trip in `ScoringTest`.
- [ ] **Positions on history rows.** Each round in the Progress history list shows small
      pills for the positions practiced (e.g. "½ · 1st · 2nd"). Works retroactively (parsed
      from the stored config), so your existing history should already show them
- [ ] **Accuracy by position.** Play a few Note-Accuracy (and/or Shift) rounds, then open
      Progress → an "Accuracy by position" section shows one bar per practiced position,
      average cents on the right; greener/fuller = more secure. Confirm the ordering is
      canonical (½, 1st, 2nd, …) and the numbers match your sense of which positions felt shaky
- [ ] **Sustain tab.** The "Accuracy by position" section does NOT appear on the Sustain tab
      (sustain isn't cents-scored) — it should just be absent, not empty/broken

### 2026-07-11 afternoon feedback fixes (verify on the bass)
Diagnosed from your five afternoon snippets + full game traces; see `docs/DETECTION.md` and
`FeedbackSnippetAnalysis` / `FeedbackRegressionTest` / `AttemptCaptureTest` in `:app` tests.
The capture rewrite (attack requirement) was verified live via traces — see the Verified block.
- [ ] **Count-in.** Every exercise (Note Accuracy / Sustain / Shift) starts with a 5 s
      visual countdown ("Get ready / pick up your bass"), no beeps; listening only starts after
- [ ] **Let's go again.** Score screen has a "Let's go again" button that restarts the same
      exercise; "Done" still exits
- [ ] **Buzz audible.** Wrong note / timeout now plays an audible buzz on the phone speaker
      (was 130 Hz, below the speaker's range → silent); chime/blip still fine
- [ ] **Drift banner readable.** The trending-sharp/flat warning is now a big banner
      readable at arm's length while playing
- [ ] **Sustain locks on Do#2.** Bow Do#2 arco — the ring should fill (it couldn't arm
      before while bowing continuously)
- [ ] **Sustain survives bow changes.** Change bow direction mid-hold — a brief scoop that
      returns should NOT reset the ring; only genuine drift (sustained out) resets it
- [ ] **Sustain in-tune bar.** A tune-up-style bar shows how sharp/flat the hold is; it
      greys out (no marker) when you're below the noise gate / not playing
- [x] **Home disabled card.** Sustain in Pizz mode reads clearly greyed/dimmed (was too subtle)
- [x] **Trace tool.** Settings → Debug → "Record & trace games" ON → play a full arco round →
      a `game-trace-…` WAV + JSONL appears in Recordings (Share it to me — I'll replay the whole
      round offline to retune arming/thresholds from real data). Turn OFF for normal play

### Big sweep view redesign (your 2026-07-11 notes)
- [x] Pitch debug → "Start sweep (big view)": the note cells now line up in even columns
      (all the same width) instead of a ragged flow
- [x] No emoji anywhere in the sweep view — the state banner uses an hourglass (waiting) /
      waveform (capturing) / music-note (play a note) icon, the last capture uses a check
      icon, and arco/pizz is a swap icon + label
- [x] Layout reads as one design: centered progress "N / 26", a caption, a progress bar, a
      big color-coded state banner, the last-captured note, then the aligned grid
- [x] The colour still does the at-2 m work (green = captured), and it's all still readable
      while holding the bass
- [x] The compact debug cards also lost their ⇄/✓/◉ emoji (swap icon, check icon, record/
      stop icon) — everything still works (mode toggle, freeze readout, long capture)

### Feedback batch (your 2026-07-11 notes)
- [ ] **Shifts always change position.** Same-string and cross-string shift rounds never
      give a start and target in the same position — every prompt is a genuine shift
      between two positions
- [x] **One position disables shifting.** Select a single position on the home screen: both
      "Shift Trainer" cards go greyed out with "Select at least two positions to shift
      between"; select a second position and they light up again
- [x] If today's focus is a shift and only one position is selected, the focus card is not
      tappable and explains why (select two positions)
- [x] **No string label on prompts.** Note Accuracy / Shift / Sustain prompts now show only
      the note + position — the confusing "(I)" string numeral is gone; you find the string
      yourself
- [x] **Streak has a label.** Top-left shows a flame icon + "N day streak" (not a bare 🔥 N)
- [x] **Proper icons, no emoji.** Home progress/settings buttons and Drone Play/Stop are
      real icons; Drone's Back is the same outlined full-width button as every other screen
- [x] **Settings has sections** (Notation & tuning / Gameplay / Feedback / Detection &
      calibration) instead of one long list
- [x] **Calibrate surroundings** now also has its own card under Home → Tools

### Drone mode (new — pure practice aid, no scoring/detection)
- [ ] Home → Tuning & ear training → Drone: tap Play → a steady tone sounds through the
      phone speaker and is clearly audible (this is the key check — bass pitches on a phone
      speaker can be too quiet)
- [ ] Each open-string chip (Mi/La/Ré/Sol) sounds the right pitch class; the "sounding at
      X" line matches what you hear (it's octave-placed up so it carries on the speaker)
- [ ] "Any note" picker: pick a low one (Mi, Do) — still comes out audible, not a buzz
- [ ] "Add fifth" toggle: the added fifth rings *in tune* with the root (pure 3:2), no beating
- [ ] Play → Stop is clean: no click/pop at the start or the end of the tone
- [ ] Retuning while it's playing (tap a different note) changes pitch smoothly, no glitch
- [ ] Volume slider changes the drone loudness live
- [ ] Leaving the screen (Back) or backgrounding the app silences the tone immediately
- [ ] Reopen Drone: it remembers your last pitch + fifth setting

### Home screen restructure (your layout idea)
- [x] Home is grouped into sections: Tuning & ear training / Practice / Tools
- [x] Progress 📊 and Settings ⚙️ are icons at the top-right; both open the right screen
- [x] Arco/Pizz + position chips now sit under the Practice header (they only affect the
      scored games — Tune up and Drone ignore them); scores still key off them as before
- [x] Pitch debug lives under Tools and still opens the debug screen
- [x] Today's focus card and 🔥 streak are still up top and still work

### Octave-error fix (from your two snippets)
- [x] Bowed open A now reads **A1** (it silently read A2 before)
- [x] Plucked low E stays **E1** through the whole decay (used to jump to E2 after ~1 s)
- [x] Pizz on D and G strings still locks quickly and correctly (sanity check after the fix)

### Debug screen fixes
- [x] Screen no longer times out while listening
- [x] Big note/cents readout is calm instead of jittery

### Tune up screen (new)
- [x] Playing an open string highlights the right one of E1/A1/D2/G2
- [x] Needle direction matches reality (sharp = right/+, flat = left/−)
- [x] A string in tune held ~1 s ticks green and stays ticked
- [x] All four green shows "All strings in tune — go play!"
- [x] Damping a string does NOT clear its green tick (your report — the mute transient
      used to untick it); a string only loses its tick after ~1 s of genuinely
      out-of-tune playing on it
- [x] A stopped note (e.g. F# on E string) does NOT get counted as a string

### M3: settings, levels, persistence, sounds (new)
- [x] Solfège names (Do Ré Mi) show everywhere: prompts, string hints, tune-up labels, debug
- [x] 2026-07-10 — Position mapping confirmed from her Simandl fingering chart:
      ½ = 1–3, 1st = 2–4, 2nd = 3–5, 3rd = 5–7, 4th = 7–9, 5th = 8–10 (semitones above open)
- [x] Position chips are now multi-select (½ 1st 2nd 3rd 4th 5th); each exact combination
      keeps its own best score
- [ ] With several positions selected, a round mixes them evenly (no easy-position bias)
- [x] Open strings no longer appear as game prompts (they stay in Tune up)
- [ ] Spot-check prompts against your chart: e.g. 2nd position on Sol string should only
      ask Si♭2/Si2/Do3; 1st position on Mi string only Fa#1/Sol1/Sol#1
- [ ] Position + string on the prompt is readable at a glance while playing
- [ ] Chime on a good note (≤15c), soft blip on close (≤30c), buzz on miss — audible, not
      annoying, and doesn't confuse the detector for the next prompt
- [ ] Finish a round → summary shows score + best-to-beat; home shows Best and 🔥 streak
- [x] Beat your own score → "New personal best!" appears
- [ ] Change difficulty or round length → separate best (by design — check it feels right)
- [ ] A4 setting: set 442 and verify the tuner/tune-up shifts accordingly

### M4: Sustain + Shift Trainer (new)
- [ ] Sustain: ring fills while you're in tune, resets when you drift out (arco on La)
- [ ] Sustain: a brief bobble (single bad reading) does NOT reset the ring
- [ ] Sustain: stopping the bow mid-hold counts as a reset and waits for you to restart
- [ ] Sustain pizz: the note dying naturally ends the attempt with partial credit
- [ ] Shift: "Start on X" — playing the wrong start note says so and re-arms
- [ ] Shift: the GO cue timing feels right (random 0.5–1.5 s after start confirmed)
- [ ] Shift: sliding slowly into the target scores where you STOP, not where you passed
- [ ] Shift: landing back on the start note doesn't score — it waits for a real shift
- [ ] Shift: fast clean landing shows the ⚡ confident-shift bonus
- [ ] Shift with 2+ positions selected gives real cross-position shifts;
      single position gives small movements (intended fallback — does it feel useful?)

### Progress page (new)
- [x] Home → Progress: chart shows your rounds (percent per round), tabs switch exercise
- [ ] Stats row and session list look right after a few real rounds
- [x] Achievements gallery on Progress: locked show 🔒, finishing a round unlocks 🎉 First round
- [x] Round summary announces newly unlocked achievements

### Overnight additions (achievements, drift, shift variants)
- [x] Existing scores/streak survived the app update (database migration)
- [x] Shift Trainer now has two cards: same-string and cross-string, separate bests
- [ ] Cross-string shifts actually prompt different strings for start and target
- [ ] Pitch-drift warning: play ~6 notes deliberately a bit sharp → banner + descending
      "come down" tones appear; toggle off in settings silences it
- [x] Sustain card is disabled in Pizz mode with an explanation (your feedback)
- [x] Settings → About & licenses shows GPL + Tuner attribution, license text loads
- [x] New launcher icon reads as a bass clef (be honest)
- [x] "Today's focus" card on top of home: one tap starts the suggested round; rotates daily
- [ ] After a second day of rounds: summary shows "X cents — last week Y ⬇/⬆" comparison
- [x] Home screen scrolls (was cut off — your report)
- [ ] Starting a game without a recent tune-up asks "Tuned up?"; completing a tune-up
      (all four green) silences it for ~8 h
- [x] **Note sweep** in Pitch debug: play chromatically through the range (arco, then flip
      the toggle and pizz) — every note should turn green; report any that stay grey
- [x] Debug capture line shows "✓ stable: <note> in <ms>" after each played note
- [ ] Long capture: start, play a slow chromatic sweep (one string arco), stop & save —
      then do the same pizz; these become the offline test corpus
- [x] Manage recordings: list shows the captures with the settings they were recorded
      with; ▶ Play plays them back; Share sends WAV + log; Delete works

### Noise gate (from your noise snippets)
- [x] Pitch debug at your desk: typing/birds no longer show notes; level bar stays grey
      with "(ignored as noise)"; playing turns it green
- [x] Note sweep no longer fills in while you're not playing
- [x] Quiet playing still registers: soft pizz and gentle bowing must still turn the bar
      green and capture — if not, lower the gate in Settings
- [x] Settings → Calibrate surroundings: quiet phase + soft-playing phase → verdict
      (✓ clear / △ tight / ✕ no separation); your desk room should be ✓ with a gate near 45
- [x] Calibrate verdict ✕: try it with loud music playing — it should refuse to set a gate
- [x] Noise-gate slider in Settings changes what the debug bar considers noise
- [ ] Pre-game "Ready to play?" dialog offers Tune up / Calibrate when either is stale;
      "Start anyway" silences both for the session; completing them silences for ~8 h

### Player level (your feedback: timeout too tight for reading + placing)
- [x] Settings → Player level: chips Beginner/Intermediate/Advanced/Expert; default is
      Beginner (20 s to start each note — enough to read, translate and place?)
- [x] Reveal stays on screen noticeably longer on Beginner than before
- [x] Play a familiar 1st-position round fast → summary offers "Switch to Intermediate
      pace"; tapping it changes the setting (check in Settings afterwards)
- [ ] Play a 3rd-position round slowly → NO speed-up suggestion appears (one slow note
      is enough to hold it back — by design)
- [ ] On a faster level, let several prompts time out → summary offers a slower pace
- [ ] Changing level does NOT split your personal bests (deliberate: same scoring
      strictness at every level, and moving up must not orphan your history — push back
      if this feels wrong)
- [ ] Shift: GO-cue window feels roomier on Beginner (8 s to depart vs 4 s before)

### Game sounds silent + gallery (your reports)
- [x] Settings → Sound feedback → volume slider: release it → you hear the chime through
      the exact in-game sound path (your design — this verifies game sounds work at all)
- [x] If the phone's media volume is muted, settings shows a ⚠ warning saying game
      sounds stay silent regardless of the slider
- [x] With the slider audible, play a Note Accuracy round → chime/blip/buzz now heard
      (if the slider chimes but the round stays silent, tell me — that's a different bug
      and there's now logging to catch it)
- [x] Progress → achievements row: all cards are the same height

### High notes read an octave low + sweep big view (your reports)
- [x] Note sweep arco: Do3, Ré3, Ré#3, Mi3, Fa3 now register at the right octave
      (your snippets showed our own octave-correction halving them; it now only fires
      when the claimed true note is below ~Do2, where the phone mic genuinely loses
      fundamentals — the six snippets are in the offline test corpus as regression tests)
- [ ] Same check pizz on those notes
- [x] Open La bowed still reads La1, and pizz Mi still stays Mi1 through the decay
      (the original octave fixes must survive the rework)
- [ ] Pitch debug → "Start sweep (big view)": grid, capture state and last capture
      readable from playing distance; "🤫 wait for quiet" vs "🎧 play a note" banner
      makes it obvious why a note isn't registering yet (your confusion report)
- [ ] Big view: arco/pizz toggle, Reset and "Save last 8 s" work (snippet button
      duplicated there on your request); system back or Exit returns to debug
- [x] Heads-up from your morning snippets (8:34–8:38): that session's playing was much
      softer than the afternoon one — median level ~20 vs 100. If that was your normal
      soft playing at 2 m, the current noise gate (45) would reject it live. If soft
      playing fails to register during games, recalibrate surroundings or tell me.

### Full calibration wizard (M5 — your go-ahead, "turn the knobs against known notes")
- [ ] Settings → "Full calibration (new phone or double bass)" → intro explains the flow
- [ ] Quiet stage: bar fills in ~4 s while you keep still
- [ ] Lowest-string stage: bow open Mi once per microphone mode (Pixel supports 3 →
      3 takes); prompt text readable from the bass
- [ ] A deliberately silent take (don't play) is rejected with "too quiet — try again"
      instead of being swallowed
- [ ] Open-strings stage: La, Ré, Sol one take each; "hearing X" line shows the right
      note while bowing
- [ ] High-note stage: Do3 on the Sol string (2nd position)
- [ ] Summary on YOUR phone should read: microphone "Standard", room ✓ clear, every
      note ✓ detected, and NO "octave handling was adjusted" line (the wizard should
      reproduce today's hand-tuned values on this phone — that's the whole point)
- [ ] Save → play a Note Accuracy round + note sweep: everything behaves exactly as
      before (the wizard must not make your phone worse)
- [ ] Cancel mid-wizard leaves settings untouched
- [ ] Run it once in a noisy room (music playing): summary refuses to save (✕ room)

### Note Accuracy game (M2)
- [ ] Arco round: notes freeze at the *first* landing — correcting a finger after landing
      doesn't improve the result
- [x] Pizz round: plucked notes get captured before they die away
- [x] Freeze timing feels right (not so slow it drags, not so fast it catches the attack)
- [x] Reveal duration (1.2 s) is long enough to read
- [x] 10 prompts is a sensible round length
- [ ] Deliberately play ~20 cents sharp → shows + (sharp); flat shows −
- [x] Deliberately play a completely different note → shows "wrong note?"
- [x] Let a note ring out — the next prompt must NOT capture the ring-over
- [x] Talk / make room noise while not playing → no false captures
- [/] Suggested strings make sense for the prompted notes

## Verified

- [x] 2026-07-10 — Bowed open strings detected on correct notes, green when in tune (first bass test)
- [x] 2026-07-10 — Low E1 (41 Hz) locks reliably when bowed
- [x] 2026-07-10 — Snippet save button produces WAV + log usable for offline debugging
- [x] 2026-07-11 — **Capture rewrite (attack requirement), verified live via game traces.** A note
      left ringing / doing nothing no longer produces a false "wrong note" (the ring has no attack,
      so it never captures); genuinely played notes — correct AND deliberately wrong — register
      correctly; deliberate octave errors show "right note, wrong octave". Full mixed arco run:
      correct notes scored, wrong flagged, rings produced zero captures. See `docs/DETECTION.md`.
- [x] 2026-07-11 — **Pizz works, both directions.** Good notes (incl. very out of tune) and
      deliberate wrong notes detected correctly; no false captures. The arming change suits pizz
      (a pluck is a pure attack).
- [x] 2026-07-11 — **Game-trace tool** records a full round (audio + detection + events) and
      replays offline — used to drive the whole capture fix.



----

PROCESSED FEEDBACK:
------------------

Feedback after testing:

Here's some of my feedback:

Note accuracy issues during playing:

- Fa2 and Fa#2 refused to get recognized while i was playing arco the correct note -> no note detected. However in pitch debug it popped up correctly immediately, I've recorded a snippet alternating between fa2 and fa#2 from the pitch debug. I didn't notice having this issue in pizzicato, when i did pizz exercises fa#2 got recognized properly.

- Wrong note is sometimes shown too fast, i barely touched a adjacent string before playing and i immediately got a wrong note, there probably needs to be a minimum duration before saying it's a wrong note. it happens because i was still playing the previous note, looking at the result and then I lifted my fingers which barely rings the strings but it picks it up and the moment it shows the next note to play it picks it up and immediately shows wrong note. --> this actually kills most of my practice notes, and makes practice almost impossible.

- I would have a count in so I have time between starting an exercise , putting my phone down, taking my bass and be ready. For all exercises really.

- Add a let's go again button at the score screen to restart the same exercise

- I only hear a correct chime, not a buzz when wrong or timeout

- The everything is trending sharp label is far too small to be able to read while playing

- When i play an octave wrong, it should show right now but wrong octave instead of wrong note
  
- Twice i was asked to play Sol#1 and I played the right note pizzicato, but it immediately said wrong note. When I check in pitch debug it does flip between other notes. I made 2 snippets with Sol#1, this is likely a combo of too quick to say wrong note and some harmonic instability that's not filtered out.


Sustain:

 - I could not for the life of me get it to lock on to Do#2, it's as if it didn't detect it at all. In pitch debug it showed up but cents did fluctuate (i recorded a snippet)

 - Each time i switched directions with my bow almost every time it resetted the sustain (you can use the same do#2 snippet for testing), this made this exercise incredibly hard because 5 seconds is a looooot on a single bow stroke.
  
 - A bar like in the tune up view that shows how in tune is being played would maybe help
  
Pitch debug:

- Playing open string Sol2 acro and pitch debug alternates between Sol2 and Sol3, I've recorded a snippet

Home screen:

- The sustain menu item on pizz is disabled but i would also change the background because it's very subtle now, between light gray and white is almost no difference.

- the layout of achievements  does not use tiles of the same size in rows, so it looks messy

- progress view does not have Chords shown yet — FIXED 2026-07-12 (Chords tab added; see Pending)

- my day streak is still 1 , but i think it should be 2 by now


- [BUG] Calibrate surroundings still use emojis.


- [UI ISSUE] The pips on top of the rounds in games don't fit fully with 20 notes in a round so the last few look like it's already finished but we haven't hit the full round yet

- [UI ISSUE] Calibration wizard layout could use some love in spacing, margin and design language consistency. It has to be readable from a distance during calibration sure, and large text takes up more space but that doesn't mean throw every element on screen without proper padding/spacing.

- [UI ISSUES] Review docs/UI_DESIGN_REVIEW.md that Claude Haiku made. Do not take them verbatim but analyze and if you agree fix them. Some are probably listed due to lacking context.


- [UI ISSUE] I would move achievements from its row to an icon in the top bar to open the achievements list with how many of total unlocked next to it.

- [UI ISSUE] In pitch debug now that we have the big sweep view there is no need to show the same thing in pitch debug view itself, just the button to start the sweep is good.
 
 
- [CRITICAL] Missing onboarding, no welcome, no anything when the app is first launched. It should have an onboarding to to explain the app and ask for a full calibration, because immediately getting tossed in the app without ever having done a full calibration is just going to lead to poor results


- [UI ISSUE] What are the cents shown in progress? It's not clear to me "avg cents" off, is that an absolute deviation both flat and sharp? What about each game in progress. The cents shown are what? Finally the cents in accuracy per position is also not something I understand. I honestly would use accuracy % metrics here, far easier to understand than raw cents. Maybe if you tap the accuracy  you can still show the cents to toggle between the 2

- [UI ISSUE/FEATURE] In the round complete add a chart of the cents per note. It'll be interesting to see the trend.


OPEN FEEDBACK & IDEAS:
--------------

[2026-13-07]


- [QUESTION/BIG ASS FEATURE] (?) I wonder if we play all notes individually, it being monophonic, would we be able to discriminate to the right note in a more polyphonic setting (like playing a piece). Most apps that do this with DNNs don't really make an initial profile of the instrument and individual string harmonic behaviour.
Polyphonic: is it possible to have a complete breakdown of the instrument with charts, would be cool to build an instrument profile.

- [FEATURE] Scales exercises or is that covered by chords?

- [FEATURE] If debug traces is on, ask for feedback after a game how it went and embed that in the trace. That will help embed user issues alongside traces, especially if it's other people traces, as well as I .. forget when i do several rounds.

- [FEATURE] Ability to send a trace to feedback@drakarah.be, once i put it on the play store and they say it doesn't work for them i can analyze their trace

- [BUG] I calibrated it said no octave drift in the title but on mi it said octave drift so which is it? I made a trace and have a screenshot. Other than that pizz/Arco split was the right call, after calibration it was far less false trigger prone.

- [BUG] Sustain is a lot better with false resets when bow stroke changes but it's not fully solved yet, I had several resets due to bow stroke reversal, see trace.


- [BUG/FEATURE] Shifting on the same string on 1st and 2nd position made me shift from first finger 1st to 4th finger 2nd, or the other way around. I never used my 2nd finger in any of the positions, I don't mind that at all, but it would be an additional difficulty that students also want to practice. I like this too though, so maybe an option for level of difficulty? Maybe the shifting exercises can be basic 1->4->1, anything in between on the same string and across strings complicating things further, so 3 levels.

- [VERIFY → DIAGNOSED 2026-07-15] I played some pizz note accuracy too and I'm not sure if I'm inaccurate or the game is too quick on the trigger to lock onto pitch before it fully stabilised. I have full traces available. It's entirely possible it's me though. But it's good to verify.
  → Verified from your traces: the game WAS partly too quick on pizz — the plucked attack reads sharp and settles flatter, and the 60/150 lock could freeze the transient (~10–20¢ sharp on short/loud plucks; long notes were fine). Arco checked and fine. Fixed: pizz capture timing is now measured per-rig by the calibration wizard (see the 2026-07-15 Pending block above). Re-run calibration to pick it up.


- [FEATURE]  Reminder notification: send the user a reminder they haven't practiced yet if it's been near 24h since the last session. Stop sending notifications if they ignore them and it's more than a week since last practice. Of course with a toggeable setting in the settings to turn this off

- [FEATURE] Glissando practice game maybe? Would that be useful, nice and even linear pitch increase/decrease between notes, no fluctuations in speed that might it sound wobbly. Not sure if this can be done with the current settings, this might require a calibration step.

- [MISSING HARDENING] Calibration wizard never asks for accidental touches like putting hand on the strings and then removing them which can cause string to ring very silently. This should be tested clearly that it does not get picked up as an input note for a game. I don't know if it's necessary with the current calibration, i don't have any issue with a correct calibration but it would be worth to verify.

- [REVIEW/EVALUATE] Potentially actionable items for robustness in calibration (not sure if necessary and making things unnecessarily complex, i honestly favor fewer variables that to overcomplicate settings that don't raelly make a big difference), after analysis of the detection algorithm:

 * Expand the calibration corpus beyond open strings. Include a few representative stopped notes (especially low positions and one higher position) so octave-fitting and threshold tuning aren't based almost entirely on open strings.
 * Validate calibration against playing variation, not just calibration takes. During the wizard's offline replay, simulate or include soft, loud, short, and long attacks to ensure the chosen parameters still work across a realistic range of playing dynamics.
 * Document that calibration assumes representative playing. State explicitly that users should play naturally during calibration—neither exaggeratedly loud nor overly careful—because energy-related thresholds are derived from those recordings.
 * Document environmental assumptions. Note that calibration is expected to be rerun after major changes such as switching microphones/devices, changing strings, moving to a dramatically noisier environment, or if Android audio processing changes.
 * Stress-test onset detection across playing styles. Build a regression corpus containing extremely soft legato, aggressive attacks, different bow pressures, and different pizzicato styles to ensure onset-rise thresholds remain robust.
 * Broaden the octave-validation corpus. Verify the octave-settle and octave-down calibration against different strings, fingered notes, and multiple instruments if possible, rather than only the reference bass.
 * Keep calibration and player modelling separate. Continue treating microphone/instrument properties (wizard) and human behaviour (player level, reaction time) as distinct domains, and resist moving player-specific behaviour into calibration.
 * Add a small generalization margin when selecting fitted parameters. If two candidate thresholds perform equally well on the calibration corpus, prefer the slightly more conservative choice rather than the absolute minimum that passes.
 * Document the operating assumptions explicitly. Add a section listing assumptions such as: single instrument, one note at a time, acoustic bass, no accompaniment, calibration representative of future playing, and stable microphone behaviour.
 * Collect calibration traces from multiple devices and players. Before considering the calibration procedure "finished," replay the same corpus across several phones and different basses to identify thresholds that are overfitted to the reference setup

- [REFACTOR] Detection should be UI-agnostic.
      Right now onCaptured() is essentially implementing a domain rule engine:
      ring-over rejection
      too-soon rejection
      harmonic artifact rejection
      flimsy rejection
      unplayable rejection
      wrong-octave handling
      ignoreWrongOctave option
      discard counting
      continue listening vs score

      That's not presentation logic.

      It's game logic.

- [REFACTOR] Review docs/CODE_REVIEW_2026-07-12.md findings and refactor where necessary
- [REFACTOR] RoundScreen / round in general is really a misnomer and an artifact when only note accuracy was implemented. I would refactor that to noteaccuracy just like sustain and chords exist


- [FEATURE] Teacher's notebook
      Given all the data you collect, you could build a "teacher's notebook."

      Not pages of graphs.

      Just one or two observations after each session.

      For example:

      "Your pitch settled more quickly today than yesterday."
      "Most misses were slightly flat on descending shifts."
      "Your pizzicato accuracy is becoming as consistent as your arco."
      "You've become much more consistent after the first note of each round."

      Those are exactly the kinds of comments a good teacher makes. They connect practice to improvement, which is one of the strongest intrinsic motivators. It's also something a conventional tuner simply can't provide, because it doesn't remember your musical journey—it only reports the present moment.

      It's behaving like a really observant teacher.

      Imagine opening the app and seeing:

      Last week you struggled to find Fa♯2 cleanly.

      Today you hit it first try three times.

      That's real progress.

      That feels personal.

      No leaderboard.

      No coins.

      No fireworks.

      Just someone noticing something you hadn't.
