# UI Issues by Screen — Quick Reference & Implementation Map

**Cross-reference:** See [UI_DESIGN_REVIEW.md](UI_DESIGN_REVIEW.md) and [UI_REMEDIATION_GUIDE.md](UI_REMEDIATION_GUIDE.md) for details  
**Purpose:** At-a-glance issue inventory per screen for sprint planning  
**Status:** Ready for task breakdown and assignment  

---

## Issues by Screen

### 🔴 TuneUpScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| **NO back button** | Navigation | 85–90 | 🔴 Critical | 10 min |
| Card padding 12.dp | Spacing | 69 | High | 2 min |
| No scroll | UX | — | Medium | 5 min |

**Summary:** Only screen without back button. Add TopAppBar with back icon.

**Checklist:**
- [ ] Add TopAppBar with back navigation icon
- [ ] Update card padding to Spacing.CARD_PADDING (16.dp)
- [ ] Add `.verticalScroll(rememberScrollState())` to main Column
- [ ] Test: Can exit without action? Is back button always visible?

---

### 🔴 WizardScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| **Back hidden on Summary/Failed** | Navigation | 113 | 🔴 Critical | 10 min |
| Missing contentDescription (emojis) | Accessibility | 108–110, 156 | High | 5 min |
| Failed state lacks visual container | UX | 183–186 | Medium | 15 min |
| Long text may truncate | UX | 169 | Medium | 5 min |
| Font sizes mixed (140.sp, displayLarge) | Typography | 95, 243 | Medium | 10 min |

**Summary:** Back navigation conditionally hidden. Emoji icons lack descriptions. Failed state needs emphasis.

**Checklist:**
- [ ] Add TopAppBar with always-visible back navigation
- [ ] Add contentDescription to all emoji/icon elements
- [ ] Wrap failed state in Card with ResultColors.off background + icon
- [ ] Add .maxLines(N) to long text at line 169
- [ ] Replace 140.sp with TextSizes.COUNTDOWN_NUMBER
- [ ] Replace displayLarge with TextSizes.PROMPT_NOTE

---

### ⚠️ DebugPitchScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| Card padding inconsistent (12.dp) | Spacing | 216 | High | 2 min |
| SweepView padding 20.dp | Spacing | 390 | High | 2 min |
| Missing contentDescription (Stop, CheckCircle icons) | Accessibility | 246, 299 | High | 5 min |
| Canvas needle (visual-only) | Accessibility | 75–100 | Medium | 5 min |
| Text colors inconsistent | Typography | 161–165 | Medium | 10 min |

**Summary:** Padding variance, missing icon descriptions, inconsistent text colors.

**Checklist:**
- [ ] Update card padding to Spacing.CARD_PADDING (16.dp)
- [ ] Update SweepView padding to Spacing.SCREEN_EDGE_HORIZONTAL (24.dp)
- [ ] Add contentDescription to Stop and CheckCircle icons
- [ ] Add semantics { contentDescription = "..." } to needle component
- [ ] Standardize text colors (onSurface, onSurfaceVariant, or ResultColors)

---

### ⚠️ RecordingsScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| Inconsistent card padding (10–16.dp) | Spacing | 186, 173 | High | 5 min |
| Button sizing inconsistent | UX | 218–222 | High | 10 min |
| Text truncation without maxLines | UX | 189 | Medium | 5 min |
| Missing contentDescription | Accessibility | (various) | High | 5 min |

**Summary:** Card padding mixed, buttons too small, text can overflow.

**Checklist:**
- [ ] Update all Card padding to Spacing.CARD_PADDING
- [ ] Convert Play/Share/Delete TextButtons to OutlinedButton with fillMaxWidth
- [ ] Add .maxLines(1) + TextOverflow.Ellipsis to recording names
- [ ] Add contentDescription to all icons
- [ ] Add LazyColumn verticalArrangement = spacedBy(Spacing.ITEM_SPACING)

---

### ⚠️ AchievementsScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| Card padding 10.dp | Spacing | 76 | High | 2 min |
| Title spacing only 4.dp (should be 8.dp) | Spacing | 46 | High | 2 min |
| Fixed card height (responsive issue) | UX | 66, 150.dp | Medium | 15 min |
| Text color contrast (locked cells) | Readability | 93–96 | Medium | 5 min |
| Locked badges not visually distinct | UX | — | Medium | 20 min |
| Header styling (labelLarge vs headlineMedium) | Typography | — | Low | 5 min |

**Summary:** Spacing inconsistent, cards fixed-size, locked achievements not visually clear.

**Checklist:**
- [ ] Update all Card padding to Spacing.CARD_PADDING
- [ ] Update Spacer after title to Spacing.FINE_SPACING (8.dp)
- [ ] Remove fixed card height; use adaptive sizing or min/max constraints
- [ ] Test contrast on locked cells (may need darker text or adjusted background)
- [ ] Add 🔒 icon + greyed styling to locked achievement cells
- [ ] Use headlineMedium for screen title (consistent with other screens)

---

### ⚠️ ProgressScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| Empty state minimal (no container/icon) | UX | 108–112 | Medium | 15 min |
| Section spacing inconsistency | Spacing | — | Medium | 5 min |
| Sustain tab "Accuracy by position" should not appear | UX | — | Medium | 5 min |

**Summary:** Empty state lacks visual container, spacing could be more consistent.

**Checklist:**
- [ ] Wrap empty state in Card with icon + descriptive text (see remediation guide)
- [ ] Verify all section vertical arrangement uses Spacing.ITEM_SPACING or Spacing.SECTION_BREAK
- [ ] Check Sustain filter: "Accuracy by position" should be hidden (or handled in code)

---

### ⚠️ CalibrateScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| **Cancel button hidden when saved** | Navigation | 76–84 | 🔴 Critical | 10 min |
| Header styling (labelLarge vs headlineMedium) | Typography | 52 | Medium | 3 min |
| No scroll (may overflow on small screens) | UX | — | Medium | 5 min |
| Loading state (LinearProgressIndicator) lacks label/icon | UX | 77 | Medium | 15 min |
| Result verdict lacks visual emphasis | UX | 111–116 | Medium | 15 min |

**Summary:** Cancel button conditionally hidden, header styling inconsistent, loading state unclear.

**Checklist:**
- [ ] Add TopAppBar with always-visible back navigation
- [ ] Change StepLabel from labelLarge to headlineMedium
- [ ] Add `.verticalScroll(rememberScrollState())` to main Column
- [ ] Wrap loading state in Container with icon + label (see remediation guide)
- [ ] Wrap result verdict in Card with ResultColors background + clear text

---

### ⚠️ DroneScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| Spacer heights lack rhythm (4–32.dp mixed) | Spacing | 75–160 | High | 20 min |
| Missing contentDescription on Play/Stop icons | Accessibility | 85 | High | 2 min |
| No instructional subtitle (unlike TuneUpScreen) | UX | 75 | Low | 3 min |
| Volume slider with no value display | UX | 124 | Low | 10 min |
| Playing/stopped state color-only conveyance | Accessibility | 82 | Medium | 10 min |

**Summary:** Spacing rhythm inconsistent, missing icon descriptions, no volume feedback.

**Checklist:**
- [ ] Audit and standardize all Spacer heights to Spacing constants
- [ ] Add contentDescription to Play/Stop icons
- [ ] Add subtitle: "Reference pitch for ear training — no scoring, just sound."
- [ ] Add Text display showing current volume percentage
- [ ] Add text + icon to playing/stopped state (not just color)

---

### ⚠️ ChordsScreen.kt
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| ProgressDots too small (12.dp) | Accessibility | 102–105 | High | 15 min |
| ProgressDots color-only, no icons | Accessibility | 102–105 | High | 15 min |
| Missing contentDescription on emoji 🎧 | Accessibility | 141 | High | 2 min |
| Font size mixed (96.sp, manual) | Typography | 156 | Medium | 5 min |
| Text truncation risk (no maxLines) | UX | 201–216 | Medium | 5 min |
| Drift warning very faint (alpha 0.18f) | Readability | 63–68 | Medium | 3 min |
| Game instruction text small (bodySmall) | Readability | 142 | Medium | 3 min |

**Summary:** ProgressDots too small + color-only, typography mixed, text can truncate.

**Checklist:**
- [ ] Increase ProgressDots from 12.dp to Spacing.PROGRESS_DOT_SIZE (16.dp)
- [ ] Add icons to dots (✓, ✗, ~, etc. with descriptions)
- [ ] Add contentDescription to emoji elements
- [ ] Replace 96.sp with TextSizes.PROMPT_NOTE or similar
- [ ] Add .maxLines() to all text that could overflow
- [ ] Increase alpha on drift warning (0.18f → 0.25f)
- [ ] Consider increasing game instruction text size

---

### ⚠️ TuneUpScreen.kt (already in 🔴 section, repeated for completeness)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| **NO back button** | Navigation | 85–90 | 🔴 Critical | 10 min |
| Card padding inconsistency | Spacing | 69 | High | 2 min |
| No scroll (may overflow) | UX | — | Medium | 5 min |
| Canvas needle (visual-only) | Accessibility | 75–100 | Medium | 5 min |

---

### ✅ RoundScreen.kt (Good — Minimal Issues)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| ProgressDots should have icons (Optional) | Polish | 102–105 | Low | 15 min |

**Status:** Well-designed, follows patterns consistently. Optional: add icons to ProgressDots for consistency.

---

### ✅ SustainScreen.kt (Good — Minimal Issues)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| (None identified) | — | — | — | — |

**Status:** Consistent with RoundScreen patterns, well-executed.

---

### ✅ ShiftScreen.kt (Good — Minimal Issues)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| (None identified) | — | — | — | — |

**Status:** Consistent, well-designed.

---

### ✅ SettingsScreen.kt (Good — Follows Patterns)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| (None identified — 20.dp section spacing is intentional) | — | — | — | — |

**Status:** Established as the reference for configuration screens.

---

### ✅ HomeScreen.kt (Good — Follows Patterns)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| (None identified) | — | — | — | — |

**Status:** Well-designed, consistent patterns.

---

### ✅ AboutScreen.kt (Good — Minimal Issues)
| Issue | Type | Line | Priority | Effort |
|-------|------|------|----------|--------|
| (None identified) | — | — | — | — |

**Status:** Simple screen, follows patterns.

---

## Implementation Priority & Roadmap

### 🔴 Critical (Must Fix — 1 Hour)
1. **TuneUpScreen: Add back button** (10 min)
2. **CalibrateScreen: Unhide back button** (10 min)
3. **WizardScreen: Unhide back button** (10 min)
4. **Audit & add all missing contentDescription** (30 min — systematic pass)

**Total:** ~60 min  
**Impact:** High (user can exit any screen; accessibility compliance)

### ⚠️ High Priority (Before v1 Release — 3 Hours)
5. **Create Spacing object & apply to all screens** (90 min)
   - File: `ui/theme/Spacing.kt`
   - Apply to: All 15 screens
6. **Fix ProgressDots size + add icons** (30 min)
   - RoundScreen, ChordsScreen
   - Add TextSizes.PROGRESS_DOT_SIZE, icons, accessibility
7. **Create TextSizes object & standardize font sizes** (30 min)
   - File: `ui/theme/TextSizes.kt`
   - Apply to: DebugPitchScreen, ChordsScreen, WizardScreen, DroneScreen
8. **Add empty/error states to all screens** (30 min)
   - ProgressScreen, AchievementsScreen, CalibrateScreen, WizardScreen

**Total:** ~3 hours  
**Impact:** Very High (professional appearance, consistency, UX)

### 📱 Medium Priority (Polish — 2–3 Hours)
9. **Fix text truncation** (30 min)
   - RecordingsScreen, ChordsScreen
10. **Add scroll to setup screens** (15 min)
    - TuneUpScreen, CalibrateScreen
11. **Improve loading states** (30 min)
    - CalibrateScreen, WizardScreen
12. **Add volume display to DroneScreen** (15 min)
13. **Fix text colors consistency** (30 min)
14. **Audit & increase contrast on dark theme** (20 min)

**Total:** ~2.5 hours  
**Impact:** Medium (UX polish, readability)

### 💡 Lower Priority (Nice-to-Have)
15. **Add icons to color-only state conveyance** (30 min)
16. **Fix section spacing rhythm** (20 min)
17. **Add drone subtitle + other descriptions** (20 min)

**Total:** ~1.5 hours  
**Impact:** Low (visual polish)

---

## Grand Total Estimate

| Phase | Effort | Impact |
|-------|--------|--------|
| 🔴 Critical | 1 hour | High |
| ⚠️ High | 3 hours | Very High |
| 📱 Medium | 2.5 hours | Medium |
| 💡 Low | 1.5 hours | Low |
| **Total** | **~8 hours** | **Production Ready** |

---

## Sprint Breakdown (Recommended 2-Sprint Approach)

### Sprint 1: Foundational Fixes (3–4 hours)
**Goal:** Navigation consistency, spacing scale, accessibility compliance

- [ ] Create Spacing.kt (single source of truth)
- [ ] Create TextSizes.kt (single source of truth)
- [ ] Add back button to TuneUpScreen + ensure all screens have consistent back navigation
- [ ] Audit & add contentDescription to all icons (30 min systematic pass)
- [ ] Apply Spacing constants to all 15 screens (90 min)
- [ ] Fix ProgressDots size + add icons
- [ ] Test on device: navigation flows, spacing consistency, accessibility

**Deliverable:** App feels cohesive, navigation predictable, accessibility-compliant.

### Sprint 2: UX Polish (3–4 hours)
**Goal:** Empty states, error handling, typography, readability

- [ ] Add empty/error states to all screens
- [ ] Fix text truncation on RecordingsScreen, ChordsScreen
- [ ] Add scroll to TuneUpScreen, CalibrateScreen
- [ ] Improve loading states (icon + label)
- [ ] Standardize text colors across screens
- [ ] Add volume display to DroneScreen
- [ ] Audit contrast on dark theme + fix if needed
- [ ] Test on device: responsiveness, empty states, load states, color contrast

**Deliverable:** App is production-ready for PlayStore (professional polish, complete UX).

---

## Testing Checklist

After each phase:

- [ ] **Visual walk-through:** Each screen on Pixel 6a at 2m distance
- [ ] **Navigation:** Back button present and works on every screen
- [ ] **Spacing:** Consistent 24.dp edges, 16.dp card padding, 12.dp item spacing
- [ ] **Typography:** All font sizes from TextSizes or Material3 styles
- [ ] **Accessibility:** Run Android Accessibility Scanner, test TalkBack
- [ ] **Contrast:** Verify WCAG AA (4.5:1) on all text + backgrounds
- [ ] **Responsive:** Test on 360dp (emulator) and 412dp (device)
- [ ] **Empty states:** All screens show proper "no data" messaging
- [ ] **User feedback:** Sarah tests navigation flows and accessibility

---

## Files to Create

1. **`ui/theme/Spacing.kt`** — Spacing scale constants
2. **`ui/theme/TextSizes.kt`** — Custom font sizes for game contexts

## Files to Modify (All 15 screens)

```
HomeScreen.kt
RoundScreen.kt
SustainScreen.kt
ShiftScreen.kt
TuneUpScreen.kt ⚠️ (back button)
CalibrateScreen.kt ⚠️ (back button)
WizardScreen.kt ⚠️ (back button)
DebugPitchScreen.kt
ProgressScreen.kt
SettingsScreen.kt
RecordingsScreen.kt
AchievementsScreen.kt
AboutScreen.kt
DroneScreen.kt
ChordsScreen.kt
```

---

**Next Step:** Create a GitHub project or Trello board with these tasks, assign to sprint, and start with Phase 1 foundational work.

For detailed code examples, see [UI_REMEDIATION_GUIDE.md](UI_REMEDIATION_GUIDE.md).
