package com.ankit.attendwise.data.local

import com.ankit.attendwise.data.*
import com.ankit.attendwise.models.AttendanceRecordWithSubject
import com.ankit.attendwise.models.AttendanceStatistics
import com.ankit.attendwise.models.SubjectWithAttendance
import kotlinx.coroutines.flow.Flow

class LocalDataSource(
    private val attendanceDao: AttendanceDao,
    private val pendingOperationDao: PendingOperationDao
) {

    // Pending Operations
    suspend fun insertPendingOperation(operation: PendingOperation) = pendingOperationDao.insert(operation)
    suspend fun getAllPendingOperations() = pendingOperationDao.getAllPending()
    suspend fun deletePendingOperation(operation: PendingOperation) = pendingOperationDao.delete(operation)
    suspend fun deletePendingOperationByEntity(entityId: String, entityType: EntityType) = pendingOperationDao.deleteByEntity(entityId, entityType)
    suspend fun deleteAllPendingOperations() = pendingOperationDao.deleteAll()

    // Subjects
    fun getAllSubjects(): Flow<List<Subject>> = attendanceDao.getAllSubjects()
    suspend fun getSubjectById(id: String): Subject? = attendanceDao.getSubjectById(id)
    fun getSubjectsWithAttendance(): Flow<List<SubjectWithAttendance>> = attendanceDao.getSubjectsWithAttendance()
    suspend fun upsertSubject(subject: Subject) = attendanceDao.upsertSubject(subject)
    suspend fun deleteSubjectAtomic(subjectId: String) = attendanceDao.deleteSubjectAtomic(subjectId)
    suspend fun deleteAllSubjects() = attendanceDao.deleteAllSubjects()
    suspend fun getSubjectCount(): Int = attendanceDao.getSubjectCount()

    // Schedules
    fun getAllSchedules(): Flow<List<ClassSchedule>> = attendanceDao.getAllSchedules()
    fun getSchedulesForSubjectFlow(subjectId: String): Flow<List<ClassSchedule>> = attendanceDao.getSchedulesForSubjectFlow(subjectId)
    suspend fun getSchedulesForSubject(subjectId: String): List<ClassSchedule> = attendanceDao.getSchedulesForSubject(subjectId)
    fun getSchedulesForDay(dayOfWeek: Int): Flow<List<ClassSchedule>> = attendanceDao.getSchedulesForDay(dayOfWeek)
    suspend fun getSchedulesForDayNow(dayOfWeek: Int): List<ClassSchedule> = attendanceDao.getSchedulesForDayNow(dayOfWeek)
    suspend fun getScheduleById(id: String): ClassSchedule? = attendanceDao.getScheduleById(id)
    suspend fun insertSchedules(schedules: List<ClassSchedule>) = attendanceDao.insertSchedules(schedules)
    suspend fun deleteSchedule(schedule: ClassSchedule) = attendanceDao.deleteSchedule(schedule)
    suspend fun deleteAllSchedules() = attendanceDao.deleteAllSchedules()

    // Attendance Records
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendanceRecords()
    suspend fun getAttendanceRecordById(id: String): AttendanceRecord? = attendanceDao.getAttendanceRecordById(id)
    fun getAttendanceRecordsForSubject(subjectId: String): Flow<List<AttendanceRecord>> = attendanceDao.getAttendanceRecordsForSubject(subjectId)
    suspend fun getAttendanceRecordsForSubjectOnDate(subjectId: String, date: Long): List<AttendanceRecord> = attendanceDao.getAttendanceRecordsForSubjectOnDate(subjectId, date)
    suspend fun getAllAttendanceRecordsOnDateNow(date: Long): List<AttendanceRecord> = attendanceDao.getAllAttendanceRecordsOnDateNow(date)
    suspend fun insertAttendanceRecord(record: AttendanceRecord) = attendanceDao.insertAttendanceRecord(record)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>) = attendanceDao.insertAttendanceRecords(records)
    suspend fun deleteAttendanceRecord(record: AttendanceRecord) = attendanceDao.deleteAttendanceRecord(record)
    suspend fun deleteAttendanceRecordsForSubjectOnDate(subjectId: String, date: Long) = attendanceDao.deleteAttendanceRecordsForSubjectOnDate(subjectId, date)
    suspend fun markAttendanceTransaction(recordIdsToClean: List<String>, newRecord: AttendanceRecord) = attendanceDao.markAttendanceTransaction(recordIdsToClean, newRecord)
    suspend fun markHolidayTransaction(date: Long, holidayRecord: AttendanceRecord) = attendanceDao.markHolidayTransaction(date, holidayRecord)
    suspend fun deleteAllAttendanceRecords() = attendanceDao.deleteAllAttendanceRecords()
    fun isDateHolidayFlow(date: Long): Flow<Boolean> = attendanceDao.isDateHolidayFlow(date)
    fun getRecordsForDateWithSubject(date: Long): Flow<List<AttendanceRecordWithSubject>> = attendanceDao.getRecordsForDateWithSubject(date)

    // Statistics
    fun getOverallStatisticsFlow(): Flow<AttendanceStatistics> = attendanceDao.getOverallStatisticsFlow()
    suspend fun getTotalClassesForSubject(subjectId: String): Int = attendanceDao.getTotalClassesForSubject(subjectId)
    suspend fun getPresentClassesForSubject(subjectId: String): Int = attendanceDao.getPresentClassesForSubject(subjectId)
    
    // Batch
    suspend fun restoreDataBatch(subjects: List<Subject>, schedules: List<ClassSchedule>, records: List<AttendanceRecord>) = 
        attendanceDao.restoreDataBatch(subjects, schedules, records)
}
