package com.attendwise.backend.schedule

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class ScheduleRequest(
    val id: String? = null,

    @field:NotBlank(message = "Subject ID is required")
    val subjectId: String,
    
    @field:Min(0) @field:Max(6)
    val dayOfWeek: Int,
    
    @field:Min(0) @field:Max(23)
    val startHour: Int,
    
    @field:Min(0) @field:Max(59)
    val startMinute: Int,
    
    @field:Min(0) @field:Max(23)
    val endHour: Int,
    
    @field:Min(0) @field:Max(59)
    val endMinute: Int
)

data class ScheduleResponse(
    val id: String,
    val subjectId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val lastUpdated: Long
)
