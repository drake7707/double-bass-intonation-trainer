package be.drakarah.intonation.data

import androidx.room.withTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** How an imported backup interacts with data already on the device. */
enum class ImportMode { MERGE, REPLACE }

data class ImportSummary(
    val importedSessions: Int,
    val skippedSessions: Int,
    val importedPersonalBests: Int,
    val importedAchievements: Int,
)

/** Backup export/import. Reads/writes the DB and streams gzipped JSON (text compresses ~10×, so a
 * large history stays a few MB, and we never build a giant in-memory String). Invoked only by a
 * thin Settings action — never wired into a game ViewModel. The derived `daily_stats` rollup is not
 * stored; it is rebuilt from the raw rows after import. */
@OptIn(ExperimentalSerializationApi::class)
class BackupService(private val db: IntonationDatabase) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Serializes the whole history to [out] as gzipped JSON. Caller supplies app metadata. */
    suspend fun export(out: OutputStream, appVersionCode: Int, exportedAt: Long) {
        val attemptsBySession = db.sessionDao().allAttempts().groupBy { it.sessionId }
        val sessions = db.sessionDao().allSessions().map { s ->
            s.toBackup(attemptsBySession[s.id] ?: emptyList())
        }
        val envelope = BackupEnvelope(
            format = BACKUP_FORMAT,
            backupVersion = BACKUP_VERSION,
            dbSchemaVersion = CURRENT_DB_SCHEMA,
            appVersionCode = appVersionCode,
            exportedAt = exportedAt,
            data = BackupData(
                sessions = sessions,
                personalBests = db.personalBestDao().allBests().map {
                    BackupPersonalBest(it.configKey, it.score, it.maxScore, it.achievedAt)
                },
                achievements = db.achievementDao().allAchievements().map {
                    BackupAchievement(it.achievementId, it.unlockedAt)
                },
            ),
        )
        GZIPOutputStream(out).use { gz -> json.encodeToStream(envelope, gz) }
    }

    /** Reads a backup from [input] and applies it. MERGE keeps existing data and adds only rounds
     * not already present; REPLACE wipes everything first. Rebuilds `daily_stats` either way. */
    suspend fun import(input: InputStream, mode: ImportMode): ImportSummary {
        val envelope = GZIPInputStream(input).use { gz -> json.decodeFromStream<BackupEnvelope>(gz) }
        require(envelope.format == BACKUP_FORMAT) { "This file is not an Intonation Trainer backup." }
        require(envelope.backupVersion <= BACKUP_VERSION && envelope.dbSchemaVersion <= CURRENT_DB_SCHEMA) {
            "This backup is from a newer version of the app. Please update first."
        }

        // clearAllTables runs its own transaction and must not be nested inside withTransaction.
        if (mode == ImportMode.REPLACE) db.clearAllTables()

        var importedSessions = 0
        var skippedSessions = 0
        var importedBests = 0
        var importedAchievements = 0

        db.withTransaction {
            val seen = if (mode == ImportMode.MERGE)
                db.sessionDao().allSessions().mapTo(mutableSetOf()) { dedupKey(it.epochDay, it.exerciseType, it.configKey, it.totalScore) }
            else mutableSetOf()

            for (s in envelope.data.sessions) {
                val key = dedupKey(s.epochDay, s.exerciseType, s.configKey, s.totalScore)
                if (mode == ImportMode.MERGE && !seen.add(key)) { skippedSessions++; continue }
                val sessionId = db.sessionDao().insertSession(s.toEntity())
                db.sessionDao().insertAttempts(s.attempts.map { it.toEntity(sessionId) })
                importedSessions++
            }

            for (pb in envelope.data.personalBests) {
                val current = db.personalBestDao().get(pb.configKey)
                if (current == null || pb.score > current.score) {
                    // sessionId is informational (no FK); the source session was re-keyed, so 0.
                    db.personalBestDao().upsert(PersonalBestEntity(pb.configKey, 0, pb.score, pb.maxScore, pb.achievedAt))
                    importedBests++
                }
            }

            val existingUnlocks = db.achievementDao().allAchievements().associate { it.achievementId to it.unlockedAt }
            val freshAchievements = envelope.data.achievements.mapNotNull { a ->
                val existingAt = existingUnlocks[a.achievementId]
                if (existingAt == null || a.unlockedAt < existingAt) AchievementEntity(a.achievementId, a.unlockedAt) else null
            }
            if (freshAchievements.isNotEmpty()) db.achievementDao().upsert(freshAchievements)
            importedAchievements = freshAchievements.size

            // Rebuild the derived rollup from the merged raw rows.
            db.dailyStatsDao().clear()
            db.openHelper.writableDatabase.execSQL(IntonationDatabase.ROLLUP_BACKFILL_SQL)
        }
        return ImportSummary(importedSessions, skippedSessions, importedBests, importedAchievements)
    }

    /** Deduplication identity for a session on MERGE. */
    private fun dedupKey(epochDay: Int?, exerciseType: String, configKey: String, totalScore: Int) =
        "$epochDay|$exerciseType|$configKey|$totalScore"

    companion object {
        /** Must track the `@Database(version=…)` of [IntonationDatabase]. */
        const val CURRENT_DB_SCHEMA = 4
    }
}
