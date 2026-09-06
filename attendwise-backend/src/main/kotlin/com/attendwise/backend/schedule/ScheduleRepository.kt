package com.attendwise.backend.schedule

import org.springframework.data.mongodb.repository.MongoRepository

interface ScheduleRepository : MongoRepository<Schedule, String> {
    fun findAllByUserId(userId: String): List<Schedule>
    fun findAllBySubjectId(subjectId: String): List<Schedule>
    fun deleteAllByUserId(userId: String)
    fun deleteAllBySubjectId(subjectId: String)
}
