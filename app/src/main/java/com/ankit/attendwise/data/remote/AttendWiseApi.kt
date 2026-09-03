package com.ankit.attendwise.data.remote

import com.ankit.attendwise.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AttendWiseApi {

    // Health
    @GET("api/health")
    suspend fun checkHealth(): Response<Map<String, String>>

    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<UserDto>

    @PUT("api/auth/me")
    suspend fun updateCurrentUser(@Body request: UpdateUserRequest): Response<UserDto>

    // Subjects
    @GET("api/subjects")
    suspend fun getAllSubjects(): Response<List<SubjectDto>>

    @POST("api/subjects")
    suspend fun createSubject(@Body request: CreateSubjectRequest): Response<SubjectDto>

    @GET("api/subjects/{id}")
    suspend fun getSubject(@Path("id") id: String): Response<SubjectDto>

    @PUT("api/subjects/{id}")
    suspend fun updateSubject(@Path("id") id: String, @Body request: UpdateSubjectRequest): Response<SubjectDto>

    @DELETE("api/subjects/{id}")
    suspend fun deleteSubject(@Path("id") id: String): Response<Unit>

    // Schedules
    @GET("api/subjects/{subjectId}/schedules")
    suspend fun getSchedulesForSubject(@Path("subjectId") subjectId: String): Response<List<ScheduleDto>>

    @POST("api/subjects/{subjectId}/schedules")
    suspend fun createSchedule(@Path("subjectId") subjectId: String, @Body request: CreateScheduleRequest): Response<ScheduleDto>

    @PUT("api/schedules/{id}")
    suspend fun updateSchedule(@Path("id") id: String, @Body request: UpdateScheduleRequest): Response<ScheduleDto>

    @DELETE("api/schedules/{id}")
    suspend fun deleteSchedule(@Path("id") id: String): Response<Unit>

    // Attendance
    @GET("api/subjects/{subjectId}/attendance")
    suspend fun getAttendanceForSubject(@Path("subjectId") subjectId: String): Response<List<AttendanceDto>>

    @POST("api/attendance")
    suspend fun createAttendance(@Body request: CreateAttendanceRequest): Response<AttendanceDto>

    @PUT("api/attendance/{id}")
    suspend fun updateAttendance(@Path("id") id: String, @Body request: UpdateAttendanceRequest): Response<AttendanceDto>

    @DELETE("api/attendance/{id}")
    suspend fun deleteAttendance(@Path("id") id: String): Response<Unit>

    // Statistics
    @GET("api/statistics")
    suspend fun getOverallStatistics(): Response<OverallStatisticsDto>

    @GET("api/subjects/{subjectId}/statistics")
    suspend fun getSubjectStatistics(@Path("subjectId") subjectId: String): Response<SubjectStatisticsDto>
}
