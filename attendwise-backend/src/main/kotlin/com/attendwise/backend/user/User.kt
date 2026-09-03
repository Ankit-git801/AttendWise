package com.attendwise.backend.user

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id
    val id: String,
    @Indexed(unique = true)
    val email: String,
    val passwordHash: String,
    val name: String,
    val onboardingComplete: Boolean = false,
    
    @CreatedDate
    val createdAt: Instant = Instant.now(),
    @LastModifiedDate
    val updatedAt: Instant = Instant.now()
)
