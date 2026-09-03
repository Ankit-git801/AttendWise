package com.ankit.attendwise.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_JWT_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
    }

    val jwtToken: Flow<String?> = context.dataStore.data.map { it[KEY_JWT_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }

    suspend fun saveSession(token: String, id: String, email: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_JWT_TOKEN] = token
            prefs[KEY_USER_ID] = id
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_JWT_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
        }
    }
}
