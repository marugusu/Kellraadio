package ws.ct.radiow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [RadioStation::class, HistoryItem::class, Alarm::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao
    abstract fun historyDao(): HistoryDao
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