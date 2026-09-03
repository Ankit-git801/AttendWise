package com.attendwise.backend.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun healthCheck(): Map<String, String> {
        return mapOf("status" to "UP")
    }
}
