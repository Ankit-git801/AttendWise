package com.ankit.attendwise.data.remote

import com.ankit.attendwise.data.remote.dto.*

class RemoteDataSource(private val api: AttendWiseApi) {

    // Auth
    suspend fun register(request: RegisterRequest) = safeApiCall { api.register(request) }
    suspend fun login(request: LoginRequest) = safeApiCall { api.login(request) }
    suspend fun getCurrentUser() = safeApiCall { api.getCurrentUser() }
    suspend fun updateCurrentUser(request: UpdateUserRequest) = safeApiCall { api.updateCurrentUser(request) }

    // Subjects
    suspend fun getAllSubjects() = safeApiCall { api.getAllSubjects() }
    suspend fun createSubject(request: CreateSubjectRequest) = safeApiCall { api.createSubject(request) }
    suspend fun getSubject(id: String) = safeApiCall { api.getSubject(id) }
    suspend fun updateSubject(id: String, request: UpdateSubjectRequest) = safeApiCall { api.updateSubject(id, request) }
    suspend fun deleteSubject(id: String) = safeApiCall { api.deleteSubject(id) }

    // Schedules
    suspend fun getSchedulesForSubject(subjectId: String) = safeApiCall { api.getSchedulesForSubject(subjectId) }
    suspend fun createSchedule(subjectId: String, request: CreateScheduleRequest) = safeApiCall { api.createSchedule(subjectId, request) }
    suspend fun updateSchedule(id: String, request: UpdateScheduleRequest) = safeApiCall { api.updateSchedule(id, request) }
    suspend fun deleteSchedule(id: String) = safeApiCall { api.deleteSchedule(id) }

    // Attendance
    suspend fun getAttendanceForSubject(subjectId: String) = safeApiCall { api.getAttendanceForSubject(subjectId) }
    suspend fun createAttendance(request: CreateAttendanceRequest) = safeApiCall { api.createAttendance(request) }
    suspend fun updateAttendance(id: String, request: UpdateAttendanceRequest) = safeApiCall { api.updateAttendance(id, request) }
    suspend fun deleteAttendance(id: String) = safeApiCall { api.deleteAttendance(id) }

    // Statistics
    suspend fun getOverallStatistics() = safeApiCall { api.getOverallStatistics() }
    suspend fun getSubjectStatistics(subjectId: String) = safeApiCall { api.getSubjectStatistics(subjectId) }
}
