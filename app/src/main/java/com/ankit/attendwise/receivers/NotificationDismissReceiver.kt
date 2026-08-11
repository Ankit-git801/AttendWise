package com.ankit.attendwise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ankit.attendwise.data.AppDatabase
import com.ankit.attendwise.data.RecordType
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_MANUAL
import com.ankit.attendwise.utils.NotificationHelper
import kotlinx.coroutines.*
import java.time.LocalDate

class NotificationDismissReceiver : BroadcastReceiver() {
    private val tag = "NotificationDismiss"

    override fun onReceive(context: Context, intent: Intent) {
        val subjectId = intent.getStringExtra("subject_id") ?: ""
        val scheduleId = intent.getStringExtra("schedule_id") ?: ""
        val sessionDateEpoch = intent.getLongExtra("EXTRA_SESSION_DATE", LocalDate.now().toEpochDay())

        if (subjectId.isEmpty() || scheduleId.isEmpty()) return

        // TRACKER: If this notification was just processed by an action OR in-app mark, do nothing
        if (NotificationProcessingTracker.isRecentlyProcessed(subjectId, scheduleId)) {
            Log.d(tag, "Notification for $subjectId / $scheduleId was recently processed. Ignoring dismiss.")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).attendanceDao()
                
                // CRITICAL: Check if it was already marked (Present/Absent/Cancelled/Holiday)
                // before re-posting. This prevents re-posting if marked just before swipe.
                val records = dao.getAttendanceRecordsForSubjectOnDate(subjectId, sessionDateEpoch)
                val isMarked = records.any { 
                    it.scheduleId == scheduleId || it.scheduleId == ID_SCHEDULE_MANUAL 
                }
                
                val dateRecords = dao.getAllAttendanceRecordsOnDateNow(sessionDateEpoch)
                val isHoliday = dateRecords.any { it.type == RecordType.HOLIDAY }

                if (!isMarked && !isHoliday) {
                    Log.d(tag, "Notification swiped but not marked. Re-posting for $subjectId (Session: $sessionDateEpoch)")
                    val subject = dao.getSubjectById(subjectId)
                    val schedule = dao.getScheduleById(scheduleId)
                    
                    if (subject != null && schedule != null) {
                        NotificationHelper.showAttendanceNotification(context, subject, schedule, sessionDateEpoch)
                    }
                } else {
                    Log.d(tag, "Notification swiped but already marked or holiday. Not re-posting.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in NotificationDismissReceiver: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
