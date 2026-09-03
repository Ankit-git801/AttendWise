package com.attendwise.backend.auth

import com.attendwise.backend.user.User
import com.attendwise.backend.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.findByEmail(request.email).isPresent) {
            throw IllegalArgumentException("User with this email already exists")
        }

        val user = User(
            id = UUID.randomUUID().toString(),
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name
        )

        val savedUser = userRepository.save(user)
        val token = jwtService.generateToken(savedUser.id)

        return AuthResponse(token, savedUser.toResponse())
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalArgumentException("Invalid email or password") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val token = jwtService.generateToken(user.id)
        return AuthResponse(token, user.toResponse())
    }

    fun getUserById(userId: String): UserResponse {
        return userRepository.findById(userId)
            .map { it.toResponse() }
            .orElseThrow { NoSuchElementException("User not found") }
    }

    fun updateUser(userId: String, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val updatedUser = user.copy(
            name = request.name
        )
        
        return userRepository.save(updatedUser).toResponse()
    }

    private fun User.toResponse() = UserResponse(
        id = id,
        email = email,
        name = name,
        onboardingComplete = onboardingComplete,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
