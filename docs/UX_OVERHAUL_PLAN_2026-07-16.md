# UX Overhaul Plan — Coaching identity, plain language, i18n, onboarding

**Date:** 2026-07-16
**Status:** PLAN — approved direction from Sarah (Q&A recorded in §2), implementation not started.
**Goal:** Play Store launch readiness for a wide, young, non-technical, Dutch/French/English-speaking audience
(promotion at Sarah's music academy). Frictionless at first contact, no dumbing down for those who
want depth.

Companion documents:
- `docs/UI_DESIGN_REVIEW.md` + checklist (2026-07-14) — the *visual* review (spacing, accessibility,
  navigation). **Already implemented** ("ui readability improvements" commit). This plan is the
  *language/identity* axis and does not re-open those items.
- `docs/confusing-terminology.md` — earlier jargon inventory; folded into §5C below (some of its
  suggestions superseded here).
- The string audit this plan is based on (2026-07-16) found **~350 distinct hardcoded user-facing
  strings, zero i18n infrastructure** (`strings.xml` contains only `app_name`), and expert mode wired
  to the Progress screen only.

---

## 1. Why

The app started as a "trainer" steered by a developer; it is now becoming a **coach**. The original
goal was always motivation: intonation practice is dry, and games + scores + a coach who *notices
things* keep a student practicing. Three forces drive this overhaul:

1. **Audience shift** — from Sarah (developer + bassist) to academy students, many young, many not
   comfortable with English, none comfortable with DSP vocabulary.
2. **Cognitive load** — the Settings screen has grown into a wall; first contact must be chunked
   (wizard, one question per screen), not a data dump.
3. **Identity** — the metrics now support real coaching (mastery words, weekly trends, one
   actionable tip). The Progress screen already made this transition; the rest of the app still
   talks like its developer.

**What does NOT change:** the core philosophy (not a tuner; no live needles during exercises),
scoring rules, detection behavior, the games themselves, dark-only theme, the database schema.

---

## 2. Decisions already made (Sarah, 2026-07-16)

| Question | Decision |
|---|---|
| Launch languages | **English (base) + Dutch + French.** Claude drafts both translations; Sarah reviews Dutch natively; French needs a review pass (open: by whom — see §10). |
| App name | Leaning **"Double Bass Coach"** (future-proof if rhythm exercises come) over "Double bass intonation coach". Not final — see §5A for Play Store naming facts and the concrete proposal. |
| Pitch Analyzer / trace tools | **Stay prominent during the beta.** They are the remote-diagnostics channel (only one phone + one bass tested so far). Not hidden behind expert mode — expert mode changes too much UI to double as a debug gate. Reframe them in plain language instead (§5H). |
| "Expert mode" vs Player level "Expert" | Keep them **independent**. Player level is the misnomer — it only sets time pressure, so rename it to what it is (a **pace** setting). The display toggle gets a self-describing name ("Show technical details"). No grouping of display detail under player level. |

**Second round (Sarah, inline answers to §10 + execution notes, same day):** all five open
questions are resolved — see §10 for her answers and the operative resolutions. Execution
directives (branch/commit style, distance-readability rule) are §11.

---

## 3. Principles for every string in the app

1. **Coach, don't grade.** The headline is always what the student did and what to try next, in
   words. Numbers support; they never lead. The Progress screen's `metrics/Coaching.kt` pattern
   (mastery words, bias phrases, one insight) is the reference implementation.
2. **The 10-second rule.** A 12-year-old opening any screen must know within 10 seconds what it is
   for and what to do. If a sentence needs the reader to know how the app works internally
   ("first stable pitch", "noise gate"), it fails.
3. **Progressive disclosure, not removal.** Technical detail (cents, Hz, thresholds) moves behind
   the "Show technical details" toggle or an in-place expander — it is never deleted. Experts and
   curious students lose nothing.
4. **Plain ≠ childish.** Musical terms a bass student learns at the academy (arco, pizzicato,
   position, sharp/flat, triad) are fine — first occurrence gets a gloss where cheap ("Pizz
   (plucked)"). Engineering terms (gate, window, trace, calibration verdicts in ms) are not fine.
5. **One name per concept, everywhere** (§4 glossary). Translated UIs amplify inconsistency;
   fix it in English first.
6. **Every string is a resource.** No user-visible literal in Kotlin. This is both the i18n
   mechanism and the enforcement mechanism for 1–5 (all copy reviewable in one file).
7. **Readable at playing distance.** Any screen used while holding the bass — game prompts and
   reveals, the calibration wizard's mid-flow steps, count-ins — stays large, low-density, and
   legible from ~2 m. Results/summary screens (phone back in hand) may be denser. A rewrite that
   adds words must not shrink the type: prefer fewer words over smaller text. (Sarah, 2026-07-16;
   extends the existing TESTING.md 2-meter item.)

---

## 4. Canonical glossary (English)

Resolve today's drift to **one term per concept**. This is the source of truth for the rewrite and
for translators.

| Concept | Canonical term | Replaces / notes |
|---|---|---|
| The app | **Double Bass Coach** (pending final confirmation §10) | "Intonation Trainer", "Double bass\nintonation trainer", "Bass Pitch" |
| An exercise type | **game** | "exercise", "practice mode". Games are the brand — motivation is the point. |
| One playthrough | **round** | "game" (as playthrough), "session" |
| The four games | **Find the Note** (was Note Accuracy/"Accuracy"), **Long Notes** (was Sustain — "Hold Steady" vetoed by Sarah, wrong image for a moving bow; "long tones" is the classic pedagogy term and translates cleanly), **Shifts** (was Shift Trainer), **Chords** | Home cards, Progress tabs, and internals must agree on the display names. (DB `configKey` exercise ids stay untouched — display names only.) |
| Shift game levels | **One string — big shifts / One string — all fingers / Across strings** | "Basic/Intermediate/Advanced" (collided with pace labels) |
| Pace setting | **Pace** with levels **Calm / Steady / Quick / Swift** — labels must honestly match the timeout feel (Sarah: "Lightning with several seconds doesn't feel lightning"). Timeouts themselves get a provisional tightening in their own commit (§10 Q3). | "Player level — Beginner/Intermediate/Advanced/Expert". Enum ids/persisted values unchanged. |
| Technical display toggle | **Show technical details** | "Expert mode" |
| Playing modes | **Arco (bowed)** / **Pizz (plucked)** — gloss on first occurrence per screen, plain "arco"/"pizz" elsewhere | "PIZZ", "Pizz", "plucked", "pizzicato" used interchangeably |
| Right-after-play feedback | **spot on / close / off** (aligned with the existing ResultColors semantics) | mixed vocabularies |
| Longer-term skill words | **Locked in / Solid / Developing** (unchanged, from Coaching.kt) | — |
| Timeout outcome | **"No note heard"** (one phrasing for all games) | "no note detected" / "no shift detected" / "—" |
| Open string tone (Chords) | **open string** | "open" vs "open string" within one screen |
| Personal best | **Best** (spelled out; never "PB") | "PB 270/300" on Home |
| Room noise setup | **Room check** (the quick one) / **Full setup** (the wizard) | "Calibrate surroundings", "Full calibration wizard", "noise gate", "separation verdict" |
| Saved diagnostics | **recording** (audio you saved), **practice report** (game trace zip sent as feedback) | "snippet", "trace", "detection log" as user-facing words |
| Spelling/style | Sentence case for buttons and titles ("Tune up", "Let's go again"); **Practice** (US) consistently; musical glyphs ♯/♭ | "Get Started" vs "Skip for now"; "Practise" in two achievements |

---

## 5. Workstreams

### A. App identity & naming

Play Store facts that answer Sarah's "how easy is it to change later":
- The **applicationId** (`be.drakarah.intonation`) is permanent — it can never change once published.
  It is invisible to users; no action needed.
- The **display name** (Play listing title, launcher label, in-app branding) can be changed at any
  time with a normal release/listing update (re-review may apply, but it's routine). So the name
  choice is **not** a one-way door.
- Play listing title limit: **30 characters**. "Double bass intonation coach" = 28 (fits, no room to
  breathe); "Double Bass Coach" = 17.

**Proposal (pending Sarah's confirmation):**
- Play listing title: **"Double Bass Coach"** — future-proof (rhythm exercises later), memorable.
- Play short description carries the searchable specifics: "Intonation coach for double bass —
  games, scores and feedback that keep you practicing."
- Launcher label: **"Double Bass Coach"** too — Sarah's call (§10 Q1): never shorten to "Bass
  Coach", people read "bass" alone as bass guitar. Accept that some launchers ellipsize a 17-char
  label; "Double Bass…" still reads correctly.
- In-app: Home header and Onboarding title both say **Double Bass Coach**; the "Welcome to Bass
  Pitch" and "Double bass\nintonation trainer" strings disappear.

Tasks:
1. Fix `strings.xml` `app_name`, add `launcher_label` if different, update Home header,
   Onboarding title, About text, feedback email subject ("Intonation Trainer trace: …").
2. Play Store listing texts live in `docs/play-store/` as versioned files (title, short desc, full
   desc, in EN/NL/FR) — they are user-facing strings too and follow the same review process.

### B. i18n foundation (EN base, NL + FR at launch)

**Architecture**
1. **Extract every user-facing literal to `res/values/strings.xml`.** The audit's file list is the
   work queue: all of `ui/**`, plus label-bearing domain code (see next point). Use resource ids
   namespaced by screen (`home_`, `settings_`, `wizard_`, `game_`, `coach_`…).
2. **Keep pure-Kotlin domain pure.** `metrics/Coaching.kt`, `game/Achievements.kt`,
   `game/Positions.kt` (ShiftLevel), `game/PlayerLevel.kt`, `game/Scoring.kt` (Difficulty),
   `game/ChordPool.kt` (ChordFingering, chord quality), `ui/home/HomeViewModel.kt` (DailyFocus)
   currently carry English labels *inside* enums/data. Pattern: **domain exposes the enum/id only;
   a UI-layer mapper (`ui/common/Labels.kt`) maps id → `R.string`**. This preserves unit-testability
   (tests assert enums, not prose) and makes every label translatable.
   - `CoachingTest` and any test asserting label text must be updated to assert enums/ids.
   - `selectInsight(...)` currently *composes English sentences* in domain code. Refactor to return
     a typed `Insight` (kind + position + mode + direction); the UI renders it via resources. Same
     for `WeekTrend.phrase`, `Bias.label/detailedLabel`, Sustain coaching lines
     (`coachingText` in SustainScreen), and the chord-name builder ("Ré Majeur"/"D major" —
     quality word becomes a resource; nl/fr both use "majeur/mineur", EN "major/minor").
3. **Plurals and placeholders.** Use `<plurals>` for: day streak, "N time(s) this week", rounds,
   notes, stars, seconds. Replace `String.format`/interpolation with positional `%1$s` resource
   args. Never concatenate translated fragments ("start %+.0f¢ · landed %+.0f¢" becomes one
   parameterized string).
4. **Locale plumbing.** Add `values-nl/` and `values-fr/`; add `localeConfig` XML + manifest entry
   (Android 13+ per-app language via system settings). v1 follows the system language — no in-app
   language picker (add later only if academy feedback asks for it; would need the appcompat
   per-app-locale backport for pre-13 devices).
5. **Note names are a setting, not a locale.** Solfège vs letters stays user-chosen (asked in
   onboarding). One nuance: solfège spelling is currently French-accented ("Ré"); Dutch solfège
   writes "re". Make the solfège table locale-aware (`values-nl` overrides the accent), keep
   pitch math untouched (`music/NoteSpec.kt` display names → resources or locale-keyed table).
6. **What stays English:** log/trace file contents, JSONL keys, filenames, code comments, the
   feedback email *body template* can stay EN (Sarah reads the reports) — but the visible dialog
   text around sending it is localized.

**Translation process**
1. String freeze after the plain-language rewrite (§5C) — translate once, not twice.
2. Claude drafts NL and FR including the musical vocabulary (zuiver spelen / juste, "een tikje te
   hoog", arco/pizzicato are international). Coaching words need care, not literalism:
   e.g. Locked in / Solid / Developing → nl draft "Vast / Stevig / Groeit nog", fr draft
   "Bien ancré / Solide / En progrès" — **Sarah reviews NL natively; FR reviewer TBD (§10)**.
3. Longest-language check: German-style expansion doesn't apply, but NL/FR run ~15–25% longer than
   EN — verify the big game texts (prompt, reveal words, buttons) at Pixel 6a width and the 2-meter
   readability rule (TESTING.md item) in all three languages. Use Android Studio's pseudolocale
   (`en-XA`) during extraction to catch missed literals.

### C. Plain-language rewrite + "Show technical details" sweep

The rule for every number: **words first; the number appears when "Show technical details" is on**
(or inside an explicit expander on diagnostic screens). What the beginner sees instead of cents is
the existing result vocabulary (spot on / close / off + sharp/flat direction) — already proven on
the Progress screen.

Screen-by-screen direction (from the 2026-07-16 audit; file references are the audit's):

| Screen | Today's problem | Direction |
|---|---|---|
| **Home** | "PB", "pitch detection has not been tuned to this phone's microphone", card subtitles leak mechanics ("First stable pitch counts", "Don't let the ring reset") | Gate dialog rewritten as a coach: "Let's get set up first — the app needs to learn your bass and your room (2 minutes, once)." Card subtitles describe the *goal*: "Land the note right the first time." / "Keep one note perfectly steady." "Best: 270/300" spelled out. |
| **Game reveals (all four)** | Raw "%+.1f cents" for everyone; "no note detected"/"no shift detected"/"—" inconsistent; "shift distance"; "TRENDING SHARP / come down" | Beginner reveal: word + direction ("Close — a little sharp"). Technical-details ON adds "(+12.3¢)". Unified "No note heard". Shift breakdown "start +20¢ · landed +20¢" becomes a sentence: "Great shift — your starting note was a little sharp." (numbers behind the toggle). Drift banner: "You're drifting sharp — aim a touch lower." |
| **Round summaries** | "average 7.3 cents off", "cents off per note" axis, ImprovementLine "%.1f cents average — last week %.1f" | Headline = score + one coach line (see §5D). "How in tune: Solid" instead of avg cents; ImprovementLine becomes "More in tune than last week" (already have `WeekTrend`). Numbers behind the toggle. |
| **Tune up** | "cents sharp/flat" numeric readout | The needle stays (it's the one tuner screen); label becomes "a little sharp / a little flat / in tune", number shown with technical details ON. |
| **Drone** | "sounding at Sol3", "Add fifth" unexplained | "You'll hear: Sol (higher octave — phone speakers can't play a bass's low notes)". "Add fifth — a second tone that makes tuning easier". |
| **Calibrate surroundings → "Room check"** | "noise floor", "gate", "Gate set to 45", "separation", raw levels | Frame as a hearing test: "Step 1 — stay quiet so the app can hear your room. Step 2 — play as softly as you ever would." Verdicts: "Your room is nice and quiet" / "Workable, but very soft notes might get missed" / "Too noisy — the app can't tell your playing from the background. Try a quieter room." Numbers/gate value behind technical details. Button "Use this gate" → "Save". |
| **Full calibration wizard → "Full setup"** | Summary is a DSP diagnostic report ("octave-settle 300 ms", "Pizz lock timing", "wait/hold ms", references to a "Pitch debug screen" that is actually labeled Pitch Analyzer) | Split the summary: **top = per-note friendly verdict** (✓ "heard correctly" / "had trouble" per note, one overall sentence: "Your phone and bass are set up — everything was heard correctly."), **bottom = "Technical details" expander** with the current full report (kept verbatim for feedback emails/screenshots — this is a diagnostic channel, §5H). Fix the cross-reference name. Intro copy: "The app listens to a few notes to learn how your bass sounds on this phone." |
| **Onboarding** | "Welcome to Bass Pitch", "pitch detection may be unreliable" | Replaced wholesale by the wizard (§5E). |
| **Settings** | See §5F | See §5F. |
| **Achievements** | 6 descriptions use raw cents ("Land a note within 2 cents"); Practice/Practise mix | Beginner description in feel-terms ("Land a note dead center"); cents version shown with technical details ON (two resource variants per cents-achievement). Spelling unified. |
| **Progress** | Already the reference — small leaks: "¢" in Sustain "Steadiness" metric not gated; tab "Accuracy" ≠ Home "Note Accuracy"; insight lines say "arco 1st" without gloss | Gate the Sustain ¢; rename tabs to the canonical game names; keep insight phrasing (already coach-voice). |
| **About** | "freezes the first stable pitch" developer framing | "It listens for the first note you land and scores that — so you learn to place the note right, not to correct it afterwards." License/attribution untouched (GPL obligation). |
| **Recordings / Pitch Analyzer** | See §5H | See §5H. |

Mechanism: `LocalTechnicalDetails` (a CompositionLocal or plumbed boolean, sourced from
`settings.expertMode`) so any composable can branch without each screen re-reading the repository.
The shared result components (§5G) are where most of the gating lives once, not per screen.

### D. Coaching voice (games + scores stay; the app starts *talking* like a coach)

Scope-controlled: this is a copy + one-component change, not a features project.

1. **Tone guide** (goes in this doc's §appendix when written; enforced in review): short sentences,
   direct address ("you"), always one of: celebrate / steady / redirect. Never blame the player for
   detection artifacts. Never two tips at once.
2. **One coach line on every round summary.** Reuse `selectInsight`-style logic on the *round's own
   attempts* (bias within the round, timeout clusters, improvement vs. player's average) to say one
   thing: "Most of your misses were a touch flat — aim a hair higher next round." This is the
   lightweight first step toward the backlog "Teacher's notebook" (TESTING.md), and it reuses the
   round data already in hand — no schema change.
3. **Existing gamification is untouched** (scores, stars, streaks, bests, achievements, sounds).
   The coaching layer wraps it; it does not replace it.
4. Rename residual grader-language: "Round complete" → "Nice work!" (+ score), pace-change
   suggestions already have the right tone (keep).

### E. Onboarding wizard (replaces the single welcome screen)

One question per screen, big touch targets, a progress indicator ("2 of 7"), every screen skippable
(sensible default already selected), everything changeable later in Settings (last screen says so).
Answers persist immediately via existing `SettingsRepository` setters. `onboardingCompleted`
semantics unchanged.

Proposed screens (order matters — easy, identity-forming questions first):

1. **Welcome** — app name + one sentence of promise: "Your double bass coach — short games that
   train you to play in tune." (No feature list, no calibration talk yet.)
2. **Note names** — "How do you name your notes?" → **Do Ré Mi** / **C D E**, with a visual preview
   of the same note both ways. Sets `noteNameStyle`. (Explainer: "Whatever your teacher uses.")
3. **Pace** — "How much thinking time do you want?" → the four pace levels with plain descriptions
   ("Calm — plenty of time to find each note", …). Sets `playerLevel`. Default Calm. Copy notes
   scoring is equally fair at every pace.
4. **Round length** — "How long should one round be?" → 5 / 10 / 20 notes ("Short — about a
   minute", …). Sets `roundLength`.
5. **Technical details** — "How should the app talk to you?" → **Plain language** (default) /
   **Show the numbers** ("exact cents and percentages — for advanced players and the curious").
   Sets `expertMode`.
6. **Beta note** — honest, friendly: "Double Bass Coach is new. It has been tested on a small number
   of phones and basses, so it might not hear *your* setup perfectly yet. If something seems off,
   you can record a practice report and email it to the developer — that's how the app gets better.
   You'll find this under Settings → Help improve the app." (Explains the trace/feedback loop in
   one breath; the mic-privacy warning stays at the moment of actually sending, where it belongs.)
7. **Set-up nudge (last)** — "One more thing — the app needs to learn your bass. It listens to your
   room and a few notes you play. Takes about 2 minutes, once." → **Set up now** (→ wizard/Full
   setup) / **Later** (→ existing skip-confirmation, then Home; the pre-game "Ready to play?" gate
   remains the safety net).

Not asked in the wizard (deliberate, anti-overwhelm): positions (defaults to 1st; chips are
discoverable on Home where they're used), difficulty (Standard default), sound feedback (on by
default; a toggle question is not worth a screen), A4 (440 default), chord fingering, mix
sharps & flats. All remain Settings-only.

Implementation: keep route `onboarding`, rebuild `OnboardingScreen.kt` as a pager of small
composable steps; each step = title + one-line explainer + 2–4 fat option cards. Reuse the wizard's
big-text styles for 2-meter readability (a student may prop the phone on the stand already).

### F. Settings reorganization

Same settings, new grouping and copy (all strings through §5C review; descriptions max ~2 lines
beginner + expandable "more" where genuinely needed):

- **Coaching** — Show technical details (renamed from Expert mode; description no longer
  overpromises "across the app" — after this plan it will actually BE across the app), Pace
  (renamed from Player level), Difficulty ("How forgiving scoring is" — cents figure behind
  technical details), Round length.
- **Notes & tuning** — Note names, Mix sharps & flats (rewritten: "Sometimes show La♯ as Si♭ so you
  learn both names" — drop "enharmonic"), Concert pitch ("The A your orchestra tunes to — leave at
  440 unless your teacher says otherwise"), Chord fingering.
- **Sounds & warnings** — Sound feedback + volume, Drift warning ("Warns when everything you play
  is leaning sharp or flat"), Count right note wrong octave (rewritten: "Low notes can fool the
  microphone by an octave. Keep this on so that never costs you points." — the
  fundamental/overtone explanation moves behind technical details).
- **Set-up** — Room check, Full setup ("new phone or new bass"), last-run timestamps in friendly
  relative form ("2 days ago"). The raw noise-gate slider/value visible only with technical
  details ON (the room check sets it for everyone else).
- **Help improve the app** (renamed from "Debug" — this is the beta feedback channel, §5H) —
  Record practice reports (renamed trace toggle; description: "Records your rounds — sound and
  all — so you can email a report when something seems wrong."), View reports, and the feedback
  email address surfaced here explicitly.
- **Your data** — backup/restore (copy already good; keep).
- **About & licenses** — unchanged position.

### G. Shared game UI consolidation (enabler, do first among code changes)

The four game screens each duplicate `CountIn`, the reveal skeleton, and `DoneContent`
(audit: NoteAccuracyScreen/SustainScreen/ShiftScreen/ChordsScreen each have private copies;
"Let's go again" literal exists 4×). Consolidating is what makes §5C/§5D cheap and consistent:

1. `ui/common/GameCountIn.kt` — one count-in.
2. `ui/common/RevealBadge.kt` — word-first result (spot on / close / off + direction), with the
   technical-details cents suffix in exactly one place.
3. `ui/common/RoundSummaryScaffold.kt` — score headline, coach line slot (§5D), game-specific
   breakdown slot, ImprovementLine (word-first version), AchievementUnlocks, TraceFeedbackPrompt,
   "Let's go again"/"Done" footer.
4. While in there: apply the TESTING.md open item "results screens legible from 2 meters" to the
   shared components once (the Shift reveal small-text complaint).

Games keep their own phase machines and prompts; only presentation shells unify.

### H. Diagnostics stay visible, get reframed (beta posture)

Sarah's call: with one phone + one bass tested, the diagnostic loop is a *feature* of the beta, not
developer residue. So:

- **Pitch Analyzer** stays on Home under Tools, renamed copy: card title stays "Pitch Analyzer"
  (recognizable in feedback conversations), subtitle: "Check what the app hears — useful if notes
  aren't being picked up." Inside: top half friendly (big detected note, "heard you: Sol2 ✓",
  the note-sweep as "Check every note" with plain state banners), all pipeline numbers (raw/
  smoothed/accepted Hz, harmonic energy, window/overlap/source/sensitivity line) collapse into a
  "Technical details" expander — visible to anyone who opens it, hidden by default. Fix the
  wizard's textual reference ("Pitch debug screen" → "Pitch Analyzer").
- **Recordings screen**: user-facing words per glossary ("recording", "practice report"); the
  `window 4096 · gate 45 · src 1` config line moves into each row's expandable detail; the
  mic-privacy sentence before emailing stays (good as-is, translate it). Keep the raw filename
  visible (support conversations reference it).
- **Trace toggle** reframed as feedback ("Help improve the app", §5F) and introduced by onboarding
  screen 6. `TraceFeedbackPrompt` copy: "Recorded this round for a practice report — how did it
  go?"
- **Post-beta follow-up** (explicitly deferred): revisit whether Pitch Analyzer moves off Home once
  calibration is proven on N>“just Sarah's” rigs.

---

## 6. Sequencing

Ordered so translation happens exactly once, against frozen strings:

| Phase | Work | Size | Depends on |
|---|---|---|---|
| 1 | §5G shared game components + `LocalTechnicalDetails` plumbing | M | — |
| 2 | English rewrite: glossary application + all §5C copy + §5F settings regroup + §5H reframing + §5D coach line on summaries | L | 1 |
| 3 | §5A naming (in-app strings; Play listing drafts to `docs/play-store/`) | S | 2 (name confirmed §10) |
| 4 | §5E onboarding wizard | M | 2 (uses final copy patterns) |
| 5 | i18n extraction of the (now-final) English strings; domain-label refactor (enums → UI mapper); plurals; localeConfig | L | 2–4 (string freeze) |
| 6 | NL + FR translation drafts; Sarah's NL review; FR review; length/readability pass on device in all 3 languages | M | 5 |
| 7 | Play Store collateral (listing texts EN/NL/FR; screenshots per language later with marketing task) | S | 3, 6 |

Rough total: the two L phases dominate; everything is mechanical after Phase 2's copywriting.
Phases 1–2 are a natural first PR; 4 and 5 can proceed in parallel after the freeze.

## 7. Testing & verification

- **Unit:** domain-label refactor updates `CoachingTest` (assert enums, not prose); new tests for
  the round coach-line selection (§5D) as a pure function; existing game/regression suites must
  pass untouched (no behavior changes).
- **TESTING.md discipline:** each phase lands with Pending items. Highlights: wizard flow end-to-end
  on her Pixel (fresh install → all 7 screens → Full setup), "Ready to play?" gate still fires after
  skip, every game reveal readable from 2 m in NL (longest strings), technical-details toggle flips
  every gated surface (checklist per screen), per-app language switch on Android 13+ shows NL/FR.
- **Fresh-install path:** onboarding testing needs cleared app data; note the emulator quickboot
  snapshot caveat (CLAUDE.md) — verify on-device.
- **Pseudolocale pass** (`en-XA`) to prove zero remaining hardcoded literals before Phase 6.

## 8. Risks

- **Translation drift**: any copy change after Phase 5 costs 3×. Mitigation: hard string freeze,
  copy changes batched.
- **Domain-label refactor breadth**: touches Achievements/Coaching/Positions/Scoring/ChordPool and
  their tests in one sweep. Mitigation: it's Phase 5's first commit, purely mechanical, no threshold
  or logic edits allowed in the same commit.
- **`configKey` invariance**: game display names change, but the persisted exercise/variant ids in
  `configKey(...)` must NOT (scores/PBs would silently fork). Explicit check in Phase 2 review.
- **Tone flattening**: rewriting 350 strings risks bland uniformity. Mitigation: Sarah reviews the
  full English string table (one file after extraction) — she wanted pushback and gives it.
- **Name change on Play Store**: not yet published, so zero risk now; title remains changeable
  post-launch (only applicationId is frozen).

## 9. Out of scope (tracked elsewhere / later)

- Teacher's notebook cross-session observations (TESTING.md backlog — §5D is the seed).
- Reminder notifications, new games (glissando, scales), polyphonic profiling (backlog).
- Light theme, in-app language picker, tablet layouts.
- Play Store graphics/marketing assets (separate TESTING.md item; the *texts* are Phase 7 here).
- Pizz-vs-arco mismatch detection (open feedback item, detection work not UI).

## 10. Open questions — RESOLVED (Sarah, 2026-07-16)

1. **Final name:** "Double bass is prominent so i would avoid truncating it to bass, people think
   bass guitar when they don't see 'double bass'."
   → **Resolution:** "Double Bass Coach" everywhere — Play title, launcher label, in-app. Never
   shorten to "Bass Coach"; accept launcher ellipsis. (§5A updated.)
2. **French review:** "Who knows, my teacher knows french, maybe i can ask him."
   → **Resolution:** FR drafted in Phase 6; review by her teacher (tentative). If that falls
   through, ship FR with a "beta quality — tell us about awkward phrasing" note.
3. **Pace labels:** "If the timeout times actually make sense for the naming. Lightning with
   several seconds doesn't feel lightning for example. I think the timeouts are a little too
   lenient anyway, i can play on advanced/expert and i am definitely not an expert, i just
   finished one year."
   → **Resolution:** labels **Calm / Steady / Quick / Swift** (no over-promising "Lightning"),
   AND a **provisional timeout tightening in its own commit** (gameplay change, not copy —
   gets a TESTING.md Pending item for her play-verification; her one-year-in skill landing on
   "Expert" means the ladder is compressed at the top).
4. **Game display names:** "'Hold steady' sounds really bad for a moving bow imo."
   → **Resolution:** Sustain = **"Long Notes"** (the classic long-tones exercise; nl "Lange
   noten", fr "Notes longues"). "Find the Note" / "Shifts" / "Chords" stand.
5. **Coach line tone:** natural coach voice confirmed; consistent with `docs/metrics-plan.md`'s
   premise and the "Teacher's notebook" feedback item. Her guidance verbatim: praise is not
   optional — "it's very demotivating to not get any thumbs up… then it feels like work, chore" —
   but avoid sycophancy; celebrate real results *and* give one concrete thing to work on.
   → **Resolution:** coach lines always pair acknowledgment with (at most) one actionable point;
   praise states *what* was good ("your shifts landed clean today"), never generic cheerleading.

## 11. Execution notes (Sarah, 2026-07-16)

- **Distance readability rule** (now principle #7 in §3): anything used while holding the bass —
  games except results, calibration wizard except results — stays large, legible from distance,
  and information-sparse.
- **Attack plan:**
  - Break the plan into digestible todos.
  - Work on git branch **`ux-overhaul`**.
  - **One commit per todo** — reviewable steps, not one huge commit touching all files.
  - Implemented directly in the main session (full context); no subagents re-reading everything.
