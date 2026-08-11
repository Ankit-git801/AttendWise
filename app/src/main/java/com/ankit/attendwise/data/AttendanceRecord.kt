package com.ankit.attendwise.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

import com.google.firebase.firestore.PropertyName

@Keep
@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["date"]),
        Index(value = ["type", "isPresent"]),
        Index(value = ["subjectId", "type", "isPresent"])
    ]
)
data class AttendanceRecord(
    @PrimaryKey
    val id: String = "",
    val subjectId: String = "",
    val scheduleId: String = "",
    val date: Long = 0,
    @get:PropertyName("isPresent")
    @set:PropertyName("isPresent")
    var isPresent: Boolean = false,
    val note: String = "",
    val type: RecordType = RecordType.CLASS,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Keep
enum class RecordType {
    CLASS,
    CANCELLED,
    HOLIDAY,
    MANUAL
}
