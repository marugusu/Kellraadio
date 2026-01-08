package ee.minu.kellraadio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// MUUDATUS: Versioon 2 -> 3
@Database(entities = [RadioStation::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raadio_database"
                )
                    .fallbackToDestructiveMigration() // See rida lubab versiooni muutusel andmebaasi uuendada
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}