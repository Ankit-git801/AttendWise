package com.ankit.attendwise.receivers

import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe tracker to prevent race conditions between notification actions 
 * and notification dismissals.
 */
object NotificationProcessingTracker {
    private val processedNotifications = ConcurrentHashMap<String, Long>()
    private const val EXPIRATION_MS = 10000 // 10 seconds expiration

    fun markAsProcessed(notificationId: Int) {
        processedNotifications[notificationId.toString()] = System.currentTimeMillis()
    }

    fun markAsProcessed(subjectId: String, scheduleId: String) {
        val key = "${subjectId}_${scheduleId}"
        processedNotifications[key] = System.currentTimeMillis()
    }

    fun isRecentlyProcessed(notificationId: Int): Boolean {
        if (checkKey(notificationId.toString())) return true
        return false
    }

    fun isRecentlyProcessed(subjectId: String, scheduleId: String): Boolean {
        val key = "${subjectId}_${scheduleId}"
        return checkKey(key) || checkKey(scheduleId.hashCode().toString())
    }

    private fun checkKey(key: String): Boolean {
        val timestamp = processedNotifications[key] ?: return false
        val isRecent = (System.currentTimeMillis() - timestamp) < EXPIRATION_MS
        if (!isRecent) processedNotifications.remove(key)
        return isRecent
    }

    fun clearExpired() {
        val now = System.currentTimeMillis()
        processedNotifications.entries.removeIf { (now - it.value) > EXPIRATION_MS }
    }
}
