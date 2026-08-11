package com.ankit.attendwise.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ankit.attendwise.data.ClassSchedule
import com.ankit.attendwise.data.Subject
import com.ankit.attendwise.receivers.AlarmReceiver
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    const val ACTION_CLASS_REMINDER = "com.ankit.attendwise.ACTION_CLASS_REMINDER"

    fun scheduleClassAlarm(context: Context, subject: Subject, schedule: ClassSchedule, forceNextWeek: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // MODERN FIX: Using java.time for robust scheduling
        // Convert Calendar day (1=Sun, 2=Mon) to java.time.DayOfWeek (1=Mon, 7=Sun)
        val targetDayOfWeek = if (schedule.dayOfWeek == 1) DayOfWeek.SUNDAY else DayOfWeek.of(schedule.dayOfWeek - 1)
        val targetTime = LocalTime.of(schedule.endHour, schedule.endMinute)
        val startTime = LocalTime.of(schedule.startHour, schedule.startMinute)
        val isOvernight = targetTime.isBefore(startTime) || (targetTime == startTime)
        
        val now = LocalDateTime.now()
        var alarmDateTime = if (forceNextWeek) {
            now.with(TemporalAdjusters.next(targetDayOfWeek))
        } else {
            now.with(TemporalAdjusters.nextOrSame(targetDayOfWeek))
        }
        
        alarmDateTime = alarmDateTime.with(targetTime)
            .withSecond(0)
            .withNano(0)

        // The session date is the date the class STARTS.
        // For non-overnight, sessionDate = alarmDate.
        // For overnight, the alarm fires on sessionDate + 1, so sessionDate = alarmDate - 1.
        var sessionDate = alarmDateTime.toLocalDate()
        if (isOvernight) {
            alarmDateTime = alarmDateTime.plusDays(1)
        }

        // If the calculated time has already passed, schedule for next week
        if (!forceNextWeek && alarmDateTime.isBefore(now.plusSeconds(10))) {
            alarmDateTime = now.with(TemporalAdjusters.next(targetDayOfWeek))
            sessionDate = alarmDateTime.toLocalDate()
            
            // Re-apply time and overnight adjustment if we jumped to next week
            alarmDateTime = alarmDateTime.with(targetTime).withSecond(0).withNano(0)
            if (isOvernight) {
                alarmDateTime = alarmDateTime.plusDays(1)
            }
        }

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            putExtra("subject_id", subject.id)
            putExtra("schedule_id", schedule.id)
            putExtra("EXTRA_SESSION_DATE", sessionDate.toEpochDay())
        }

        val requestCode = schedule.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMs = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        Log.d(TAG, "Scheduling alarm for ${subject.name} at: ${alarmDateTime.format(formatter)}")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule exact alarm: Permission missing (SCHEDULE_EXACT_ALARM)")
                    return
                }
            }

            // CRITICAL FIX: Use setAlarmClock for absolute precision. 
            // It is the highest priority alarm and forces the device to wake up exactly on time, 
            // even if the system is restricting other background activities.
            val clockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, null)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            
            Log.d(TAG, "Alarm successfully set for: ${subject.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm: ${e.message}")
        }
    }

    fun cancelClassAlarm(context: Context, schedule: ClassSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("subject_id", schedule.subjectId)
            putExtra("schedule_id", schedule.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Canceled alarm for schedule ID: ${schedule.id}")
        }
    }
}
