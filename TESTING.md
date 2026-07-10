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
- [ ] **Position level mapping is provisional — correct me!** Current assumption
      (semitones above the open string): Half = 1–3, First = 2–4, Second = 4–6, Third = 5–7.
      Check a round at your level only asks notes you know, and tell me which notes belong where.
- [ ] Level select chips on home: switching level changes which notes get asked
- [ ] Position + string on the prompt is readable at a glance while playing
- [ ] Chime on a good note (≤15c), soft blip on close (≤30c), buzz on miss — audible, not
      annoying, and doesn't confuse the detector for the next prompt
- [ ] Finish a round → summary shows score + best-to-beat; home shows Best and 🔥 streak
- [ ] Beat your own score → "New personal best!" appears
- [ ] Change difficulty or round length → separate best (by design — check it feels right)
- [ ] A4 setting: set 442 and verify the tuner/tune-up shifts accordingly

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
