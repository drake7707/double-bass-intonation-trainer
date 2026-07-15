package be.drakarah.intonation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class, AttemptEntity::class, PersonalBestEntity::class,
        AchievementEntity::class, DailyStatsEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class IntonationDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun personalBestDao(): PersonalBestDao
    abstract fun achievementDao(): AchievementDao
    abstract fun dailyStatsDao(): DailyStatsDao

    companion object {
        /** Local epoch-day of a ms timestamp, in SQL. Must match [epochDayOf] in Kotlin:
         * julianday(localtime) - JD(1970-01-01) truncated = LocalDate.toEpochDay(). */
        private fun localEpochDaySql(msColumn: String) =
            "CAST((julianday($msColumn / 1000, 'unixepoch', 'localtime') - 2440587.5) AS INTEGER)"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS achievements (" +
                        "achievementId TEXT NOT NULL PRIMARY KEY, unlockedAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE attempts ADD COLUMN stringMidi INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN positionId TEXT")
            }
        }

        /** v4: coaching metrics + scale. New nullable columns, denormalized epochDay, and the
         * incrementally-maintained daily_stats rollup (backfilled once here). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. New columns (all nullable → safe).
                db.execSQL("ALTER TABLE sessions ADD COLUMN epochDay INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN contextJson TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN epochDay INTEGER")
                db.execSQL("ALTER TABLE attempts ADD COLUMN outcome TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN energyLevel REAL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN retryCount INTEGER")
                db.execSQL("ALTER TABLE attempts ADD COLUMN sustainHeldMs INTEGER")
                db.execSQL("ALTER TABLE attempts ADD COLUMN sustainResets INTEGER")
                db.execSQL("ALTER TABLE attempts ADD COLUMN steadinessCents REAL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN captureWobbleCents REAL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN extrasJson TEXT")

                // 2. Backfill epochDay (local day) so date-bucketed reads are index-friendly.
                db.execSQL("UPDATE sessions SET epochDay = ${localEpochDaySql("startedAt")}")
                db.execSQL("UPDATE attempts SET epochDay = ${localEpochDaySql("timestamp")}")

                // 3. Backfill a coarse outcome for old rows (no wrong-octave flag was stored;
                //    octave misses fold into WRONG_NOTE). New rows get precise classification.
                db.execSQL(
                    "UPDATE attempts SET outcome = CASE " +
                        "WHEN quality = 'TIMEOUT' OR centsError IS NULL THEN 'TIMEOUT' " +
                        "WHEN ABS(centsError) > 450 THEN 'WRONG_NOTE' " +
                        "ELSE 'SCORED' END"
                )

                // 4. Indexes for the new access paths (sessionId/timestamp already indexed).
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_exerciseType_epochDay` ON `sessions` (`exerciseType`, `epochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_exerciseType_epochDay` ON `attempts` (`exerciseType`, `epochDay`)")

                // 5. Create daily_stats (schema must match the entity for Room's open-time check).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_stats` (" +
                        "`epochDay` INTEGER NOT NULL, `exerciseType` TEXT NOT NULL, " +
                        "`mode` TEXT NOT NULL, `positionId` TEXT NOT NULL, " +
                        "`attemptCount` INTEGER NOT NULL, `scoredCount` INTEGER NOT NULL, " +
                        "`sessionCount` INTEGER NOT NULL, `sumAbsCents` REAL NOT NULL, " +
                        "`sumSqAbsCents` REAL NOT NULL, `sumSignedCents` REAL NOT NULL, " +
                        "`cleanCount` INTEGER NOT NULL, `timeoutCount` INTEGER NOT NULL, " +
                        "`wrongNoteCount` INTEGER NOT NULL, `wrongOctaveCount` INTEGER NOT NULL, " +
                        "`firstTryCount` INTEGER NOT NULL, `sumRetries` INTEGER NOT NULL, " +
                        "`sumTimeToStableMs` INTEGER NOT NULL, `sumEnergy` REAL NOT NULL, " +
                        "`sumHeldMs` INTEGER NOT NULL, `sumResets` INTEGER NOT NULL, " +
                        "`sumSteadiness` REAL NOT NULL, `sumWobbleCents` REAL NOT NULL, " +
                        "PRIMARY KEY(`epochDay`, `exerciseType`, `mode`, `positionId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_stats_epochDay` ON `daily_stats` (`epochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_stats_exerciseType_epochDay` ON `daily_stats` (`exerciseType`, `epochDay`)")

                // 6. Backfill the rollup once from raw attempts (the only full scan, at migrate time).
                db.execSQL(ROLLUP_BACKFILL_SQL)
            }
        }

        /** Rebuilds daily_stats from raw attempts. Used by the migration and by backup import.
         * Cents sums accumulate over SCORED attempts only. */
        internal const val ROLLUP_BACKFILL_SQL =
            "INSERT INTO daily_stats (" +
                "epochDay, exerciseType, mode, positionId, attemptCount, scoredCount, sessionCount, " +
                "sumAbsCents, sumSqAbsCents, sumSignedCents, cleanCount, timeoutCount, wrongNoteCount, " +
                "wrongOctaveCount, firstTryCount, sumRetries, sumTimeToStableMs, sumEnergy, sumHeldMs, " +
                "sumResets, sumSteadiness, sumWobbleCents) " +
                "SELECT a.epochDay, a.exerciseType, s.mode, COALESCE(a.positionId, ''), " +
                "COUNT(*), " +
                "SUM(CASE WHEN a.outcome = 'SCORED' THEN 1 ELSE 0 END), " +
                "COUNT(DISTINCT a.sessionId), " +
                "SUM(CASE WHEN a.outcome = 'SCORED' AND a.centsError IS NOT NULL THEN ABS(a.centsError) ELSE 0 END), " +
                "SUM(CASE WHEN a.outcome = 'SCORED' AND a.centsError IS NOT NULL THEN a.centsError * a.centsError ELSE 0 END), " +
                "SUM(CASE WHEN a.outcome = 'SCORED' AND a.centsError IS NOT NULL THEN a.centsError ELSE 0 END), " +
                "SUM(CASE WHEN a.quality = 'CLEAN' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN a.outcome = 'TIMEOUT' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN a.outcome = 'WRONG_NOTE' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN a.outcome = 'WRONG_OCTAVE' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN a.retryCount = 0 THEN 1 ELSE 0 END), " +
                "SUM(COALESCE(a.retryCount, 0)), " +
                "SUM(COALESCE(a.timeToStableMs, 0)), " +
                "SUM(COALESCE(a.energyLevel, 0)), " +
                "SUM(COALESCE(a.sustainHeldMs, 0)), " +
                "SUM(COALESCE(a.sustainResets, 0)), " +
                "SUM(COALESCE(a.steadinessCents, 0)), " +
                "SUM(COALESCE(a.captureWobbleCents, 0)) " +
                "FROM attempts a JOIN sessions s ON a.sessionId = s.id " +
                "WHERE s.completed = 1 AND a.epochDay IS NOT NULL " +
                "GROUP BY a.epochDay, a.exerciseType, s.mode, COALESCE(a.positionId, '')"

        fun build(context: Context): IntonationDatabase =
            Room.databaseBuilder(context, IntonationDatabase::class.java, "intonation.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
