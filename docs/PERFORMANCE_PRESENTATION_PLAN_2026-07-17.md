# Performance presentation redesign — results screen & the metric model

**Status:** proposal for Sarah's review. Nothing implemented yet (except the interim removal
of the misleading "How in tune" word from the History list — see §1). Written 2026-07-17.

**Trigger:** Sarah opened a Shifts round in History (16 Jul 2026 14:21) that read **"Locked in"**
in the list but **"Solid"** on the results screen, and — looking at the results — felt "Solid"
was too generous for a round with several notes clearly off. Investigating that one round exposed
a systemic problem, not a one-line bug.

---

## 1. What actually went wrong (the concrete bug)

Session 41 (Shifts · pizz · 20 prompts, 18 landed, 2 timeouts, avg 17.6¢ landing error):

- **History list row** graded it with the raw, *uncapped* band:
  `MasteryBand.of(avgAbsCents, thresholds)` → 17.6¢ on the lenient SHIFT scale (Locked ≤20¢) =
  **LOCKED → "Locked in"**. [`HistoryViewModel.kt:45-46`]
- **Results / detail screen** graded it with `cappedMasteryBand` → the 90 % hit-rate (2 timeouts)
  caps it one band down to **SOLID**. [`RoundSummary.kt:119-135`, `237`]

So the *same round* showed two different words. The results screen's "Solid" was the intended
value; the list bypassed the hit-rate cap because it was built to read only the session row
(which doesn't store the miss count).

**Interim action taken:** removed the band word from the History list entirely
([`HistoryViewModel.kt`], [`HistoryScreen.kt`]) — it's re-introduced correctly once the model
below exists. No data was deleted. Compiles clean.

That fixes the *inconsistency*. Sarah's deeper objection — "Solid feels wrong, and score vs stars
vs accuracy is confusing even to me, the developer" — is the real work, below.

---

## 2. The disease: three overlapping ratings with no hierarchy

Every cents-based round currently shows the player **three different judgements of the same
playing**, on **two different cents scales**, plus per-game raw numbers:

| Rating | Scale | Where | Problem |
|---|---|---|---|
| **Score** (points) | 0–100/attempt, **difficulty-dependent** linear curve to `zeroAtCents` | hero number | the "currency"; fine |
| **Stars** (0–3) | **fixed** 5 / 15 / 30¢ (`STAR_*_CENTS`) | top-bar dots, reveal card, chart dot colours, "28 of 60 stars" line | a *second* per-note rating on a *different* scale than the band |
| **"How in tune" band** | per-game 10/25 (NOTE), 20/45 (SHIFT), 15/30 (CHORDS), hit-rate capped | headline word | a *third* rating; per-game leniency makes an all-orange shift read "Solid" |

On top of that the screen stacks **"How in tune: Solid"** directly above a **chart whose dot
colours follow the star scale** — two scales, adjacent, looking like one thing. That's exactly why
it reads as contradictory.

And it is genuinely inconsistent under the hood (from the code survey):

- **Shift** stars/coach/reveal grade the **interval travelled** (`shiftCents`), but the persisted
  `centsError` and the band grade the **landing**. Two different cents notions inside one game.
  [`ShiftViewModel.kt:290,308,363,376`]
- **Sustain**'s summary shows **none** of the band / chart / stars-line the other three show — it's
  a completely different shape (score + one sentence). [`RoundSummaryScaffold.kt:145`]
- **Chords** `maxScore` floats (fingered tones × 100), unlike the others' `roundLength × 100`.
- Score is difficulty-dependent; stars are not — so **stars and score disagree** across difficulties.

Sarah's verdict: *"New users are never going to understand that there is 1) score, 2) stars,
3) accuracy. We're trying to fix a thing while it's a systematic issue."* Correct.

---

## 3. Design principles (locked with Sarah)

1. **"Pitch accuracy" is one metric, identical across all games.** "How in tune were the notes I
   played" does not depend on the game. One cents scale, everywhere. *(confirmed: use the strict
   Note-accuracy scale.)*
2. **Scoring stays game-specific.** How points are earned (shift distance weighting, sustain hold,
   chord tones) is legitimately per-game. That belongs to the *score*, never to "pitch accuracy".
3. **Fitness-watch model.** Several **named gauges**, each legible on its own, **rolling up into one
   aggregate score** on top. Not one metric wearing three hats.
4. **One chart per metric, with the metric selector *above* the chart.** The chart is *fully* the
   selected metric's — its **y-axis (units + range), its data points, and its colours all change**
   with the selection (signed cents for Pitch/Shift, wobble-cents for Steadiness, seconds for
   Hold). **Colour and y-axis are the same scale, synced:** the chart draws that metric's threshold
   zones as bands on the y-axis, and each dot's colour is simply *which band its y-value falls in* —
   so axis and colour can never disagree (today the axis lines sit at ±15/±30¢ while the dot colours
   come from the unrelated 5/15/30¢ star scale). Never one chart doing double duty beside a
   differently-scaled headline.
5. **Metrics speak for themselves.** No "here's a star for you" rating standing in for a real
   measurement on the results screen.
6. **Stars retire from Results** (they only add noise to an already dense screen). Kept only on the
   *mid-round reveal card* as the instant "did I nail this note" glance.
7. **One honest pitch scale for everyone, wrapped in growth framing.** The Pitch-accuracy
   thresholds are absolute (10/25¢) at every difficulty — "in tune is in tune", and a verdict stays
   comparable across levels. Because a beginner will legitimately sit in "Developing" for a while,
   the *encouragement* comes from elsewhere: the score (which already respects difficulty), the
   improvement trend, and the one coaching line — never from moving the goalposts. **Colour
   discipline:** red is reserved for actual misses / wrong notes; "Developing" is **orange**, a
   growth word, not a failure state.
8. **One layout for both audiences** (children → professionals). The technical/non-technical toggle
   swaps **units only** (cents ↔ plain words), it does *not* remove the charts or controls — a chart
   packs a lot into a small space and kids read them fine. Don't dumb it down.

---

## 4. The target model — three tiers, three questions

Each tier answers a different question, with **no overlap**:

```
┌─────────────────────────────────────────────┐
│  TIER 1 — POINTS         "How did I do?"      │   one number, the game currency
│  1402 of 2000                                 │   (drives PB / streak / achievements)
├─────────────────────────────────────────────┤
│  TIER 2 — GAUGES         "Why / on what?"     │   1–3 named skills, each a plain word
│  ◐ Pitch accuracy  Solid   (18¢ avg)          │   + its value. One universal
│  ◐ Shift accuracy  Good    (14¢ avg)          │   (Pitch accuracy) + game-specific.
├─────────────────────────────────────────────┤
│  TIER 3 — CHART          "Note by note"       │   one chart per gauge, pill-toggled;
│  [Pitch] [Shift]   ← pills                    │   coloured by *that gauge's* scale.
│   •  •     •   •  •                            │   default = the universal gauge.
└─────────────────────────────────────────────┘
```

The key reframe: **the gauges are the components the score is already built from** — we're
*surfacing* them instead of hiding them behind stars. For Shift the score *literally* is
`0.7·distance + 0.3·landing`, so the two gauges (Shift accuracy, Pitch accuracy) are exactly its
inputs. Same idea for Sustain (accuracy + steadiness − resets) and Chords (sum of tones).

### 4.1 Gauges per game

| Game | Universal: **Pitch accuracy** (source) | Game-specific gauge(s) |
|---|---|---|
| **Note Accuracy** | landing cents | — (pitch accuracy *is* the whole game) |
| **Shift** | landing cents (`centsError`) | **Shift accuracy** — interval error `shiftCents`, start-independent |
| **Sustain** | \|median pitch centre\| (`accuracyCents`) | **Steadiness** (wobble, `steadinessCents`) · **Hold** (held time / goal) |
| **Chords** | mean \|cents\| over fingered tones | **Cleanliness** — weakest / all-tones-landed *(optional, §8)* |

Every gauge already has its data persisted today — **no new capture and no DB migration**
(§7). Legacy rounds missing shift `extras` simply hide the Shift-accuracy gauge and still show
Pitch accuracy.

### 4.2 The one Pitch-accuracy scale (all games)

Strict Note-accuracy thresholds, hit-rate capped, everywhere:

| Word | avg \|cents\| |
|---|---|
| **Excellent** | ≤ 10¢ |
| **Solid** | ≤ 25¢ |
| **Developing** | > 25¢ |

(Renamed from "Locked in" → **"Excellent"** — plainer to a new user. Needs new NL/FR copy.)

**Absolute at every difficulty** (principle 7): these thresholds do *not* loosen on RELAXED. A
beginner who averages ~30¢ reads "Developing" — honestly — and is encouraged by the difficulty-
aware *score*, the improvement trend, and the coaching line instead. "Developing" renders **orange**
(a growth colour); **red is only ever a miss or wrong note.**

Hit-rate cap unchanged (`< 80 %` landed → can't beat Developing; `< 95 %` → can't beat Solid).
Consequence, by design: **shifts now read stricter** — a 40¢ average landing that used to read
"Solid" on the 20/45 SHIFT scale now reads "Developing", exactly as it would in Note Accuracy.
The width of shifts is expressed in the **Shift-accuracy** gauge and in scoring, not by loosening
"in tune".

*(Session 41 recomputed under this model: Pitch accuracy = Solid — 17.6¢ landing, 90 % capped;
Shift accuracy = its own scale on the interval. The two now clearly say different things, and the
Pitch chart's colours + y-axis bands both sit at 10/25¢ instead of star colours on ±15/±30 lines.)*

### 4.3 Game-specific gauge scales (deliberately *not* the pitch scale)

- **Shift accuracy** (interval): keep the wider band — a shift genuinely lands wider than a
  planted note. Reuse ~20 / 45¢. Words e.g. *Precise / Good / Loose*.
- **Steadiness** (sustain wobble): good ≤ ~8¢ (`STEADY_GOOD_CENTS`), wobbly ~≥25¢. Words already
  exist: *Rock steady / A little wobbly / Wobbly*.
- **Hold** (sustain): held / goal time, or holds-succeeded / attempts. Words already exist:
  *All held / Most held / Few held*.

These are separate skills, so separate scales are correct (principle 2). Only **pitch accuracy** is
forced universal.

---

## 5. Results-screen layout (per game)

Shared scaffold, one shape for **all four** games (Sustain finally joins it). Gauges render as
**labeled horizontal bars**. The chart always carries a **metric selector above it**; when a game
has one metric the selector is a single static label, when it has more it's a toggle. Selecting a
metric swaps the chart's y-axis, data and colour bands together.

Two elements appear on **every** game (they aren't game-specific, so they're only drawn once in the
first wireframe below): the hero number is explicitly labeled **"Score"** so it reads as the *game*
result, distinct from the gauges (which are labeled as *the playing*); and a single plain-language
**"Next:" coaching line** sits just above the buttons — the teacher's voice, and the pedagogical
anchor for beginners (from the existing `RoundCoach` verdict). Non-technical mode swaps cents for
words but keeps this exact layout (principle 8).

### Note Accuracy  (one metric)  — shows the two universal elements: "Score" label + "Next:" line
```
             Score
           1402  of 2000
    ─────────────────────────────
     Your playing
     Pitch accuracy
     ███████░░░  Solid   18¢ avg
     based on 18 of 20 notes landed
    ─────────────────────────────
     ▸ Pitch accuracy              ← chart selector (single label here)
     +25┤        •
       0┤━•━━•━━━━━•━•━━  ← y-axis zones green≤10 / yellow≤25 / orange beyond
     −25┤   •        •       (dot colour = its zone; red only for a miss)
    ─────────────────────────────
     Next: aim the higher notes a touch lower     ← coaching line (every game)
    ─────────────────────────────
     [ Let's go again ]   [ Done ]
```

### Shift  (two metrics → selector toggles)
```
        Round complete
           1402  of 2000
    ─────────────────────────────
     Pitch accuracy
     ███████░░░  Solid   18¢
     Shift accuracy
     ████████░░  Good    14¢
     based on 18 of 20 shifts landed
     ⚑ an off start pushed some landings off   (kept: the "check your start" note)
    ─────────────────────────────
     [ Pitch ] [ Shift ]           ← selector; chart y-axis+colour follow the pick
       •  •   •  • •   •
    ─────────────────────────────
     [ Let's go again ]   [ Done ]
```

### Sustain  (was a different screen entirely — now unified)
```
        Round complete
           780  of 1000
    ─────────────────────────────
     Pitch accuracy   ██████░░  Solid  14¢
     Steadiness       ███████░  Rock steady
     Hold             ████████  All held
    ─────────────────────────────
     [ Pitch ] [ Steadiness ] [ Hold ]   ← selector
      ▎▎ ▎ ▎▎   (Hold: per-hold seconds vs goal; Steadiness: wobble-cents; Pitch: signed cents)
    ─────────────────────────────
     [ Let's go again ]   [ Done ]
```

### Chords
```
        Round complete
           640  of 900
    ─────────────────────────────
     Pitch accuracy   ███████░░  Solid  16¢
     Cleanliness      ██████░░░  3 of 4 chords clean   (optional — §8)
     based on 11 of 12 tones landed
    ─────────────────────────────
     ▸ Pitch accuracy   (one dot per fingered tone)
       •  •  •   •  •  •
    ─────────────────────────────
     [ Let's go again ]   [ Done ]
```

Mid-round **reveal cards** are unchanged in spirit and **keep their StarRating** — the fast
"did I nail it" beat lives there, not on the summary.

---

## 6. What changes in code (by layer)

Pure-domain first, UI second — matches the metrics-layer discipline in `metrics-plan.md`.

### 6.1 `metrics/` (pure, unit-tested — no Android)
- **New `RoundGauge` value type**: `{ kind, label, band/word, value, unit, scale, chartPoints }`.
  `RoundSummaryData` gains `gauges: List<RoundGauge>` and the single `band`/`chartPoints`/
  `shiftStartFlagged` fold into it. [`RoundSummary.kt`]
- **Pitch-accuracy gauge** built for *every* exercise, always on `MasteryThresholds.NOTE`,
  hit-rate capped. `masteryThresholdsFor(exerciseType)` stops driving the headline; it stays only
  for the game-specific gauges that legitimately differ.
- **Shift**: emit two gauges — Pitch accuracy (landing) + Shift accuracy (interval, wider scale).
  Resolves the "two cents notions" mess by naming both.
- **Sustain**: emit Pitch accuracy (from `accuracyCents`) + Steadiness (`steadinessCents`) + Hold.
  Sustain gets a real, comparable pitch reading for the first time.
- **Chords**: Pitch accuracy over fingered tones + optional Cleanliness.
- `buildRoundSummary` returns the gauge list; `RoundCoach` verdict stays but is scoped to Pitch
  accuracy wording (shift keeps its interval-based coaching sentence).

### 6.2 `ui/common/`
- **Rewrite `RoundSummaryScaffold` breakdown**: render a **labeled-bar gauge row** + a **metric
  selector + a per-metric chart** (one chart *per* metric, one visible at a time, toggled by the
  selector); **delete the stars-total line** (`note_stars_line`) and all star colours.
- **New `GaugeBar.kt`** — one labeled horizontal bar per gauge (fill = `masteryFraction`-style, word
  + value). **New `MetricSelector.kt`** — the segmented control above the chart (single static label
  when a game has one metric).
- **New `MetricChart.kt`** replaces `SummaryCentsChart`: takes the **selected metric** and renders
  *that metric's* y-axis (units + range), its data points, and its **threshold zones as y-axis
  bands — with each dot coloured by the band it lands in** (colour and axis are the same scale).
  Variants: signed-cents scatter (Pitch, Shift interval), wobble-cents per hold, seconds-vs-goal per
  hold. `RoundGauge` carries the axis spec (`zones`, `unit`, `range`) so the chart is data-driven.
- **`AccuracyLegend`** becomes per-chart, derived from the selected metric's zones (no global star
  legend).
- Sustain screen's bespoke summary block is removed in favour of the shared scaffold.

### 6.3 Reveal cards / stars
- Keep `StarRating` on all four reveal cards. Remove every star reference from the *summary*.
- Keep the `stars` DB column (reveal + possible achievements). **Verify achievement defs** don't
  count summary stars in a way the removal breaks (`game/Achievements.kt`).

### 6.4 History
- Re-introduce the **Pitch-accuracy gauge** on the list row once the model exists — now computed
  the *same* way as the detail (capped, unified). Needs per-session scored/attempt counts: add one
  cheap aggregate query (`SELECT sessionId, COUNT(*), SUM(outcome='SCORED' …) GROUP BY sessionId`)
  rather than reconstructing every round. [`Daos.kt`, `HistoryViewModel.kt`]

### 6.5 i18n
- New gauge labels (`gauge_pitch_accuracy`, `gauge_shift_accuracy`, `gauge_steadiness`,
  `gauge_hold`, `gauge_cleanliness`) + shift-accuracy words, in `values/` then `values-nl` /
  `values-fr` drafts (same discipline as the ux-overhaul: EN base, NL/FR await sign-off).
- Retire/repurpose `note_stars_line`; keep band words.

### 6.6 Tests
- `RoundSummaryTest`: gauge set + scale per game; unified pitch scale; hit-rate cap on the pitch
  gauge; Sustain now produces gauges; legacy shift (no extras) hides Shift-accuracy gauge.
- Replay **session 41** as a fixture and assert Pitch accuracy = Solid, list == detail.

---

## 7. No DB migration needed

Everything the gauges need is already persisted (survey §3): landing `centsError`, shift
`extras{startCents, shiftCents}`, sustain `sustainHeldMs`/`steadinessCents`/`centsError`,
chord per-tone rows. This is a **presentation** change over existing data. Legacy rounds degrade
gracefully (missing shift extras → no Shift-accuracy gauge, Pitch accuracy still shown).

---

## 8. Decisions

**Locked with Sarah (2026-07-17):**
1. **Gauge visual → labeled horizontal bars.**
2. **Top pitch word → "Excellent"** (was "Locked in"). New NL/FR copy needed.
3. **Stars off the Results screen** — they only add noise to a dense screen. Kept on the mid-round
   reveal card only.
4. **A chart per metric, toggled** (one visible at a time): metric selector above it; selecting
   swaps y-axis (units+range), data, and colour zones together; **dot colour and y-axis are one
   synced scale**.
5. **Absolute pitch scale (10/25¢) at every difficulty**, wrapped in growth framing (principle 7).
   Encouragement comes from score + trend + coaching line, not looser thresholds. **"Developing" is
   orange; red is only a miss/wrong note.**
6. **One layout for both audiences; the toggle swaps units only** (cents ↔ words), never removes
   charts or controls (principle 8).
7. **Score is labeled "Score"; the gauge block is labeled as the playing**; a single coaching line
   sits above the buttons on every game.
8. **One consolidated coaching lightbulb** (styled like the Progress insight callout): a brief
   "what went well" (only when a gauge is genuinely strong) + the one thing to watch, in one line.
   It **absorbs** the shift "check your start" caution and the old coach sentence, so the summary
   never stacks several text blocks below the chart. Analytical gauges + this one coaching line
   coexist (the numbers justify the coaching; the coaching makes them actionable). Copy is
   provisional — Sarah tunes the voice.

**Still open (small — sensible defaults assumed unless Sarah says otherwise):**
- **Chords second gauge** — default: show Pitch accuracy + a plain "tones landed X/Y" context line,
  *no* separate Cleanliness gauge, unless it earns its place.
- **Hero** — default: the "Score" number stays the biggest element (it's the game currency); gauges
  sit below.

## 9. Phasing

1. Domain: gauge model + unified pitch scale + `buildRoundSummary` gauges (pure, tested). No UI.
2. UI: gauge row + pill chart in the shared scaffold; remove summary stars; Sustain joins.
3. i18n EN, then NL/FR drafts.
4. Re-add History Pitch-accuracy gauge (capped/unified) via the aggregate count query.
5. Verify on Sarah's phone (TESTING.md pending item); replay session 41 to confirm list == detail.

Each phase is independently reviewable; nothing ships until Sarah signs off on §8.
