package be.drakarah.intonation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, AttemptEntity::class, PersonalBestEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IntonationDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun personalBestDao(): PersonalBestDao

    companion object {
        fun build(context: Context): IntonationDatabase =
            Room.databaseBuilder(context, IntonationDatabase::class.java, "intonation.db")
                .build()
    }
}
