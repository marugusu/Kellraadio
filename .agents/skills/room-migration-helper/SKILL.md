---
name: room-migration-helper
description: Guides the agent through modifying Room database entities, increasing the database version, writing correct SQLite migration commands, and registering migrations to prevent application crashes and data loss.
---

# Room Database Migration Skill

Use this skill when you need to perform database schema modifications, such as adding new fields to existing models (`Alarm`, `RadioStation`, `HistoryItem`) or introducing new tables in the Kellraadio project.

## Trigger Scenarios

*   The user asks to "save a new state/variable to the database" or "add a field/setting to alarms/stations".
*   You find yourself modifying an entity class annotated with `@Entity` in the project.

## Procedural Walkthrough

### Step 1: Update the Entity Class
1.  Locate the entity file (e.g., `Alarm.kt`, `RadioStation.kt`, `HistoryItem.kt`).
2.  Add the new property to the data class constructor. Ensure that:
    *   If the new field is not nullable, it **must** have a default value (e.g., `val isMuted: Boolean = false`).
    *   Alternatively, make the field nullable (e.g., `val description: String? = null`).

### Step 2: Increment Database Version
1.  Open [AppDatabase.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/ws/ct/radiow/AppDatabase.kt).
2.  Locate the `@Database` annotation.
3.  Increment the `version` property by `1`.
    *   *Example:* If it was `version = 9`, change it to `version = 10`.

### Step 3: Write the Migration Object
In [AppDatabase.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/ws/ct/radiow/AppDatabase.kt)'s `companion object`, write a new `Migration` definition.
*   **Syntax for Adding a Column**:
    ```kotlin
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0")
        }
    }
    ```
    *Note: SQLite does not have a separate Boolean type. Use `INTEGER` with values 0 (false) or 1 (true).*

*   **Syntax for Creating a New Table**:
    Match the SQLite table definitions precisely to the Room generated schemas.
    ```kotlin
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        }
    }
    ```

### Step 4: Register the Migration
In the `getDatabase(context)` builder inside [AppDatabase.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/ws/ct/radiow/AppDatabase.kt):
1.  Locate the `.addMigrations(...)` call.
2.  Append your new migration object to the list.
    *   *Example:* `.addMigrations(MIGRATION_8_9, MIGRATION_9_10)`.

## Verification Checklists

- [ ] Verify that the database compiles successfully without annotation processing errors.
- [ ] Ensure that existing tables keep their user data (e.g., user-added stations, saved alarms) when the application is launched.
- [ ] Confirm no `IllegalStateException: Room cannot verify the data integrity...` crashes happen on app startup.
