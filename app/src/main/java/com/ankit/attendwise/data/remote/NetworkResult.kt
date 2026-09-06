package com.ankit.attendwise.data.remote

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String?) : NetworkResult<Nothing>()
    data class Exception(val e: Throwable) : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(apiCall: suspend () -> retrofit2.Response<T>): NetworkResult<T> {
    val maxAttempts = 2
    for (attempts in 1..maxAttempts) {
        try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    return NetworkResult.Success(body)
                } else {
                    return NetworkResult.Error(response.code(), "Empty response body")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val cleanError = if (errorBody?.contains("Database connection error") == true) {
                    "Database connection error. Please try again later."
                } else {
                    errorBody
                }
                return NetworkResult.Error(response.code(), cleanError)
            }
        } catch (e: Exception) {
            // On first attempt, retry after a short delay for temporary network glitches or cold starts
            if (attempts < maxAttempts && (e is SocketTimeoutException || e is UnknownHostException || e is ConnectException)) {
                kotlinx.coroutines.delay(1000)
                continue
            }
            
            val cleanMessage = when (e) {
                is UnknownHostException -> "No internet connection. Please check your network."
                is SocketTimeoutException -> "Server is taking too long to respond. It might be waking up, please try again."
                is ConnectException -> "Failed to connect to the server."
                else -> "An unexpected network error occurred."
            }
            return NetworkResult.Exception(java.lang.Exception(cleanMessage, e))
        }
    }
    return NetworkResult.Exception(java.lang.Exception("Network request failed after retries"))
}
