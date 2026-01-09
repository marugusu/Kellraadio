package ee.minu.kellraadio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// MUUDATUS: Versioon 3 -> 4, lisatud HistoryItem
@Database(entities = [RadioStation::class, HistoryItem::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao
    abstract fun historyDao(): HistoryDao // UUS

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
                    .fallbackToDestructiveMigration() // Lubame uuesti luua, kui versioon muutub
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}