# UI Review Checklist — Quick Implementation Guide

**Print this out and check off as you fix.**

---

## 🔴 PHASE 1: CRITICAL — DONE ✅

### Back Button Fixes
 - [x] **TuneUpScreen** — Add TopAppBar with back icon
 - [x] **CalibrateScreen** — Make back button always visible
 - [x] **WizardScreen** — Make back button always visible

### Accessibility: contentDescription
Systematic audit pass—check each file for Icon() without contentDescription:

 - [x] **DebugPitchScreen**
 - [x] **DroneScreen**
 - [x] **ChordsScreen**
 - [x] **TuneUpScreen**
 - [x] **WizardScreen**

✅ **After Phase 1:**
- All screens have back buttons (consistent navigation)
- All icons have descriptions (accessibility compliance)

---

## ⚠️ PHASE 2A: HIGH PRIORITY — SPACING — DONE ✅

### Create Spacing.kt
 - [x] Create file: `ui/theme/Spacing.kt`

### Apply Spacing to All 15 Screens
- [x] All 15 screens updated to use `Spacing` constants for edges, card padding, and vertical rhythm.

---

## ⚠️ PHASE 2B: HIGH PRIORITY — COMPONENTS — DONE ✅

### Create TextSizes.kt
- [x] Create file: `ui/theme/TextSizes.kt`

### Fix ProgressDots Size + Add Icons
- [x] **Common ProgressDots** (increased to 24.dp for distance readability)
- [x] Added semantic icons (✓, ~, ✗) to all 4 game screens.
  
### Fix Button Sizing
- [x] **RecordingsScreen** — Converted big buttons to compact IconButtons to save vertical space.

### Fix Component Padding
- [x] **AchievementsScreen** — Card height standardized.
- [x] **TuneUpScreen** — Card padding standardized.

### Scroll Additions
- [x] **TuneUpScreen** — main Column scrolls.
- [x] **CalibrateScreen** — main Column scrolls.
- [x] **WizardScreen** — main Column scrolls.

---

## ⚠️ PHASE 2C: HIGH PRIORITY — UX STATES — DONE ✅

### Add Empty States
- [x] **ProgressScreen** — Icon + Card for empty session list.
- [x] **AchievementsScreen** — Locked state styling + 🔒 icon.
- [x] **CalibrateScreen** — Result verdict wrapped in color-coded Card.
- [x] **WizardScreen** — Failed state in prominent Card.

### Add Color + Icon to State Conveyance
- [x] **DroneScreen** — Playing/stopped state now has icon (VolumeUp/Off) + label.

### Improve Loading States
- [x] **CalibrateScreen** — Measuring states show icon + prominent label + large progress bar.
- [x] **WizardScreen** — Analyzing state shows Hourglass icon + label.

---

## 📱 PHASE 3: MEDIUM PRIORITY — POLISH

### Typography Standardization
- [x] All screens updated: manual `fontSize = X.sp` replaced with `TextSizes` or `MaterialTheme.typography`.

### Text Truncation Fixes
- [x] **RecordingsScreen** — Ellipsis on file names.
- [x] **ChordsScreen** — Ellipsis on pitch names in strip.
- [x] **WizardScreen** — Ellipsis/maxLines on warning text.

### Text Color Consistency
- [ ] Audit pass for `onSurfaceVariant` on labels/hints.

### Readability Improvements (SARAH'S FEEDBACK)
- [x] **Increased visibility for distance:** 
  - [x] Game position labels moved to `headlineSmall` / `displaySmall`.
  - [x] Drift banners high-contrast (0.25 alpha).
  - [x] Phase status in calibration views (`displaySmall`).
  - [x] Volume display in Drone mode.

### Typography Header Consistency
- [x] **CalibrateScreen** — StepLabel standardized to `headlineMedium`.

✅ **Final Goal:**
- Production-ready consistency with accessible touch targets and excellent distance readability.
