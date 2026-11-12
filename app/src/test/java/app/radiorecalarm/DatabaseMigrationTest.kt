package app.radiorecalarm

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun migration9to10_addsRecordAudioColumnToAlarmsTable() {
        val executedSql = mutableListOf<String>()

        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_9_10.migrate(db)

        assertTrue(
            "Expected ALTER TABLE alarms ADD COLUMN recordAudio to be executed",
            executedSql.any { it.contains("ALTER TABLE alarms ADD COLUMN recordAudio INTEGER NOT NULL DEFAULT 0") }
        )
    }
}
