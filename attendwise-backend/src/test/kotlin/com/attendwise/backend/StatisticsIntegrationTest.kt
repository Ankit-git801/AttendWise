package com.attendwise.backend

import com.attendwise.backend.attendance.AttendanceRecord
import com.attendwise.backend.attendance.AttendanceRecordRepository
import com.attendwise.backend.attendance.RecordType
import com.attendwise.backend.auth.AuthResponse
import com.attendwise.backend.auth.RegisterRequest
import com.attendwise.backend.subject.Subject
import com.attendwise.backend.subject.SubjectRepository
import com.attendwise.backend.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.*
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class StatisticsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var subjectRepository: SubjectRepository

    @Autowired
    lateinit var attendanceRepository: AttendanceRecordRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private lateinit var token: String
    private lateinit var userId: String

    @BeforeEach
    fun setup() {
        attendanceRepository.deleteAll()
        subjectRepository.deleteAll()
        userRepository.deleteAll()

        val result = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RegisterRequest("stats@test.com", "password", "Stats User"))
        }.andReturn()
        val response = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
        token = response.token
        userId = response.user.id
    }

    @Test
    fun `test overall statistics calculation`() {
        // Setup 2 subjects
        val sub1 = subjectRepository.save(Subject(UUID.randomUUID().toString(), userId, "Sub 1"))
        val sub2 = subjectRepository.save(Subject(UUID.randomUUID().toString(), userId, "Sub 2"))

        // Attendance for Sub 1: 3 present, 1 absent
        repeat(3) { addAttendance(sub1.id, true) }
        addAttendance(sub1.id, false)

        // Attendance for Sub 2: 2 present, 2 absent
        repeat(2) { addAttendance(sub2.id, true) }
        repeat(2) { addAttendance(sub2.id, false) }

        // Total: 8 classes, 5 present, 3 absent. Percentage: 62.5%
        mockMvc.get("/api/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalClasses") { value(8) }
            jsonPath("$.totalPresent") { value(5) }
            jsonPath("$.totalAbsent") { value(3) }
            jsonPath("$.overallPercentage") { value(62.5) }
            jsonPath("$.subjectCount") { value(2) }
        }
    }

    @Test
    fun `test subject statistics and bunk analysis`() {
        val target = 75
        val sub = subjectRepository.save(Subject(UUID.randomUUID().toString(), userId, "Math", targetAttendance = target))

        // 6 present, 2 absent (Total 8, 75%)
        repeat(6) { addAttendance(sub.id, true) }
        repeat(2) { addAttendance(sub.id, false) }

        mockMvc.get("/api/subjects/${sub.id}/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.attendancePercentage") { value(75.0) }
            jsonPath("$.classesToBunk") { value(0) }
            jsonPath("$.classesToAttend") { value(0) }
            jsonPath("$.isAtRisk") { value(false) }
        }

        // Add 1 more present (7/9 = 77.7%)
        addAttendance(sub.id, true)
        mockMvc.get("/api/subjects/${sub.id}/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.classesToBunk") { value(0) } // (100*7)/75 - 9 = 9.33 - 9 = 0.33 -> 0
        }

        // Add 3 more present (10/12 = 83.3%)
        repeat(3) { addAttendance(sub.id, true) }
        mockMvc.get("/api/subjects/${sub.id}/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.classesToBunk") { value(1) } // (100*10)/75 - 12 = 13.33 - 12 = 1
        }

        // Add many absent to fall below target (10/20 = 50%)
        repeat(8) { addAttendance(sub.id, false) }
        mockMvc.get("/api/subjects/${sub.id}/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.attendancePercentage") { value(50.0) }
            jsonPath("$.isAtRisk") { value(true) }
            jsonPath("$.classesToAttend") { value(20) } // (75*20 - 100*10)/(100-75) = (1500-1000)/25 = 500/25 = 20
        }
    }

    @Test
    fun `test statistics for non-existent subject`() {
        mockMvc.get("/api/subjects/invalid-id/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `test zero classes case`() {
        val sub = subjectRepository.save(Subject(UUID.randomUUID().toString(), userId, "Empty"))
        mockMvc.get("/api/subjects/${sub.id}/statistics") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalClasses") { value(0) }
            jsonPath("$.attendancePercentage") { value(0.0) }
        }
    }

    private fun addAttendance(subjectId: String, isPresent: Boolean, type: RecordType = RecordType.CLASS) {
        attendanceRepository.save(AttendanceRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            subjectId = subjectId,
            scheduleId = "test-sched",
            date = System.currentTimeMillis() / (24 * 60 * 60 * 1000),
            isPresent = isPresent,
            type = type
        ))
    }
}
