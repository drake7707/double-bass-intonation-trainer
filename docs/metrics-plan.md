# Capture rich game metrics + backup export/import (built to scale)

## Context

Traces (`GameTrace.kt`) exist to *debug detection* and save full audio — heavy, transient,
discarded after a fix. Wrong home for long-term learning data.

We want to **coach the student over time** (the "Teacher's notebook" idea in TESTING.md:
*"Last week you struggled to find Fa♯2 cleanly; today you hit it first try three times"*).
Coaching-later needs data-now. Today we persist a solid baseline (`sessions` + `attempts`,
DB v3) but **the games produce more than we save** — bow steadiness, hold time, note
loudness/confidence, how many tries a note took. Those are the signals a teacher comments on,
and they're currently discarded.

This plan does three things and deliberately **stops short of building the coaching feature**:
1. **Enrich what each round records** (DB v3 → v4).
2. **Make the data model scale to years of daily play** (this is the core of this revision).
3. **Add backup export/import**, because this becomes irreplaceable data.

Decisions locked with Sarah: import **offers both Merge and Replace**; we **record completed
rounds only** for now. Sarah's scaling concern is the driver of the design below.

## Architecture & separation of concerns (build the metrics work as a standalone layer)

Hard constraint (Sarah): **no spaghetti, no gluing metrics logic onto game code or ViewModels.**
The metrics/coaching capability is its own concern and gets its own layer. This also aligns with
debt already logged in TESTING.md — the `onCaptured()` rule engine flagged at l.618 ("That's not
presentation logic. It's game logic.") and the UI/domain-split refactor. We build clean here so the
later refactor is smaller, and we do **not** worsen the current mix.

**Layering (dependencies point downward; upper layers never leak into lower):**
- **`dsp/`** (module) — raw pitch stream. Untouched.
- **`game/`** (existing pure-Kotlin, unit-tested) — state machines emit *pure result objects*
  (`CapturedPitch`, `SustainResult`, `ShiftResult`, chord tones). **No metrics/DB/rollup code added
  here — ever.**
- **NEW `metrics/` domain package** — pure Kotlin, **zero `android.*`/`androidx.*` imports**, unit-
  tested with synthetic inputs exactly like `game/`. Home for *all* new logic:
  - neutral value types (`RoundRecord`, `AttemptRecord`, `RoundContext`) — **not** Room entities;
  - `RoundRecorder` — the single entry point a VM calls (`record(RoundRecord): RoundOutcome`):
    classify outcomes → map to entities → persist → fold rollups → PB/achievement/week-trend;
  - pure rollup fold (`DailyStatsFold.apply(existing, attempts) → updated`) and derived-metric
    functions (confidence, consistency) — no I/O, trivially testable;
  - `CoachingAggregator` (later) reads rollups; no ad-hoc queries in UI.
- **`data/`** — demoted to **persistence only**: Room entities, DAOs, and domain↔entity mapping at
  this boundary. `SessionRepository`'s current PB/achievement/week-trend *orchestration* moves up
  into `RoundRecorder` (it's business logic); the repository becomes a thin DAO wrapper.
- **`ui/` ViewModels** — presentation + audio-stream + driving the state machine only. On round
  end they assemble a neutral `RoundRecord` from the game results they already hold and call
  `RoundRecorder`. They **stop** building `AttemptEntity`, mapping `outcome`, computing `configKey`
  inline, and touching rollups. Net effect: VMs get *smaller*.

**Outcome classification.** The wrong-note/octave/artifact rules are game logic, not presentation
(per l.618). The metrics feature only needs the *result* of that classification. Minimum: the VM
passes the `wrongNote`/`wrongOctave`/timeout booleans it already computes into `RoundRecord`, and
`RoundRecorder` maps them to `outcome` — no detection code touched. **Recommended companion (flag,
can sequence first):** extract the `onCaptured` rule engine into a pure `game/` classifier shared by
the VM and recorder — a *behavior-preserving* move guarded by `FeedbackRegressionTest` /
`RealBassRegressionTest`. Kept optional so the metrics feature is never blocked on a detection refactor.

**Backup** is its own `BackupService` (domain) + `@Serializable` DTOs (data), invoked by a thin
Settings action — not wired into any game VM.

## Performance & scale — the load, and why the current schema won't hold

**Projected load.** A dedicated student, 5 years:
- Moderate (10 rounds/day, ~10 prompts): ≈ **18k sessions, ≈ 180k attempts** (~25 MB db).
- Heavy (30 rounds/day): ≈ **55k sessions, ≈ 550k attempts** (~130 MB db over ~10 yrs).

SQLite stores millions of rows without blinking — **row count is not the problem; our query
shapes are.** Four existing queries in [Daos.kt](app/src/main/java/be/drakarah/intonation/data/Daos.kt)
degrade badly, and coaching would add more of the same:

- **`positionAccuracy` (l.39–45) — the worst.** A `GROUP BY` aggregate over the *entire*
  `attempts` table, returned as a **`Flow`**. Room re-executes it on **every insert to
  `attempts`**, i.e. a full 180k–550k-row scan several times per round, forever. No index.
- **`attemptsOnSameDay` (l.23)** and **`practiceDays` (l.20)** wrap the indexed timestamp column
  in `date(...)`, which **defeats the index** → full scans, run every round for achievements/streak.
- **`averageCentsBetween` (l.29–34)** range-scans `sessions` with no composite index on
  `(exerciseType, startedAt)`.

If we only add columns (v4) and leave these, we ship the bottleneck. So v4 fixes the model.

### The architecture: raw detail + incremental rollups + real indexes

**Principle.** `attempts`/`sessions` are the immutable source of truth (detail view + export),
and are **only ever read by indexed, bounded access** — by `sessionId`, or by a date range for a
specific window. **No unbounded aggregate ever scans them.** All aggregate/coaching/progress
reads hit small **rollup tables** maintained *incrementally* at round completion. So read cost
scales with the *time window shown*, not with *total history*; write cost is O(prompts-per-round).

**1. Denormalize the day at write time.** Add `epochDay: Int` (days since epoch, local tz) to
`sessions` and `attempts`, computed in Kotlin when the row is built. Every date-bucketed query
then filters an **indexed integer**, never `date()`. Kills the three `date()` scans.

**2. Rollup table `daily_stats`** — the coaching/progress read surface. One row per
`(epochDay, exerciseType, mode, positionId)`; store **sums + counts, not averages** so they
compose:
`attemptCount, scoredCount, sessionCount, sumAbsCents, sumSqAbsCents, sumSignedCents, cleanCount,
timeoutCount, wrongNoteCount, wrongOctaveCount, firstTryCount (retryCount==0), sumRetries,
sumTimeToStableMs, sumEnergy, sumHeldMs, sumResets, sumSteadiness, sumWobbleCents`.
**Cents sums accumulate over `SCORED` attempts only** — divide by `scoredCount`, never
`attemptCount` — so wrong-note/octave/timeout mistakes never pollute intonation (see
"Data integrity"). Averages derive as `sum/count`; **variance/consistency** derives from
`sumSq` (`E[x²]−E[x]²`) — you cannot recover spread from a stored mean, and "becoming more
consistent" is central to the coaching vision, so `sumSqAbsCents` goes in now.
Row cardinality is tiny and bounded: 4 exercises × 2 modes × ~6 positions ≈ 48 combos/day,
realistically ~10 populated/day → **≈ 18k–35k rollup rows over 5 years**, indexed by `epochDay`.
Coaching "last week vs this week" = an indexed range scan over a few hundred rows.

**3. Maintain rollups incrementally** in `SessionRepository.recordCompletedRound`, inside the
same `@Transaction` that inserts the round: for each `(exercise,mode,position)` bucket in the
round, `UPSERT daily_stats … SET sumX = sumX + :delta` (SQLite `ON CONFLICT … DO UPDATE`).
No history scan, ever.

**4. Re-point existing reads at rollups / indexes:**
- `positionAccuracy` → `SELECT positionId, SUM(sumAbsCents)/SUM(attemptCount)… FROM daily_stats
  WHERE exerciseType=… GROUP BY positionId` (scans the small rollup, still a `Flow`, now cheap).
- `averageCentsBetween` → sum over `daily_stats` by `epochDay` range.
- `attemptsOnSameDay` / `totalAttempts` → `SUM(attemptCount)` over `daily_stats` (small);
  `practiceDays`/streak → `SELECT DISTINCT epochDay FROM daily_stats`.

**5. Add the missing indexes** regardless: composite `(exerciseType, epochDay)` on `sessions`
and `attempts`; keep the existing `sessionId` index (drives detail + export-by-session).

**6. Export/import at scale** — see Part B: stream + gzip so a 130 MB db doesn't become a
300 MB in-memory JSON string.

**Escape hatch, not needed day one:** because rollups already hold every aggregate, we *can*
later prune raw `attempts` older than N years without losing coaching history. Design leaves the
door open; no pruning ships now.

## Part A — Enrich per-round metrics (DB v3 → v4)

Hybrid storage: typed queryable columns for what we aggregate/coach on, plus one nullable JSON
column per table for the forward-compatible long tail (avoids a migration per future metric).

**New columns on `attempts`** (all nullable → safe `ALTER TABLE ADD COLUMN`):
- `epochDay: Int?` (scale, above) — also backfilled from `timestamp` in the migration.
- `outcome: String?` — musical result classification: `SCORED | WRONG_NOTE | WRONG_OCTAVE | TIMEOUT`.
  The VMs **already compute** `wrongNote`/`wrongOctave` ([NoteAccuracyViewModel.kt:338](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L338))
  and drop them at persistence today — thread them into this column instead. This is what lets
  coaching keep genuine mistakes *out of* the intonation average (see "Data integrity" below).
  Orthogonal to `quality` (CLEAN/SHAKY = detection confidence, meaningful when `outcome=SCORED`).
- `energyLevel: Float?` — note loudness/confidence (`CapturedPitch.energyLevel`, already computed).
- `retryCount: Int?` — discarded/re-armed captures before the scored one (NoteAccuracy wrong-note
  filter + `ArpeggioCapture` already re-arm; surface the count). The "took three tries" signal.
- `sustainHeldMs: Long?` = `SustainResult.bestHeldMs`; `sustainResets: Int?` = `.resets`;
  `steadinessCents: Float?` = `.steadinessCents` — all computed today and dropped at
  [SustainViewModel.kt:275](app/src/main/java/be/drakarah/intonation/ui/sustain/SustainViewModel.kt#L275).
- `captureWobbleCents: Float?` — **cross-game pitch wobble**, nearly free. `AttemptCapture.freeze`
  already receives the stability window and `stableWindow()` already computes its cents spread
  ([AttemptCapture.kt:309](app/src/main/java/be/drakarah/intonation/game/AttemptCapture.kt#L309))
  to *gate* stability, then discards it. Return spread/MAD of the frozen window on `CapturedPitch`
  and persist it, giving Note Accuracy / Shift / Chords a steadiness dimension (not just Sustain).
  Caveats: bounded by the stability band on CLEAN freezes (small range), wider/richer on SHAKY
  freezes; it's micro-steadiness over a short window at the moment of freeze, weaker than Sustain's
  long-held-window `steadinessCents` — an extra signal, not a replacement.
- `extrasJson: String?` — per-attempt overflow (chord tone role, shift glide detail, and the
  **signed cents of the discarded/wrong tries** before the scored one — so "she kept landing a
  semitone flat before correcting" is answerable, not just the `retryCount` number).

**New columns on `sessions`:** `epochDay: Int?`, and `contextJson: String?` (a4Hz,
micSensitivity, difficulty, roundLength, mins-since-tune-up, mins-since-calibration,
`BuildConfig.VERSION_CODE`) — explanatory context we read back but don't filter rounds by.

**Migration & schema.** Bump `IntonationDatabase` to v4; `MIGRATION_3_4` adds the columns
(mirrors `MIGRATION_2_3`), **backfills `epochDay`** for existing rows, **creates `daily_stats`
+ indexes**, and **backfills `daily_stats` once** from existing attempts (a single one-time
aggregate scan — acceptable at migration time). Update entities in
[Entities.kt](app/src/main/java/be/drakarah/intonation/data/Entities.kt).

**Plumbing the values in (through the domain layer, not the VMs).** Each exercise's game result
already holds (or can cheaply carry) these values. Per the architecture section, the VM assembles a
neutral `RoundRecord` (prompts + game results + `RoundContext`) and hands it to `RoundRecorder`;
**all** of `epochDay`/`outcome`/`contextJson` derivation, entity mapping, and the rollup upsert live
**once** in the domain layer — never duplicated across the four VMs. This *replaces* the four inline
`AttemptEntity(...)` builders that currently sit in the VMs (which is where the entity-building
business logic is extracted *out of* presentation):
[NoteAccuracyViewModel.kt:418](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L418),
[SustainViewModel.kt:275](app/src/main/java/be/drakarah/intonation/ui/sustain/SustainViewModel.kt#L275),
[ShiftViewModel.kt:309](app/src/main/java/be/drakarah/intonation/ui/shift/ShiftViewModel.kt#L309),
[ChordsViewModel.kt:321](app/src/main/java/be/drakarah/intonation/ui/chords/ChordsViewModel.kt#L321).

## Data integrity — student mistakes vs app mistakes (never punish for detection errors)

Two very different "mistakes" must be kept apart, or coaching will mislead:

**App / detection mistakes — already defended, keep them out of coaching.** `PitchGate`'s
octave-up correction plus `NoteAccuracyViewModel.onCaptured`
([l.264](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L264))
already *discard and keep listening* on ring-over, too-soon, harmonic-artifact, unplayable, and
flimsy (SHAKY / low-energy) captures — these never reach the DB, so they can't punish. We preserve
that. Optionally persist discards as **flagged, non-scoring** rows (excluded from every coaching
aggregate) purely so we can audit whether the gate drifts too aggressive/lenient over time — a
long-term safety net for the student, not a score input.

**Student mistakes — classify, count separately, exclude from intonation.** The VMs already
compute `wrongNote`/`wrongOctave`
([l.338](app/src/main/java/be/drakarah/intonation/ui/noteaccuracy/NoteAccuracyViewModel.kt#L338),
boundary = `WRONG_NOTE_CENTS`) but **drop the classification at persistence** — so today a
confident wrong note is stored as a huge `centsError`, indistinguishable from a badly-flat *right*
note, and would wreck any average. Fix: persist `outcome` (above); then in the rollup, **cents
sums include `SCORED` attempts only**, while `wrongNoteCount` / `wrongOctaveCount` / `timeoutCount`
are their own dimension (note-finding, octave awareness, follow-through) — real coaching signal,
just never folded into "how in-tune are you." A right-but-flat note (within `WRONG_NOTE_CENTS`)
still counts as intonation, as it should.

**Guiding principle: bias toward "not the student's fault."** Thresholds are provisional, so
separation is imperfect; when uncertain, exclude from intonation rather than log a maybe-bogus
error. Optionally winsorize/cap scored cents in the mean so a single near-`WRONG_NOTE_CENTS`
miss can't dominate a day.

**Known subtlety for the coaching phase (not solved now):** `PitchGate`'s octave-up correction and
the `ignoreWrongOctave` practice-aid can *mask* a genuine student octave error by snapping it onto
the target. So `WRONG_OCTAVE` counts are a floor, not a full tally — revisit when building coaching.

**A "confidence score" needs no new schema — store the ingredients, derive the score later.** A
continuous 0..1 confidence is a *formula*, and it comes in two flavours: (a) **per-capture detection
confidence** — a weighted blend of `energyLevel`, `captureWobbleCents`, `quality`, `retryCount`,
`timeToStableMs` (all already stored) — which lets coaching **weight** attempts smoothly instead of
the binary `SCORED`/excluded cliff; and (b) **per-note mastery confidence** — a recency-weighted
(EWMA) blend of accuracy + consistency (`sumSq`) + first-try rate + hesitation, i.e. "she's got
Fa♯2 now." Both are derivable at coaching time from data this schema already keeps. We deliberately
**do not** persist a confidence column: the weighting is provisional, and freezing a provisional
formula into storage is exactly what forces a re-migration. Raw attempts retain every input, so a
confidence-weighted rollup can be backfilled in one pass later without a schema change.

## Part B — Backup export / import (Settings)

**Format:** gzipped JSON (`.json.gz`). Text compresses ~10× → a heavy 130 MB db exports as a
handful of MB. Envelope: `{ format:"intonation-trainer-backup", backupVersion:1, dbSchemaVersion:4,
appVersionCode, exportedAt, data:{ sessions:[{session, attempts:[…]}], personalBests, achievements } }`.
`daily_stats` is **not** exported — it's derived; rebuild it on import (cheap, one pass). Use
dedicated `@Serializable` DTOs (not `@Entity` classes) so a future migration can't silently
change the on-disk format.

**Streaming, not one big String.** Export writes the JSON directly to a
`GZIPOutputStream` while **paging attempts by session** (`getAttemptsForSession(id)`), so peak
memory stays flat regardless of history size. Import reads/parses the stream and inserts in
batched transactions.

**New `BackupService` (`metrics/` domain) + `@Serializable` DTOs (`data/`).** Merge/validation/
serialization logic is domain; the DTOs and the DAO reads/writes it drives are `data/`; the Settings
action calls `BackupService` only (no game/VM coupling).
- `exportTo(outputStream)` — page sessions, nest their attempts, write incrementally.
- `import(inputStream, mode ∈ {MERGE, REPLACE})`:
  - Validate `format`, `backupVersion`, `dbSchemaVersion ≤ current` (refuse newer; accept older,
    null-fill new columns).
  - **REPLACE:** `db.clearAllTables()`, insert everything (session→new id→re-key its attempts;
    upsert PBs; insert achievements), then **rebuild `daily_stats`**.
  - **MERGE:** skip sessions already present, deduped by `(epochDay, exerciseType, configKey,
    totalScore)`; insert the rest re-keyed; PBs/achievements upsert (best score / earliest
    unlock); **incrementally fold merged rounds into `daily_stats`**. One `@Transaction`, batched.
- New "get-all / by-session" DAO reads for export; batched inserts for import; `clearAllTables()`
  is built in.

**Settings UI ([SettingsScreen.kt](app/src/main/java/be/drakarah/intonation/ui/settings/SettingsScreen.kt)):**
- **Export** → write to `getExternalFilesDir(null)/backups/…json.gz`, share via existing
  `FileProvider` (`${applicationId}.fileprovider`) + `ACTION_SEND`, exactly like
  [RecordingsScreen.kt:311](app/src/main/java/be/drakarah/intonation/ui/recordings/RecordingsScreen.kt#L311).
- **Import** → `ActivityResultContracts.OpenDocument`, then a **Merge / Replace** dialog (Replace
  gets a destructive confirmation). Summary snackbar ("Imported 42 sessions, skipped 3 dup.").
- Wire `BackupService` + `RoundRecorder` into [AppContainer.kt](app/src/main/java/be/drakarah/intonation/di/AppContainer.kt)
  + the relevant VM factories.

## Questions this data can answer — and the gaps

Sanity-check of the schema against the coaching questions a teacher/student would actually ask.
✓ = answerable; **R** = needs a windowed scan of raw `attempts` (cheap, bounded by the
`(exerciseType, epochDay)` index — fine for "this week vs last"); rollup = cheap at any range.

**Intonation**
- Overall accuracy trend over time (avg |cents|/day/week) — ✓ rollup
- Systematic sharp/flat bias — ✓ rollup (`sumSignedCents`)
- Consistency trend ("becoming more consistent") — ✓ rollup (`sumSqAbsCents` → variance)
- Accuracy per position / per string / arco vs pizz — ✓ rollup
- Accuracy per note ("Fa♯2 last week vs this week") — R (filter `targetMidi` in a window)
- First note of round vs the rest — R (`promptIndex`)
- Intra-session drift (tires/drifts across a session) — R (signed-cents split by `promptIndex`/time)
- Interval-size effect (big shifts less accurate) — ✓ `|targetMidi−startMidi|` vs cents

**Timing / effort / cleanliness**
- Settle-time & reaction-time trend ("settled faster today") — ✓ rollup (`sumTimeToStable`)
- Shift landing-time trend — ✓ rollup
- Retries-per-note ("took 3 tries → first try 3×") — ✓ rollup (`firstTryCount`) + R detail
- Clean / shaky / timeout & give-up rates — ✓ rollup
- Tentative-play correlation (low `energyLevel` ↔ flat) — R (`energyLevel` vs cents)

**Bow control / steadiness** — sustain hold-duration, steadiness, reset trends — ✓ rollup;
cross-game pitch wobble at the moment of freeze (arco Note Accuracy / Shift / Chords, not just
sustain) — ✓ rollup (`sumWobbleCents`, from `captureWobbleCents`)

**Practice behaviour** — streak, volume, time-of-day, exercise mix, difficulty progression,
PBs, achievements — ✓ (rollup + `contextJson` + existing tables)

**Data-quality context** — was calibration/tune-up stale during a bad session — ✓ `contextJson`

**Gaps we are closing now** (cheap here, a painful re-migration later — matches the
"proper now" priority): `sumSqAbsCents` in the rollup (consistency) and discarded-try cents in
`extrasJson` (what the wrong tries were). Both folded into the schema above.

**Gaps we are consciously NOT closing** (each for a distinct, deliberate reason — call out, don't build):
- *Post-onset pitch trajectory* (does a non-sustain note drift after it's frozen) — a value call,
  declined: it needs new sampling *after* freeze + a time-series per attempt for unproven value,
  and Sustain already covers held-pitch steadiness. (Note: `captureWobbleCents` above already adds
  a free pre-freeze steadiness signal, which is the cheaper part of this.)
- *Tempo / rhythm* — no exercise has a rhythmic dimension, so there's no ground truth to score
  against; this waits on a rhythm-training mode that doesn't exist, not on storage.
- *Peer / population norms* — the app is fully local/offline (no backend, no population) and
  uploading practice data is a consent/Play-store-disclosure step we don't want; the whole
  Teacher's-notebook framing is deliberately "no leaderboard" (you vs your past self).
- *Ear-training / pitch-memory data* — a distinct signal the current exercises don't produce;
  waits for the backlogged "Guess First" mode.

## Appendix: double bass coaching dimensions vs. what we can measure

What a teacher actually watches, checked against our audio-only, first-stable-pitch capture.
**✓** coachable now · **◐** partial/indirect proxy · **✗** out of reach (reason given). None of the
✗ items are *storage* gaps — they need a camera, spectral analysis, or an exercise that doesn't
exist, so capturing more now wouldn't help.

- **A. Posture & physical setup** — ✗ all (needs video): posture/breathing, bass height/angle/
  endpin/balance, shoulder-neck tension, left-hand frame shape & thumb, bow hold, arm height.
- **B. Left hand & intonation** (strongest area): overall accuracy ✓ `|cents|`; sharp/flat bias ✓
  signed cents; weak notes/positions/strings ✓; arco-vs-pizz ✓ `mode`; consistency ✓ `sumSq`;
  shift landing accuracy ✓; shift direction & distance ✓ (`startMidi` vs target); shift glide
  quality ◐ (filtered out — would stash in `extrasJson`); land-then-fix behaviour ◐ (`retryCount`
  + discarded cents); fingering/hand-frame spacing ◐ (only inferable — we know the note not the
  finger); **vibrato ✗** (freeze-first-stable; conflicts with wobble; not her level yet); finger
  pressure/independence ✗ physical.
- **C. Bow (arco)**: long tones ✓ sustain hold; tone steadiness ◐ `steadinessCents` +
  `captureWobbleCents` (pitch proxy, not tone quality); bow changes ◐ sustain `resets`;
  straight-bow/contact-point/sounding-point ✗ physical+spectral; bow distribution ✗; dynamics ◐
  `energyLevel` (crude); string crossings ◐ (cross-string shifts, accuracy only); articulation ✗
  (no such exercise).
- **D. Pizz**: intonation & consistency ✓ `mode`; **plucking location ✗** (tonal/spectral +
  physical, not in the pitch); attack/tone consistency ◐ `energyLevel`; note-length/decay control
  ✗ for now (backlog).
- **E. Rhythm & timing** — ✗ (no rhythmic exercise): tempo/pulse/subdivision ✗; target-response
  speed ◐ `reactionTimeMs` (pitch-finding, not rhythm).
- **F. Musicianship & reading** — ✗: phrasing, musical dynamics, sight-reading, style, memory.
- **G. Ear / aural**: in-tune-with-drone ◐ (Drone mode, unscored); drift off inner reference ◐
  (`DriftDetector` / signed-cents series); interval & pitch recognition ✗ (waits for "Guess First");
  tuning the instrument ◐ (Tune-up, `lastTunedAt`, unscored).
- **H. Practice process** (other strong area): consistency/streak ✓; volume/frequency ✓; warm-up
  discipline ✓ `contextJson`; progress & retention ✓ trends; balanced coverage ✓ exercise/position
  mix; readiness to advance ✓ (`LevelAdvisor` + accuracy); intra-session fatigue ◐ (drift +
  `promptIndex`); confidence ◐ `energyLevel`; error recovery ◐ (`retryCount`/`resets`/first-try);
  time-of-day patterns ✓ `timestamp`.

**Headline:** we can coach longitudinally on the two things a tuner can't — **intonation (every
angle)** and **practice process** — plus proxy signals for bow/pizz steadiness. Physical technique
and rhythm/reading/expression are out of scope without new hardware or new exercises, not without
more storage. The v4 schema above carries **every ✓ signal and most ◐ proxies**; a few ◐ items
stay optional and unbuilt (shift-glide detail in `extrasJson`; scoring Drone/Tune-up as attempts).

## Out of scope (explicit follow-ups)

- **Coaching engine** — this plan only *feeds* it; the rollup schema is designed so it can read
  windows cheaply. Add a `CoachingRepository` over `daily_stats` later.
  - *Started 2026-07-16:* the Progress-screen redesign proved out the read surface — windowed
    rollup reads (`DailyStatsDao.windowAgg` / per-position accuracy split by mode) feed a pure
    `metrics/Coaching.kt` (mastery bands, sharp/flat bias, week trend, one coaching insight),
    gated by a minimum sample size. Thresholds are provisional. A dedicated `CoachingRepository`
    can still consolidate these reads when the wider coaching feature lands.
- **Finer rollup dimensions** (per-note `daily_note_stats`, shift-direction) — add when coaching
  needs them; raw attempts + `epochDay` index make backfilling a new rollup a one-pass job, not a
  redesign.
- **Abandoned/quit rounds** — needs the aggregate queries to filter `completed=1`/route via
  rollups first (this plan makes that safe); still deferred.
- **Post-game "how did it go?"** (selected TESTING.md line) — belongs to the trace tool, separate.

## Verification

- **Pure-domain unit tests (no Android)** — the `metrics/` layer is tested with synthetic
  `RoundRecord`s exactly like `game/` today: `RoundRecorder` classification (booleans → `outcome`),
  `DailyStatsFold` correctness, derived confidence/consistency functions. No Room, no ViewModel.
- **Integration tests** (in-memory Room, corpus style already used in `:app`):
  - `BackupRoundTripTest` — export → import into empty db → row-for-row equality, FKs intact,
    `daily_stats` rebuilt correctly.
  - `BackupMergeTest` — dedup skips existing session, keeps local rounds, PB upserts to better score.
  - `Migration3to4Test` — open a v3 db with rows, run `MIGRATION_3_4`, assert new columns exist,
    old rows survive, `epochDay` + `daily_stats` backfilled.
  - **`RollupScaleTest`** — seed ~50k synthetic attempts, assert (a) `positionAccuracy`/weekly-avg
    now read `daily_stats` (query plan / timing sanity), (b) round insert + rollup upsert time is
    flat vs. a 1k-row db. This directly answers the scaling concern.
  - **`MistakeExclusionTest`** — a round mixing SCORED, a confident WRONG_NOTE, a WRONG_OCTAVE, and
    a TIMEOUT; assert the intonation average uses `scoredCount` only (the wrong note's huge cents
    does **not** move avg-cents), while `wrongNoteCount`/`wrongOctaveCount`/`timeoutCount` tally
    correctly. Guards the "don't punish for mistakes" invariant.
- Build + test: `$env:JAVA_HOME=…jbr`; `.\gradlew.bat :app:testDebugUnitTest :dsp:testDebugUnitTest :app:assembleDebug`.
- **On her Pixel 6a** (logcat; emulator quickboot reverts userdata): one round of each exercise →
  confirm new columns populate and `daily_stats` updates; progress screen still correct. Export →
  share sheet + valid `.json.gz`. Import Merge → "skipped N duplicates"; Replace → confirm dialog
  then data matches.
- Add **Pending** items to TESTING.md for the on-phone checks (→ Verified/dated on confirm);
  keep FEATURES.md in sync (Settings backup section).
