package com.attendwise.backend.statistics

import com.attendwise.backend.attendance.AttendanceRecordRepository
import com.attendwise.backend.attendance.RecordType
import com.attendwise.backend.common.OwnershipException
import com.attendwise.backend.subject.SubjectRepository
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class StatisticsService(
    private val attendanceRepository: AttendanceRecordRepository,
    private val subjectRepository: SubjectRepository
) {
    private val statisticalTypes = listOf(RecordType.CLASS, RecordType.MANUAL)

    fun getOverallStatistics(userId: String): OverallStatisticsResponse {
        val totalClasses = attendanceRepository.countByUserIdAndTypeIn(userId, statisticalTypes)
        val totalPresent = attendanceRepository.countByUserIdAndIsPresentAndTypeIn(userId, true, statisticalTypes)
        val subjectCount = subjectRepository.countByUserId(userId)

        val totalAbsent = totalClasses - totalPresent
        val overallPercentage = if (totalClasses > 0) {
            (totalPresent.toDouble() / totalClasses) * 100.0
        } else {
            0.0
        }

        return OverallStatisticsResponse(
            totalClasses = totalClasses,
            totalPresent = totalPresent,
            totalAbsent = totalAbsent,
            overallPercentage = overallPercentage,
            subjectCount = subjectCount.toInt()
        )
    }

    fun getSubjectStatistics(userId: String, subjectId: String): SubjectStatisticsResponse {
        val subject = subjectRepository.findById(subjectId)
            .orElseThrow { NoSuchElementException("Subject not found") }

        if (subject.userId != userId) {
            throw OwnershipException("You do not own this subject")
        }

        val totalClasses = attendanceRepository.countBySubjectIdAndTypeIn(subjectId, statisticalTypes)
        val presentClasses = attendanceRepository.countBySubjectIdAndIsPresentAndTypeIn(subjectId, true, statisticalTypes)
        val absentClasses = totalClasses - presentClasses
        val percentage = if (totalClasses > 0) {
            (presentClasses.toDouble() / totalClasses) * 100.0
        } else {
            0.0
        }

        val bunkAnalysis = calculateBunkAnalysis(presentClasses, totalClasses, subject.targetAttendance.toDouble())

        return SubjectStatisticsResponse(
            subjectId = subjectId,
            totalClasses = totalClasses,
            presentClasses = presentClasses,
            absentClasses = absentClasses,
            attendancePercentage = percentage,
            targetAttendance = subject.targetAttendance,
            classesToBunk = bunkAnalysis.classesToBunk,
            classesToAttend = bunkAnalysis.classesToAttend,
            isAtRisk = percentage < subject.targetAttendance
        )
    }

    private data class BunkAnalysisResult(val classesToBunk: Int, val classesToAttend: Int)

    private fun calculateBunkAnalysis(present: Int, total: Int, target: Double): BunkAnalysisResult {
        if (total == 0) return BunkAnalysisResult(0, 0)
        
        if (target <= 0) return BunkAnalysisResult(classesToBunk = 999, classesToAttend = 0)
        
        if (target >= 100.0) {
            return if (present >= total) BunkAnalysisResult(0, 0) 
            else BunkAnalysisResult(0, 999) 
        }

        val currentPercentage = (present.toDouble() / total) * 100.0

        return if (currentPercentage >= target) {
            val bunksAllowed = ((100.0 * present) / target).toInt() - total
            BunkAnalysisResult(classesToBunk = bunksAllowed.coerceAtLeast(0), classesToAttend = 0)
        } else {
            val mustAttend = ceil(((target * total) - (100.0 * present)) / (100.0 - target)).toInt()
            BunkAnalysisResult(classesToBunk = 0, classesToAttend = mustAttend.coerceAtLeast(0))
        }
    }
}
