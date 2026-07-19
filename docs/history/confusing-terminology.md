# Confusing Terminology Review (Double Bass Student Perspective)

This document lists UI labels and technical terms that are confusing for a music student and feel too much like "developer speak."

## 1. Technical & Engineering Terms
*   **"Traces" / "Game traces"**: Used in Recordings and settings. 
    *   *Recommendation:* Use **"Practice Sessions,"** **"Recordings,"** or **"Session Data."**
*   **"Capture"**: Used throughout (Game capture, Long capture, Capture stable). 
    *   *Recommendation:* Use **"Record"** or **"Listen."** For "Capture stable," use **"Note detected."**
*   **"Detection log"**: Confusing when sharing. 
    *   *Recommendation:* Use **"Pitch Data"** or **"Analysis File."**
*   **"window 4096" / "overlap 0.75"**: DSP terminology that means nothing to a musician.
    *   *Recommendation:* **Hide** from the main UI. If needed for debugging, put it under an "Advanced" or "Technical Details" toggle.
*   **"gate 45"**: In music, a "gate" is an effect, but here it's used as a threshold. 
    *   *Recommendation:* Use **"Noise Threshold"** or **"Silence Level."**
*   **"src 1"**: Cryptic abbreviation for "Source."
    *   *Recommendation:* Use **"Microphone"** or **"Audio Source."**
*   **"Harmonic energy"**: Found in Pitch Analyzer. Unclear if higher is better.
    *   *Recommendation:* Use **"Tone Clarity"** or **"Pitch Strength."**
*   **"Octave-settle"**: Found in Calibration summary. 
    *   *Recommendation:* Use **"Response Time"** or **"Detection Delay."**
*   **"Separation Verdict"**: Too formal/legal. 
    *   *Recommendation:* Use **"Room Check"** or **"Environment Rating."**
*   **"MIDI" / "MIDI range"**: Classical students think in note names.
    *   *Recommendation:* Display as **"Note Range"** (e.g., E1 to G4).
*   **"Stable" / "Stability"**: Used in "Capture stable" or "Stability window."
    *   *Recommendation:* Use **"Settled"** or **"Locked in."**

## 2. Navigation & UI Labels
*   **"Snippets"**: Sounds like code. 
    *   *Recommendation:* Use **"Clips"** or **"Recordings."**
*   **"Note sweep"**: Ambiguous. 
    *   *Recommendation:* Use **"Range Tester"** or **"Check All Notes."**
*   **"Calibration wizard"**: "Wizard" is software-speak. 
    *   *Recommendation:* Use **"Setup Guide"** or **"Auto-Calibration."**
*   **"Enharmonics"**: Technical music term. 
    *   *Recommendation:* Use **"Sharps and Flats."**
*   **"Game"**: Used for practice modes.
    *   *Recommendation:* Use **"Exercise"** or **"Practice Session."**
*   **"Prompt"**: Suggests a computer command.
    *   *Recommendation:* Use **"Target Note"** or **"Play This."**
*   **"Spelling"**: Used in settings/logic.
    *   *Recommendation:* Use **"Notation"** or **"Note Naming."**
*   **"PB"**: Used on the Home screen for "Personal Best."
    *   *Recommendation:* Use **"Best"** or **"Personal Best"** in full.
*   **"Arco" / "Pizz"**: Standard music terms, but potentially intimidating for beginners.
    *   *Recommendation:* Consider adding **"(Bowed)"** or **"(Plucked)"** as subtitles or secondary labels.

## 3. Scoring & Results
*   **"Cents"**: Raw numeric offset.
    *   *Recommendation:* Supplement with descriptive text like **"Sharp by 5c"** or **"Flat by 5c."**
*   **"Outcome"**: Used in the logic for round summaries.
    *   *Recommendation:* Use **"Round Summary"** or **"Session Results."**
*   **"Drift warning"**: Technical description of a pitch trend.
    *   *Recommendation:* Use **"Consistency Warning"** or **"Pitch Trend"** (e.g., "Trending Sharp").
*   **"Count right note, wrong octave as correct"**: A very long and technical setting label.
    *   *Recommendation:* Use **"Allow octave shifts"** or **"Ignore octave errors."**

## 4. Progress & Statistics
*   **"Deviation"**: Found in Progress screen ("avg deviation").
    *   *Recommendation:* Use **"Cents off"** or **"Intonation error."**
*   **"Absolute"**: Used in "average absolute cents."
    *   *Recommendation:* **Omit.** Musicians care about the average error, but "absolute" is a mathematical term that clutters the description.
*   **"Breakdown"**: Used for "Position breakdown."
    *   *Recommendation:* Use **"Detailed view"** or **"Summary by position."**

## 5. Screen-Specific Confusions

### Recordings Screen
*   "Full game or calibration traces for analysis."
    *   *Recommendation:* **"Detailed session logs for troubleshooting."**
*   "Share sends the audio plus its detection log."
    *   *Recommendation:* **"Share recording and pitch analysis data."**

### Pitch Analyzer (Debug)
*   "raw", "smoothed", "accepted"
    *   *Recommendation:* **Hide by default.** Label the section as **"Technical Pipeline Details"** if visible.

### Calibration Wizard
*   "Turning the knobs…"
    *   *Recommendation:* **"Optimizing for your microphone..."** or **"Fine-tuning detection..."**
*   "thresholds adjusted"
    *   *Recommendation:* **"Calibration settings updated."**
