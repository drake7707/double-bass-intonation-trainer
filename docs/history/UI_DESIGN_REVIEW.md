# UI Design & Consistency Review — Intonation Trainer

**Date:** 2026-07-14  
**Scope:** Comprehensive design language audit across all 15 UI screens  
**Goal:** Production-ready PlayStore app consistency  
**Status:** ⚠️ Multiple issues requiring remediation before v1 release  

---

## Executive Summary

The app demonstrates **strong foundational design discipline** (unified Material3 theme, excellent result-color semantics, good typography hierarchy). However, it exhibits **scattered inconsistencies accumulated across iterative development** that undermine its polish and professional appearance. Issues range from **critical (navigation pattern breakage)** to **medium (spacing rhythm, padding variance)** to **low (visual polish details)**.

### Key Findings

| Category | Status | Issue Count | Severity |
|----------|--------|-------------|----------|
| **Padding & Spacing** | ⚠️ Needs standardization | 7 issues | High |
| **Back/Navigation Patterns** | 🔴 **Pattern-breaking** | 3 issues | **Critical** |
| **Typography** | ⚠️ Mixed semantic + manual | 5 issues | Medium |
| **Accessibility** | 🔴 **Compliance risk** | 12+ issues | **High** |
| **Card & Component Styling** | ⚠️ Inconsistent dimensions | 4 issues | High |
| **Scroll Behavior** | ⚠️ Selective application | 3 issues | Medium |
| **Empty/Error States** | ⚠️ Incomplete | 4 issues | Medium |
| **Visual Contrast & Readability** | ⚠️ Some dark-theme issues | 5 issues | Medium |

---

## Detailed Findings

### 🔴 CRITICAL ISSUES (Must Fix Before Release)

#### 1. **Navigation Pattern Breaking: Missing Back Button on TuneUpScreen**

**File:** [TuneUpScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/tune/TuneUpScreen.kt#L85-L90)

**Issue:** TuneUp is the ONLY screen without a back button. Users have only "Done" and "Skip" options.

**Impact:**
- Users cannot exit the screen without action (violates Material3 back-button convention)
- Inconsistent with every other screen (ProgressScreen, DebugScreen, RecordingsScreen, AchievementsScreen, DroneScreen all have back)
- Creates confusion: "What if I want to cancel mid-tune?"

**Fix:** Add a standard back button (OutlinedButton "Back" or TopAppBar with back icon) above or below the content.

---

#### 2. **Conditional Back Button Hiding: CalibrateScreen & WizardScreen**

**Files:**
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt#L76-L84) – Cancel button hidden when saved
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L113) – Back button hidden for Summary/Failed states

**Issue:** Users cannot see an obvious way to exit certain states. Back button presence should be consistent.

**Impact:**
- Summary screen (after calibration completes) has no visible back button
- Failed state offers no clear escape path
- Creates "trapped" UX (users don't know how to proceed)

**Fix:** Always show back navigation. Use `TopAppBar` with navigation icon, or keep OutlinedButton visible at all times. Only disable if truly unsupported, not hidden.

---

#### 3. **Accessibility Compliance: Missing ContentDescription on Icons (12+ instances)**

**Files with missing contentDescription:**
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt#L299) – Stop icon
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt#L246-L248) – CheckCircle icon
- [DroneScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/drone/DroneScreen.kt#L85) – PlayArrow/Stop icons
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L96-L105) – ProgressDots (12 individual dots)
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L141) – Emoji "🎧" in text
- [TuneUpScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/tune/TuneUpScreen.kt#L75-L100) – Canvas needle (visual-only)
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L108-L110) – Emoji icons
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L156) – State display icons

**Issue:** Screen readers cannot describe interactive/informational icons. PlayStore review will flag this.

**Impact:**
- App fails accessibility audits (PlayStore may reject)
- Visually impaired users cannot understand UI
- Legal compliance risk (accessibility regulations in many regions)

**Fix:** Add `contentDescription` param to all `Icon()` calls. For decorative icons, use `contentDescription = null`.

---

### ⚠️ HIGH PRIORITY (Before v1 Release)

#### 4. **Padding Inconsistency: 6 Different Padding Values Across Screens**

**Affected Files:**

| File | Location | Issue | Standard |
|------|----------|-------|----------|
| DebugPitchScreen | Line 216 | Card padding 12.dp | Should be 16.dp |
| DebugPitchScreen | Line 390 | SweepView 20.dp horizontal | Should be 24.dp |
| TuneUpScreen | Line 69 | Card vertical 12.dp | Should be 16.dp |
| RecordingsScreen | Line 186 | Card 16.dp horiz, 10.dp vert | Should be 16.dp both |
| AchievementsScreen | Line 76 | AchievementCell 10.dp | Should be 16.dp |
| RecordingsScreen | Line 173 | LazyColumn spacedBy 8.dp | Should be 12.dp |

**Issue:** Card padding ranges from 10–20dp; vertical spacers from 4–32dp. No rhythm or consistency.

**Impact:**
- Visual density unpredictable
- Some screens feel cramped, others loose
- Professional appearance undermined by ad-hoc spacing
- Maintenance nightmare (every screen has different rules)

**Fix:** Establish spacing scale:
- **Card padding:** Always 16.dp
- **Screen edges:** Always 24.dp horizontal, 24.dp top, 16.dp bottom
- **Vertical between sections:** 20.dp (generous, for major breaks)
- **Vertical between items:** 12.dp (standard item spacing)
- **Vertical for fine spacing:** 8.dp (related elements)
- **Between components in a row:** 8.dp (horizontal)

Create `object DimensionDefaults` or `object Spacing` as a single source of truth:
```kotlin
object Spacing {
    val CARD_PADDING = 16.dp
    val SCREEN_EDGE_H = 24.dp
    val SCREEN_EDGE_V_TOP = 24.dp
    val SCREEN_EDGE_V_BOTTOM = 16.dp
    val SECTION_BREAK = 20.dp
    val ITEM_SPACING = 12.dp
    val FINE_SPACING = 8.dp
}
```

Then audit all 15 screens and apply consistently.

---

#### 5. **Component Padding & Sizing Inconsistencies**

**AchievementCell Fixed Height Issue:**
- [AchievementsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/achievements/AchievementsScreen.kt#L66) – Card fixed 150.dp height
- Issue: Card doesn't scale with content; truncates descriptions or wastes space

**ProgressDots Too Small (Accessibility):**
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L102-L105) – 12.dp circles
- Issue: Below Material3 48.dp minimum tap target. Violates accessibility standards.
- Fix: Increase to 16.dp or wrap in `size(Modifier.minimumTouchTargetSize())`

**Button Sizing Issue:**
- [RecordingsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/recordings/RecordingsScreen.kt#L218-L222) – Play/Share/Delete TextButtons without fillMaxWidth
- Issue: Buttons are cramped, inconsistent with other screens (RoundScreen, ProgressScreen all use fillMaxWidth OutlinedButton)
- Fix: Convert to OutlinedButton with fillMaxWidth, or at least match the sizing pattern of the rest of the app

---

#### 6. **Color-Only State Conveyance (Accessibility Risk)**

**ProgressDots & Interactive States:**
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L102-L105) – Green/yellow/red dots with no icons/text
- [DroneScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/drone/DroneScreen.kt#L82) – Playing/stopped shown only via color change
- Issue: Color-blind users cannot perceive state changes

**Fix:** Add icons or text labels:
- Excellent (green) = ✓ icon or "done"
- Close (yellow) = ~ icon or "close"
- Off (red) = ✗ icon or "miss"

---

#### 7. **Incomplete Empty States & Error Messages**

**ProgressScreen:**
- [ProgressScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/progress/ProgressScreen.kt#L108-L112)
- Issue: Empty state is just text ("No rounds yet") with no container or icon
- Fix: Use a Card or Container with subtle background + icon + descriptive text

**AchievementsScreen:**
- [AchievementsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/achievements/AchievementsScreen.kt)
- Issue: No empty state logic; all 25 achievement cells render whether unlocked or not
- Fix: Show a small visual difference (🔒 icon + greyed text) for locked badges

**CalibrateScreen Results:**
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt#L111-L116)
- Issue: OVERLAP verdict lacks visual emphasis; only uses a small colored result indicator
- Fix: Show result in a prominent Box with background color + clear text ("Cannot calibrate: room too noisy")

**WizardScreen Failed State:**
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L183-L186)
- Issue: Failed state text lacks container or visual hierarchy
- Fix: Show in prominent Card with error background color + retry button

---

#### 8. **Scroll Behavior Inconsistency**

**Setup/Config Screens (Should All Scroll):**
- [TuneUpScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/tune/TuneUpScreen.kt) – NO scroll
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt) – NO scroll
- Issue: On small phones (portrait), content may overflow. Compare to SettingsScreen which scrolls.

**Fix:** Add `.verticalScroll(rememberScrollState())` to main Column on all setup screens.

**Game Screens (Should NOT Scroll):**
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt) – HAS scroll in SweepView
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt) – NO scroll
- Issue: Inconsistent scrolling in game context (one has scroll, one doesn't)

**Fix:** Ensure game screens never need to scroll (content should fit or be scrollable only for specific regions, not the whole screen).

---

### 📱 MEDIUM PRIORITY (Good to Fix Before Release)

#### 9. **Typography Mixing Semantic and Manual Font Sizes**

**Files with inconsistencies:**
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt#L143) – Uses `displayLarge` (semantic)
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L156) – Uses `96.sp` (manual)
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L243) – Uses `140.sp` for countdown (compare to RoundScreen which uses same)
- [DroneScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/drone/DroneScreen.kt#L80) – Uses `72.sp` manually

**Issue:** Mix of `MaterialTheme.typography.STYLE` and hardcoded `fontSize = X.sp` makes maintenance hard and breaks cohesion.

**Fix:**
1. Establish standard sizes for prompts, scores, and hints as documented in [Findings: Typography](##typography)
2. Create `object TextSizes` or extend Theme.kt with custom styles if needed:
   ```kotlin
   object TextSizes {
       val PROMPT = 112.sp
       val COUNTDOWN = 140.sp
       val SCORE_DISPLAY = 88.sp
   }
   ```
3. Audit all screens and replace manual `fontSize = X.sp` with semantic styles or defined sizes
4. Document which style to use for each context (prompts, scores, labels, hints)

---

#### 10. **Text Color Inconsistencies**

**Issue:** Some text lacks explicit color specification, defaulting to `onSurface`. Others explicitly set `onSurfaceVariant` for secondary text. Inconsistent.

**Files:**
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt#L161-L165) – Conditional ResultColors but some text lacks color
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt#L52) – StepLabel uses `color = MaterialTheme.colorScheme.primary`

**Fix:** Establish text color rules:
- **Primary text (headings, prompts):** `MaterialTheme.colorScheme.onSurface` (default, omit)
- **Secondary text (labels, hints):** Explicit `color = MaterialTheme.colorScheme.onSurfaceVariant`
- **Semantic success/fail (result scores):** `ResultColors.excellent` / `ResultColors.close` / `ResultColors.off`
- **Disabled/locked states:** `MaterialTheme.colorScheme.surfaceVariant`

Audit and apply consistently.

---

#### 11. **Text Truncation Without maxLines**

**Files:**
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L201-L216) – Pitch class name (no maxLines)
- [RecordingsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/recordings/RecordingsScreen.kt#L189) – Recording baseName (no maxLines)
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L169) – Long warning text

**Issue:** Text can overflow unexpectedly or be cut off on narrow screens.

**Fix:** Add `.maxLines(N)` + `.overflow(TextOverflow.Ellipsis)` to:
- Single-line labels: `.maxLines(1)`
- Multi-line descriptions: `.maxLines(3)` (or as designed)

---

#### 12. **Inconsistent Section Spacing**

**Files:**
- [SettingsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/settings/SettingsScreen.kt) – `Arrangement.spacedBy(20.dp)` (generous)
- [HomeScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/home/HomeScreen.kt) – `Arrangement.spacedBy(12.dp)` (default)
- [RecordingsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/recordings/RecordingsScreen.kt#L173) – `Arrangement.spacedBy(8.dp)` (tight)

**Issue:** Vertical rhythm is inconsistent; no clear hierarchy.

**Fix:** Define spacing scale (see issue #4). Use 20.dp only for major section breaks (between titled groups), 12.dp for default item lists.

---

#### 13. **Loading State Clarity**

**Files:**
- [DebugPitchScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/debug/DebugPitchScreen.kt#L135) – Permission waiting shows only text
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt#L77) – LinearProgressIndicator with default theming (not ResultColors)
- [WizardScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/WizardScreen.kt#L116-L118) – Analyzing state unnamed indicator

**Issue:** Loading states lack visual clarity and progress indication.

**Fix:**
- Add labels ("Analyzing…", "Waiting for permission…")
- Use consistent color (MaterialTheme.colorScheme.primary or a ResultColors variant)
- Add spinner icon or animated progress bar
- Show estimated time if available

---

### 💡 LOWER PRIORITY (Visual Polish)

#### 14. **Inconsistent Header Styling**

**Files:**
- [CalibrateScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/calibrate/CalibrateScreen.kt#L52) – StepLabel uses `labelLarge` + primary color
- Standard pattern: `headlineMedium` with onSurface color

**Fix:** Use consistent header style everywhere. StepLabel should match TuneUpScreen/ProgressScreen/SettingsScreen ("Settings", "Progress", "Tune up").

---

#### 15. **Drone Screen: Missing Instructional Subtitle**

**File:** [DroneScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/drone/DroneScreen.kt#L75)

**Issue:** Title "Drone" lacks descriptive subtitle. Compare to TuneUpScreen ("Tune open strings") or ProgressScreen.

**Fix:** Add subtitle below title: "Reference pitch for ear training — no scoring, just sound."

---

#### 16. **Volume Slider With No Value Display**

**File:** [DroneScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/drone/DroneScreen.kt#L124)

**Issue:** Volume slider has no label or value text. User cannot see the current level.

**Fix:** Add text display above or beside slider: "Volume: 70%"

---

#### 17. **Dark Theme Contrast Issues**

**Files:**
- [AchievementsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/achievements/AchievementsScreen.kt#L93-L96) – Locked cells: surfaceVariant background + onSurfaceVariant text (may be low contrast)
- [ChordsScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/chords/ChordsScreen.kt#L63-L68) – Drift warning: `ResultColors.close.copy(alpha = 0.18f)` very faint

**Issue:** Some text on dark backgrounds has insufficient contrast (WCAG AA standard: 4.5:1 for normal text).

**Fix:**
- Test locked achievement cells against contrast checker
- Increase alpha on drift warning background (use 0.25 instead of 0.18) or use a darker shade

---

## Recommendations

### Phase 1: Critical Fixes (Before v1 Release)
1. **Add back button to TuneUpScreen** (5 min)
2. **Make all back buttons consistently visible** (10 min)
3. **Add contentDescription to all icons** (30 min — systematic audit)
4. **Standardize padding scale** (60 min — audit + apply to all 15 screens)
5. **Fix ProgressDots size + add icons** (20 min)
6. **Increase tap targets** (15 min)

**Estimated effort:** 2–3 hours  
**Impact:** High (professional appearance, accessibility compliance)

### Phase 2: High-Priority Polish (Before PlayStore)
7. Add complete empty/error state handling (60 min)
8. Standardize typography (audit + apply semantic styles, 90 min)
9. Fix text color consistency (30 min)
10. Add scroll to all setup screens (15 min)
11. Add icons/labels to color-only state conveyance (30 min)

**Estimated effort:** 4–5 hours  
**Impact:** High (consistency, readability, user experience)

### Phase 3: Visual Polish (Nice-to-Have)
12. Fix section spacing rhythm (30 min)
13. Improve loading states (30 min)
14. Add drone screen subtitle (5 min)
15. Add volume display (10 min)
16. Audit dark-theme contrast (20 min)

**Estimated effort:** 1.5 hours  
**Impact:** Medium (polish, professional finish)

---

## Checklist for Remediation

- [ ] **Phase 1: Critical (2–3 hrs)**
  - [ ] TuneUpScreen back button added
  - [ ] All conditional back buttons now always visible
  - [ ] All Icon components have contentDescription
  - [ ] Padding standardized (16dp cards, 24dp edges, 20dp section breaks, 12dp items)
  - [ ] ProgressDots increased to 16dp with icons
  - [ ] All tap targets ≥48dp or wrapped in minimumTouchTargetSize()

- [ ] **Phase 2: Polish (4–5 hrs)**
  - [ ] Empty states: all screens have proper "no data" messaging
  - [ ] Error states: all errors in Card with background + clear text
  - [ ] Typography: all font sizes either semantic (Material3 styles) or defined in TextSizes object
  - [ ] Text colors: consistent use of onSurface, onSurfaceVariant, and ResultColors
  - [ ] Text truncation: all labels have .maxLines() + TextOverflow.Ellipsis
  - [ ] Scroll added to setup screens (TuneUp, Calibrate)
  - [ ] State indicators: all color conveyance also has icon or text label

- [ ] **Phase 3: Polish (1.5 hrs)**
  - [ ] Section spacing rhythm verified (20dp major, 12dp item baseline)
  - [ ] Loading states: all show label + icon + consistent color
  - [ ] Header styling consistent across all screens
  - [ ] Drone subtitle added + volume display shows
  - [ ] Contrast verification on dark theme (test with checker tool)

---

## Testing & Validation

After remediation:

1. **Visual Walk-through:** Screen each screen on Pixel 6a (her phone) at 2m distance while holding bass
2. **Accessibility Audit:**
   - Use Android Accessibility Scanner (Google Play)
   - Test with screen reader (TalkBack) on 3–5 key screens
   - Run WebAIM Contrast Checker on color combinations
3. **Responsive Testing:**
   - Test on emulator (Medium_Phone_API_36.1, 360dp wide)
   - Test on small phone (e.g., Pixel 3, 360dp)
   - Test on larger phone (e.g., Pixel 6 Pro, 412dp)
4. **User Feedback:** Sarah tests all navigation flows + accessibility with screen reader enabled

---

## Appendix: Design System Reference

### Color Palette
- **Primary:** `#7BD88F` (success green)
- **Semantic Result Colors:**
  - **Excellent (3★):** `#7BD88F` (green)
  - **Close (1–2★):** `#E7C664` (gold)
  - **Off (0★):** `#E06C75` (red)
- **Text:**
  - Primary: `#E2E2E6` (onSurface, default)
  - Secondary: `#C3C8BC` (onSurfaceVariant, labels/hints)
  - Disabled: `#43483F` (surfaceVariant)
- **Background/Surface:** `#121318` (near-black, dark theme only)

### Spacing Scale
- **Screen edges:** 24.dp horizontal, 24.dp top, 16.dp bottom
- **Card padding:** 16.dp (all sides)
- **Section break:** 20.dp (vertical, between titled groups)
- **Item spacing:** 12.dp (vertical, default list spacing)
- **Fine spacing:** 8.dp (horizontal in rows, related elements)
- **Progress dots:** 12.dp each, spacedBy 8.dp

### Typography
- **Page titles:** `headlineMedium` (onSurface)
- **Section headers:** `labelLarge` (onSurface)
- **Scores/large prompts:** `headlineSmall` or custom 64–140.sp (bold, onSurface or ResultColors)
- **Labels/info:** `titleMedium` (onSurface)
- **Body text:** `bodyLarge` or `bodyMedium` (onSurface)
- **Helper text:** `bodySmall` (onSurfaceVariant)

### Component Patterns
- **Buttons:**
  - Primary action: `Button(fillMaxWidth)`
  - Secondary action: `OutlinedButton(fillMaxWidth)`
  - Minimal action: `TextButton()`
  - All buttons should be ≥48.dp tall for touch targets
- **Cards:** 16.dp padding, default Material3 style (no custom colors unless primaryContainer)
- **ProgressDots:** 12.dp circles, spacedBy 8.dp, use ResultColors or onSurfaceVariant
- **Chips:** FilterChip (multi-select), SegmentedButton (exclusive choice)
- **Modals:** AlertDialog with clear title + descriptive text + action buttons

---

## Version History

| Date | Author | Notes |
|------|--------|-------|
| 2026-07-14 | AI Review | Initial comprehensive audit across all 15 screens |

---

**Next Steps:**  
1. Share this document with Sarah for feedback on priorities
2. Create GitHub issues for each critical/high-priority item
3. Schedule remediation work in small, testable chunks
4. Plan accessibility audit after fixes applied
5. Coordinate testing on her Pixel 6a for real-world validation
