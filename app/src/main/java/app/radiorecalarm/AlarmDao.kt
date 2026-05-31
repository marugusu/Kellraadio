package app.radiorecalarm

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    /**
     * Lisab uue äratuse andmebaasi. Kui sellise ID-ga äratus on juba olemas,
     * siis asendatakse see uuega. Tagastab uue äratuse ID (Long).
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    /**
     * Uuendab olemasoleva äratuse andmeid.
     * Room leiab õige rea äratuse 'id' välja järgi.
     */
    @Update
    suspend fun update(alarm: Alarm)

    /**
     * Kustutab äratuse andmebaasist.
     */
    @Delete
    suspend fun delete(alarm: Alarm)

    /**
     * Küsib andmebaasist KÕIK äratused ja tagastab need nimekirjana.
     * Tulemus on Flow, mis tähendab, et kui andmed muutuvad,
     * uuendatakse UI automaatselt. Sorteerime äratused kellaaja järgi.
     */
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    /**
     * Küsib andmebaasist ühe konkreetse äratuse selle ID järgi.
     * Seda on vaja näiteks äratuse käivitumisel AlarmReceiveris.
     */
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    /**
     * Küsib kõik äratused, mis on sisse lülitatud (isEnabled = true).
     * Seda on vaja BootReceiveris, et taastada ainult aktiivsed äratused.
     */
    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getAllEnabledAlarms(): List<Alarm>

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    suspend fun getAllAlarmsList(): List<Alarm>
}
