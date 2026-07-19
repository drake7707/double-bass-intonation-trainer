# UI Remediation Guide — Code Examples & Fixes

**Document:** Companion to [UI_DESIGN_REVIEW.md](UI_DESIGN_REVIEW.md)  
**Purpose:** Concrete code patterns and examples for fixing identified issues  
**Status:** Ready for implementation  

---

## 1. Standardize Spacing (Issue #4)

### Create Spacing Object (New File)

**File:** `ui/theme/Spacing.kt`

```kotlin
package be.drakarah.intonation.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing across all screens.
 * Use these constants instead of hardcoded dp values to ensure consistency.
 */
object Spacing {
    // Screen edges
    val SCREEN_EDGE_HORIZONTAL = 24.dp
    val SCREEN_EDGE_TOP = 24.dp
    val SCREEN_EDGE_BOTTOM = 16.dp

    // Card & component padding
    val CARD_PADDING = 16.dp
    val CARD_PADDING_SMALL = 12.dp  // Only for very compact items

    // Vertical spacing (Column arrangement)
    val SECTION_BREAK = 20.dp  // Major break between titled groups
    val ITEM_SPACING = 12.dp   // Default vertical space between items
    val FINE_SPACING = 8.dp    // Related elements, fine details

    // Horizontal spacing (Row arrangement)
    val ITEM_HORIZONTAL = 8.dp  // Between chips, buttons in a row
    val COMPONENT_SPACING = 4.dp // Icon + text, very tight

    // Size constants for interactive elements
    val PROGRESS_DOT_SIZE = 16.dp  // Updated from 12.dp for accessibility
    val PROGRESS_DOT_SPACING = 8.dp
}
```

### Apply to Screens

**Before:**
```kotlin
Column(
    .padding(horizontal = 24.dp)
    .verticalScroll(...),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) { }
```

**After:**
```kotlin
Column(
    .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
    .verticalScroll(...),
    verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
) { }
```

### Card Padding (All 15 screens)

**Before (inconsistent across files):**
```kotlin
// DebugPitchScreen
Card(Modifier.padding(12.dp)) { }
// RecordingsScreen
Card(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { }
// AchievementsScreen
Card(Modifier.padding(10.dp)) { }
```

**After (consistent everywhere):**
```kotlin
Card(Modifier.padding(Spacing.CARD_PADDING)) { }
```

---

## 2. Add Back Button to TuneUpScreen (Issue #1)

**File:** [TuneUpScreen.kt](../app/src/main/java/be/drakarah/intonation/ui/tune/TuneUpScreen.kt)

### Current Structure
```kotlin
@Composable
fun TuneUpScreen(
    onExit: () -> Unit,
    viewModel: TuneUpViewModel = viewModel(factory = TuneUpViewModel.Factory),
) {
    // ... state collection ...

    Scaffold { padding ->
        Column(
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            // Content
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
```

### Fix: Add TopAppBar
```kotlin
@Composable
fun TuneUpScreen(
    onExit: () -> Unit,
    viewModel: TuneUpViewModel = viewModel(factory = TuneUpViewModel.Factory),
) {
    // ... state collection ...

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tune up") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))
            // ... content (remove manual title, TopAppBar provides it) ...
            Spacer(Modifier.weight(1f))
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}
```

**Import:** `androidx.compose.material3.TopAppBar`

---

## 3. Fix Navigation Back Button Visibility (Issue #2)

### CalibrateScreen

**Current (Cancel hidden when saved):**
```kotlin
@Composable
fun CalibrateScreen(onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold { padding ->
        Column(...) {
            // Only show Cancel if not yet saved
            if (state.savedSuccessfully != true) {
                OutlinedButton(onClick = onExit) {
                    Text("Cancel")
                }
            }
        }
    }
}
```

**Fix (Always visible):**
```kotlin
@Composable
fun CalibrateScreen(onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrate") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(...) {
            // Content — no conditional exit button
        }
    }
}
```

### WizardScreen

**Current (Back hidden for Summary/Failed):**
```kotlin
val phase by viewModel.phase.collectAsStateWithLifecycle()

if (phase !is WizardPhase.Summary && phase !is WizardPhase.Failed) {
    OutlinedButton(onClick = onExit) {
        Text("Back")
    }
}
```

**Fix:**
```kotlin
// Always show back in TopAppBar, OR
// For Summary/Failed: show "Done" button instead but no "stuck" state
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Calibration") },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }
) { padding ->
    // ... content ...
}
```

---

## 4. Add ContentDescription to Icons (Issue #3)

### Systematic Fix Pattern

**Before (missing contentDescription):**
```kotlin
// DebugPitchScreen line 299
Icon(Icons.Filled.Stop, contentDescription = null)  // ❌ No description

// DroneScreen line 85
IconButton(onClick = togglePlayback) {
    Icon(Icons.Filled.PlayArrow, contentDescription = null)  // ❌
}

// ChordsScreen line 96–105
Row {
    repeat(state.roundLength) { i ->
        Box(
            Modifier
                .size(12.dp)
                .background(colorForState(i), CircleShape)
            // ❌ No contentDescription for dots
        )
    }
}
```

**After (with descriptions):**
```kotlin
// Functional icon (stop recording)
Icon(Icons.Filled.Stop, contentDescription = "Stop recording")

// Button with icon
IconButton(onClick = togglePlayback) {
    Icon(
        Icons.Filled.PlayArrow,
        contentDescription = if (isPlaying) "Stop playing" else "Start playing"
    )
}

// Progress dots with semantic description
Row {
    repeat(state.roundLength) { i ->
        val state = stateForDot(i)
        Box(
            Modifier
                .size(16.dp)
                .background(colorForState(state), CircleShape)
                .semantics {
                    contentDescription = when(state) {
                        DotState.DONE_EXCELLENT -> "Round ${i+1} — perfect"
                        DotState.DONE_CLOSE -> "Round ${i+1} — close"
                        DotState.DONE_OFF -> "Round ${i+1} — missed"
                        DotState.CURRENT -> "Round ${i+1} — now playing"
                        DotState.PENDING -> "Round ${i+1} — upcoming"
                    }
                }
        )
    }
}
```

### Complete Audit Checklist
- [ ] DebugPitchScreen: Stop icon (L299), CheckCircle icon (L246–248)
- [ ] DroneScreen: PlayArrow/Stop icons (L85)
- [ ] ChordsScreen: ProgressDots (L102–105), emoji 🎧 (L141)
- [ ] TuneUpScreen: Canvas needle component (L75–100)
- [ ] WizardScreen: All emoji icons (L108–110, L156, etc.)

---

## 5. Fix ProgressDots Size & Add Icons (Issue #5)

**File:** Update all screens using ProgressDots

### Before (12.dp, color-only)
```kotlin
@Composable
private fun ProgressDots(state: RoundUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.roundLength) { i ->
            val result = state.results.getOrNull(i)
            val color = when {
                result == null && i == state.promptIndex ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                result == null -> MaterialTheme.colorScheme.surfaceVariant
                result.timedOut || result.starCount == 0 -> ResultColors.off
                result.starCount == 3 -> ResultColors.excellent
                else -> ResultColors.close
            }
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
```

### After (16.dp, with icons + accessibility)
```kotlin
@Composable
private fun ProgressDots(state: RoundUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.PROGRESS_DOT_SPACING)) {
        repeat(state.roundLength) { i ->
            val result = state.results.getOrNull(i)
            val (color, icon, description) = when {
                result == null && i == state.promptIndex ->
                    Triple(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Icons.Default.Circle,  // or hourglass for "waiting"
                        "Round ${i+1} — now playing"
                    )
                result == null ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        Icons.Default.Circle,
                        "Round ${i+1} — upcoming"
                    )
                result.timedOut || result.starCount == 0 ->
                    Triple(
                        ResultColors.off,
                        Icons.Default.Clear,  // ✗
                        "Round ${i+1} — missed"
                    )
                result.starCount == 3 ->
                    Triple(
                        ResultColors.excellent,
                        Icons.Default.Check,  // ✓
                        "Round ${i+1} — perfect"
                    )
                else ->
                    Triple(
                        ResultColors.close,
                        Icons.Default.HorizontalRule,  // ~ (approximately)
                        "Round ${i+1} — close"
                    )
            }
            
            Box(
                Modifier
                    .size(Spacing.PROGRESS_DOT_SIZE)
                    .background(color, CircleShape)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,  // Already covered by Box semantics
                    modifier = Modifier.size(12.dp),
                    tint = Color.White  // High contrast on colored background
                )
            }
        }
    }
}
```

---

## 6. Add Tap Target Wrappers (Issue #5 & #6)

### Small Interactive Elements Need Minimum 48dp

**Before:**
```kotlin
Box(
    Modifier
        .size(12.dp)
        .background(color, CircleShape)
        .clickable { ... }
)
```

**After (48dp minimum):**
```kotlin
Box(
    Modifier
        .size(Modifier.minimumTouchTargetSize())  // Wraps to ≥48dp
        .background(color, CircleShape)
        .clickable { ... }
        .semantics { role = Role.Button }
)
```

Or explicit:
```kotlin
Box(
    Modifier
        .size(48.dp)  // Explicit minimum
        .wrapContentSize(Alignment.Center)
) {
    Box(
        Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .clickable { ... }
    )
}
```

---

## 7. Fix Color-Only State Conveyance (Issue #6)

### ProgressDots (Already handled in Section 5)

### DroneScreen Playing State

**Before (color-only):**
```kotlin
val playingColor = if (isPlaying) 
    MaterialTheme.colorScheme.primary 
else 
    MaterialTheme.colorScheme.surfaceVariant

Box(
    Modifier
        .size(40.dp)
        .background(playingColor, CircleShape)
)
```

**After (color + icon + text):**
```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)
) {
    Icon(
        if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
        contentDescription = if (isPlaying) "Playing" else "Stopped",
        tint = if (isPlaying) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    )
    Text(
        if (isPlaying) "Playing" else "Stopped",
        style = MaterialTheme.typography.bodyMedium,
        color = if (isPlaying) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    )
}
```

---

## 8. Add Empty States (Issue #7)

### ProgressScreen Empty State

**Before:**
```kotlin
if (sessions.isEmpty()) {
    Text("No rounds yet...", bodyLarge, onSurfaceVariant)
}
```

**After:**
```kotlin
if (sessions.isEmpty()) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.CARD_PADDING)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                MaterialTheme.shapes.medium
            )
            .padding(Spacing.CARD_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)
    ) {
        Icon(
            Icons.Default.PlayCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "No rounds yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Play a game to see your progress here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
```

### AchievementsScreen Empty Achievement Cells

**Before:**
```kotlin
GridItem(
    modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
    content = { /* always renders, locked or not */ }
)
```

**After:**
```kotlin
GridItem(
    modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
    content = {
        if (isUnlocked) {
            // Bright badge
            Card { /* badge details */ }
        } else {
            // Dimmed locked state
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.CARD_PADDING),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.FINE_SPACING))
                        Text(
                            badge.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            badge.unlockCondition,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
)
```

---

## 9. Standardize Typography (Issue #10)

### Create Typography Sizes

**File:** `ui/theme/TextSizes.kt`

```kotlin
package be.drakarah.intonation.ui.theme

import androidx.compose.ui.unit.sp

/**
 * Custom text sizes for game-specific contexts.
 * Use these instead of hardcoded fontSize values.
 */
object TextSizes {
    val PROMPT_NOTE = 112.sp        // Main note to play (Round, Chords, Sustain)
    val COUNTDOWN_NUMBER = 140.sp   // Large count-in (3, 2, 1…)
    val SCORE_DISPLAY = 88.sp       // Final score or round result
    val SCORE_CENTS = 64.sp         // Cents display (±15c, etc.)
    val SCORE_STARS = 56.sp         // Attempt result stars
    val HOLD_TIME = 48.sp           // Sustain hold duration display
    val FREQUENCY_DISPLAY = 44.sp   // "442 Hz" in debug/tune screens
}
```

### Usage in Screens

**Before (mixed semantic and manual):**
```kotlin
// RoundScreen
Text("$secsLeft", fontSize = 140.sp, fontWeight = FontWeight.Bold)
Text(note, fontSize = 112.sp, fontWeight = FontWeight.Bold)
Text(score, fontSize = 88.sp)

// DebugPitchScreen
Text(frequency, style = MaterialTheme.typography.displayLarge)

// DroneScreen
Text(note, fontSize = 72.sp)
```

**After (consistent):**
```kotlin
import be.drakarah.intonation.ui.theme.TextSizes

// RoundScreen
Text("$secsLeft", fontSize = TextSizes.COUNTDOWN_NUMBER, fontWeight = FontWeight.Bold)
Text(note, fontSize = TextSizes.PROMPT_NOTE, fontWeight = FontWeight.Bold)
Text(score, fontSize = TextSizes.SCORE_DISPLAY)

// DebugPitchScreen
Text(frequency, fontSize = TextSizes.FREQUENCY_DISPLAY, fontWeight = FontWeight.Bold)

// DroneScreen
Text(note, fontSize = TextSizes.PROMPT_NOTE)
```

---

## 10. Fix Text Truncation (Issue #11)

### Systematic Pattern

**Before (can overflow):**
```kotlin
Text(recordingFileName)
Text(pitchClassName)
Text(warningMessage)
```

**After (with truncation):**
```kotlin
Text(
    recordingFileName,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)

Text(
    pitchClassName,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)

Text(
    warningMessage,
    maxLines = 3,  // Allow multi-line but cap at 3
    overflow = TextOverflow.Ellipsis
)
```

---

## 11. Add Scroll to Setup Screens (Issue #8)

**TuneUpScreen & CalibrateScreen:**

**Before:**
```kotlin
Scaffold { padding ->
    Column(
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp),
        // NO scroll!
    ) { }
}
```

**After:**
```kotlin
Scaffold { padding ->
    Column(
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),  // ADD THIS
        verticalArrangement = Arrangement.spacedBy(Spacing.SECTION_BREAK)
    ) { }
}
```

---

## 12. Loading State Improvements (Issue #13)

**Before (CalibrateScreen):**
```kotlin
LinearProgressIndicator()
```

**After:**
```kotlin
Column(
    Modifier
        .fillMaxWidth()
        .padding(Spacing.CARD_PADDING)
        .background(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            MaterialTheme.shapes.medium
        )
        .padding(Spacing.CARD_PADDING),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)
) {
    Icon(
        Icons.Default.Hourglass,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp)
    )
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        "Analyzing…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
```

---

## 13. Fix Text Colors (Issue #10)

### Establish Rules

**Primary text (headings, prompts):**
```kotlin
Text("Tune up", style = MaterialTheme.typography.headlineMedium)
// Implicit onSurface — no color needed
```

**Secondary text (labels, hints):**
```kotlin
Text(
    "Helper text…",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant  // EXPLICIT
)
```

**Semantic colors (results):**
```kotlin
Text(
    "+15 cents",
    style = MaterialTheme.typography.headlineSmall,
    color = ResultColors.close
)
```

**Disabled/Locked:**
```kotlin
Text(
    "Locked achievement",
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.surfaceVariant
)
```

---

## 14. Audit Checklist for Implementation

```kotlin
// Copy this into each screen file and work through systematically

// ✅ Padding & Spacing
// ✅ All horizontal padding = Spacing.SCREEN_EDGE_HORIZONTAL (24.dp)
// ✅ All card padding = Spacing.CARD_PADDING (16.dp)
// ✅ Vertical arrangement = Spacing.SECTION_BREAK (20.dp) or Spacing.ITEM_SPACING (12.dp)
// ✅ Back button present & always visible
// ✅ All Icon components have contentDescription
// ✅ All text colors explicit (or implicit onSurface)
// ✅ Font sizes use TextSizes or MaterialTheme.typography
// ✅ Text with potential overflow has .maxLines() + TextOverflow.Ellipsis
// ✅ Empty states have Card container + icon + descriptive text
// ✅ Error states in Card with ResultColors.off
// ✅ Loading states show icon + label + progress indicator
// ✅ All tap targets ≥48.dp or wrapped in minimumTouchTargetSize()
```

---

## Testing

After applying fixes:

1. **Visual regression:** Compare each screen to before screenshots
2. **Accessibility:** Run Android Accessibility Scanner
3. **Responsive:** Test on emulator (360dp) and her phone (412dp)
4. **Navigation:** Verify all back buttons work and are always visible
5. **Colors:** Check contrast with WebAIM checker (dark theme)
6. **User test:** Sarah confirms navigation flows feel natural

---

**Next:** Use this guide alongside [UI_DESIGN_REVIEW.md](UI_DESIGN_REVIEW.md) to tackle issues in order of priority.
