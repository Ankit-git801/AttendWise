package com.ankit.attendwise.utils

import com.ankit.attendwise.data.BunkAnalysis
import kotlin.math.ceil

object AttendanceUtils {
    /**
     * Calculates the attendance percentage safely.
     */
    fun calculatePercentage(present: Int, total: Int): Double {
        return if (total > 0) {
            (present.toDouble() / total) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Formats the percentage to one decimal place as a string.
     */
    fun formatPercentage(percentage: Double): String {
        return "%.1f%%".format(percentage)
    }

    /**
     * Performs a mathematical bunk analysis based on target attendance.
     */
    fun calculateBunkAnalysis(present: Int, total: Int, target: Double): BunkAnalysis {
        if (total == 0) return BunkAnalysis(0, 0)
        
        // BOUNDARY CHECK: Target 0%
        if (target <= 0) return BunkAnalysis(classesToBunk = 999, classesToAttend = 0)
        
        // BOUNDARY CHECK: Target 100%
        if (target >= 100.0) {
            return if (present >= total) BunkAnalysis(0, 0) 
            else BunkAnalysis(0, 999) // User-friendly 'Impossible' cap
        }

        val currentPercentage = calculatePercentage(present, total)

        return if (currentPercentage >= target) {
            // Formula: B <= (100*A / T) - N
            val bunksAllowed = ((100.0 * present) / target).toInt() - total
            BunkAnalysis(classesToBunk = bunksAllowed.coerceAtLeast(0), classesToAttend = 0)
        } else {
            // Formula: M >= (T*N - 100*A) / (100 - T)
            val mustAttend = ceil(((target * total) - (100.0 * present)) / (100.0 - target)).toInt()
            BunkAnalysis(classesToBunk = 0, classesToAttend = mustAttend.coerceAtLeast(0))
        }
    }
}
