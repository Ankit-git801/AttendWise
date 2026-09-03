package com.attendwise.backend.attendance

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class AttendanceRequest(
    val id: String? = null,

    @field:NotBlank(message = "Subject ID is required")
    val subjectId: String,
    
    @field:NotBlank(message = "Schedule ID is required")
    val scheduleId: String,
    
    @field:NotNull(message = "Date is required")
    val date: Long,
    
    val isPresent: Boolean,
    
    val note: String = "",
    
    val type: RecordType = RecordType.CLASS
)

data class AttendanceResponse(
    val id: String,
    val subjectId: String,
    val scheduleId: String,
    val date: Long,
    val isPresent: Boolean,
    val note: String,
    val type: RecordType,
    val lastUpdated: Long
)
