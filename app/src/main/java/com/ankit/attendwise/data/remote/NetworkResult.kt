package com.ankit.attendwise.data.remote

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String?) : NetworkResult<Nothing>()
    data class Exception(val e: Throwable) : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(apiCall: suspend () -> retrofit2.Response<T>): NetworkResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.Error(response.code(), "Empty response body")
            }
        } else {
            NetworkResult.Error(response.code(), response.errorBody()?.string())
        }
    } catch (e: Exception) {
        NetworkResult.Exception(e)
    }
}
