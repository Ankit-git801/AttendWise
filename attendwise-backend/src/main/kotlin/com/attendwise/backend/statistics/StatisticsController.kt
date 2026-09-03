package com.attendwise.backend.statistics

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class StatisticsController(
    private val statisticsService: StatisticsService
) {

    @GetMapping("/statistics")
    fun getOverallStatistics(@AuthenticationPrincipal userId: String): OverallStatisticsResponse {
        return statisticsService.getOverallStatistics(userId)
    }

    @GetMapping("/subjects/{subjectId}/statistics")
    fun getSubjectStatistics(
        @AuthenticationPrincipal userId: String,
        @PathVariable subjectId: String
    ): SubjectStatisticsResponse {
        return statisticsService.getSubjectStatistics(userId, subjectId)
    }
}
