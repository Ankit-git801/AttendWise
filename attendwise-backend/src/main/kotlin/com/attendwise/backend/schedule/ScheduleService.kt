package com.attendwise.backend.schedule

import com.attendwise.backend.common.OwnershipException
import com.attendwise.backend.subject.SubjectRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val subjectRepository: SubjectRepository
) {

    fun getSchedulesForSubject(userId: String, subjectId: String): List<ScheduleResponse> {
        verifySubjectOwnership(userId, subjectId)
        return scheduleRepository.findAllBySubjectId(subjectId).map { it.toResponse() }
    }

    fun createSchedule(userId: String, request: ScheduleRequest): ScheduleResponse {
        verifySubjectOwnership(userId, request.subjectId)
        
        val schedule = Schedule(
            id = request.id ?: UUID.randomUUID().toString(),
            userId = userId,
            subjectId = request.subjectId,
            dayOfWeek = request.dayOfWeek,
            startHour = request.startHour,
            startMinute = request.startMinute,
            endHour = request.endHour,
            endMinute = request.endMinute
        )
        return scheduleRepository.save(schedule).toResponse()
    }

    fun updateSchedule(userId: String, id: String, request: ScheduleRequest): ScheduleResponse {
        val schedule = scheduleRepository.findById(id)
            .orElseThrow { NoSuchElementException("Schedule not found") }
        
        if (schedule.userId != userId) {
            throw OwnershipException("You do not own this schedule")
        }
        
        // If changing subject, verify ownership of new subject
        if (schedule.subjectId != request.subjectId) {
            verifySubjectOwnership(userId, request.subjectId)
        }
        
        val updatedSchedule = schedule.copy(
            subjectId = request.subjectId,
            dayOfWeek = request.dayOfWeek,
            startHour = request.startHour,
            startMinute = request.startMinute,
            endHour = request.endHour,
            endMinute = request.endMinute,
            lastUpdated = System.currentTimeMillis()
        )
        
        return scheduleRepository.save(updatedSchedule).toResponse()
    }

    fun deleteSchedule(userId: String, id: String) {
        val schedule = scheduleRepository.findById(id)
            .orElseThrow { NoSuchElementException("Schedule not found") }
        
        if (schedule.userId != userId) {
            throw OwnershipException("You do not own this schedule")
        }
        
        scheduleRepository.delete(schedule)
    }

    private fun verifySubjectOwnership(userId: String, subjectId: String) {
        val subject = subjectRepository.findById(subjectId)
            .orElseThrow { NoSuchElementException("Subject not found") }
        if (subject.userId != userId) {
            throw OwnershipException("You do not own the parent subject")
        }
    }

    private fun Schedule.toResponse() = ScheduleResponse(
        id = id,
        subjectId = subjectId,
        dayOfWeek = dayOfWeek,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        lastUpdated = lastUpdated
    )
}
