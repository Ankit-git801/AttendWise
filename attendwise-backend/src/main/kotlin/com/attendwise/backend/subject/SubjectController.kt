package com.attendwise.backend.subject

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/subjects")
class SubjectController(
    private val subjectService: SubjectService
) {

    @GetMapping
    fun getAllSubjects(@AuthenticationPrincipal userId: String): List<SubjectResponse> {
        return subjectService.getAllSubjectsForUser(userId)
    }

    @GetMapping("/{id}")
    fun getSubject(@AuthenticationPrincipal userId: String, @PathVariable id: String): SubjectResponse {
        return subjectService.getSubjectById(userId, id)
    }

    @PostMapping
    fun createSubject(@AuthenticationPrincipal userId: String, @Valid @RequestBody request: SubjectRequest): ResponseEntity<SubjectResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubject(userId, request))
    }

    @PutMapping("/{id}")
    fun updateSubject(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: String,
        @Valid @RequestBody request: SubjectRequest
    ): SubjectResponse {
        return subjectService.updateSubject(userId, id, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSubject(@AuthenticationPrincipal userId: String, @PathVariable id: String) {
        subjectService.deleteSubject(userId, id)
    }
}
