package com.attendwise.backend.schedule

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "schedules")
data class Schedule(
    @Id
    val id: String,
    @Indexed
    val userId: String,
    @Indexed
    val subjectId: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
