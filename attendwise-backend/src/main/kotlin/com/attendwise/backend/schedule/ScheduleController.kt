package com.attendwise.backend.schedule

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ScheduleController(
    private val scheduleService: ScheduleService
) {

    @GetMapping("/subjects/{subjectId}/schedules")
    fun getSchedulesForSubject(@AuthenticationPrincipal userId: String, @PathVariable subjectId: String): List<ScheduleResponse> {
        return scheduleService.getSchedulesForSubject(userId, subjectId)
    }

    @PostMapping("/subjects/{subjectId}/schedules")
    fun createSchedule(
        @AuthenticationPrincipal userId: String,
        @PathVariable subjectId: String,
        @Valid @RequestBody request: ScheduleRequest
    ): ResponseEntity<ScheduleResponse> {
        // Ensure subjectId in path matches request body or use body
        val finalRequest = if (request.subjectId != subjectId) request.copy(subjectId = subjectId) else request
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createSchedule(userId, finalRequest))
    }

    @PutMapping("/schedules/{id}")
    fun updateSchedule(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: String,
        @Valid @RequestBody request: ScheduleRequest
    ): ScheduleResponse {
        return scheduleService.updateSchedule(userId, id, request)
    }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSchedule(@AuthenticationPrincipal userId: String, @PathVariable id: String) {
        scheduleService.deleteSchedule(userId, id)
    }
}
