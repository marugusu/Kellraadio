package ee.minu.kellraadio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// --- MUUDATUSED ---
// 1. Lisasime 'Alarm::class' entities nimekirja.
// 2. Tõstsime versiooni 4 -> 5.
// 3. Lisasime @TypeConverters(Converters::class), et Room oskaks Set<Int> käsitleda.
@Database(entities = [RadioStation::class, HistoryItem::class, Alarm::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao
    abstract fun historyDao(): HistoryDao

    // --- UUS FUNKTSIOON ---
    // See annab meile ligipääsu AlarmDao liidesele.
    abstract fun alarmDao(): AlarmDao

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
                    // fallbackToDestructiveMigration tähendab, et kui versioon muutub
                    // (nagu praegu 4->5), siis vana andmebaas kustutatakse ja luuakse
                    // uus, tühi. Arengu faasis on see OK.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}