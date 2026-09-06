package com.attendwise.backend.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class OwnershipException(message: String) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        val message = e.message ?: "Invalid request"
        val status = when {
            message.contains("already exists") -> HttpStatus.CONFLICT
            message.contains("Invalid email or password") -> HttpStatus.UNAUTHORIZED
            else -> HttpStatus.BAD_REQUEST
        }
        logger.warn("Illegal argument exception: {}", message)
        return ResponseEntity.status(status).body(mapOf("error" to message))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, String>> {
        logger.warn("Not found exception: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "Resource not found")))
    }

    @ExceptionHandler(OwnershipException::class)
    fun handleOwnership(e: OwnershipException): ResponseEntity<Map<String, String>> {
        logger.warn("Ownership exception: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "Access denied")))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        logger.warn("Validation exception: {}", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "Validation failed", "details" to errors))
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<Map<String, String>> {
        logger.error("Unhandled exception occurred: ", e)
        val userFriendlyMessage = when {
            e.message?.contains("Mongo") == true || e.message?.contains("Timed out") == true -> "Database connection error. Please try again."
            else -> "An unexpected error occurred. Please try again."
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to userFriendlyMessage))
    }
}
