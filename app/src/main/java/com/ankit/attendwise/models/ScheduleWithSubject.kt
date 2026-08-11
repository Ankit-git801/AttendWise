package com.ankit.attendwise.models

import androidx.annotation.Keep
import com.ankit.attendwise.data.AttendanceRecord
import com.ankit.attendwise.data.ClassSchedule
import com.ankit.attendwise.data.Subject
import java.time.LocalDate
import java.time.LocalTime

@Keep
data class ScheduleWithSubject(
    val schedule: ClassSchedule,
    val subject: Subject,
    val attendanceRecord: AttendanceRecord? = null,
    val isLive: Boolean = false,
    val isCompleted: Boolean = false,
    val effectiveDate: LocalDate? = null
)
