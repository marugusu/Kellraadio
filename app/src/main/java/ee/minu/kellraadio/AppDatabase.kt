package ee.minu.kellraadio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RadioStation::class], version = 2, exportSchema = false) // Versioon 1 -> 2
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
                    .fallbackToDestructiveMigration() // See rida lisatud turvalisusemõttes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}