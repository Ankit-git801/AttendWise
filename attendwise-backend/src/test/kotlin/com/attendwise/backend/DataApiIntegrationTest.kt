package com.attendwise.backend

import com.attendwise.backend.auth.AuthResponse
import com.attendwise.backend.auth.RegisterRequest
import com.attendwise.backend.subject.SubjectRequest
import com.attendwise.backend.subject.SubjectResponse
import com.attendwise.backend.schedule.ScheduleRequest
import com.attendwise.backend.schedule.ScheduleResponse
import com.attendwise.backend.attendance.AttendanceRequest
import com.attendwise.backend.user.UserRepository
import com.attendwise.backend.subject.SubjectRepository
import com.attendwise.backend.schedule.ScheduleRepository
import com.attendwise.backend.attendance.AttendanceRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class DataApiIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository
    
    @Autowired
    lateinit var subjectRepository: SubjectRepository
    
    @Autowired
    lateinit var scheduleRepository: ScheduleRepository
    
    @Autowired
    lateinit var attendanceRepository: AttendanceRecordRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private lateinit var tokenUser1: String
    private lateinit var tokenUser2: String

    @BeforeEach
    fun setup() {
        attendanceRepository.deleteAll()
        scheduleRepository.deleteAll()
        subjectRepository.deleteAll()
        userRepository.deleteAll()

        tokenUser1 = registerAndGetToken("user1@test.com", "User One")
        tokenUser2 = registerAndGetToken("user2@test.com", "User Two")
    }

    private fun registerAndGetToken(email: String, name: String): String {
        val result = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RegisterRequest(email, "password", name))
        }.andReturn()
        return objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).token
    }

    @Test
    fun `test subject lifecycle and ownership`() {
        val request = SubjectRequest(name = "Math")
        
        // 1. User 1 creates subject
        val result = mockMvc.post("/api/subjects") {
            header("Authorization", "Bearer $tokenUser1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Math") }
        }.andReturn()

        val subjectId = objectMapper.readValue(result.response.contentAsString, SubjectResponse::class.java).id

        // 2. User 2 cannot see User 1's subject
        mockMvc.get("/api/subjects/$subjectId") {
            header("Authorization", "Bearer $tokenUser2")
        }.andExpect {
            status { isForbidden() }
        }

        // 3. User 1 can see their subject
        mockMvc.get("/api/subjects") {
            header("Authorization", "Bearer $tokenUser1")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(subjectId) }
        }
    }

    @Test
    fun `test schedule and attendance integration`() {
        // Setup: User 1 Subject
        val subResult = mockMvc.post("/api/subjects") {
            header("Authorization", "Bearer $tokenUser1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubjectRequest("Physics"))
        }.andReturn()
        val subjectId = objectMapper.readValue(subResult.response.contentAsString, SubjectResponse::class.java).id

        // 1. Create Schedule
        val schedRequest = ScheduleRequest(subjectId, 1, 10, 0, 11, 0)
        val schedResult = mockMvc.post("/api/subjects/$subjectId/schedules") {
            header("Authorization", "Bearer $tokenUser1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(schedRequest)
        }.andExpect {
            status { isCreated() }
        }.andReturn()
        val scheduleId = objectMapper.readValue(schedResult.response.contentAsString, ScheduleResponse::class.java).id

        // 2. Create Attendance
        val attRequest = AttendanceRequest(subjectId, scheduleId, 19000L, true, "Good class")
        mockMvc.post("/api/attendance") {
            header("Authorization", "Bearer $tokenUser1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(attRequest)
        }.andExpect {
            status { isCreated() }
        }

        // 3. Verify List
        mockMvc.get("/api/subjects/$subjectId/attendance") {
            header("Authorization", "Bearer $tokenUser1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].note") { value("Good class") }
        }
    }

    @Test
    fun `test invalid subject ownership rejection`() {
        // User 1's subject
        val sub1 = subjectRepository.save(com.attendwise.backend.subject.Subject(UUID.randomUUID().toString(), "u1", "Math"))

        // User 2 tries to create schedule for User 1's subject
        mockMvc.post("/api/subjects/${sub1.id}/schedules") {
            header("Authorization", "Bearer $tokenUser2")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ScheduleRequest(sub1.id, 1, 9, 0, 10, 0))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
