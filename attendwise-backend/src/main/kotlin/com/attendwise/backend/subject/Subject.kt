package com.attendwise.backend.subject

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "subjects")
data class Subject(
    @Id
    val id: String,
    @Indexed
    val userId: String,
    val name: String,
    val color: String = "#4CAF50",
    val targetAttendance: Int = 75,
    val lastUpdated: Long = System.currentTimeMillis()
)
