package com.ankit.attendwise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ankit.attendwise.data.AppDatabase
import com.ankit.attendwise.utils.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED) {
            
            val pendingResult = goAsync()
            // It's safe to launch a coroutine here to do the work off the main thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getDatabase(context).attendanceDao()

                    // Get all subjects from the database (DAO already filters out the internal System subject)
                    val allSubjects = dao.getAllSubjects().first()

                    // For each subject, get its schedules and reschedule the alarms
                    for (subject in allSubjects) {
                        val schedules = dao.getSchedulesForSubject(subject.id)
                        schedules.forEach { schedule ->
                            AlarmScheduler.scheduleClassAlarm(context, subject, schedule)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
