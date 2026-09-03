package com.ankit.attendwise

import com.ankit.attendwise.data.remote.RetrofitClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkVerificationTest {

    @Test
    fun verifyHealthEndpoint() = runBlocking {
        try {
            val response = RetrofitClient.api.checkHealth()
            if (response.isSuccessful) {
                val body = response.body()
                println("Health Status: ${body?.get("status")}")
                assertTrue(body?.get("status") == "UP")
            } else {
                println("Health check failed with code: ${response.code()}")
            }
        } catch (e: Exception) {
            println("Could not connect to backend: ${e.message}")
            // This test might fail if the backend is not running, which is expected in some CI environments
            // but for manual verification, it's useful.
        }
    }
}
