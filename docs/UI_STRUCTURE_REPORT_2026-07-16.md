# UI Structure Report — navigation, onboarding, expert mode, settings, theme — 2026-07-16

**Source:** code-level exploration, baseline = working tree of 2026-07-16.
**Purpose:** structural companion to `docs/UI_STRING_AUDIT_2026-07-16.md`; both feed
`docs/UX_OVERHAUL_PLAN_2026-07-16.md`. Line numbers will drift; treat them as pointers.

---

## 1. Navigation

Uses **Jetpack Compose Navigation** (`androidx.navigation.compose`). Single `NavHost`, no bottom nav / drawer. Everything is defined in one file:

- `ui/AppNav.kt:32-55` — `Routes` object (all route constants + builder helpers).
- `ui/AppNav.kt:57-179` — `AppNav()` composable with the `NavHost`.

Start destination is chosen at `AppNav.kt:73`: `Routes.HOME` if `onboardingCompleted`, else `Routes.ONBOARDING`. Settings are collected first and `null` short-circuits render (`AppNav.kt:66`) to avoid flashing onboarding.

Routes and how each is reached:

| Route | Const (`AppNav.kt`) | Reached from |
|---|---|---|
| `home` | :33 | Start dest (post-onboarding); popUpTo target from onboarding/wizard |
| `onboarding` | :34 | Start dest when `!onboardingCompleted` |
| `wizard` | :43 | Onboarding "Get Started" (:78); Settings "Run wizard" (:117) |
| `calibrate` | :42 | Home "Calibrate surroundings" (:100/`HomeScreen.kt:327`); Settings (:116); pre-game gate |
| `tune` | :36 | Home "Tune up" (:98/`HomeScreen.kt:233`); pre-game gate |
| `drone` | :44 | Home "Drone mode" (:99/`HomeScreen.kt:239`) |
| `settings` | :37 | Home Settings icon (:102/`HomeScreen.kt:162`) |
| `progress` | :38 | Home BarChart icon (:101/`HomeScreen.kt:159`) |
| `achievements` | :39 | **Nested** under Progress only (:138, `ProgressScreen` → `onOpenAchievements`) |
| `about` | :40 | **Nested** under Settings only (:115) |
| `recordings?onlyTraces={}` | :41 | Settings "Traces" (onlyTraces=true, :118); Debug "Recordings" (onlyTraces=false, :175) |
| `noteaccuracy/{mode}` | :45 | Home Note Accuracy card + Today's focus (:94) |
| `sustain/{mode}` | :46 | Home Sustain card + focus (:95) |
| `shift/{mode}/{level}` | :47 | Home per-level Shift cards + focus (:96) |
| `chords/{mode}` | :48 | Home Chords card + focus (:97) |
| `debug` | :35 | Home "Pitch Analyzer" card (:103/`HomeScreen.kt:333`) |

**Debug screen entry points:** The debug/pitch screen (`ui/debug/DebugPitchScreen.kt`) is reached from Home's **"Pitch Analyzer"** card (`HomeScreen.kt:329-334` → `onOpenDebug`). It is not gated or hidden — it sits in the Home "Tools" section. From Debug the user can open `recordings` with `onlyTraces=false` (`AppNav.kt:175`). Game traces (`recordings?onlyTraces=true`) are reached from Settings.

Wizard back-handling is conditional (`AppNav.kt:124-133`): during first-run (`!onboardingCompleted`) back goes to HOME; otherwise `popBackStack`.

---

## 2. First-run / onboarding

**Onboarding exists.** Flag: `AppSettings.onboardingCompleted: Boolean = false` (`settings/SettingsRepository.kt:102`), key `booleanPreferencesKey("onboardingCompleted")` (:188), stored in DataStore Preferences (datastore name `"settings"`, :153). Setters: `setOnboardingCompleted` (:284), and it is also force-set `true` when a full calibration completes (`setFullCalibration`, :280).

Screen: `ui/onboarding/OnboardingScreen.kt`. It is a single welcome page:
- Music-note icon, title **"Welcome to Bass Pitch"** (:81 — mismatches the actual app name "Intonation Trainer"), tagline about double-bass intonation (:90).
- Two explainer items (`OnboardingItem`, :98-110): "Room calibration" (measures background noise) and "Instrument setup" (play notes to tune detection).
- Primary button **"Get Started"** → `onStartCalibration` → navigates to `wizard`, popping onboarding (`AppNav.kt:77-81`).
- Secondary **"Skip for now"** → confirmation `AlertDialog` ("Skip calibration?", :43-61) → "Skip anyway" sets `onboardingCompleted=true` and goes HOME (`AppNav.kt:82-89`).

So onboarding is a **single intro screen that funnels into the calibration wizard**; there is no multi-step tour of the games/features. It explains only calibration, not how to play. (The overhaul plan §5E replaces it with a 7-screen wizard.)

**Pre-game "Ready to play?" gate** (`ui/home/HomeScreen.kt:67-112`, backed by `HomeViewModel.kt:93-120`): Before launching any scored game (and Today's Focus), `gated { start }` (`HomeScreen.kt:69-71`) checks three staleness flags:
- `needsFullCalibrationReminder` — `fullCalibrationAt == 0L`, i.e. wizard never run (`HomeViewModel.kt:112-115`).
- `needsTuneReminder` — `now - lastTunedAt > 8h` (`TUNE_STALE_AFTER_MS`, `HomeViewModel.kt:96-100,167`).
- `needsCalibrationReminder` — `now - lastCalibratedAt > 8h` (:104-108).

If any is set, an `AlertDialog` titled **"Ready to play?"** lists the specific problems (`HomeScreen.kt:79-87`) and offers per-issue fixes ("Run full calibration wizard" → Settings, "Tune up first", "Calibrate surroundings first") plus **"Start anyway"** which calls `viewModel.suppressReminders()` (sets an in-memory flag until app restart, `HomeViewModel.kt:117-120`) and proceeds. `lastTunedAt`/`lastCalibratedAt`/`fullCalibrationAt` all live in SettingsRepository (`SettingsRepository.kt:50-53,92`).

---

## 3. Expert mode

- **Setting:** `AppSettings.expertMode: Boolean` (`settings/SettingsRepository.kt:106`), **default `false`**. Key: `booleanPreferencesKey("expertMode")` (:189); read at :239; setter `setExpertMode` (:363-365).
- **Toggle UI:** `ui/settings/SettingsScreen.kt:189-206`, under a **"Display"** section header — the first setting on the Settings screen. A `SettingBlock("Expert mode", …)` with a `Switch` bound to `settings.expertMode` → `repo.setExpertMode(it)`. Description: "Show the technical detail — exact cents, percentages and deviations — across the app. Off keeps things in plain language for younger or newer players."
- **Every read of it today:**
  1. `SettingsRepository.kt` (definition/key/read/setter).
  2. `ui/progress/ProgressViewModel.kt:64-67` — exposes `expertMode: StateFlow<Boolean>`.
  3. `ui/progress/ProgressScreen.kt:101` — `val expert by viewModel.expertMode…`, then threaded into `CoachingSummaryCard(it, isSustain, expert)` (:163) and `MasteryByPosition(state.positionMastery, expert)` (:168).

  It is currently read **only on the Progress screen**. Nowhere else in the app consumes it yet — this is the pattern intended to spread (the plan renames it "Show technical details" and plumbs it via a CompositionLocal).

**`metrics/Coaching.kt` summary** (pure Kotlin, no `android.*` — unit-tested via `CoachingTest`; single source of truth for how Progress *describes* intonation):

- **Mastery words, not percentages.** `MasteryBand` enum (:30-42): `LOCKED` ("Locked in"), `SOLID` ("Solid"), `DEVELOPING` ("Developing"). `MasteryBand.of(avgAbsCents, thresholds)` classifies by average absolute cents.
- **Per-exercise thresholds** `MasteryThresholds(lockedMax, solidMax)` (:18-27): `NOTE(10,25)`, `SHIFT(20,45)`, `CHORDS(15,30)` — shifts land wider so they get looser bands.
- **`masteryFraction(avgAbsCents, thresholds)`** (:47-59): bar fill 0..1, piecewise-lerped and anchored so crossing a tier visibly fills (floor 0.05 so a bar is always visible).
- **Bias** (`BiasDirection`, `Bias`, `biasOf`, :63-94): sharp/flat tendency. `Bias.label` = plain ("a bit flat"), `Bias.detailedLabel` = expert ("runs 12¢ flat"). Centered below `BIAS_CENTERED_MAX = 6f`.
- **Week trend** (`TrendDirection`, `WeekTrend`, `weekTrend`, :96-132): you-vs-last-week. `WeekTrend.phrase` = beginner text ("more in tune than last week"); steady band `TREND_STEADY_BAND = 2f`.
- **`PositionMastery`** (:136-152) — per-(position,mode) row; derives `band`, `fraction`, `bias`; `hasEnoughData` gate via `MIN_SCORED_FOR_VERDICT = 12` (:160).
- **`CoachingSummary`** (:171-184) — the "teacher's notebook" block: rounds this week, streak, `weekBand`, trend, `rightNotePct`/`steadyPct`, `insight`, optional `SustainSummary` (:163-167, sustain has no scored cents).
- **`selectInsight(positions, trend)`** (:196-220) — the single "watch this" line, prioritised: biggest systematic bias (concrete fix, `INSIGHT_BIAS_MIN = 15f`) → celebrate tightening trend → name most secure position as anchor → null.

**How Progress uses it:** `ProgressViewModel.buildState` (`ProgressViewModel.kt:84-151`) builds `PositionMastery` rows (:107-120), gates verdicts on `MIN_SCORED_FOR_VERDICT` (:126-129), computes `weekBand`/`trend`/`selectInsight` and assembles `CoachingSummary` (:130-139). `ProgressScreen.kt` renders `bandColor()` from the band (:84-87), shows `PlainSummary` vs `ExpertSummary` depending on `expert` (:312), `intonationSentence(band)` (:371-374), and in `MasteryByPosition` shows the band `label` + tiered bar always, but the raw cents/`detailedLabel` **only when `expert`** (:471-481, 495-506). This word-first / cents-as-opt-in pattern is the model to carry app-wide.

**i18n caveat (see plan §5B):** the enums above carry English prose (`label`, `phrase`, `detailedLabel`, and `selectInsight` composes full sentences). For localization the domain should return typed values (enum + parameters); the UI layer maps them to string resources. `CoachingTest` then asserts enums, not prose.

---

## 4. SettingsRepository — every setting

Source: `settings/SettingsRepository.kt:24-107` (defaults), :157-190 (keys), :192-241 (read). All persisted in a single DataStore Preferences store named `"settings"`.

| Setting | Type | Default |
|---|---|---|
| `noteNameStyle` | `NoteNameStyle` enum | `SOLFEGE` |
| `mixEnharmonics` | Boolean | `false` |
| `a4` | Double (Hz, coerced 415–446) | `440.0` |
| `difficulty` | `Difficulty` enum | `STANDARD` |
| `playerLevel` | `PlayerLevel` enum | `BEGINNER` |
| `roundLength` | Int | `10` |
| `positions` | `Set<Position>` (CSV of ids) | `{FIRST_POSITION}` |
| `chordFingering` | `ChordFingering` enum | `NATURAL` |
| `ignoreWrongOctave` | Boolean | `true` |
| `soundFeedback` | Boolean | `true` |
| `gameVolume` | Float 0..1 | `1f` |
| `driftWarning` | Boolean | `true` |
| `lastTunedAt` | Long (epoch ms) | `0` |
| `lastCalibratedAt` | Long (epoch ms) | `0` |
| `micSensitivity` | Float (coerced 20–95) | `55f` |
| `audioSource` | Int (MediaRecorder.AudioSource) | `MIC` |
| `missingFundamentalMaxHz` | Float | `63f` |
| `oddHarmonicMinRatio` | Float | `2f` |
| `oddHarmonicMinRelative` | Float | `0.02f` |
| `pizzOddHarmonicMinRatio` | Float | `1.2f` |
| `pizzOddHarmonicMinRelative` | Float | `0.01f` |
| `wrongNoteMinLevel` | Float | `55f` |
| `lowestPlayableHz` | Float | `40f` |
| `pizzOctaveSettleMs` | Long | `300` |
| `pizzAttackSkipMs` | Long | `60` |
| `pizzStabilityWindowMs` | Long | `150` |
| `fullCalibrationAt` | Long (epoch ms) | `0` |
| `dronePitchClass` | Int 0–11 | `9` (La/A) |
| `droneFifth` | Boolean | `false` |
| `traceGames` | Boolean | `false` |
| `onboardingCompleted` | Boolean | `false` |
| `expertMode` | Boolean | `false` |

For the onboarding wizard, the user-facing/pedagogy-relevant ones are: `noteNameStyle`, `mixEnharmonics`, `a4`, `difficulty`, `playerLevel`, `roundLength`, `positions`, `chordFingering`, `ignoreWrongOctave`, `soundFeedback`, `gameVolume`, `driftWarning`, `expertMode`. The block from `micSensitivity` down to `fullCalibrationAt` is calibration-wizard-owned (per phone/room). Public setters exist for most (:284-365); the calibration cluster is written atomically by `setFullCalibration` (:245-282).

---

## 5. Theme / design system

- **Colors + theme:** `ui/theme/Theme.kt`. **Dark-only by design** (:9 comment "v1 is dark-only"). `darkColorScheme` hardcoded (:10-27); green primary `0xFF7BD88F`. `IntonationTrainerTheme` (:36-42) applies Material3 `MaterialTheme` with no dynamic color, no light scheme. Semantic `ResultColors` object (:29-34): `excellent`/`close`/`off` for game feedback.
- **Spacing:** `ui/theme/Spacing.kt` — single source of truth (SCREEN_EDGE_*, CARD_PADDING, SECTION_BREAK, ITEM_SPACING, FINE_SPACING, ITEM_HORIZONTAL, COMPONENT_SPACING, PROGRESS_DOT_*). Well-adopted across screens.
- **Type sizes:** `ui/theme/TextSizes.kt` — custom large `.sp` for game contexts (PROMPT_NOTE 112sp, COUNTDOWN_NUMBER 140sp, SCORE_DISPLAY 88sp, etc.). Typography otherwise via `MaterialTheme.typography`.
- **Shared component library:** `ui/common/` —
  - `StarRating.kt` — 3-star row (`StarRating`).
  - `ProgressDots.kt` — `ProgressDotsCommon` + `DotInfo` (per-attempt dots).
  - `AchievementUnlocks.kt` — "X unlocked!" lines for summaries.
  - `TraceFeedbackPrompt.kt` — post-round trace feedback (only when traced).
  - `ImprovementLine.kt` — this-round vs last-week cents line (raw "cents" text, `ImprovementLine.kt:29-33` — **not** expert-gated).
  - `RequireMicPermission.kt` — mic-permission gate + keep-screen-on wrapper.
  - `RememberAppSettings.kt` — `rememberAppSettings()` for VM-less screens.
  - `GameConstants.kt` — `COUNT_IN_SECS = 5` (shared count-in duration).
- **Material3:** Yes, throughout (Scaffold, Card, Switch, SegmentedButton, FilterChip, AlertDialog, etc.).
- **Styling inconsistencies worth noting:**
  - Two parallel color systems: the Compose `Theme.kt` scheme vs the Android XML `values/themes.xml` (`Theme.Material.NoActionBar`) + `colors.xml` (`window_background #121318`) used for window/status bars. The `#121318` background is duplicated as a literal in three places (Theme.kt :19-21, colors.xml).
  - App name mismatch: manifest label "Intonation Trainer" vs onboarding title "Welcome to Bass Pitch" (`OnboardingScreen.kt:81`).
  - `ImprovementLine` and the game summaries surface raw "cents"/percentages regardless of `expertMode` — inconsistent with the Progress screen's plain-language gating.
  - Some literal `dp`/`sp` remain (e.g. icon `.size(80.dp)` in onboarding, `32.dp` star default) rather than Spacing/TextSizes constants.

---

## 6. String resources / locale infrastructure

- `app/src/main/res/values/strings.xml` **exists** but contains **only one entry**: `<string name="app_name">Intonation Trainer</string>` (:3).
- **No locale infrastructure.** Only `res/values/` exists — no `values-nl`, `values-fr`, or any other locale directory.
- **App name resource:** `@string/app_name` = "Intonation Trainer", used in `AndroidManifest.xml` (`android:label="@string/app_name"`).
- **Essentially all UI text is hardcoded English string literals in Kotlin** — not externalised to resources. Localization needs extraction first (see the string audit doc).
- **AndroidManifest**: no `localeConfig`, no `android:locale`, no per-app-language setup; no `resConfigs` in gradle. Only `RECORD_AUDIO` permission + microphone feature, single `MainActivity` (launcher), and a `FileProvider`. `android:supportsRtl="true"` is set. Per-app language / `localeConfig` must be added from scratch.

---

## 7. Game flow shared UI — duplication map

The four scored games — Note Accuracy (`ui/noteaccuracy`), Sustain (`ui/sustain`), Shift (`ui/shift`), Chords (`ui/chords`) — **share a set of common leaf components but duplicate their overall scaffolding, count-in, and results structure.**

**Shared (good) — pulled from `ui/common/`:**
- `ProgressDotsCommon` — per-attempt dots (all four: `NoteAccuracyScreen.kt:72`, `SustainScreen.kt:68`, `ShiftScreen.kt:71`, `ChordsScreen.kt:75`).
- `StarRating` — per-attempt/result stars (all four).
- `AchievementUnlocks`, `TraceFeedbackPrompt` — on every summary.
- `ImprovementLine` — on the three cents-based games (not Sustain).
- `COUNT_IN_SECS` constant + `TextSizes` (PROMPT_NOTE, SCORE_DISPLAY, COUNTDOWN_NUMBER) shared.

**Duplicated (consolidation targets — plan §5G):**
- **Count-in:** every game defines its own **private** `CountIn(secsLeft)` composable — `NoteAccuracyScreen.kt:162`, `SustainScreen.kt:232`, `ShiftScreen.kt:331`, `ChordsScreen.kt:316`. Near-identical (big COUNTDOWN_NUMBER text). Only the shared constant is factored out, not the UI.
- **Results/"Done" summary screen:** each game has its own `DoneContent(...)` — `NoteAccuracySummary`/reveal block (`NoteAccuracyScreen.kt:274`), `SustainScreen.kt:352`, `ShiftScreen.kt:353`, `ChordsScreen.kt:338`. All repeat the same skeleton: SCORE_DISPLAY final score → chart/breakdown → `ImprovementLine` → `AchievementUnlocks` → `TraceFeedbackPrompt` → **"Let's go again"** button (literal string duplicated at `NoteAccuracyScreen.kt:363`, `SustainScreen.kt:399`, `ShiftScreen.kt:406`, `ChordsScreen.kt:392`).
- **Per-attempt reveal:** each has its own `RevealResult`/`RevealContent` (`NoteAccuracyScreen.kt:223`, `SustainScreen.kt:255`, `ShiftScreen.kt:231`, `ChordsScreen.kt:255`) — same pattern (colored score + StarRating) with game-specific detail.
- **Screen scaffold:** all four repeat the same `Scaffold { padding -> Column(...) { ProgressDotsCommon(...) ; when(phase) {...} } }` shell (`*Screen.kt:63-136` region in each), plus a `when` over a per-game sealed `*Phase` (each defines its own `CountIn`/`Playing`/`Reveal`/`Done` phase variants).

**Recommendation (adopted as plan §5G):** extract a shared game-session scaffold (count-in, progress dots, phase dispatch) and a shared round-summary scaffold (score headline + slots for game-specific breakdown + the common improvement/achievements/trace/"Let's go again" footer). This removes the 4× duplicated `CountIn`/`DoneContent` and is also where the expert-mode/"technical details" coaching-language gating should live once, not per screen.
