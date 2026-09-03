package com.ankit.attendwise.data.remote.dto

import androidx.annotation.Keep

@Keep
data class SubjectDto(
    val id: String,
    val name: String,
    val color: String,
    val targetAttendance: Int,
    val lastUpdated: Long
)

@Keep
data class CreateSubjectRequest(
    val id: String? = null,
    val name: String,
    val color: String,
    val targetAttendance: Int
)

@Keep
data class UpdateSubjectRequest(
    val name: String,
    val color: String,
    val targetAttendance: Int
)
