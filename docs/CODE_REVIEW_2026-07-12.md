# Code Review Report — 2026-07-12

Repository: `drake7707/double-bass-intonation-trainer`  
Reviewer: Copilot Task Agent  
Scope: full codebase review for maintainability, design quality, separation of concerns, complexity, naming, and error handling.

---

## Executive summary

The codebase is already strong for a POC: domain logic is mostly pure and test-heavy, DSP/game boundaries are clear, and many hard detection problems are well-documented.

To reach stable production quality, the highest-value improvements are:

1. **Reduce duplicated ViewModel orchestration** (engine startup, settings hydration, round persistence patterns repeated across multiple game ViewModels).
2. **Tighten data-layer transactional safety** (`recordCompletedRound` performs a multi-step write sequence without an explicit transaction boundary).
3. **Move remaining screen-level business behavior into ViewModels/use-cases** (notably `SettingsScreen` and pre-game gate logic in `HomeScreen`).
4. **Replace stringly-typed game identifiers with strong types** (exercise/mode/style).
5. **Extract and reuse repeated algorithms/rules** (repeat-separation logic, harmonic-artifact filtering logic variants).

---

## What is working well

- **Excellent testability at the game-core level**: state machines consume `PitchSample` streams and are covered by focused unit tests.
- **Good module boundaries**: `:dsp` and `:app` responsibilities are clearly separated.
- **Strong domain comments** around tricky detection/capture behavior (especially in capture/gating classes).
- **Good use of immutable UI state objects** (`RoundUiState`, `ShiftUiState`, etc.) and sealed-state patterns.
- **Documentation discipline** (`docs/DETECTION.md`, `FEATURES.md`, `TESTING.md`) is above average for a POC.

---

## Findings (prioritized)

## High priority

### 1) Multi-step persistence should be transactional
- **File:** `/home/runner/work/double-bass-intonation-trainer/double-bass-intonation-trainer/app/src/main/java/be/drakarah/intonation/data/SessionRepository.kt`
- **Details:** `recordCompletedRound(...)` writes session, attempts, personal best, achievements, and reads analytics in one flow but without an explicit Room transaction boundary.
- **Risk:** partial writes if a later DB step fails.
- **Recommendation:** wrap write portion in `RoomDatabase.withTransaction { ... }` (or DAO `@Transaction` orchestrator method), keep analytics reads either inside consistently or as explicit post-write reads.

### 2) Repeated ViewModel startup/pipeline code should be consolidated
- **Files:**  
  - `/app/src/main/java/be/drakarah/intonation/ui/round/RoundViewModel.kt`  
  - `/app/src/main/java/be/drakarah/intonation/ui/sustain/SustainViewModel.kt`  
  - `/app/src/main/java/be/drakarah/intonation/ui/shift/ShiftViewModel.kt`  
  - `/app/src/main/java/be/drakarah/intonation/ui/chords/ChordsViewModel.kt`
- **Details:** repeated pattern: settings first(), apply config, create `PitchEngine`, optional `GameTrace`, collect samples, replay/restart/stop lifecycle, persist round.
- **Risk:** bug fixes and behavior changes drift between modes over time.
- **Recommendation:** introduce a shared abstraction (e.g., game session coordinator / base class / helper composition) for common orchestration and lifecycle.

### 3) Business logic still leaks into Composables
- **Files:**  
  - `/app/src/main/java/be/drakarah/intonation/ui/settings/SettingsScreen.kt`  
  - `/app/src/main/java/be/drakarah/intonation/ui/home/HomeScreen.kt`
- **Details:** screens directly call repository mutation methods and contain gate-flow decisions.
- **Risk:** hard-to-test interaction logic and duplicated behavior if reused.
- **Recommendation:** route mutations and gate state through ViewModels; keep Composables render-focused.

### 4) Stringly-typed domain routing
- **Files:** multiple (`mode`, `exerciseType`, `style` as string literals/constants)
- **Details:** `"arco"`, `"pizz"`, `"same"`, `"cross"`, `"NOTE_ACCURACY"` etc.
- **Risk:** brittle refactors, runtime-only error detection.
- **Recommendation:** move to enums/sealed value types and typed nav args adapters.

---

## Medium priority

### 5) Duplicate list repeat-separation algorithm
- **Files:**  
  - `/app/src/main/java/be/drakarah/intonation/game/NotePool.kt`  
  - `/app/src/main/java/be/drakarah/intonation/game/ChordPool.kt`
- **Details:** near-identical `separateConsecutiveRepeats`/`wouldNotRepeatAt` logic.
- **Risk:** maintenance divergence.
- **Recommendation:** extract generic utility with key-selector lambda.

### 6) Complex filtering logic should be modularized
- **Files:**  
  - `/app/src/main/java/be/drakarah/intonation/ui/round/RoundViewModel.kt` (`onCaptured`)  
  - `/app/src/main/java/be/drakarah/intonation/game/ArpeggioCapture.kt` (`onFrozen`)
- **Details:** many rule branches (ring-over, too-soon, harmonic, unplayable, flimsy) inline.
- **Risk:** high cognitive load and subtle regression risk when tuning thresholds.
- **Recommendation:** split into named rule evaluators returning structured reasons; unit test each rule.

### 7) Lifecycle ownership of non-UI resources is implicit
- **Files:** game ViewModels + `PitchEngine`
- **Details:** jobs are canceled correctly, but resource ownership/cleanup contract relies on coroutine cancellation semantics.
- **Risk:** future modifications can accidentally weaken cleanup guarantees.
- **Recommendation:** centralize lifecycle with explicit close/cleanup contract for engine-related resources and trace writer handling.

### 8) Settings parsing silently falls back on invalid persisted enum strings
- **File:** `/app/src/main/java/be/drakarah/intonation/settings/SettingsRepository.kt`
- **Details:** `runCatching { Enum.valueOf(...) }.getOrNull()` fallback-to-default has no diagnostic path.
- **Risk:** silent data corruption symptoms become hard to diagnose.
- **Recommendation:** log parse failures (debug/release-appropriate channel) and count/report recoveries.

---

## Low priority

### 9) Local mutable state volume in some ViewModels
- **Files:** `RoundViewModel`, `ChordsViewModel`, `ShiftViewModel`, `SustainViewModel`
- **Details:** many mutable vars for runtime session state.
- **Risk:** harder reasoning about invariants.
- **Recommendation:** group related mutable fields into internal state holders and enforce reset/init helpers.

### 10) Magic number spread in thresholds/timeouts
- **Files:** capture/game ViewModels and game classes.
- **Details:** many constants are documented, but some remain local numeric values in methods.
- **Risk:** tuning overhead and inconsistency.
- **Recommendation:** normalize all tuning values behind named constants/config structures where practical.

---

## Architecture & maintainability scorecard

- **Separation of concerns:** 7.5/10  
- **Testability:** 8.5/10  
- **Naming/clarity:** 7.5/10  
- **Complexity management:** 7/10  
- **Error handling/resilience:** 6.5/10  
- **Overall production-readiness (code quality):** **7.4/10**

---

## Recommended stabilization roadmap

1. **Data integrity first:** transactionalize `recordCompletedRound`.
2. **Orchestration dedup:** shared game-session startup/lifecycle helper for all game ViewModels.
3. **UI purity pass:** move remaining mutation/gate logic from Composables into ViewModels.
4. **Type-safety pass:** replace string identifiers with typed enums/sealed values.
5. **Rule extraction pass:** modularize discard/filter rules and shared sequence algorithms.
6. **Operational hardening:** add diagnostics for settings parse recovery and lifecycle cleanup assertions.

---

## Final note

This codebase already has strong fundamentals for a solo-built musical app (especially deterministic game logic and corpus-driven detection work). The main gap to production is not correctness of core behavior, but **consolidation and hardening of orchestration patterns** so future features can be added without introducing drift.
