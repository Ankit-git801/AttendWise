package com.ankit.attendwise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ankit.attendwise.data.AppDatabase
import com.ankit.attendwise.data.RecordType
import com.ankit.attendwise.utils.AlarmScheduler
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_MANUAL
import com.ankit.attendwise.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    private val tag = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(tag, "AlarmReceiver triggered with action: $action")

        val subjectId = intent.getStringExtra("subject_id") ?: ""
        val scheduleId = intent.getStringExtra("schedule_id") ?: ""
        val sessionDateEpoch = intent.getLongExtra("EXTRA_SESSION_DATE", LocalDate.now().toEpochDay())

        if (subjectId.isNotEmpty() && scheduleId.isNotEmpty()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getDatabase(context).attendanceDao()
                    
                    val subject = dao.getSubjectById(subjectId)
                    val schedule = dao.getScheduleById(scheduleId)
                    
                    if (subject == null || schedule == null) {
                        Log.e(tag, "Subject or Schedule not found in DB. Stopping alarm chain.")
                        return@launch
                    }

                    // PRIORITY FIX: Post notification immediately before database checks
                    // This reduces the chance of being killed before the user sees it.
                    val existingRecords = dao.getAttendanceRecordsForSubjectOnDate(subjectId, sessionDateEpoch)
                    val isAlreadyMarked = existingRecords.any { 
                        it.scheduleId == scheduleId || it.scheduleId == ID_SCHEDULE_MANUAL
                    }

                    val dateRecords = dao.getAllAttendanceRecordsOnDateNow(sessionDateEpoch)
                    val isHoliday = dateRecords.any { it.type == RecordType.HOLIDAY }

                    if (!isAlreadyMarked && !isHoliday) {
                        Log.d(tag, "Showing notification for ${subject.name} (Session: $sessionDateEpoch)")
                        NotificationHelper.showAttendanceNotification(context, subject, schedule, sessionDateEpoch)
                    }

                    // MAINTENANCE: Reschedule for next week
                    AlarmScheduler.scheduleClassAlarm(context, subject, schedule, forceNextWeek = true)
                } catch (e: Exception) {
                    Log.e(tag, "Error in AlarmReceiver: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
