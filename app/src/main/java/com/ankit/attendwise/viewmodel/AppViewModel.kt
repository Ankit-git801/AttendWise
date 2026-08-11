/*
 * Copyright (c) 2026 Ankit. All rights reserved.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.ankit.attendwise.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ankit.attendwise.data.*
import com.ankit.attendwise.models.AttendanceRecordWithSubject
import com.ankit.attendwise.models.AttendanceStatistics
import com.ankit.attendwise.models.ScheduleWithSubject
import com.ankit.attendwise.models.SubjectWithAttendance
import com.ankit.attendwise.receivers.NotificationProcessingTracker
import com.ankit.attendwise.utils.AlarmScheduler
import com.ankit.attendwise.utils.AttendanceUtils
import com.ankit.attendwise.utils.Constants.EXTRA_CLASS_START_HOUR
import com.ankit.attendwise.utils.Constants.EXTRA_CLASS_START_MINUTE
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_EXTRA
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_HOLIDAY
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_MANUAL
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_PAST
import com.ankit.attendwise.utils.Constants.ID_SUBJECT_HOLIDAY
import com.ankit.attendwise.utils.NotificationHelper
import com.ankit.attendwise.R
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val attendanceDao = AppDatabase.getDatabase(application).attendanceDao()
    private val preferencesManager = PreferencesManager(application)
    private val cloudSyncManager = CloudSyncManager(application)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(application)

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = attendanceDao.getAllAttendanceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubjects: StateFlow<List<Subject>> = attendanceDao.getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjectsWithAttendance: StateFlow<List<SubjectWithAttendance>> =
        attendanceDao.getSubjectsWithAttendance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSchedules: StateFlow<List<ClassSchedule>> = attendanceDao.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val theme = preferencesManager.themeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "System Default",
    )
    val userName = preferencesManager.userNameFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "",
    )

    val isOnboardingComplete: StateFlow<Boolean?> = preferencesManager.isOnboardingCompleteFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // REACIVE DATE: Updates every minute to handle midnight transitions
    val currentDate: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(1.minutes) // 1 minute for precise UI updates
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    val isTodayHoliday: StateFlow<Boolean> = currentDate
        .flatMapLatest { date -> attendanceDao.isDateHolidayFlow(date.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dashboardScheduleWithSubjects: StateFlow<Pair<Boolean, List<ScheduleWithSubject>>> =
        getDashboardSchedule().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Pair(false, emptyList())
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _attendanceActionFeedback = MutableSharedFlow<String>()
    val attendanceActionFeedback = _attendanceActionFeedback.asSharedFlow()

    private val _showHolidayDialog = MutableStateFlow<LocalDate?>(null)

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _navigationEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private val _isForceUpdate = MutableStateFlow(false)
    val isForceUpdate: StateFlow<Boolean> = _isForceUpdate.asStateFlow()

    private val _showTomorrowPreview = MutableStateFlow(false)
    val showTomorrowPreview: StateFlow<Boolean> = _showTomorrowPreview.asStateFlow()

    init {
        // Start real-time sync if user is already logged in
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            cloudSyncManager.startRealTimeSync(attendanceDao, viewModelScope)
        }

        // SELF-HEALING: Reschedule all alarms on startup in background to ensure system consistency
        viewModelScope.launch(Dispatchers.IO) {
            rescheduleAllAlarms()
        }

        viewModelScope.launch {
            try {
                // Non-blocking check for updates
                val updateInfo = cloudSyncManager.getUpdateInfo()
                if (updateInfo.latestVersionCode > com.ankit.attendwise.BuildConfig.VERSION_CODE) {
                    _isForceUpdate.value = updateInfo.isForceUpdate
                    _updateAvailable.value = true
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Update check failed: ${e.message}")
            }
        }
    }

    fun toggleTomorrowPreview() {
        val newState = !_showTomorrowPreview.value
        _showTomorrowPreview.value = newState
        
        if (newState) {
            viewModelScope.launch {
                val today = currentDate.value
                val tomorrow = today.plusDays(1)
                val tomorrowDayOfWeek = (tomorrow.dayOfWeek.value % 7) + 1
                
                val isTomorrowHoliday = attendanceDao.isDateHolidayFlow(tomorrow.toEpochDay()).first()
                val tomorrowSchedules = attendanceDao.getSchedulesForDayNow(tomorrowDayOfWeek)
                
                if (isTomorrowHoliday) {
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_tomorrow_is_holiday))
                } else if (tomorrowSchedules.isEmpty()) {
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_no_classes_tomorrow))
                }
            }
        }
    }

    override fun onCleared() {
        cloudSyncManager.stopRealTimeSync()
        Log.d("AppViewModel", "ViewModel cleared. Sync stopped.")
    }

    val bunkAnalysisMap: StateFlow<Map<String, BunkAnalysis>> =
        subjectsWithAttendance.map { list ->
            list.associateBy({ it.subject.id }, { calculateBunkAnalysisFromData(it) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val overallStatistics: StateFlow<AttendanceStatistics> =
        attendanceDao.getOverallStatisticsFlow()
            .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AttendanceStatistics(0, 0, 0, 0.0, 0)
        )

    private fun calculateBunkAnalysisFromData(data: SubjectWithAttendance): BunkAnalysis {
        return AttendanceUtils.calculateBunkAnalysis(
            present = data.presentClasses,
            total = data.totalClasses,
            target = data.subject.targetAttendance.toDouble()
        )
    }

    fun triggerNavigation(subjectId: String) {
        viewModelScope.launch {
            _navigationEvents.send(subjectId)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            preferencesManager.saveUserName(name)
            cloudSyncManager.syncUserProfile(name)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.saveTheme(theme)
        }
    }

    fun completeOnboarding(name: String) {
        viewModelScope.launch {
            preferencesManager.saveUserName(name)
            preferencesManager.setOnboardingComplete(true)
            cloudSyncManager.syncUserProfile(name, true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
            cloudSyncManager.syncUserProfile(userName.value, true)
        }
    }

    fun addOrUpdateSubject(subject: Subject, schedules: List<ClassSchedule>, pastAttended: Int = 0, pastMissed: Int = 0) {
        if (subject.id.isEmpty()) {
            addSubject(subject.name, subject.color, subject.targetAttendance, pastAttended, pastMissed, schedules)
        } else {
            updateSubject(subject.id, subject.name, subject.color, subject.targetAttendance, schedules)
        }
    }

    private fun addSubject(
        name: String,
        color: String,
        targetAttendance: Int,
        pastAttended: Int,
        pastMissed: Int,
        schedules: List<ClassSchedule>
    ) {
        viewModelScope.launch {
            val subjectId = UUID.randomUUID().toString()
            val subject = Subject(
                id = subjectId,
                name = name,
                color = color,
                targetAttendance = targetAttendance
            )

            // 1. Save Subject and Schedules locally
            attendanceDao.upsertSubject(subject)
            val updatedSchedules = schedules.map { 
                it.copy(
                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                    subjectId = subjectId
                ) 
            }
            attendanceDao.insertSchedules(updatedSchedules)

            // 2. Add Past Attendance if provided
            if ((pastAttended > 0) || (pastMissed > 0)) {
                addPastRecords(subjectId, pastAttended, pastMissed)
            }

            // 3. Sync to Cloud
            cloudSyncManager.syncSubject(subject)
            cloudSyncManager.syncSchedules(updatedSchedules)

            // 4. Schedule Alarms
            updatedSchedules.forEach { schedule ->
                AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule)
            }
        }
    }

    fun addPastRecords(subjectId: String, pastAttended: Int, pastMissed: Int) {
        viewModelScope.launch {
            val pastRecords = mutableListOf<AttendanceRecord>()
            repeat(pastAttended) {
                pastRecords.add(
                    AttendanceRecord(
                        id = UUID.randomUUID().toString(),
                        subjectId = subjectId,
                        scheduleId = ID_SCHEDULE_PAST,
                        date = LocalDate.now().toEpochDay(),
                        isPresent = true,
                        type = RecordType.MANUAL,
                        note = "Migrated Past Record"
                    )
                )
            }
            repeat(pastMissed) {
                pastRecords.add(
                    AttendanceRecord(
                        id = UUID.randomUUID().toString(),
                        subjectId = subjectId,
                        scheduleId = ID_SCHEDULE_PAST,
                        date = LocalDate.now().toEpochDay(),
                        isPresent = false,
                        type = RecordType.MANUAL,
                        note = "Migrated Past Record"
                    )
                )
            }
            attendanceDao.insertAttendanceRecords(pastRecords)
            cloudSyncManager.syncAttendanceRecords(pastRecords)
            checkAndTriggerLowAttendanceWarning(subjectId)
        }
    }

    private fun updateSubject(
        subjectId: String,
        name: String,
        color: String,
        targetAttendance: Int,
        schedules: List<ClassSchedule>
    ) {
        viewModelScope.launch {
            val subject = Subject(
                id = subjectId,
                name = name,
                color = color,
                targetAttendance = targetAttendance
            )

            // 1. Update Subject
            attendanceDao.upsertSubject(subject)
            cloudSyncManager.syncSubject(subject)

            // 2. Diff Schedules
            val currentSchedules = attendanceDao.getSchedulesForSubject(subjectId)
            
            // To Remove
            val toRemove = currentSchedules.filter { current -> 
                schedules.none { it.id == current.id } 
            }
            toRemove.forEach { 
                AlarmScheduler.cancelClassAlarm(getApplication(), it)
                attendanceDao.deleteSchedule(it)
            }
            cloudSyncManager.deleteSchedules(toRemove.map { it.id })

            // To Add/Update
            val updatedSchedules = schedules.map { 
                it.copy(
                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                    subjectId = subjectId
                ) 
            }
            attendanceDao.insertSchedules(updatedSchedules)
            cloudSyncManager.syncSchedules(updatedSchedules)

            // 3. Reschedule Alarms for all current schedules of this subject
            updatedSchedules.forEach { schedule ->
                AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule)
            }
            
            checkAndTriggerLowAttendanceWarning(subjectId)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            val schedules = attendanceDao.getSchedulesForSubject(subject.id)
            schedules.forEach { AlarmScheduler.cancelClassAlarm(getApplication(), it) }
            
            attendanceDao.deleteSubjectAtomic(subject.id)
            cloudSyncManager.deleteSubject(subject.id)
        }
    }

    private fun markAttendance(
        subjectId: String,
        scheduleId: String,
        date: LocalDate,
        type: RecordType,
        isPresent: Boolean,
        note: String
    ) {
        // TRACKER: Inform that this notification should be silenced if active
        NotificationProcessingTracker.markAsProcessed(subjectId, scheduleId)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Shared Mutex to prevent Cloud Sync race conditions during manual marking
                cloudSyncManager.syncMutex.withLock {
                    val dateAsLong = date.toEpochDay()
                    
                    // 1. Check for Holiday (Blocking check inside mutex)
                    val dayRecords = attendanceDao.getAllAttendanceRecordsOnDateNow(dateAsLong)
                    if (dayRecords.any { it.type == RecordType.HOLIDAY }) {
                         _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.error_holiday_manual_mark))
                         return@withLock
                    }

                    // 2. Identify existing records to clean (Specific-Schedule records for this subject)
                    val existingRecords = attendanceDao.getAttendanceRecordsForSubjectOnDate(subjectId, dateAsLong)
                    val recordIdsToClean = existingRecords.asSequence()
                        .filter { it.scheduleId == scheduleId }
                        .map { it.id }
                        .toList()

                    // 3. Create new record
                    val record = AttendanceRecord(
                        id = UUID.randomUUID().toString(),
                        subjectId = subjectId,
                        scheduleId = scheduleId,
                        date = dateAsLong,
                        isPresent = isPresent,
                        type = type,
                        note = note,
                    )

                    // 4. Perform atomic local update
                    attendanceDao.markAttendanceTransaction(recordIdsToClean, record)
                    
                    firebaseAnalytics.logEvent("mark_attendance") {
                        param("type", type.name)
                        param("is_present", if (isPresent) 1L else 0L)
                    }

                    // 5. Fire-and-forget cloud sync and notification cleanup in the background
                    launch {
                        withContext(NonCancellable) {
                            try {
                                if (recordIdsToClean.isNotEmpty()) {
                                    cloudSyncManager.deleteAttendanceRecords(recordIdsToClean)
                                }
                                cloudSyncManager.syncAttendanceRecord(record)

                                if (scheduleId.isNotEmpty() && scheduleId != ID_SCHEDULE_MANUAL && scheduleId != ID_SCHEDULE_EXTRA && scheduleId != ID_SCHEDULE_PAST) {
                                    // EARLY MARKER FIX: Cancel pending alarm if marked before end-time
                                    attendanceDao.getScheduleById(scheduleId)?.let { schedule ->
                                        AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                                    }
                                    NotificationHelper.cancelNotification(getApplication(), scheduleId.hashCode())
                                } else if (scheduleId == ID_SCHEDULE_MANUAL && date == LocalDate.now()) {
                                    // If marking manual for today, cancel any active notifications for this subject's schedules today
                                    val dayOfWeek = (date.dayOfWeek.value % 7) + 1
                                    attendanceDao.getSchedulesForDayNow(dayOfWeek).forEach { s ->
                                        if (s.subjectId == subjectId) {
                                            NotificationHelper.cancelNotification(getApplication(), s.id.hashCode())
                                        }
                                    }
                                }

                                checkAndTriggerLowAttendanceWarning(subjectId)
                            } catch (e: Exception) {
                                Log.e("AppViewModel", "Background sync error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error marking attendance: ${e.message}")
            }
        }
    }

    fun updateAttendanceRecord(subjectId: String, date: LocalDate, isPresent: Boolean) {
        val note = if (isPresent) "Marked Present" else "Marked Absent"
        markAttendance(subjectId, ID_SCHEDULE_MANUAL, date, RecordType.MANUAL, isPresent, note)
        viewModelScope.launch {
            _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_attendance_updated))
        }
    }

    fun deleteAttendanceRecordById(recordId: String, subjectId: String) {
        viewModelScope.launch {
            val recordToDelete = attendanceDao.getAttendanceRecordById(recordId)
            if (recordToDelete != null) {
                attendanceDao.deleteAttendanceRecord(recordToDelete)
                cloudSyncManager.deleteAttendanceRecord(recordToDelete.id)
                checkAndTriggerLowAttendanceWarning(subjectId)
            }
        }
    }

    fun deleteAttendanceRecordForDate(subjectId: String, date: LocalDate) {
        viewModelScope.launch {
            val recordsToDelete = attendanceDao.getAttendanceRecordsForSubjectOnDate(subjectId, date.toEpochDay())
            attendanceDao.deleteAttendanceRecordsForSubjectOnDate(subjectId, date.toEpochDay())
            recordsToDelete.forEach { cloudSyncManager.deleteAttendanceRecord(it.id) }
            checkAndTriggerLowAttendanceWarning(subjectId)
        }
    }

    fun markDateAsPresent(subjectId: String, scheduleId: String, date: LocalDate) {
        markAttendance(subjectId, scheduleId, date, RecordType.CLASS, true, "Marked from App")
        viewModelScope.launch {
            _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_attendance_updated))
        }
    }

    fun markDateAsAbsent(subjectId: String, scheduleId: String, date: LocalDate) {
        markAttendance(subjectId, scheduleId, date, RecordType.CLASS, false, "Marked from App")
        viewModelScope.launch {
            _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_attendance_updated))
        }
    }

    fun markDateAsCancelled(subjectId: String, scheduleId: String, date: LocalDate) {
        markAttendance(subjectId, scheduleId, date, RecordType.CANCELLED, false, "Class Cancelled")
        viewModelScope.launch {
            _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_attendance_updated))
        }
    }

    fun markDateAsCancelled(subjectId: String, date: LocalDate) {
        markAttendance(subjectId, ID_SCHEDULE_MANUAL, date, RecordType.CANCELLED, false, "Class Cancelled")
        viewModelScope.launch {
            _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_attendance_updated))
        }
    }

    fun addExtraClasses(subjectId: String, date: LocalDate, isPresent: Boolean, count: Int) {
        viewModelScope.launch {
            val dateAsLong = date.toEpochDay()
            
            // Check for Holiday
            val dayRecords = attendanceDao.getAllAttendanceRecordsOnDateNow(dateAsLong)
            if (dayRecords.any { it.type == RecordType.HOLIDAY }) {
                _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.error_holiday_manual_mark))
                return@launch
            }

            withContext(NonCancellable) {
                cloudSyncManager.syncMutex.withLock {
                    val note = "Extra Class"
                    val newRecords = mutableListOf<AttendanceRecord>()
                    repeat(count) {
                        val record = AttendanceRecord(
                            id = UUID.randomUUID().toString(),
                            subjectId = subjectId,
                            scheduleId = ID_SCHEDULE_EXTRA,
                            date = dateAsLong,
                            isPresent = isPresent,
                            type = RecordType.MANUAL,
                            note = note
                        )
                        newRecords.add(record)
                    }
                    attendanceDao.insertAttendanceRecords(newRecords)
                    cloudSyncManager.syncAttendanceRecords(newRecords)
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_extra_class_added))
                    checkAndTriggerLowAttendanceWarning(subjectId)
                }
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                cloudSyncManager.syncMutex.withLock {
                    val subjects = allSubjects.first()
                    subjects.forEach { subject ->
                        val schedules = attendanceDao.getSchedulesForSubject(subject.id)
                        schedules.forEach { schedule ->
                            AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                        }
                    }
                    attendanceDao.deleteAllAttendanceRecords()
                    attendanceDao.deleteAllSchedules()
                    attendanceDao.deleteAllSubjects()
                    cloudSyncManager.deleteAllCloudData()
                }
            }
        }
    }

    suspend fun getSubjectById(subjectId: String): Subject? = attendanceDao.getSubjectById(subjectId)

    fun getSchedulesForSubjectFlow(subjectId: String): Flow<List<ClassSchedule>> = 
        attendanceDao.getSchedulesForSubjectFlow(subjectId)

    suspend fun getSchedulesForSubject(subjectId: String): List<ClassSchedule> =
        attendanceDao.getSchedulesForSubject(subjectId)

    fun getAttendanceRecordsForSubject(subjectId: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceRecordsForSubject(subjectId)
    }

    fun getRecordsForDate(date: LocalDate): Flow<List<AttendanceRecordWithSubject>> {
        return attendanceDao.getRecordsForDateWithSubject(date.toEpochDay())
    }

    private suspend fun checkAndTriggerLowAttendanceWarning(subjectId: String) {
        attendanceDao.getSubjectById(subjectId)?.let { subject ->
            val total = attendanceDao.getTotalClassesForSubject(subjectId)
            val present = attendanceDao.getPresentClassesForSubject(subjectId)
            val newPercentage = AttendanceUtils.calculatePercentage(present, total)

            if ((newPercentage < subject.targetAttendance) && (total > 0)) {
                NotificationHelper.showAttendanceWarningNotification(
                    getApplication(),
                    subject,
                    newPercentage
                )
            }
        }
    }

    fun onHolidayToggleRequested(date: LocalDate) {
        viewModelScope.launch {
            val allRecords = attendanceDao.getAllAttendanceRecords().first()
            val holidayRecord = allRecords.find { it.date == date.toEpochDay() && it.type == RecordType.HOLIDAY }

            if (holidayRecord != null) {
                attendanceDao.deleteAttendanceRecord(holidayRecord)
                cloudSyncManager.deleteAttendanceRecord(holidayRecord.id)
                
                // Reschedule alarms for this day
                // FIX: LocalDate.dayOfWeek.value is 1 (Mon) - 7 (Sun), while Calendar uses 2 (Mon) - 1 (Sun)
                val calendarDayOfWeek = (date.dayOfWeek.value % 7) + 1
                val schedulesForDay = attendanceDao.getSchedulesForDayNow(calendarDayOfWeek)
                val allSubjectsList = attendanceDao.getAllSubjects().first()
                schedulesForDay.forEach { schedule ->
                    val subject = allSubjectsList.find { it.id == schedule.subjectId }
                    if (subject != null) {
                        AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule)
                    }
                }
            } else {
                _showHolidayDialog.value = date
            }
        }
    }

    fun onHolidayToggleConfirmed() {
        Log.d("AppViewModel", "onHolidayToggleConfirmed called.")
        viewModelScope.launch {
            _showHolidayDialog.value?.let { date ->
                withContext(NonCancellable) {
                    cloudSyncManager.syncMutex.withLock {
                        val dateAsLong = date.toEpochDay()
                        
                        // 1. Get IDs for cloud cleanup before deleting locally
                        val allDayRecords = attendanceDao.getAllAttendanceRecordsOnDateNow(dateAsLong)
                        val recordIdsToDelete = allDayRecords.map { it.id }
                        
                        // 2. Insert holiday record atomically
                        val holidayRecord = AttendanceRecord(
                            id = UUID.randomUUID().toString(),
                            subjectId = ID_SUBJECT_HOLIDAY,
                            scheduleId = ID_SCHEDULE_HOLIDAY,
                            date = dateAsLong,
                            isPresent = false,
                            note = "Holiday",
                            type = RecordType.HOLIDAY
                        )
                        
                        attendanceDao.markHolidayTransaction(dateAsLong, holidayRecord)
                        
                        // 3. Sync deletions and insertion to cloud
                        cloudSyncManager.deleteAttendanceRecords(recordIdsToDelete)
                        cloudSyncManager.syncAttendanceRecord(holidayRecord)

                        // 4. Cancel TODAY'S alarms but immediately reschedule for NEXT WEEK
                        // This ensures the chain of weekly reminders isn't broken by a single holiday.
                        val calendarDayOfWeek = (date.dayOfWeek.value % 7) + 1
                        val schedulesForDay = attendanceDao.getSchedulesForDayNow(calendarDayOfWeek)
                        val allSubjectsList = attendanceDao.getAllSubjects().first()
                        
                        schedulesForDay.forEach { schedule ->
                            // Cancel today
                            AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                            NotificationHelper.cancelNotification(getApplication(), schedule.id.hashCode())
                            
                            // Reschedule for next week (leap-frog)
                            val subject = allSubjectsList.find { it.id == schedule.subjectId }
                            if (subject != null) {
                                AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule, forceNextWeek = true)
                            }
                        }
                    }
                }
            }
            _showHolidayDialog.value = null
        }
    }


    private fun getDashboardSchedule(): Flow<Pair<Boolean, List<ScheduleWithSubject>>> {
        val refreshTimer = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(30.seconds)
            }
        }

        return currentDate.flatMapLatest { todayDate ->
            val todayDayOfWeek = (todayDate.dayOfWeek.value % 7) + 1
            val todayEpochDay = todayDate.toEpochDay()
            
            val tomorrowDate = todayDate.plusDays(1)
            val tomorrowDayOfWeek = (tomorrowDate.dayOfWeek.value % 7) + 1
            val tomorrowEpochDay = tomorrowDate.toEpochDay()

            val yesterdayDate = todayDate.minusDays(1)
            val yesterdayDayOfWeek = (yesterdayDate.dayOfWeek.value % 7) + 1
            val yesterdayEpochDay = yesterdayDate.toEpochDay()

            val flows = listOf(
                attendanceDao.getSchedulesForDay(todayDayOfWeek),
                attendanceDao.getSchedulesForDay(tomorrowDayOfWeek),
                attendanceDao.getSchedulesForDay(yesterdayDayOfWeek),
                allSubjects,
                attendanceDao.isDateHolidayFlow(todayEpochDay),
                attendanceDao.isDateHolidayFlow(tomorrowEpochDay),
                attendanceDao.isDateHolidayFlow(yesterdayEpochDay),
                allAttendanceRecords,
                refreshTimer,
                showTomorrowPreview
            )

            @Suppress("UNCHECKED_CAST")
            combine(flows) { array ->
                val todaySchedules = array[0] as List<ClassSchedule>
                val tomorrowSchedules = array[1] as List<ClassSchedule>
                val yesterdaySchedules = array[2] as List<ClassSchedule>
                val subjects = array[3] as List<Subject>
                val isTodayHoliday = array[4] as Boolean
                val isTomorrowHoliday = array[5] as Boolean
                val isYesterdayHoliday = array[6] as Boolean
                val records = array[7] as List<AttendanceRecord>
                val isTomorrowPreviewRequested = array[9] as Boolean
                
                val now = java.time.LocalTime.now()

                // 1. Calculate Today's List
                val regularClasses = if (isTodayHoliday) mutableListOf<ScheduleWithSubject>() else todaySchedules.asSequence().mapNotNull { schedule ->
                    val subject = subjects.find { it.id == schedule.subjectId } ?: return@mapNotNull null
                    val record = records.find {
                        val sId = schedule.id
                        (it.scheduleId == sId || it.scheduleId == ID_SCHEDULE_MANUAL) &&
                                it.date == todayEpochDay &&
                                it.subjectId == subject.id
                    }
                    val start = java.time.LocalTime.of(schedule.startHour, schedule.startMinute)
                    val end = java.time.LocalTime.of(schedule.endHour, schedule.endMinute)
                    
                    val isOvernight = end.isBefore(start) || (end == start && schedule.startHour != 0)
                    
                    val isLive = if (isOvernight) {
                        // Started today, ends tomorrow. Live if it's currently between start and midnight.
                        now.isAfter(start) || now == start
                    } else {
                        (now == start || now.isAfter(start)) && now.isBefore(end)
                    }
                    
                    val isCompleted = if (isOvernight) {
                        false // Cannot be completed on the same day it starts
                    } else {
                        now.isAfter(end)
                    }
                    ScheduleWithSubject(schedule, subject, record, isLive, isCompleted, effectiveDate = todayDate)
                }.toMutableList()

                // 2. Add Spillover from Yesterday
                if (!isYesterdayHoliday) {
                    yesterdaySchedules.forEach { schedule ->
                        val start = java.time.LocalTime.of(schedule.startHour, schedule.startMinute)
                        val end = java.time.LocalTime.of(schedule.endHour, schedule.endMinute)
                        val isOvernight = end.isBefore(start) || (end == start && schedule.startHour != 0)
                        
                        if (isOvernight) {
                            // This class started yesterday and ends today.
                            // Is it still live? (Between midnight and end time)
                            if (now.isBefore(end)) {
                                val subject = subjects.find { it.id == schedule.subjectId } ?: return@forEach
                                val record = records.find {
                                    val sId = schedule.id
                                    (it.scheduleId == sId || it.scheduleId == ID_SCHEDULE_MANUAL) &&
                                            it.date == yesterdayEpochDay &&
                                            it.subjectId == subject.id
                                }
                                regularClasses.add(ScheduleWithSubject(schedule, subject, record, isLive = true, isCompleted = false, effectiveDate = yesterdayDate))
                            } else {
                                // Just ended today. We should still show it as "Completed" for marking
                                val subject = subjects.find { it.id == schedule.subjectId } ?: return@forEach
                                val record = records.find {
                                    val sId = schedule.id
                                    (it.scheduleId == sId || it.scheduleId == ID_SCHEDULE_MANUAL) &&
                                            it.date == yesterdayEpochDay &&
                                            it.subjectId == subject.id
                                }
                                regularClasses.add(ScheduleWithSubject(schedule, subject, record, isLive = false, isCompleted = true, effectiveDate = yesterdayDate))
                            }
                        }
                    }
                }

                val extraClasses = records.asSequence().filter { 
                    it.date == todayEpochDay && it.scheduleId == ID_SCHEDULE_EXTRA 
                }.mapNotNull { record ->
                    val subject = subjects.find { it.id == record.subjectId } ?: return@mapNotNull null
                    val syntheticSchedule = ClassSchedule(
                        id = record.id,
                        subjectId = subject.id,
                        dayOfWeek = todayDayOfWeek,
                        startHour = EXTRA_CLASS_START_HOUR,
                        startMinute = EXTRA_CLASS_START_MINUTE
                    )
                    ScheduleWithSubject(syntheticSchedule, subject, record, isLive = false, isCompleted = true)
                }.toList()

                val todayList = (regularClasses + extraClasses).sortedWith(
                    compareByDescending<ScheduleWithSubject> { it.isLive }
                        .thenByDescending { it.attendanceRecord == null && !it.isCompleted } // Pin "Next Up" below "Live"
                        .thenBy { it.schedule.startHour }
                        .thenBy { it.schedule.startMinute }
                )

                // 3. Logic to Switch to Tomorrow (ONLY if manually requested)
                if (isTomorrowPreviewRequested) {
                    if (isTomorrowHoliday) {
                        return@combine Pair(true, emptyList())
                    }
                    
                    val tomorrowList = tomorrowSchedules.asSequence().mapNotNull { schedule ->
                        val subject = subjects.find { it.id == schedule.subjectId } ?: return@mapNotNull null
                        val record = records.find {
                            (it.scheduleId == schedule.id || it.scheduleId == ID_SCHEDULE_MANUAL) &&
                                    it.date == tomorrowEpochDay &&
                                    it.subjectId == subject.id
                        }
                        ScheduleWithSubject(schedule, subject, record, isLive = false, isCompleted = false, effectiveDate = tomorrowDate)
                    }.sortedBy { it.schedule.startHour }.toList()

                    return@combine Pair(true, tomorrowList) // true = Tomorrow
                }

                Pair(false, todayList) // false = Today
            }
        }
    }

    fun getWeeklySchedule(): Flow<Map<Int, List<ScheduleWithSubject>>> {
        return attendanceDao.getAllSchedules().combine(allSubjects) { allSchedules, allSubjects ->
            allSchedules.groupBy { it.dayOfWeek }
                .mapValues { entry ->
                    entry.value.asSequence().mapNotNull { schedule ->
                        allSubjects.find { it.id == schedule.subjectId }?.let { subject ->
                            ScheduleWithSubject(schedule, subject, isLive = false, isCompleted = false)
                        }
                    }.sortedBy { it.schedule.startHour }.toList()
            }
        }
    }

    private suspend fun rescheduleAllAlarms() {
        try {
            val subjects = attendanceDao.getAllSubjects().first()
            subjects.forEach { subject ->
                val schedules = attendanceDao.getSchedulesForSubject(subject.id)
                schedules.forEach { schedule ->
                    AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule)
                }
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Error rescheduling alarms: ${e.message}")
        }
    }

    fun signUpWithEmail(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // SYNC FIX: Upload current local state and wait for it to finish
                            viewModelScope.launch {
                                try {
                                    uploadAllLocalDataToCloud()
                                    cloudSyncManager.startRealTimeSync(attendanceDao, viewModelScope)
                                    onComplete(true, null)
                                } catch (e: Exception) {
                                    onComplete(true, "Account created, but background sync failed: ${e.message}")
                                }
                            }
                        } else {
                            onComplete(false, task.exception?.message)
                        }
                    }
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    private suspend fun uploadAllLocalDataToCloud() {
        val subjects = allSubjects.first()
        val schedules = attendanceDao.getAllSchedules().first()
        val records = allAttendanceRecords.first()

        // 1. Sync Profile Name
        cloudSyncManager.syncUserProfile(userName.value)
        
        // 2. Sync Subjects (Sequential to ensure parent exists)
        subjects.forEach { cloudSyncManager.syncSubject(it) }
        
        // 3. Sync Schedules and Records in batches
        cloudSyncManager.syncSchedules(schedules)
        cloudSyncManager.syncAttendanceRecords(records)
        
        Log.d("AppViewModel", "Offline data migration complete: ${subjects.size} subjects synced.")
    }

    fun loginWithEmail(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                auth.signInWithEmailAndPassword(email, password).await()
                
                // 1. Restore user profile data from cloud
                Log.d("AppViewModel", "Fetching stored profile from cloud...")
                val profileData = cloudSyncManager.getUserProfileData()
                
                profileData?.let { data ->
                    val storedName = data["name"] as? String
                    if (!storedName.isNullOrBlank()) {
                        preferencesManager.saveUserName(storedName)
                        Log.d("AppViewModel", "Restored name: $storedName")
                    }
                    
                    val cloudOnboarding = data["isOnboardingComplete"] as? Boolean
                    if (cloudOnboarding == true) {
                        preferencesManager.setOnboardingComplete(true)
                        Log.d("AppViewModel", "Restored onboarding status: true")
                    }
                }
                
                // 2. Restore all attendance data silently
                Log.d("AppViewModel", "Starting full data restore...")
                // Cancel existing alarms before restore to avoid duplicates/orphans
                val existingSubjects = attendanceDao.getAllSubjects().first()
                existingSubjects.forEach { s ->
                    attendanceDao.getSchedulesForSubject(s.id).forEach { 
                        AlarmScheduler.cancelClassAlarm(getApplication(), it)
                    }
                }
                
                val success = cloudSyncManager.restoreAllData(attendanceDao)
                if (success) {
                    cloudSyncManager.startRealTimeSync(attendanceDao, viewModelScope)
                    rescheduleAllAlarms()
                    onComplete(true, null)
                } else {
                    onComplete(false, "Restore failed. Please check your internet and try again.")
                }
            } catch (e: Exception) {
                val errorMsg = e.message
                Log.e("AppViewModel", "Login failed: $errorMsg")
                onComplete(false, errorMsg)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            cloudSyncManager.stopRealTimeSync()
            // 1. Cancel all active alarms before clearing data
            val subjects = attendanceDao.getAllSubjects().first()
            subjects.forEach { subject ->
                val schedules = attendanceDao.getSchedulesForSubject(subject.id)
                schedules.forEach { schedule ->
                    AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                }
            }
            
            // 2. Clear local data
            attendanceDao.deleteAllSubjects()
            attendanceDao.deleteAllSchedules()
            attendanceDao.deleteAllAttendanceRecords()
            
            // 3. Clear preferences (Keep username for a moment to prevent 'Student' flicker during transition)
            preferencesManager.setOnboardingComplete(false)

            // 4. Clear internal ViewModel state
            _showHolidayDialog.value = null
            
            // 5. Sign out from Firebase
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            
            // Final step: Clear the name after sign out is initiated
            preferencesManager.saveUserName("")
            onComplete()
        }
    }

    fun resetPassword(email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        onComplete(task.isSuccessful, task.exception?.message)
                    }
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }
}
