package com.attendwise.backend

import com.attendwise.backend.attendance.AttendanceRecord
import com.attendwise.backend.attendance.AttendanceRecordRepository
import com.attendwise.backend.attendance.RecordType
import com.attendwise.backend.schedule.Schedule
import com.attendwise.backend.schedule.ScheduleRepository
import com.attendwise.backend.subject.Subject
import com.attendwise.backend.subject.SubjectRepository
import com.attendwise.backend.user.User
import com.attendwise.backend.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class DataPersistenceTest {

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var subjectRepository: SubjectRepository

    @Autowired
    lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    lateinit var attendanceRepository: AttendanceRecordRepository

    @Test
    fun `test full data persistence flow`() {
        val userId = UUID.randomUUID().toString()
        val user = User(
            id = userId,
            email = "test-${userId}@example.com",
            passwordHash = "hashed_password",
            name = "Test User"
        )
        userRepository.save(user)
        
        val subjectId = UUID.randomUUID().toString()
        val subject = Subject(
            id = subjectId,
            userId = userId,
            name = "Mathematics"
        )
        subjectRepository.save(subject)
        
        val scheduleId = UUID.randomUUID().toString()
        val schedule = Schedule(
            id = scheduleId,
            userId = userId,
            subjectId = subjectId,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 0
        )
        scheduleRepository.save(schedule)
        
        val recordId = UUID.randomUUID().toString()
        val record = AttendanceRecord(
            id = recordId,
            userId = userId,
            subjectId = subjectId,
            scheduleId = scheduleId,
            date = 20000, // Some epoch day
            isPresent = true,
            type = RecordType.CLASS
        )
        attendanceRepository.save(record)
        
        // Verifications
        assertTrue(userRepository.findById(userId).isPresent)
        assertEquals(1, subjectRepository.findAllByUserId(userId).size)
        assertEquals(1, scheduleRepository.findAllBySubjectId(subjectId).size)
        assertEquals(1, attendanceRepository.findAllByUserIdAndDate(userId, 20000).size)
        
        // Cleanup
        attendanceRepository.deleteById(recordId)
        scheduleRepository.deleteById(scheduleId)
        subjectRepository.deleteById(subjectId)
        userRepository.deleteById(userId)
    }
}
