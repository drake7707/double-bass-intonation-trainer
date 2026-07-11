# Things to verify on the real bass

Running checklist of everything waiting for a hands-on check. Items get moved to *Verified*
with the date once confirmed. Ask Claude for "the checklist" anytime.

## Pending

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
- [ ] A stopped note (e.g. F# on E string) does NOT get counted as a string

### M3: settings, levels, persistence, sounds (new)
- [x] Solfège names (Do Ré Mi) show everywhere: prompts, string hints, tune-up labels, debug
- [x] 2026-07-10 — Position mapping confirmed from her Simandl fingering chart:
      ½ = 1–3, 1st = 2–4, 2nd = 3–5, 3rd = 5–7, 4th = 7–9, 5th = 8–10 (semitones above open)
- [x] Position chips are now multi-select (½ 1st 2nd 3rd 4th 5th); each exact combination
      keeps its own best score
- [ ] With several positions selected, a round mixes them evenly (no easy-position bias)
- [ ] Open strings no longer appear as game prompts (they stay in Tune up)
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
- [ ] Home → Progress: chart shows your rounds (percent per round), tabs switch exercise
- [ ] Stats row and session list look right after a few real rounds
- [ ] Achievements gallery on Progress: locked show 🔒, finishing a round unlocks 🎉 First round
- [x] Round summary announces newly unlocked achievements

### Overnight additions (achievements, drift, shift variants)
- [ ] Existing scores/streak survived the app update (database migration)
- [ ] Shift Trainer now has two cards: same-string and cross-string, separate bests
- [ ] Cross-string shifts actually prompt different strings for start and target
- [ ] Pitch-drift warning: play ~6 notes deliberately a bit sharp → banner + descending
      "come down" tones appear; toggle off in settings silences it
- [x] Sustain card is disabled in Pizz mode with an explanation (your feedback)
- [x] Settings → About & licenses shows GPL + Tuner attribution, license text loads
- [x] New launcher icon reads as a bass clef (be honest)
- [ ] "Today's focus" card on top of home: one tap starts the suggested round; rotates daily
- [ ] After a second day of rounds: summary shows "X cents — last week Y ⬇/⬆" comparison
- [ ] Home screen scrolls (was cut off — your report)
- [ ] Starting a game without a recent tune-up asks "Tuned up?"; completing a tune-up
      (all four green) silences it for ~8 h
- [ ] **Note sweep** in Pitch debug: play chromatically through the range (arco, then flip
      the toggle and pizz) — every note should turn green; report any that stay grey
- [x] Debug capture line shows "✓ stable: <note> in <ms>" after each played note
- [ ] Long capture: start, play a slow chromatic sweep (one string arco), stop & save —
      then do the same pizz; these become the offline test corpus
- [ ] Manage recordings: list shows the captures with the settings they were recorded
      with; ▶ Play plays them back; Share sends WAV + log; Delete works

### Noise gate (from your noise snippets)
- [x] Pitch debug at your desk: typing/birds no longer show notes; level bar stays grey
      with "(ignored as noise)"; playing turns it green
- [x] Note sweep no longer fills in while you're not playing
- [x] Quiet playing still registers: soft pizz and gentle bowing must still turn the bar
      green and capture — if not, lower the gate in Settings
- [x] Settings → Calibrate surroundings: quiet phase + soft-playing phase → verdict
      (✓ clear / △ tight / ✕ no separation); your desk room should be ✓ with a gate near 45
- [ ] Calibrate verdict ✕: try it with loud music playing — it should refuse to set a gate
- [ ] Noise-gate slider in Settings changes what the debug bar considers noise
- [ ] Pre-game "Ready to play?" dialog offers Tune up / Calibrate when either is stale;
      "Start anyway" silences both for the session; completing them silences for ~8 h

### Player level (your feedback: timeout too tight for reading + placing)
- [ ] Settings → Player level: chips Beginner/Intermediate/Advanced/Expert; default is
      Beginner (20 s to start each note — enough to read, translate and place?)
- [ ] Reveal stays on screen noticeably longer on Beginner than before
- [ ] Play a familiar 1st-position round fast → summary offers "Switch to Intermediate
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
- [ ] With the slider audible, play a Note Accuracy round → chime/blip/buzz now heard
      (if the slider chimes but the round stays silent, tell me — that's a different bug
      and there's now logging to catch it)
- [x] Progress → achievements row: all cards are the same height

### High notes read an octave low + sweep big view (your reports)
- [ ] Note sweep arco: Do3, Ré3, Ré#3, Mi3, Fa3 now register at the right octave
      (your snippets showed our own octave-correction halving them; it now only fires
      when the claimed true note is below ~Do2, where the phone mic genuinely loses
      fundamentals — the six snippets are in the offline test corpus as regression tests)
- [ ] Same check pizz on those notes
- [ ] Open La bowed still reads La1, and pizz Mi still stays Mi1 through the decay
      (the original octave fixes must survive the rework)
- [ ] Pitch debug → "Start sweep (big view)": grid, capture state and last capture
      readable from playing distance; "🤫 wait for quiet" vs "🎧 play a note" banner
      makes it obvious why a note isn't registering yet (your confusion report)
- [ ] Big view: arco/pizz toggle, Reset and "Save last 8 s" work (snippet button
      duplicated there on your request); system back or Exit returns to debug
- [ ] Heads-up from your morning snippets (8:34–8:38): that session's playing was much
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
- [ ] Freeze timing feels right (not so slow it drags, not so fast it catches the attack)
- [ ] Reveal duration (1.2 s) is long enough to read
- [ ] 10 prompts is a sensible round length
- [ ] Deliberately play ~20 cents sharp → shows + (sharp); flat shows −
- [ ] Deliberately play a completely different note → shows "wrong note?"
- [ ] Let a note ring out — the next prompt must NOT capture the ring-over
- [x] Talk / make room noise while not playing → no false captures
- [ ] Suggested strings make sense for the prompted notes

## Verified

- [x] 2026-07-10 — Bowed open strings detected on correct notes, green when in tune (first bass test)
- [x] 2026-07-10 — Low E1 (41 Hz) locks reliably when bowed
- [x] 2026-07-10 — Snippet save button produces WAV + log usable for offline debugging
