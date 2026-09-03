package com.attendwise.backend.attendance

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "attendance_records")
@CompoundIndexes(
    CompoundIndex(name = "user_subject_date", def = "{'userId': 1, 'subjectId': 1, 'date': 1}"),
    CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': 1}")
)
data class AttendanceRecord(
    @Id
    val id: String,
    @Indexed
    val userId: String,
    @Indexed
    val subjectId: String,
    @Indexed
    val scheduleId: String,
    @Indexed
    val date: Long, // Epoch Day
    val isPresent: Boolean,
    val note: String = "",
    val type: RecordType = RecordType.CLASS,
    val lastUpdated: Long = System.currentTimeMillis()
)
