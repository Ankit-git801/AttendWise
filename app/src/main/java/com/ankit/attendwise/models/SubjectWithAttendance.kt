package com.ankit.attendwise.models

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import com.ankit.attendwise.data.Subject

import com.ankit.attendwise.utils.AttendanceUtils

/**
 * An immutable data class that combines Subject details with its attendance statistics.
 * This is more efficient for Jetpack Compose as it helps avoid unnecessary recompositions.
 *
 * @property subject The core Subject entity. The @Embedded annotation tells Room how to map the cursor to this nested object.
 * @property totalClasses The total number of classes recorded for this subject.
 * @property presentClasses The number of classes marked as present.
 */
@Keep
@Immutable
data class SubjectWithAttendance(
    @Embedded val subject: Subject,
    val totalClasses: Int,
    val presentClasses: Int
) {
    /**
     * Calculates the attendance percentage on-the-fly.
     * This is a cheap calculation and avoids storing redundant data.
     */
    val percentage: Double
        get() = AttendanceUtils.calculatePercentage(presentClasses, totalClasses)
}
