package com.attendwise.backend.attendance

import org.springframework.data.mongodb.repository.MongoRepository

interface AttendanceRecordRepository : MongoRepository<AttendanceRecord, String> {
    fun findAllByUserId(userId: String): List<AttendanceRecord>
    fun findAllBySubjectId(subjectId: String): List<AttendanceRecord>
    fun findAllByUserIdAndDate(userId: String, date: Long): List<AttendanceRecord>

    fun countByUserIdAndTypeIn(userId: String, types: Collection<RecordType>): Int
    fun countByUserIdAndIsPresentAndTypeIn(userId: String, isPresent: Boolean, types: Collection<RecordType>): Int

    fun countBySubjectIdAndTypeIn(subjectId: String, types: Collection<RecordType>): Int
    fun countBySubjectIdAndIsPresentAndTypeIn(subjectId: String, isPresent: Boolean, types: Collection<RecordType>): Int
}
