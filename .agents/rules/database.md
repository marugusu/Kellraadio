# Kellraadio: Room Database Rules

Guidelines for modifying or extending the local Room persistence layer of the Kellraadio application.

## Core Database Configuration

*   **Database Class**: `AppDatabase.kt` is the central database entry point. It registers all database `@Database` entities, Version, TypeConverters, and migrations.
*   **Existing Entities**:
    1.  `RadioStation` (`stations` table) - Represents standard and user-defined radio channels.
    2.  `HistoryItem` (`history` table) - Represents played track metadata for playback history.
    3.  `Alarm` (`alarms` table) - Represents user-configured waking alarms.

## Rule: Schema & Entities Modification

*   **Field Additions/Removals**: When adding, renaming, or deleting properties in database entity classes (like `RadioStation.kt` or `Alarm.kt`), you **MUST** write an explicit schema migration to prevent database crashes.
*   **Entity Annotations**: Ensure proper primary keys (`@PrimaryKey(autoGenerate = true)` or explicit mapping), indices (`@Index`), and correct column names (`@ColumnInfo`).

## Rule: Room Migrations

*   **Version Increment**: Increment the version number in the `@Database` annotation of `AppDatabase.kt` by `1` when schema changes are introduced.
*   **Migration Classes**: 
    *   Create a private migration object in the `AppDatabase` companion object following the naming style `MIGRATION_X_Y` where X is the previous version and Y is the new version.
    *   Write exact SQLite queries to transform the database schema (e.g. `ALTER TABLE table ADD COLUMN column INTEGER NOT NULL DEFAULT 0`).
    *   Example:
        ```kotlin
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN volume INTEGER NOT NULL DEFAULT 100")
            }
        }
        ```
*   **Register Migrations**: Ensure you register the migration object in the `Room.databaseBuilder` chain using `.addMigrations(MIGRATION_X_Y)`.
*   **Destructive Migration Fallback**: While `.fallbackToDestructiveMigration()` is enabled in the builder, it is only a safety fallback. Relying on destructive migrations is **forbidden** for production releases as it wipes out all user-defined alarms, favorite ordering, and custom radio stations.

## Rule: DAOs & Threading

*   **DAO Functions**: Use Room's Coroutines integration.
    *   Use `Flow<List<T>>` for reactive database queries (Room automatically executes these on background threads and emits changes to observers).
    *   Use `suspend` keyword for simple transactional operations (e.g., `insert`, `update`, `delete`) to ensure they are called from coroutine scopes.
*   **Thread Safety**: Always ensure DAO operations are invoked on background threads (like `Dispatchers.IO`) inside repositories, rather than locking the Main UI thread.
