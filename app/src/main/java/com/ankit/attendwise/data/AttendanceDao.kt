/*
 * Copyright (c) 2026 Ankit. All rights reserved.
 */

package com.ankit.attendwise.data

import androidx.room.*
import com.ankit.attendwise.models.AttendanceRecordWithSubject
import com.ankit.attendwise.models.AttendanceStatistics
import com.ankit.attendwise.models.SubjectWithAttendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // --- Subject Queries ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: Subject): Long

    @Update
    suspend fun updateSubject(subject: Subject)

    @Transaction
    suspend fun upsertSubject(subject: Subject) {
        val id = insertSubject(subject)
        if (id == -1L) {
            updateSubject(subject)
        }
    }

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Query("SELECT * FROM subjects WHERE id != :holidayId ORDER BY name ASC")
    fun getAllSubjects(holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    suspend fun getSubjectById(subjectId: String): Subject?

    @Query("SELECT COUNT(*) FROM subjects WHERE id != :holidayId")
    suspend fun getSubjectCount(holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY): Int

    @Query("DELETE FROM subjects WHERE id = :subjectId AND id != :holidayId")
    suspend fun deleteSubjectById(subjectId: String, holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY)

    @Transaction
    suspend fun deleteSubjectAtomic(subjectId: String) {
        deleteAttendanceRecordsForSubject(subjectId)
        deleteSchedulesForSubject(subjectId)
        deleteSubjectById(subjectId)
    }

    @Query("DELETE FROM subjects WHERE id != :holidayId")
    suspend fun deleteAllSubjects(holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY)


    // --- Schedule Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ClassSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: ClassSchedule)

    @Query("SELECT * FROM class_schedules")
    fun getAllSchedules(): Flow<List<ClassSchedule>>

    @Query("SELECT * FROM class_schedules WHERE subjectId = :subjectId")
    fun getSchedulesForSubjectFlow(subjectId: String): Flow<List<ClassSchedule>>

    @Query("SELECT * FROM class_schedules WHERE subjectId = :subjectId")
    suspend fun getSchedulesForSubject(subjectId: String): List<ClassSchedule>

    @Query("DELETE FROM class_schedules WHERE subjectId = :subjectId")
    suspend fun deleteSchedulesForSubject(subjectId: String)

    @Query("SELECT * FROM class_schedules WHERE dayOfWeek = :dayOfWeek")
    fun getSchedulesForDay(dayOfWeek: Int): Flow<List<ClassSchedule>>

    @Query("SELECT * FROM class_schedules WHERE dayOfWeek = :dayOfWeek")
    suspend fun getSchedulesForDayNow(dayOfWeek: Int): List<ClassSchedule>

    @Query("SELECT * FROM class_schedules WHERE id = :scheduleId")
    suspend fun getScheduleById(scheduleId: String): ClassSchedule?

    @Query("DELETE FROM class_schedules WHERE id = :scheduleId")
    suspend fun deleteScheduleById(scheduleId: String)

    @Query("DELETE FROM class_schedules")
    suspend fun deleteAllSchedules()

    @Transaction
    suspend fun upsertSubjects(subjects: List<Subject>) {
        subjects.forEach { upsertSubject(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ClassSchedule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Transaction
    suspend fun restoreDataBatch(
        subjects: List<Subject>,
        schedules: List<ClassSchedule>,
        records: List<AttendanceRecord>,
    ) {
        deleteAllSubjects()
        deleteAllSchedules()
        deleteAllAttendanceRecords()
        
        // Ensure system subject exists before restoring records that might depend on it
        upsertSubject(Subject(id = "0", name = "System", color = "#FFC107", targetAttendance = 0))
        
        upsertSubjects(subjects)
        insertSchedules(schedules)
        insertAttendanceRecords(records)
    }


    // --- Attendance Record Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord)

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE id = :id")
    suspend fun getAttendanceRecordById(id: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId ORDER BY date DESC")
    fun getAttendanceRecordsForSubject(subjectId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE scheduleId = :scheduleId AND date = :date")
    suspend fun getRecordByScheduleIdAndDate(scheduleId: String, date: Long): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId AND date = :date AND type = 'MANUAL' LIMIT 1")
    suspend fun getManualRecordForDateAndSubject(subjectId: String, date: Long): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId AND date = :date AND type != 'HOLIDAY'")
    suspend fun getAttendanceRecordsForSubjectOnDate(subjectId: String, date: Long): List<AttendanceRecord>

    @Query("DELETE FROM attendance_records WHERE date = :date AND type = 'HOLIDAY'")
    suspend fun deleteHolidayOnDate(date: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM attendance_records WHERE date = :date AND type = 'HOLIDAY')")
    fun isDateHolidayFlow(date: Long): Flow<Boolean>

    @Query("DELETE FROM attendance_records WHERE date = :date AND type != 'HOLIDAY'")
    suspend fun deleteAttendanceRecordsOnDate(date: Long)

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    suspend fun getAllAttendanceRecordsOnDateNow(date: Long): List<AttendanceRecord>


    @Query("DELETE FROM attendance_records WHERE subjectId = :subjectId AND date = :date AND scheduleId = :scheduleId AND type != 'HOLIDAY'")
    suspend fun deleteAttendanceRecordBySchedule(subjectId: String, date: Long, scheduleId: String)

    @Query("DELETE FROM attendance_records WHERE subjectId = :subjectId AND date = :date AND type != 'HOLIDAY'")
    suspend fun deleteAttendanceRecordsForSubjectOnDate(subjectId: String, date: Long)

    @Query("DELETE FROM attendance_records WHERE subjectId = :subjectId")
    suspend fun deleteAttendanceRecordsForSubject(subjectId: String)

    @Query("DELETE FROM attendance_records WHERE id = :recordId")
    suspend fun deleteAttendanceRecordById(recordId: String)

    @Transaction
    suspend fun markHolidayTransaction(date: Long, holidayRecord: AttendanceRecord) {
        deleteAttendanceRecordsOnDate(date)
        insertAttendanceRecord(holidayRecord)
    }

    @Transaction
    suspend fun markAttendanceTransaction(recordIdsToClean: List<String>, newRecord: AttendanceRecord) {
        recordIdsToClean.forEach { deleteAttendanceRecordById(it) }
        insertAttendanceRecord(newRecord)
    }

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllAttendanceRecords()


    // --- Statistics & Joined Queries ---
    @Query("SELECT COUNT(*) FROM attendance_records WHERE type = 'CLASS' OR type = 'MANUAL'")
    suspend fun getTotalClassesOverall(): Int

    @Query("SELECT COUNT(*) FROM attendance_records WHERE isPresent = 1 AND (type = 'CLASS' OR type = 'MANUAL')")
    suspend fun getTotalPresentOverall(): Int

    @Query("SELECT COUNT(*) FROM attendance_records WHERE subjectId = :subjectId AND (type = 'CLASS' OR type = 'MANUAL')")
    suspend fun getTotalClassesForSubject(subjectId: String): Int

    @Query("SELECT COUNT(*) FROM attendance_records WHERE subjectId = :subjectId AND isPresent = 1 AND (type = 'CLASS' OR type = 'MANUAL')")
    suspend fun getPresentClassesForSubject(subjectId: String): Int

    @Query(
        """
        SELECT 
            COUNT(*) as totalClasses,
            COALESCE(SUM(CASE WHEN isPresent = 1 THEN 1 ELSE 0 END), 0) as totalPresent,
            (COUNT(*) - COALESCE(SUM(CASE WHEN isPresent = 1 THEN 1 ELSE 0 END), 0)) as totalAbsent,
            CASE WHEN COUNT(*) > 0 THEN (CAST(COALESCE(SUM(CASE WHEN isPresent = 1 THEN 1 ELSE 0 END), 0) AS REAL) / COUNT(*)) * 100.0 ELSE 0.0 END as overallPercentage,
            (SELECT COUNT(*) FROM subjects WHERE id != :holidayId) as subjectCount
        FROM attendance_records 
        WHERE type = 'CLASS' OR type = 'MANUAL'
    """
    )
    fun getOverallStatisticsFlow(holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY): Flow<AttendanceStatistics>

    @Transaction
    @Query(
        """
        SELECT s.*,
               (SELECT COUNT(*) FROM attendance_records WHERE subjectId = s.id AND (type = 'CLASS' OR type = 'MANUAL')) as totalClasses,
               (SELECT COUNT(*) FROM attendance_records WHERE subjectId = s.id AND isPresent = 1 AND (type = 'CLASS' OR type = 'MANUAL')) as presentClasses
        FROM subjects s
        WHERE s.id != :holidayId
    """
    )
    fun getSubjectsWithAttendance(holidayId: String = com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY): Flow<List<SubjectWithAttendance>>

    @Transaction
    @Query(
        """
        SELECT ar.*, s.name as subjectName, s.color as subjectColor
        FROM attendance_records ar
        LEFT JOIN subjects s ON ar.subjectId = s.id
        WHERE ar.date = :date AND ar.type != 'HOLIDAY'
    """
    )
    fun getRecordsForDateWithSubject(date: Long): Flow<List<AttendanceRecordWithSubject>>
}
