package com.attendwise.backend.attendance

import com.attendwise.backend.common.OwnershipException
import com.attendwise.backend.schedule.ScheduleRepository
import com.attendwise.backend.subject.SubjectRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class AttendanceService(
    private val attendanceRepository: AttendanceRecordRepository,
    private val subjectRepository: SubjectRepository,
    private val scheduleRepository: ScheduleRepository
) {

    fun getAttendanceForSubject(userId: String, subjectId: String): List<AttendanceResponse> {
        verifySubjectOwnership(userId, subjectId)
        return attendanceRepository.findAllBySubjectId(subjectId).map { it.toResponse() }
    }

    fun createAttendance(userId: String, request: AttendanceRequest): AttendanceResponse {
        verifySubjectOwnership(userId, request.subjectId)
        // ScheduleId can be ID_SCHEDULE_MANUAL etc from constants, but if it looks like a UUID, we should verify it
        // For simplicity, we just check if it's the user's schedule if it exists
        if (request.scheduleId.length > 20) { // rough check for UUID vs short constants
             scheduleRepository.findById(request.scheduleId).ifPresent {
                 if (it.userId != userId) throw OwnershipException("You do not own this schedule")
             }
        }

        val record = AttendanceRecord(
            id = request.id ?: UUID.randomUUID().toString(),
            userId = userId,
            subjectId = request.subjectId,
            scheduleId = request.scheduleId,
            date = request.date,
            isPresent = request.isPresent,
            note = request.note,
            type = request.type
        )
        return attendanceRepository.save(record).toResponse()
    }

    fun updateAttendance(userId: String, id: String, request: AttendanceRequest): AttendanceResponse {
        val record = attendanceRepository.findById(id)
            .orElseThrow { NoSuchElementException("Attendance record not found") }
        
        if (record.userId != userId) {
            throw OwnershipException("You do not own this record")
        }
        
        verifySubjectOwnership(userId, request.subjectId)

        val updatedRecord = record.copy(
            subjectId = request.subjectId,
            scheduleId = request.scheduleId,
            date = request.date,
            isPresent = request.isPresent,
            note = request.note,
            type = request.type,
            lastUpdated = System.currentTimeMillis()
        )
        
        return attendanceRepository.save(updatedRecord).toResponse()
    }

    fun deleteAttendance(userId: String, id: String) {
        val record = attendanceRepository.findById(id)
            .orElseThrow { NoSuchElementException("Attendance record not found") }
        
        if (record.userId != userId) {
            throw OwnershipException("You do not own this record")
        }
        
        attendanceRepository.delete(record)
    }

    private fun verifySubjectOwnership(userId: String, subjectId: String) {
        val subject = subjectRepository.findById(subjectId)
            .orElseThrow { NoSuchElementException("Subject not found") }
        if (subject.userId != userId) {
            throw OwnershipException("You do not own the parent subject")
        }
    }

    private fun AttendanceRecord.toResponse() = AttendanceResponse(
        id = id,
        subjectId = subjectId,
        scheduleId = scheduleId,
        date = date,
        isPresent = isPresent,
        note = note,
        type = type,
        lastUpdated = lastUpdated
    )
}
