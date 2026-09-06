package com.ankit.attendwise.data.remote.dto

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

@Keep
data class LoginRequest(
    val email: String,
    val password: String
)

@Keep
data class UpdateUserRequest(
    val name: String
)

@Keep
data class ResetPasswordRequest(
    val email: String,
    val newPassword: String
)

@Keep
data class AuthResponse(
    val token: String,
    val user: UserDto
)

@Keep
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val onboardingComplete: Boolean,
    val createdAt: String,
    val updatedAt: String
)
