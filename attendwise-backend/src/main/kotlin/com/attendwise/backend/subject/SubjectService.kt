package com.attendwise.backend.subject

import com.attendwise.backend.common.OwnershipException
import org.springframework.stereotype.Service
import java.util.*

@Service
class SubjectService(
    private val subjectRepository: SubjectRepository
) {

    fun getAllSubjectsForUser(userId: String): List<SubjectResponse> {
        return subjectRepository.findAllByUserId(userId).map { it.toResponse() }
    }

    fun getSubjectById(userId: String, id: String): SubjectResponse {
        val subject = subjectRepository.findById(id)
            .orElseThrow { NoSuchElementException("Subject not found") }
        
        if (subject.userId != userId) {
            throw OwnershipException("You do not own this subject")
        }
        
        return subject.toResponse()
    }

    fun createSubject(userId: String, request: SubjectRequest): SubjectResponse {
        val subject = Subject(
            id = request.id ?: UUID.randomUUID().toString(),
            userId = userId,
            name = request.name,
            color = request.color,
            targetAttendance = request.targetAttendance
        )
        return subjectRepository.save(subject).toResponse()
    }

    fun updateSubject(userId: String, id: String, request: SubjectRequest): SubjectResponse {
        val subject = subjectRepository.findById(id)
            .orElseThrow { NoSuchElementException("Subject not found") }
        
        if (subject.userId != userId) {
            throw OwnershipException("You do not own this subject")
        }
        
        val updatedSubject = subject.copy(
            name = request.name,
            color = request.color,
            targetAttendance = request.targetAttendance,
            lastUpdated = System.currentTimeMillis()
        )
        
        return subjectRepository.save(updatedSubject).toResponse()
    }

    fun deleteSubject(userId: String, id: String) {
        val subject = subjectRepository.findById(id)
            .orElseThrow { NoSuchElementException("Subject not found") }
        
        if (subject.userId != userId) {
            throw OwnershipException("You do not own this subject")
        }
        
        subjectRepository.delete(subject)
    }

    private fun Subject.toResponse() = SubjectResponse(
        id = id,
        name = name,
        color = color,
        targetAttendance = targetAttendance,
        lastUpdated = lastUpdated
    )
}
