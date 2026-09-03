package com.attendwise.backend.subject

import jakarta.validation.constraints.NotBlank

data class SubjectRequest(
    val id: String? = null,

    @field:NotBlank(message = "Subject name is required")
    val name: String,
    
    val color: String = "#4CAF50",
    
    val targetAttendance: Int = 75
)

data class SubjectResponse(
    val id: String,
    val name: String,
    val color: String,
    val targetAttendance: Int,
    val lastUpdated: Long
)
