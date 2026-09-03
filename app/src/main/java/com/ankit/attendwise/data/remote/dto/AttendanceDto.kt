package com.ankit.attendwise.data.remote.dto

import androidx.annotation.Keep
import com.ankit.attendwise.data.RecordType

@Keep
data class AttendanceDto(
    val id: String,
    val subjectId: String,
    val scheduleId: String,
    val date: Long,
    val isPresent: Boolean,
    val note: String,
    val type: RecordType,
    val lastUpdated: Long
)

@Keep
data class CreateAttendanceRequest(
    val id: String? = null,
    val subjectId: String,
    val scheduleId: String,
    val date: Long,
    val isPresent: Boolean,
    val note: String,
    val type: RecordType
)

@Keep
data class UpdateAttendanceRequest(
    val subjectId: String,
    val scheduleId: String,
    val date: Long,
    val isPresent: Boolean,
    val note: String,
    val type: RecordType
)
