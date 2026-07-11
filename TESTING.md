# Things to verify on the real bass

Running checklist of everything waiting for a hands-on check. Items get moved to *Verified*
with the date once confirmed. Ask Claude for "the checklist" anytime.

## Pending

### Octave-error fix (from your two snippets)
- [ ] Bowed open A now reads **A1** (it silently read A2 before)
- [ ] Plucked low E stays **E1** through the whole decay (used to jump to E2 after ~1 s)
- [ ] Pizz on D and G strings still locks quickly and correctly (sanity check after the fix)

### Debug screen fixes
- [ ] Screen no longer times out while listening
- [ ] Big note/cents readout is calm instead of jittery

### Tune up screen (new)
- [ ] Playing an open string highlights the right one of E1/A1/D2/G2
- [ ] Needle direction matches reality (sharp = right/+, flat = left/−)
- [ ] A string in tune held ~1 s ticks green and stays ticked
- [ ] All four green shows "All strings in tune — go play!"
- [ ] A stopped note (e.g. F# on E string) does NOT get counted as a string

### M3: settings, levels, persistence, sounds (new)
- [ ] Solfège names (Do Ré Mi) show everywhere: prompts, string hints, tune-up labels, debug
- [x] 2026-07-10 — Position mapping confirmed from her Simandl fingering chart:
      ½ = 1–3, 1st = 2–4, 2nd = 3–5, 3rd = 5–7, 4th = 7–9, 5th = 8–10 (semitones above open)
- [ ] Position chips are now multi-select (½ 1st 2nd 3rd 4th 5th); each exact combination
      keeps its own best score
- [ ] With several positions selected, a round mixes them evenly (no easy-position bias)
- [ ] Open strings no longer appear as game prompts (they stay in Tune up)
- [ ] Spot-check prompts against your chart: e.g. 2nd position on Sol string should only
      ask Si♭2/Si2/Do3; 1st position on Mi string only Fa#1/Sol1/Sol#1
- [ ] Position + string on the prompt is readable at a glance while playing
- [ ] Chime on a good note (≤15c), soft blip on close (≤30c), buzz on miss — audible, not
      annoying, and doesn't confuse the detector for the next prompt
- [ ] Finish a round → summary shows score + best-to-beat; home shows Best and 🔥 streak
- [ ] Beat your own score → "New personal best!" appears
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
- [ ] Home → Progress: chart shows your rounds (percent per round), tabs switch exercise
- [ ] Stats row and session list look right after a few real rounds
- [ ] Achievements gallery on Progress: locked show 🔒, finishing a round unlocks 🎉 First round
- [ ] Round summary announces newly unlocked achievements

### Overnight additions (achievements, drift, shift variants)
- [ ] Existing scores/streak survived the app update (database migration)
- [ ] Shift Trainer now has two cards: same-string and cross-string, separate bests
- [ ] Cross-string shifts actually prompt different strings for start and target
- [ ] Pitch-drift warning: play ~6 notes deliberately a bit sharp → banner + descending
      "come down" tones appear; toggle off in settings silences it
- [ ] Sustain card is disabled in Pizz mode with an explanation (your feedback)
- [ ] Settings → About & licenses shows GPL + Tuner attribution, license text loads
- [ ] New launcher icon reads as a bass clef (be honest)
- [ ] "Today's focus" card on top of home: one tap starts the suggested round; rotates daily
- [ ] After a second day of rounds: summary shows "X cents — last week Y ⬇/⬆" comparison
- [ ] Home screen scrolls (was cut off — your report)
- [ ] Starting a game without a recent tune-up asks "Tuned up?"; completing a tune-up
      (all four green) silences it for ~8 h
- [ ] **Note sweep** in Pitch debug: play chromatically through the range (arco, then flip
      the toggle and pizz) — every note should turn green; report any that stay grey
- [ ] Debug capture line shows "✓ stable: <note> in <ms>" after each played note
- [ ] Long capture: start, play a slow chromatic sweep (one string arco), stop & save —
      then do the same pizz; these become the offline test corpus
- [ ] Manage recordings: list shows the captures with the settings they were recorded
      with; ▶ Play plays them back; Share sends WAV + log; Delete works

### Noise gate (from your noise snippets)
- [ ] Pitch debug at your desk: typing/birds no longer show notes; level bar stays grey
      with "(ignored as noise)"; playing turns it green
- [ ] Note sweep no longer fills in while you're not playing
- [ ] Quiet playing still registers: soft pizz and gentle bowing must still turn the bar
      green and capture — if not, lower the gate in Settings
- [ ] Settings → Calibrate surroundings: 5 s quiet measurement suggests a sensible gate
      (should land near 45 in your room); saving applies it everywhere
- [ ] Noise-gate slider in Settings changes what the debug bar considers noise

### Note Accuracy game (M2)
- [ ] Arco round: notes freeze at the *first* landing — correcting a finger after landing
      doesn't improve the result
- [ ] Pizz round: plucked notes get captured before they die away
- [ ] Freeze timing feels right (not so slow it drags, not so fast it catches the attack)
- [ ] Reveal duration (1.2 s) is long enough to read
- [ ] 10 prompts is a sensible round length
- [ ] Deliberately play ~20 cents sharp → shows + (sharp); flat shows −
- [ ] Deliberately play a completely different note → shows "wrong note?"
- [ ] Let a note ring out — the next prompt must NOT capture the ring-over
- [ ] Talk / make room noise while not playing → no false captures
- [ ] Suggested strings make sense for the prompted notes

## Verified

- [x] 2026-07-10 — Bowed open strings detected on correct notes, green when in tune (first bass test)
- [x] 2026-07-10 — Low E1 (41 Hz) locks reliably when bowed
- [x] 2026-07-10 — Snippet save button produces WAV + log usable for offline debugging
