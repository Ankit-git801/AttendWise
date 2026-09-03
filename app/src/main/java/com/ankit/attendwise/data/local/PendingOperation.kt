package com.ankit.attendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OperationType {
    CREATE, UPDATE, DELETE
}

enum class EntityType {
    SUBJECT, SCHEDULE, ATTENDANCE
}

@Entity(tableName = "pending_operations")
data class PendingOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: EntityType,
    val entityId: String,
    val parentId: String? = null, // e.g., subjectId for schedules
    val operation: OperationType,
    val createdAt: Long = System.currentTimeMillis()
)
