# Double Bass Intonation Trainer

- Gamified intonation trainer for double bass. Kotlin + Jetpack Compose, GPL-3.0-or-later,
package `be.drakarah.intonation`. 
- Developer: Sarah — double bassist, thinks in **fixed-do
solfège** (Do Ré Mi; "la string" = A string), plays arco + pizz, learns from Simandl,
currently knows up to 2nd position. 
- I give design feedback constantly and **wants pushback when an idea is weak** — don't blindly implement.
- During planning don't make assumptions, ask explicitly.
- Target users: myself but also other double bass students, aiming for a play store release
  
- **Core philosophy (never violate):** this is NOT a tuner. During exercises there is no
live pitch readout. Detect onset → wait for stability → freeze the FIRST stable pitch →
score it. Live needles exist only on the Tune-up screen and Pitch debug screen.

## Key documents

- `docs/architecture.md` — **structural overview** of modules, the detection pipeline, and the game
  domain, with mermaid diagrams. Start here for the big picture; `DETECTION.md` is the deep-dive.
- `docs/DETECTION.md` — **the definitive capture/detection reference.** The problem history,
  every design decision, what worked/didn't, the trace-driven fix, threshold ownership, and the
  drill for diagnosing detection issues.
- `FEATURES.md` — complete user-facing feature description. Keep in sync with changes.
- `TESTING.md` — her hands-on verification checklist. **Discipline: every change that
  needs bass/phone verification gets a Pending item; move to Verified (dated) when she
  confirms.** She asks "give me the checklist".
- `docs/history`: Contains all plans, refactors that were done.
- Auto-memory has project status, her preferences, build quirks.

## Architecture

See `docs/architecture.md`. Contains completed design documents, refactors, and historical implementation plans. Do not treat these as current architecture unless still referenced elsewhere.

## Test corpus workflow (the superpower — use it)

Real recordings live in `dsp/src/test/resources/wav/` (WAV float32 + JSONL detection
logs): open-string arco/pizz, desk noise, birdsong. When she reports detection
weirdness: she saves a snippet → pull via adb (or she Shares it) → add to corpus →
replay offline (`SnippetReplayAnalysis`, `OctaveDiagnosis` in dsp tests write reports to
`dsp/build/reports/`) → fix → add a regression test. `:app` tests also read the corpus
(sourceSets points at `../dsp/src/test/resources`).

## Build / device (Windows box quirks in auto-memory too)

- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every gradlew.
- Build+test: `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`
- adb: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` (not on PATH). Her Pixel 6a
  connects via **wireless debugging** (mDNS auto-connects when she enables it; ask her to
  toggle it if no device). Install:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- Emulator AVD `Medium_Phone_API_36.1` — note its quickboot snapshot reverts userdata
  between boots (don't trust it for persistence/migration verification; verify on her
  phone via logcat instead).
- Git Bash mangles `/sdcard/...` paths → prefix `MSYS_NO_PATHCONV=1`. Screenshots:
  `adb exec-out screencap -p > f.png` from Bash only (PowerShell corrupts binary).
  Never round-trip UTF-8 files through PS5.1 Get/Set-Content (mojibake) — use Write/Edit.
- Commits: descriptive, note which ideas were hers ("user feedback/design/request").
