package app.radiorecalarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

// See @Entity ütleb Room'ile, et sellest klassist tuleb andmebaasi tabel nimega "alarms"
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "alarms")
data class Alarm(
    // @PrimaryKey teeb sellest väljast unikaalse võtme.
    // autoGenerate = true tähendab, et Room genereerib ID ise (1, 2, 3...).
    // See on ülioluline, et iga AlarmManageri äratus saaks unikaalse ID.
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val hour: Int,
    val minute: Int,

    // See hoiab nädalapäevi (nt {Calendar.MONDAY, Calendar.TUESDAY}).
    // Kasutame TypeConverter'it, et Room oskaks Set<Int> andmestruktuuri
    // andmebaasi salvestada (tekstina kujul "2,3") ja sealt lugeda.
    val days: Set<Int>,

    val stationName: String,
    val stationUrl: String,

    // See lipp võimaldab äratuse sisse/välja lülitada ilma seda kustutamata.
    val isEnabled: Boolean = true
)

/**
 * See klass "õpetab" Room'ile, kuidas konvertida keerulist andmetüüpi (Set<Int>)
 * lihtsaks andmetüübiks (String), mida see oskab andmebaasi salvestada, ja vastupidi.
 */
class Converters {
    @TypeConverter
    fun fromDaysSet(days: Set<Int>): String {
        // Muudab komplekti {2, 3, 4} tekstiks "2,3,4"
        return days.joinToString(",")
    }

    @TypeConverter
    fun toDaysSet(daysString: String): Set<Int> {
        // Kui tekst on tühi, tagastame tühja komplekti
        if (daysString.isBlank()) return emptySet()
        // Muudab teksti "2,3,4" tagasi komplektiks {2, 3, 4}
        return daysString.split(',').map { it.toInt() }.toSet()
    }
}
