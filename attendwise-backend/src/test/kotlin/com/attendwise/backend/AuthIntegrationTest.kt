package com.attendwise.backend

import com.attendwise.backend.auth.AuthResponse
import com.attendwise.backend.auth.LoginRequest
import com.attendwise.backend.auth.RegisterRequest
import com.attendwise.backend.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
    }

    @Test
    fun `test registration and login flow`() {
        val registerRequest = RegisterRequest(
            email = "test@example.com",
            password = "password123",
            name = "Test User"
        )

        // 1. Register
        val registerResult = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { exists() }
            jsonPath("$.user.email") { value("test@example.com") }
        }.andReturn()

        val authResponse = objectMapper.readValue(registerResult.response.contentAsString, AuthResponse::class.java)
        val token = authResponse.token

        // 2. Verify password is hashed in DB
        val savedUser = userRepository.findByEmail("test@example.com").get()
        assertTrue(passwordEncoder.matches("password123", savedUser.passwordHash))

        // 3. Login
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
        }

        // 4. Get Current User (Me)
        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("test@example.com") }
            jsonPath("$.name") { value("Test User") }
        }
    }

    @Test
    fun `test unauthorized access rejection`() {
        mockMvc.get("/api/auth/me")
            .andExpect { status { isForbidden() } } // Spring Security default for unauthenticated is often 403 or 401 depending on config
    }

    @Test
    fun `test duplicate registration rejection`() {
        val registerRequest = RegisterRequest(
            email = "dup@example.com",
            password = "password123",
            name = "User"
        )
        userRepository.save(com.attendwise.backend.user.User(
            id = "1",
            email = "dup@example.com",
            passwordHash = "hash",
            name = "User"
        ))

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `test invalid login rejection`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest("no@test.com", "wrong"))
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
