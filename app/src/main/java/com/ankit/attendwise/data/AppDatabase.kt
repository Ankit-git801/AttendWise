package com.ankit.attendwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Subject::class, ClassSchedule::class, AttendanceRecord::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Ensure System Subject exists for holidays
                db.execSQL("INSERT OR IGNORE INTO subjects (id, name, color, targetAttendance, lastUpdated) VALUES ('0', 'System', '#FFC107', 0, ${System.currentTimeMillis()})")

                // 1b. SANITIZATION: Delete orphaned records that don't point to a valid subject
                // This prevents Foreign Key constraint violations during migration.
                db.execSQL("DELETE FROM attendance_records WHERE subjectId NOT IN (SELECT id FROM subjects)")

                // 2. Create new table with Foreign Key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attendance_records_new` (
                        `id` TEXT NOT NULL, 
                        `subjectId` TEXT NOT NULL, 
                        `scheduleId` TEXT NOT NULL, 
                        `date` INTEGER NOT NULL, 
                        `isPresent` INTEGER NOT NULL, 
                        `note` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `lastUpdated` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())

                // 3. Copy data
                db.execSQL("""
                    INSERT INTO `attendance_records_new` (`id`, `subjectId`, `scheduleId`, `date`, `isPresent`, `note`, `type`, `lastUpdated`)
                    SELECT `id`, `subjectId`, `scheduleId`, `date`, `isPresent`, `note`, `type`, `lastUpdated` FROM `attendance_records`
                """.trimIndent())

                // 4. Drop old table and rename
                db.execSQL("DROP TABLE `attendance_records`")
                db.execSQL("ALTER TABLE `attendance_records_new` RENAME TO `attendance_records`")

                // 5. Re-create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_subjectId` ON `attendance_records` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_date` ON `attendance_records` (`date`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_type_isPresent` ON `attendance_records` (`type`, `isPresent`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_subjectId_type_isPresent` ON `attendance_records` (`subjectId`, `type`, `isPresent`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendwise_db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    // Ensure Foreign Key constraints (like CASCADE DELETE) are enabled
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize with Holiday subject to satisfy Foreign Key constraints
                            db.execSQL("INSERT OR IGNORE INTO subjects (id, name, color, targetAttendance, lastUpdated) VALUES ('0', 'System', '#FFC107', 0, ${System.currentTimeMillis()})")
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys=ON;")
                            // ENSURE SYSTEM SUBJECT EXISTS (Critical for Foreign Key integrity on holidays)
                            db.execSQL("INSERT OR IGNORE INTO subjects (id, name, color, targetAttendance, lastUpdated) VALUES ('0', 'System', '#FFC107', 0, ${System.currentTimeMillis()})")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
