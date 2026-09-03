package com.attendwise.backend.attendance

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class AttendanceController(
    private val attendanceService: AttendanceService
) {

    @GetMapping("/subjects/{subjectId}/attendance")
    fun getAttendanceForSubject(@AuthenticationPrincipal userId: String, @PathVariable subjectId: String): List<AttendanceResponse> {
        return attendanceService.getAttendanceForSubject(userId, subjectId)
    }

    @PostMapping("/attendance")
    fun createAttendance(@AuthenticationPrincipal userId: String, @Valid @RequestBody request: AttendanceRequest): ResponseEntity<AttendanceResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.createAttendance(userId, request))
    }

    @PutMapping("/attendance/{id}")
    fun updateAttendance(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: String,
        @Valid @RequestBody request: AttendanceRequest
    ): AttendanceResponse {
        return attendanceService.updateAttendance(userId, id, request)
    }

    @DeleteMapping("/attendance/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAttendance(@AuthenticationPrincipal userId: String, @PathVariable id: String) {
        attendanceService.deleteAttendance(userId, id)
    }
}
