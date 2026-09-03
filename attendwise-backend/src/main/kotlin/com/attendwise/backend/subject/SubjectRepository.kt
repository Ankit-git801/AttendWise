package com.attendwise.backend.subject

import org.springframework.data.mongodb.repository.MongoRepository

interface SubjectRepository : MongoRepository<Subject, String> {
    fun findAllByUserId(userId: String): List<Subject>
    fun countByUserId(userId: String): Long
}
