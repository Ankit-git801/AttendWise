package com.attendwise.backend.statistics

data class OverallStatisticsResponse(
    val totalClasses: Int,
    val totalPresent: Int,
    val totalAbsent: Int,
    val overallPercentage: Double,
    val subjectCount: Int
)

data class SubjectStatisticsResponse(
    val subjectId: String,
    val totalClasses: Int,
    val presentClasses: Int,
    val absentClasses: Int,
    val attendancePercentage: Double,
    val targetAttendance: Int,
    val classesToBunk: Int,
    val classesToAttend: Int,
    val isAtRisk: Boolean
)
