package com.ankit.attendwise

import android.app.Application
import com.ankit.attendwise.data.AppDatabase
import com.ankit.attendwise.data.local.LocalDataSource
import com.ankit.attendwise.data.remote.RemoteDataSource
import com.ankit.attendwise.data.remote.RetrofitClient
import com.ankit.attendwise.data.remote.SessionManager
import com.ankit.attendwise.data.repository.AttendWiseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AttendWiseApplication : Application() {

    lateinit var repository: AttendWiseRepository
        private set

    lateinit var sessionManager: SessionManager
        private set
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize Session Manager
        sessionManager = SessionManager(this)

        // Sync token with Retrofit Client
        applicationScope.launch {
            sessionManager.jwtToken.collect { token ->
                RetrofitClient.setAuthToken(token)
            }
        }

        // Initialize Repository
        val database = AppDatabase.getDatabase(this)
        val localDataSource = LocalDataSource(database.attendanceDao(), database.pendingOperationDao())
        val remoteDataSource = RemoteDataSource(RetrofitClient.api)
        repository = AttendWiseRepository(localDataSource, remoteDataSource, sessionManager)

        // PRE-WARM SERVER: Fire async ping on app launch so Render wakes up before user clicks Sign In
        applicationScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.checkHealth()
            } catch (_: Exception) {
                // Fire-and-forget pre-warm ping
            }
        }
    }
}
