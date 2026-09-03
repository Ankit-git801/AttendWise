package com.ankit.attendwise.data.local

import androidx.room.*

@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperation)

    @Query("SELECT * FROM pending_operations ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingOperation>

    @Delete
    suspend fun delete(operation: PendingOperation)

    @Query("DELETE FROM pending_operations WHERE entityId = :entityId AND entityType = :entityType")
    suspend fun deleteByEntity(entityId: String, entityType: EntityType)

    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll()
}
