package com.ankit.attendwise.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ankit.attendwise.MainActivity
import com.ankit.attendwise.R
import com.ankit.attendwise.data.ClassSchedule
import com.ankit.attendwise.data.Subject
import com.ankit.attendwise.receivers.NotificationActionReceiver
import com.ankit.attendwise.receivers.NotificationDismissReceiver
import java.time.LocalDate

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "attendance_channel"
    private const val WARNING_CHANNEL_ID = "warning_channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(CHANNEL_ID, "Attendance Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Class attendance reminder notifications"
            }
            val warningChannel = NotificationChannel(WARNING_CHANNEL_ID, "Low Attendance Warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Low attendance warning notifications"
            }

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    fun buildAttendanceNotification(context: Context, subject: Subject, schedule: ClassSchedule, sessionDateEpoch: Long): Notification {
        val notificationId = schedule.id.hashCode()

        val presentIntent = createActionIntent(context, subject.id, schedule.id, notificationId, true, sessionDateEpoch)
        val absentIntent = createActionIntent(context, subject.id, schedule.id, notificationId, false, sessionDateEpoch)
        val cancelIntent = createCancelActionIntent(context, subject.id, schedule.id, notificationId, sessionDateEpoch)

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra("subject_id", subject.id)
                putExtra("schedule_id", schedule.id)
                putExtra("EXTRA_SESSION_DATE", sessionDateEpoch)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("subject_id", subject.id) // SMART NAV: Route to specific subject history
        }
        val pendingMainIntent = PendingIntent.getActivity(context, notificationId, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = Html.fromHtml(context.getString(R.string.attendance_for_label, "<b>${subject.name}</b>"), Html.FROM_HTML_MODE_LEGACY)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingMainIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.notification_action_present), presentIntent)
            .addAction(0, context.getString(R.string.notification_action_absent), absentIntent)
            .addAction(0, context.getString(R.string.notification_action_cancel), cancelIntent)
            .build()
    }

    fun showAttendanceNotification(context: Context, subject: Subject, schedule: ClassSchedule, sessionDateEpoch: Long) {
        val notificationId = schedule.id.hashCode()
        val notification = buildAttendanceNotification(context, subject, schedule, sessionDateEpoch)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showUpdatedAttendanceNotification(context: Context, subjectId: String, subjectName: String, newPercentage: Double, notificationId: Int, wasCancelled: Boolean) {
        val message = if (wasCancelled) context.getString(R.string.notification_cancelled_text) else context.getString(R.string.notification_marked_text, newPercentage)
        
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("subject_id", subjectId)
        }
        val pendingMainIntent = PendingIntent.getActivity(context, notificationId + 1, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = Html.fromHtml("<b>$subjectName</b>", Html.FROM_HTML_MODE_LEGACY)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingMainIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showAttendanceWarningNotification(context: Context, subject: Subject, newPercentage: Double) {
        val notificationId = subject.id.hashCode() + 1000

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("subject_id", subject.id)
        }
        val pendingMainIntent = PendingIntent.getActivity(context, notificationId, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val content = Html.fromHtml(context.getString(R.string.notification_warning_text, "<b>${subject.name}</b>", newPercentage), Html.FROM_HTML_MODE_LEGACY)

        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Low Attendance Warning")
            .setContentText(content)
            .setContentIntent(pendingMainIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun createActionIntent(context: Context, subjectId: String, scheduleId: String, notificationId: Int, isPresent: Boolean, dateEpoch: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_ATTENDANCE
            putExtra(NotificationActionReceiver.EXTRA_SUBJECT_ID, subjectId)
            putExtra(NotificationActionReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_IS_PRESENT, isPresent)
            putExtra(NotificationActionReceiver.EXTRA_DATE_EPOCH, dateEpoch)
        }
        return PendingIntent.getBroadcast(context, (notificationId * 10) + if (isPresent) 1 else 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createCancelActionIntent(context: Context, subjectId: String, scheduleId: String, notificationId: Int, dateEpoch: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_CANCELLED
            putExtra(NotificationActionReceiver.EXTRA_SUBJECT_ID, subjectId)
            putExtra(NotificationActionReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_DATE_EPOCH, dateEpoch)
        }
        return PendingIntent.getBroadcast(context, notificationId * 10 + 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
