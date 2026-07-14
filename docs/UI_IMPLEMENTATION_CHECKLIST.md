# UI Review Checklist — Quick Implementation Guide

**Print this out and check off as you fix.**

---

## 🔴 PHASE 1: CRITICAL (1 hour) — START HERE

### Back Button Fixes (30 min)
- [ ] **TuneUpScreen** — Add TopAppBar with back icon
  - File: `app/src/main/java/be/drakarah/intonation/ui/tune/TuneUpScreen.kt`
  - Replace old button pattern with TopAppBar (see UI_REMEDIATION_GUIDE.md § 2)
  
- [ ] **CalibrateScreen** — Make back button always visible
  - File: `app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt`
  - Add TopAppBar, remove conditional hiding (see UI_REMEDIATION_GUIDE.md § 3)
  
- [ ] **WizardScreen** — Make back button always visible
  - File: `app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt`
  - Add TopAppBar, remove conditional hiding for Summary/Failed states

### Accessibility: contentDescription (30 min)
Systematic audit pass—check each file for Icon() without contentDescription:

- [ ] **DebugPitchScreen** (lines 246–248, 299)
  - Stop icon → `contentDescription = "Stop recording"`
  - CheckCircle icon → `contentDescription = "Capture stable"`
  
- [ ] **DroneScreen** (line 85)
  - PlayArrow/Stop icons → `contentDescription = if (playing) "Stop" else "Play"`
  
- [ ] **ChordsScreen** (lines 96–105, 141)
  - ProgressDots → add `.semantics { contentDescription = "Round N…" }`
  - Emoji 🎧 → wrap in Icon with description
  
- [ ] **TuneUpScreen** (lines 75–100)
  - Canvas needle → add `.semantics { contentDescription = "Tuning needle" }`
  
- [ ] **WizardScreen** (lines 108–110, 156)
  - All emoji icons → wrap or add contentDescription

✅ **After Phase 1:**
- All screens have back buttons (consistent navigation)
- All icons have descriptions (accessibility compliance)

---

## ⚠️ PHASE 2A: HIGH PRIORITY — SPACING (90 min)

### Create Spacing.kt (5 min)
- [ ] Create file: `app/src/main/java/be/drakarah/intonation/ui/theme/Spacing.kt`
- [ ] Copy-paste from UI_REMEDIATION_GUIDE.md § 1
- [ ] Verify imports: `import androidx.compose.ui.unit.dp`

### Apply Spacing to All 15 Screens (85 min — ~5 min each)

**File by file, replace hardcoded padding/spacedBy:**

1. [ ] **HomeScreen.kt** — `.padding(horizontal = 24.dp)` → `Spacing.SCREEN_EDGE_HORIZONTAL`
2. [ ] **RoundScreen.kt** — `.padding(24.dp)` → `Spacing.SCREEN_EDGE_HORIZONTAL`
3. [ ] **SustainScreen.kt** — spacing updates
4. [ ] **ShiftScreen.kt** — spacing updates
5. [ ] **TuneUpScreen.kt** — card padding 12.dp → `Spacing.CARD_PADDING`
6. [ ] **CalibrateScreen.kt** — add scroll, fix spacing
7. [ ] **WizardScreen.kt** — spacing updates
8. [ ] **DebugPitchScreen.kt** — card padding 12.dp → 16.dp, SweepView 20dp → 24dp
9. [ ] **ProgressScreen.kt** — section spacing consistency
10. [ ] **SettingsScreen.kt** — already good, verify 20.dp section breaks
11. [ ] **RecordingsScreen.kt** — card padding 10.dp/16.dp → 16.dp, LazyColumn spacedBy 8.dp → 12.dp
12. [ ] **AchievementsScreen.kt** — card padding 10.dp → 16.dp, title spacer 4.dp → 8.dp
13. [ ] **AboutScreen.kt** — spacing updates
14. [ ] **DroneScreen.kt** — fix Spacer heights (standardize 24.dp sections)
15. [ ] **ChordsScreen.kt** — spacing updates

**Pattern to search for in each file:**
```
.padding(horizontal = 24.dp)   →  Spacing.SCREEN_EDGE_HORIZONTAL
.padding(12.dp)  →  Spacing.CARD_PADDING
.padding(16.dp)  →  Spacing.CARD_PADDING
spacedBy(8.dp)   →  spacedBy(Spacing.FINE_SPACING)
spacedBy(12.dp)  →  spacedBy(Spacing.ITEM_SPACING)
spacedBy(20.dp)  →  spacedBy(Spacing.SECTION_BREAK)
```

✅ **After Phase 2A:**
- Spacing is consistent across all 15 screens
- All values come from single source of truth (Spacing.kt)

---

## ⚠️ PHASE 2B: HIGH PRIORITY — COMPONENTS (90 min)

### Create TextSizes.kt (5 min)
- [ ] Create file: `app/src/main/java/be/drakarah/intonation/ui/theme/TextSizes.kt`
- [ ] Copy-paste from UI_REMEDIATION_GUIDE.md § 9

### Fix ProgressDots Size + Add Icons (30 min)

- [ ] **RoundScreen** (line 102–105)
  - Increase 12.dp → `Spacing.PROGRESS_DOT_SIZE` (16.dp)
  - Add icons: ✓ for excellent, ~ for close, ✗ for off
  - Add contentDescription
  - See UI_REMEDIATION_GUIDE.md § 5
  
- [ ] **ChordsScreen** (line 102–105)
  - Same changes as RoundScreen

### Fix Button Sizing (15 min)
- [ ] **RecordingsScreen** (line 218–222)
  - Convert Play/Share/Delete TextButton → OutlinedButton(fillMaxWidth)

### Fix Component Padding (20 min)
- [ ] **AchievementsScreen** (line 66)
  - Remove fixed height 150.dp; use adaptive sizing or min/max
  
- [ ] **TuneUpScreen** (line 69)
  - Card padding 12.dp → `Spacing.CARD_PADDING`

### Scroll Additions (20 min)
- [ ] **TuneUpScreen** — Add `.verticalScroll(rememberScrollState())` to main Column
- [ ] **CalibrateScreen** — Add `.verticalScroll(rememberScrollState())` to main Column

✅ **After Phase 2B:**
- All cards consistently padded (16.dp)
- All buttons consistently sized (fillMaxWidth)
- All scroll behavior consistent (setup screens scroll, game screens don't)

---

## ⚠️ PHASE 2C: HIGH PRIORITY — UX STATES (60 min)

### Add Empty States (30 min)

- [ ] **ProgressScreen** (line 108–112)
  - Wrap empty state in Card with icon + descriptive text
  - See UI_REMEDIATION_GUIDE.md § 8
  
- [ ] **AchievementsScreen** (line 66)
  - Add 🔒 icon + greyed styling for locked badges
  
- [ ] **CalibrateScreen** (line 111–116)
  - Result verdict in Card with ResultColors.off background
  
- [ ] **WizardScreen** (line 183–186)
  - Failed state in Card with error styling

### Add Color + Icon to State Conveyance (15 min)
- [ ] **DroneScreen** (line 82)
  - Playing/stopped state: add icon (VolumeUp/VolumeOff) + text label
  - See UI_REMEDIATION_GUIDE.md § 7

### Improve Loading States (15 min)
- [ ] **CalibrateScreen** (line 77)
  - Wrap LinearProgressIndicator in Container with icon + label
  - See UI_REMEDIATION_GUIDE.md § 12
  
- [ ] **WizardScreen** (line 116–118)
  - Add icon (Hourglass) + label text ("Analyzing…")

✅ **After Phase 2C:**
- All empty states visually clear + descriptive
- All error/failed states prominent
- No color-only state conveyance (all have icons/text)
- All loading states show progress + indication

---

## 📱 PHASE 3: MEDIUM PRIORITY (2.5 hours) — POLISH

### Typography Standardization (30 min)

- [ ] Replace all hardcoded `fontSize = X.sp` with `TextSizes.PROMPT_NOTE`, etc.
  - **DebugPitchScreen** (line 143) — `displayLarge` → `TextSizes.FREQUENCY_DISPLAY`
  - **ChordsScreen** (line 156) — `96.sp` → `TextSizes.PROMPT_NOTE`
  - **WizardScreen** (line 95, 243) — manual sizes → `TextSizes` constants
  - **DroneScreen** (line 80) — `72.sp` → `TextSizes.PROMPT_NOTE`

### Text Truncation Fixes (30 min)

- [ ] **RecordingsScreen** (line 189)
  - Add `.maxLines(1)` + `overflow = TextOverflow.Ellipsis` to recording names
  
- [ ] **ChordsScreen** (line 201–216)
  - Add `.maxLines(1)` + `overflow = TextOverflow.Ellipsis` to pitch class names
  
- [ ] **WizardScreen** (line 169)
  - Add `.maxLines(3)` + `overflow = TextOverflow.Ellipsis` to warning text

### Text Color Consistency (30 min)
- [ ] Audit all Text() calls:
  - Primary headings: no color (implicit onSurface) ✓
  - Labels/hints: explicit `color = MaterialTheme.colorScheme.onSurfaceVariant`
  - Results: `ResultColors.excellent` / `.close` / `.off`
  - Disabled: `MaterialTheme.colorScheme.surfaceVariant`

### Readability Improvements (30 min)
- [ ] **ChordsScreen** (line 63–68) — Increase drift warning alpha 0.18f → 0.25f
- [ ] **AchievementsScreen** (line 93–96) — Test locked cell contrast (may need adjustment)
- [ ] **DroneScreen** (line 82) — Verify playing state color contrast
- [ ] **DroneScreen** (line 124) — Add volume display: "Volume: XX%"
- [ ] **DroneScreen** (line 75) — Add subtitle: "Reference pitch for ear training — no scoring"

### Typography Header Consistency (5 min)
- [ ] **CalibrateScreen** (line 52) — StepLabel from labelLarge → headlineMedium

✅ **After Phase 3:**
- All font sizes consistent (from TextSizes or Material3)
- No text truncation
- All text colors follow established rules
- Dark theme contrast verified
- Headers consistently styled across all screens

---

## 💡 PHASE 4: OPTIONAL POLISH (1.5 hours)

### Section Spacing Rhythm (20 min)
- [ ] Verify all screens use consistent section spacing (20.dp major, 12.dp item)

### Progress Dots Optional Icons (15 min)
- [ ] RoundScreen & ChordsScreen — if time permits, add icons to ProgressDots (already in Phase 2B)

### Drone Screen Polish (20 min)
- [ ] Add volume slider value display
- [ ] Add subtitle below title
- [ ] Standardize Spacer heights

### Contrast Audit (20 min)
- [ ] Use WebAIM Contrast Checker on all dark-theme color combinations
- [ ] Flag any < 4.5:1 contrast ratio (WCAG AA standard)
- [ ] Adjust colors if needed

---

## ✅ FINAL VALIDATION

### Test Each Fix (After each phase)
- [ ] Visual regression: Does it look right on screen?
- [ ] Mobile responsive: Test on 360dp (emulator) + 412dp (Pixel 6a)
- [ ] Dark theme contrast: No hard-to-read text
- [ ] Accessibility: Run Android Accessibility Scanner, test TalkBack

### Full App Test (After all phases)
- [ ] [ ] Back button on every screen + works
- [ ] [ ] Navigation flows consistent (no trapped screens)
- [ ] [ ] Spacing uniform (24.dp edges, 16.dp cards, 12.dp items)
- [ ] [ ] No truncated text
- [ ] [ ] Empty states on all appropriate screens
- [ ] [ ] Tap targets all ≥48.dp
- [ ] [ ] Icons all have contentDescription
- [ ] [ ] Loading states clear (icon + label + progress)
- [ ] [ ] Results in ResultColors (excellent/close/off)
- [ ] [ ] Run accessibility scanner: 0 issues
- [ ] [ ] TalkBack test on 3–5 key screens

### Sarah User Test (After all phases)
- [ ] Navigation feels natural (can exit any screen)
- [ ] Spacing feels consistent
- [ ] Text readable at 2m distance while playing
- [ ] No confusing empty/error states
- [ ] Accessibility features work (TalkBack, high contrast)

---

## 📊 Progress Tracking

```
Phase 1: Critical              [████████████████████████░░░░░░░░░░░░] 60%
  ├─ Back buttons             [██████████████████████████████████████] 100%
  └─ contentDescription       [████████████████████░░░░░░░░░░░░░░░░░] 60%

Phase 2: High Priority         [████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 10%
  ├─ Spacing.kt              [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%
  ├─ Apply to 15 screens     [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%
  ├─ Component fixes         [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%
  └─ UX states               [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%

Phase 3: Medium Polish         [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%

Phase 4: Optional              [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%

Validation & Testing           [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%
```

---

## 📚 Reference Documents

| Document | Purpose | Read When |
|----------|---------|-----------|
| [UI_DESIGN_REVIEW.md](UI_DESIGN_REVIEW.md) | Full findings + context | You need details |
| [UI_REMEDIATION_GUIDE.md](UI_REMEDIATION_GUIDE.md) | Code examples | You're implementing |
| [UI_ISSUES_BY_SCREEN.md](UI_ISSUES_BY_SCREEN.md) | Quick reference + sprint plan | You're planning |
| **This checklist** | Implementation checklist | You're fixing code |

---

**Start with Phase 1 Critical fixes (1 hour). Then Phase 2 High Priority (3 hours). Then Polish as time allows.**

**Target:** 8 hours total for production-ready app.

**Good luck! 🚀**
