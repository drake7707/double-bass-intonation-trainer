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
        AchievementEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class IntonationDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun personalBestDao(): PersonalBestDao
    abstract fun achievementDao(): AchievementDao

    companion object {
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

        fun build(context: Context): IntonationDatabase =
            Room.databaseBuilder(context, IntonationDatabase::class.java, "intonation.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
