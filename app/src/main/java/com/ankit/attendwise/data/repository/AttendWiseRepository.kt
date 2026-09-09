package com.ankit.attendwise.data.repository

import com.ankit.attendwise.data.*
import com.ankit.attendwise.data.local.*
import com.ankit.attendwise.data.remote.NetworkResult
import com.ankit.attendwise.data.remote.RemoteDataSource
import com.ankit.attendwise.data.remote.SessionManager
import com.ankit.attendwise.data.remote.dto.*
import com.ankit.attendwise.models.AttendanceRecordWithSubject
import com.ankit.attendwise.models.AttendanceStatistics
import com.ankit.attendwise.models.SubjectWithAttendance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttendWiseRepository(
    private val localDataSource: LocalDataSource,
    private val remoteDataSource: RemoteDataSource,
    private val sessionManager: SessionManager
) {

    val syncMutex = Mutex()

    // --- AUTH (Remote + Session) ---
    val jwtToken: Flow<String?> = sessionManager.jwtToken
    val userId: Flow<String?> = sessionManager.userId
    val userEmail: Flow<String?> = sessionManager.userEmail
    val userName: Flow<String?> = sessionManager.userName

    suspend fun register(request: RegisterRequest): NetworkResult<AuthResponse> {
        val result = remoteDataSource.register(request)
        if (result is NetworkResult.Success) {
            saveSession(result.data)
            // Push any existing local data to the new account
            pushLocalDataToRemote()
        }
        return result
    }

    private suspend fun pushLocalDataToRemote() {
        val subjects = localDataSource.getAllSubjects().first()
        for (s in subjects) {
            remoteDataSource.createSubject(s.toCreateRequest())
            val schedules = localDataSource.getSchedulesForSubject(s.id)
            for (sch in schedules) {
                remoteDataSource.createSchedule(sch.subjectId, sch.toCreateRequest())
            }
            // Get all records for subject (reactive flow to list)
            val records = localDataSource.getAttendanceRecordsForSubject(s.id).first()
            for (r in records) {
                remoteDataSource.createAttendance(r.toCreateRequest())
            }
        }
    }

    suspend fun login(request: LoginRequest): NetworkResult<AuthResponse> {
        val result = remoteDataSource.login(request)
        if (result is NetworkResult.Success) {
            saveSession(result.data)
        }
        return result
    }

    suspend fun resetPassword(request: ResetPasswordRequest): NetworkResult<Map<String, String>> {
        return remoteDataSource.resetPassword(request)
    }

    suspend fun getCurrentUser() = remoteDataSource.getCurrentUser()

    suspend fun updateCurrentUser(request: UpdateUserRequest): NetworkResult<UserDto> {
        val result = remoteDataSource.updateCurrentUser(request)
        if (result is NetworkResult.Success) {
            sessionManager.updateUserName(result.data.name)
        }
        return result
    }

    suspend fun logout() {
        sessionManager.clearSession()
        localDataSource.deleteAllPendingOperations()
    }

    private suspend fun saveSession(response: AuthResponse) {
        sessionManager.saveSession(
            token = response.token,
            id = response.user.id,
            email = response.user.email,
            name = response.user.name
        )
    }

    // --- SYNC LOGIC ---

    suspend fun syncAll(): NetworkResult<Unit> {
        return syncMutex.withLock {
            // 1. Sync pending local changes to remote
            syncPendingOperations()
            
            // 2. Sync remote changes to local
            syncRemoteToLocal()
        }
    }

    private suspend fun syncPendingOperations() {
        val pending = localDataSource.getAllPendingOperations()
        for (op in pending) {
            val success = when (op.entityType) {
                EntityType.SUBJECT -> syncPendingSubject(op)
                EntityType.SCHEDULE -> syncPendingSchedule(op)
                EntityType.ATTENDANCE -> syncPendingAttendance(op)
            }
            if (success) {
                localDataSource.deletePendingOperation(op)
            }
        }
    }

    private suspend fun syncPendingSubject(op: PendingOperation): Boolean {
        return when (op.operation) {
            OperationType.CREATE, OperationType.UPDATE -> {
                val subject = localDataSource.getSubjectById(op.entityId) ?: return true // Already deleted locally
                val result = if (op.operation == OperationType.CREATE) {
                    remoteDataSource.createSubject(subject.toCreateRequest())
                } else {
                    remoteDataSource.updateSubject(subject.id, subject.toUpdateRequest())
                }
                result is NetworkResult.Success
            }
            OperationType.DELETE -> {
                val result = remoteDataSource.deleteSubject(op.entityId)
                result is NetworkResult.Success || (result is NetworkResult.Error && result.code == 404)
            }
        }
    }

    private suspend fun syncPendingSchedule(op: PendingOperation): Boolean {
        return when (op.operation) {
            OperationType.CREATE, OperationType.UPDATE -> {
                val schedule = localDataSource.getScheduleById(op.entityId) ?: return true
                val result = if (op.operation == OperationType.CREATE) {
                    remoteDataSource.createSchedule(schedule.subjectId, schedule.toCreateRequest())
                } else {
                    remoteDataSource.updateSchedule(schedule.id, schedule.toUpdateRequest())
                }
                result is NetworkResult.Success
            }
            OperationType.DELETE -> {
                val result = remoteDataSource.deleteSchedule(op.entityId)
                result is NetworkResult.Success || (result is NetworkResult.Error && result.code == 404)
            }
        }
    }

    private suspend fun syncPendingAttendance(op: PendingOperation): Boolean {
        return when (op.operation) {
            OperationType.CREATE, OperationType.UPDATE -> {
                val record = localDataSource.getAttendanceRecordById(op.entityId) ?: return true
                val result = if (op.operation == OperationType.CREATE) {
                    remoteDataSource.createAttendance(record.toCreateRequest())
                } else {
                    remoteDataSource.updateAttendance(record.id, record.toUpdateRequest())
                }
                result is NetworkResult.Success
            }
            OperationType.DELETE -> {
                val result = remoteDataSource.deleteAttendance(op.entityId)
                result is NetworkResult.Success || (result is NetworkResult.Error && result.code == 404)
            }
        }
    }

    private suspend fun syncRemoteToLocal(): NetworkResult<Unit> = coroutineScope {
        val subjectsResult = remoteDataSource.getAllSubjects()
        if (subjectsResult !is NetworkResult.Success) return@coroutineScope mapError(subjectsResult)
        
        val remoteSubjects = subjectsResult.data
        for (rs in remoteSubjects) {
            val local = localDataSource.getSubjectById(rs.id)
            if (local == null || rs.lastUpdated > local.lastUpdated) {
                localDataSource.upsertSubject(rs.toDomain())
            }
        }
        
        // Parallel sync for schedules and attendance
        val scheduleJobs = remoteSubjects.map { rs ->
            async { remoteDataSource.getSchedulesForSubject(rs.id) }
        }
        val attendanceJobs = remoteSubjects.map { rs ->
            async { remoteDataSource.getAttendanceForSubject(rs.id) }
        }
        
        scheduleJobs.forEach { job ->
            val schedulesResult = job.await()
            if (schedulesResult is NetworkResult.Success) {
                for (rss in schedulesResult.data) {
                    val local = localDataSource.getScheduleById(rss.id)
                    if (local == null || rss.lastUpdated > local.lastUpdated) {
                        localDataSource.insertSchedules(listOf(rss.toDomain()))
                    }
                }
            }
        }
        
        attendanceJobs.forEach { job ->
            val attendanceResult = job.await()
            if (attendanceResult is NetworkResult.Success) {
                for (ra in attendanceResult.data) {
                    val local = localDataSource.getAttendanceRecordById(ra.id)
                    if (local == null || ra.lastUpdated > local.lastUpdated) {
                        localDataSource.insertAttendanceRecord(ra.toDomain())
                    }
                }
            }
        }
        
        NetworkResult.Success(Unit)
    }

    private fun <T> mapError(result: NetworkResult<T>): NetworkResult<Unit> {
        return when (result) {
            is NetworkResult.Error -> NetworkResult.Error(result.code, result.message)
            is NetworkResult.Exception -> NetworkResult.Exception(result.e)
            else -> NetworkResult.Success(Unit)
        }
    }

    // --- MAPPERS ---

    private fun Subject.toCreateRequest() = CreateSubjectRequest(id, name, color, targetAttendance)
    private fun Subject.toUpdateRequest() = UpdateSubjectRequest(name, color, targetAttendance)
    private fun SubjectDto.toDomain() = Subject(id, name, color, targetAttendance, lastUpdated)

    private fun ClassSchedule.toCreateRequest() = CreateScheduleRequest(id, subjectId, dayOfWeek, startHour, startMinute, endHour, endMinute)
    private fun ClassSchedule.toUpdateRequest() = UpdateScheduleRequest(subjectId, dayOfWeek, startHour, startMinute, endHour, endMinute)
    private fun ScheduleDto.toDomain() = ClassSchedule(id, subjectId, dayOfWeek, startHour, startMinute, endHour, endMinute, lastUpdated)

    private fun AttendanceRecord.toCreateRequest() = CreateAttendanceRequest(id, subjectId, scheduleId, date, isPresent, note, type)
    private fun AttendanceRecord.toUpdateRequest() = UpdateAttendanceRequest(subjectId, scheduleId, date, isPresent, note, type)
    private fun AttendanceDto.toDomain() = AttendanceRecord(id, subjectId, scheduleId, date, isPresent, note, type, lastUpdated)

    // --- SUBJECTS ---
    fun getAllSubjectsLocal(): Flow<List<Subject>> = localDataSource.getAllSubjects()
    suspend fun getSubjectByIdLocal(id: String): Subject? = localDataSource.getSubjectById(id)
    fun getSubjectsWithAttendanceLocal(): Flow<List<SubjectWithAttendance>> = localDataSource.getSubjectsWithAttendance()
    suspend fun deleteAllSubjectsLocal() = localDataSource.deleteAllSubjects()

    suspend fun addSubject(subject: Subject): NetworkResult<SubjectDto> {
        localDataSource.upsertSubject(subject)
        val result = remoteDataSource.createSubject(subject.toCreateRequest())
        if (result !is NetworkResult.Success) {
            localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.SUBJECT, entityId = subject.id, operation = OperationType.CREATE))
        }
        return result
    }

    suspend fun updateSubject(subject: Subject): NetworkResult<SubjectDto> {
        localDataSource.upsertSubject(subject)
        val result = remoteDataSource.updateSubject(subject.id, subject.toUpdateRequest())
        if (result !is NetworkResult.Success) {
            localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.SUBJECT, entityId = subject.id, operation = OperationType.UPDATE))
        }
        return result
    }

    suspend fun deleteSubject(subjectId: String): NetworkResult<Unit> {
        localDataSource.deleteSubjectAtomic(subjectId)
        val result = remoteDataSource.deleteSubject(subjectId)
        if (result !is NetworkResult.Success) {
            localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.SUBJECT, entityId = subjectId, operation = OperationType.DELETE))
        }
        return result
    }

    // --- SCHEDULES ---
    fun getAllSchedulesLocal(): Flow<List<ClassSchedule>> = localDataSource.getAllSchedules()
    fun getSchedulesForSubjectFlowLocal(subjectId: String): Flow<List<ClassSchedule>> = localDataSource.getSchedulesForSubjectFlow(subjectId)
    suspend fun getSchedulesForSubjectLocal(subjectId: String): List<ClassSchedule> = localDataSource.getSchedulesForSubject(subjectId)
    fun getSchedulesForDayLocal(dayOfWeek: Int): Flow<List<ClassSchedule>> = localDataSource.getSchedulesForDay(dayOfWeek)
    suspend fun getSchedulesForDayNowLocal(dayOfWeek: Int): List<ClassSchedule> = localDataSource.getSchedulesForDayNow(dayOfWeek)
    suspend fun getScheduleByIdLocal(id: String): ClassSchedule? = localDataSource.getScheduleById(id)
    suspend fun deleteAllSchedulesLocal() = localDataSource.deleteAllSchedules()

    suspend fun addSchedules(schedules: List<ClassSchedule>) {
        localDataSource.insertSchedules(schedules)
        for (s in schedules) {
            val result = remoteDataSource.createSchedule(s.subjectId, s.toCreateRequest())
            if (result !is NetworkResult.Success) {
                localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.SCHEDULE, entityId = s.id, parentId = s.subjectId, operation = OperationType.CREATE))
            }
        }
    }

    suspend fun deleteSchedule(schedule: ClassSchedule) {
        localDataSource.deleteSchedule(schedule)
        val result = remoteDataSource.deleteSchedule(schedule.id)
        if (result !is NetworkResult.Success) {
            localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.SCHEDULE, entityId = schedule.id, operation = OperationType.DELETE))
        }
    }

    // --- ATTENDANCE ---
    fun getAllAttendanceRecordsLocal(): Flow<List<AttendanceRecord>> = localDataSource.getAllAttendanceRecords()
    suspend fun getAttendanceRecordByIdLocal(id: String): AttendanceRecord? = localDataSource.getAttendanceRecordById(id)
    fun getAttendanceRecordsForSubjectLocal(subjectId: String): Flow<List<AttendanceRecord>> = localDataSource.getAttendanceRecordsForSubject(subjectId)
    suspend fun getAttendanceRecordsForSubjectOnDateLocal(subjectId: String, date: Long): List<AttendanceRecord> = localDataSource.getAttendanceRecordsForSubjectOnDate(subjectId, date)
    suspend fun getAllAttendanceRecordsOnDateNowLocal(date: Long): List<AttendanceRecord> = localDataSource.getAllAttendanceRecordsOnDateNow(date)
    suspend fun deleteAllAttendanceRecordsLocal() = localDataSource.deleteAllAttendanceRecords()
    fun isDateHolidayFlowLocal(date: Long): Flow<Boolean> = localDataSource.isDateHolidayFlow(date)
    fun getRecordsForDateWithSubjectLocal(date: Long): Flow<List<AttendanceRecordWithSubject>> = localDataSource.getRecordsForDateWithSubject(date)

    suspend fun markAttendance(recordIdsToClean: List<String>, newRecord: AttendanceRecord) {
        localDataSource.markAttendanceTransaction(recordIdsToClean, newRecord)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in recordIdsToClean) {
                    remoteDataSource.deleteAttendance(id)
                    localDataSource.deletePendingOperationByEntity(id, EntityType.ATTENDANCE)
                }
                val result = remoteDataSource.createAttendance(newRecord.toCreateRequest())
                if (result !is NetworkResult.Success) {
                    localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.ATTENDANCE, entityId = newRecord.id, operation = OperationType.CREATE))
                }
            } catch (e: Exception) {
                localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.ATTENDANCE, entityId = newRecord.id, operation = OperationType.CREATE))
            }
        }
    }

    suspend fun deleteAttendanceRecord(record: AttendanceRecord) {
        localDataSource.deleteAttendanceRecord(record)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = remoteDataSource.deleteAttendance(record.id)
                if (result !is NetworkResult.Success) {
                    localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.ATTENDANCE, entityId = record.id, operation = OperationType.DELETE))
                }
            } catch (e: Exception) {
                localDataSource.insertPendingOperation(PendingOperation(entityType = EntityType.ATTENDANCE, entityId = record.id, operation = OperationType.DELETE))
            }
        }
    }

    suspend fun deleteAttendanceRecordsForSubjectOnDateLocal(subjectId: String, date: Long) {
        localDataSource.deleteAttendanceRecordsForSubjectOnDate(subjectId, date)
    }

    suspend fun insertAttendanceRecordsLocal(records: List<AttendanceRecord>) {
        localDataSource.insertAttendanceRecords(records)
    }

    // --- STATISTICS ---
    fun getOverallStatisticsFlowLocal(): Flow<AttendanceStatistics> = localDataSource.getOverallStatisticsFlow()
    suspend fun getTotalClassesForSubjectLocal(subjectId: String): Int = localDataSource.getTotalClassesForSubject(subjectId)
    suspend fun getPresentClassesForSubjectLocal(subjectId: String): Int = localDataSource.getPresentClassesForSubject(subjectId)
}
