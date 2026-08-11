package com.ankit.attendwise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ankit.attendwise.data.*
import com.ankit.attendwise.utils.AttendanceUtils
import com.ankit.attendwise.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private val actionMutex = Mutex() // Global safety lock for notification actions
        const val ACTION_MARK_ATTENDANCE = "com.ankit.attendwise.ACTION_MARK_ATTENDANCE"
        const val ACTION_MARK_CANCELLED = "com.ankit.attendwise.ACTION_MARK_CANCELLED"
        const val EXTRA_SUBJECT_ID = "EXTRA_SUBJECT_ID"
        const val EXTRA_IS_PRESENT = "EXTRA_IS_PRESENT"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_SCHEDULE_ID = "EXTRA_SCHEDULE_ID"
        const val EXTRA_DATE_EPOCH = "EXTRA_DATE_EPOCH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_ATTENDANCE -> handleMarkAttendance(context, intent)
            ACTION_MARK_CANCELLED -> handleMarkCancelled(context, intent)
        }
    }

    private fun handleMarkAttendance(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                actionMutex.withLock {
                    val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: ""
                    val isPresent = intent.getBooleanExtra(EXTRA_IS_PRESENT, false)
                    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                    val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: ""
                    val targetDate = intent.getLongExtra(EXTRA_DATE_EPOCH, LocalDate.now().toEpochDay())

                    if (subjectId.isNotEmpty()) {
                        val dao = AppDatabase.getDatabase(context).attendanceDao()
                        val cloudSyncManager = CloudSyncManager(context)

                        // TRACKER: Mark as processed to prevent race conditions with dismissal
                        NotificationProcessingTracker.markAsProcessed(notificationId)

                        val allDayRecords = dao.getAllAttendanceRecordsOnDateNow(targetDate)
                        if (allDayRecords.any { it.type == RecordType.HOLIDAY }) {
                            NotificationHelper.cancelNotification(context, notificationId)
                            return@withLock
                        }

                        val existingRecords = dao.getAttendanceRecordsForSubjectOnDate(subjectId, targetDate)
                        val recordIdsToClean = existingRecords.asSequence().filter { it.scheduleId == scheduleId }.map { it.id }.toList()

                        val record = AttendanceRecord(
                            id = UUID.randomUUID().toString(),
                            subjectId = subjectId,
                            scheduleId = scheduleId,
                            date = targetDate,
                            isPresent = isPresent,
                            note = "Marked from notification",
                            type = RecordType.CLASS,
                        )
                        
                        dao.markAttendanceTransaction(recordIdsToClean, record)
                        
                        // SEQUENTIAL SYNC: Ensure cloud backup is complete before receiver finishes
                        if (recordIdsToClean.isNotEmpty()) {
                            cloudSyncManager.deleteAttendanceRecords(recordIdsToClean)
                        }
                        cloudSyncManager.syncAttendanceRecord(record)

                        val subject = dao.getSubjectById(subjectId)
                        subject?.let { sub ->
                            val total = dao.getTotalClassesForSubject(subjectId)
                            val present = dao.getPresentClassesForSubject(subjectId)
                            val newPercentage = AttendanceUtils.calculatePercentage(present, total)

                            NotificationHelper.showUpdatedAttendanceNotification(context, subjectId, sub.name, newPercentage, notificationId, wasCancelled = false)

                            if ((newPercentage < sub.targetAttendance) && (total > 0)) {
                                NotificationHelper.showAttendanceWarningNotification(context, sub, newPercentage)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error marking attendance: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkCancelled(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                actionMutex.withLock {
                    val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: ""
                    val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: ""
                    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                    val targetDate = intent.getLongExtra(EXTRA_DATE_EPOCH, LocalDate.now().toEpochDay())

                    if (subjectId.isNotEmpty()) {
                        val dao = AppDatabase.getDatabase(context).attendanceDao()
                        val cloudSyncManager = CloudSyncManager(context)

                        // TRACKER: Mark as processed to prevent race conditions with dismissal
                        NotificationProcessingTracker.markAsProcessed(notificationId)

                        val allDayRecords = dao.getAllAttendanceRecordsOnDateNow(targetDate)
                        if (allDayRecords.any { it.type == RecordType.HOLIDAY }) {
                            NotificationHelper.cancelNotification(context, notificationId)
                            return@withLock
                        }

                        val existingRecords = dao.getAttendanceRecordsForSubjectOnDate(subjectId, targetDate)
                        val recordIdsToClean = existingRecords.asSequence().filter { it.scheduleId == scheduleId }.map { it.id }.toList()

                        val record = AttendanceRecord(
                            id = UUID.randomUUID().toString(),
                            subjectId = subjectId,
                            scheduleId = scheduleId,
                            date = targetDate,
                            isPresent = false,
                            note = "Class Cancelled",
                            type = RecordType.CANCELLED,
                        )
                        
                        dao.markAttendanceTransaction(recordIdsToClean, record)
                        
                        // SEQUENTIAL SYNC: Ensure cloud backup is complete before receiver finishes
                        if (recordIdsToClean.isNotEmpty()) {
                            cloudSyncManager.deleteAttendanceRecords(recordIdsToClean)
                        }
                        cloudSyncManager.syncAttendanceRecord(record)

                        val subject = dao.getSubjectById(subjectId)
                        subject?.let { sub ->
                            val total = dao.getTotalClassesForSubject(subjectId)
                            val present = dao.getPresentClassesForSubject(subjectId)
                            val newPercentage = AttendanceUtils.calculatePercentage(present, total)
                            
                            NotificationHelper.showUpdatedAttendanceNotification(context, subjectId, sub.name, newPercentage, notificationId, wasCancelled = true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error marking cancelled: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
