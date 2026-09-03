package com.ankit.attendwise.data.remote.dto

import androidx.annotation.Keep

@Keep
data class ScheduleDto(
    val id: String,
    val subjectId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val lastUpdated: Long
)

@Keep
data class CreateScheduleRequest(
    val id: String? = null,
    val subjectId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

@Keep
data class UpdateScheduleRequest(
    val subjectId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
