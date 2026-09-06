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
import com.ankit.attendwise.AttendWiseApplication
import com.ankit.attendwise.data.*
import com.ankit.attendwise.data.remote.NetworkResult
import com.ankit.attendwise.data.remote.dto.LoginRequest
import com.ankit.attendwise.data.remote.dto.RegisterRequest
import com.ankit.attendwise.data.remote.dto.ResetPasswordRequest
import com.ankit.attendwise.data.remote.dto.UpdateUserRequest
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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AttendWiseApplication).repository
    private val preferencesManager = PreferencesManager(application)

    val currentUser = repository.userId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val userEmail = repository.userEmail.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.getAllAttendanceRecordsLocal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubjects: StateFlow<List<Subject>> = repository.getAllSubjectsLocal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjectsWithAttendance: StateFlow<List<SubjectWithAttendance>> =
        repository.getSubjectsWithAttendanceLocal()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSchedules: StateFlow<List<ClassSchedule>> = repository.getAllSchedulesLocal()
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
        .flatMapLatest { date -> repository.isDateHolidayFlowLocal(date.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dashboardScheduleWithSubjects: StateFlow<Pair<Boolean, List<ScheduleWithSubject>>> =
        getDashboardSchedule().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Pair(false, emptyList())
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncData() {
        if (_isSyncing.value) return // Prevent overlapping syncs
        viewModelScope.launch {
            _isSyncing.value = true
            repository.syncAll()
            _isSyncing.value = false
        }
    }

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
        // Sync local username with session username
        viewModelScope.launch {
            repository.userName.collect { name ->
                if (name != null && name != userName.value) {
                    preferencesManager.saveUserName(name)
                }
            }
        }

        // Start sync logic when user is logged in
        viewModelScope.launch {
            repository.jwtToken.collect { token ->
                if (token != null) {
                    syncData()
                }
            }
        }

        // SELF-HEALING: Reschedule all alarms on startup in background to ensure system consistency
        viewModelScope.launch(Dispatchers.IO) {
            rescheduleAllAlarms()
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
                
                val isTomorrowHoliday = repository.isDateHolidayFlowLocal(tomorrow.toEpochDay()).first()
                val tomorrowSchedules = repository.getSchedulesForDayNowLocal(tomorrowDayOfWeek)
                
                if (isTomorrowHoliday) {
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_tomorrow_is_holiday))
                } else if (tomorrowSchedules.isEmpty()) {
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_no_classes_tomorrow))
                }
            }
        }
    }

    override fun onCleared() {
        Log.d("AppViewModel", "ViewModel cleared.")
    }

    val bunkAnalysisMap: StateFlow<Map<String, BunkAnalysis>> =
        subjectsWithAttendance.map { list ->
            list.associateBy({ it.subject.id }, { calculateBunkAnalysisFromData(it) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val overallStatistics: StateFlow<AttendanceStatistics> =
        repository.getOverallStatisticsFlowLocal()
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
            if (currentUser.value != null) {
                repository.updateCurrentUser(UpdateUserRequest(name))
            }
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
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
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

            val updatedSchedules = schedules.map { 
                it.copy(
                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                    subjectId = subjectId
                ) 
            }

            // Perform synced save
            repository.addSubject(subject)
            repository.addSchedules(updatedSchedules)

            // 2. Add Past Attendance if provided
            if ((pastAttended > 0) || (pastMissed > 0)) {
                addPastRecords(subjectId, pastAttended, pastMissed)
            }

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
            repository.insertAttendanceRecordsLocal(pastRecords)
            // Sync
            pastRecords.forEach { repository.markAttendance(emptyList(), it) }
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

            repository.updateSubject(subject)

            // 2. Diff Schedules
            val currentSchedules = repository.getSchedulesForSubjectLocal(subjectId)
            
            // To Remove
            val toRemove = currentSchedules.filter { current -> 
                schedules.none { it.id == current.id } 
            }
            toRemove.forEach { 
                AlarmScheduler.cancelClassAlarm(getApplication(), it)
                repository.deleteSchedule(it)
            }

            // To Add/Update
            val updatedSchedules = schedules.map { 
                it.copy(
                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                    subjectId = subjectId
                ) 
            }
            repository.addSchedules(updatedSchedules)

            // 3. Reschedule Alarms for all current schedules of this subject
            updatedSchedules.forEach { schedule ->
                AlarmScheduler.scheduleClassAlarm(getApplication(), subject, schedule)
            }
            
            checkAndTriggerLowAttendanceWarning(subjectId)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            val schedules = repository.getSchedulesForSubjectLocal(subject.id)
            schedules.forEach { AlarmScheduler.cancelClassAlarm(getApplication(), it) }
            
            repository.deleteSubject(subject.id)
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
                repository.syncMutex.withLock {
                    val dateAsLong = date.toEpochDay()
                    
                    // 1. Check for Holiday (Blocking check inside mutex)
                    val dayRecords = repository.getAllAttendanceRecordsOnDateNowLocal(dateAsLong)
                    if (dayRecords.any { it.type == RecordType.HOLIDAY }) {
                         _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.error_holiday_manual_mark))
                         return@withLock
                    }

                    // 2. Identify existing records to clean (Specific-Schedule records for this subject)
                    val existingRecords = repository.getAttendanceRecordsForSubjectOnDateLocal(subjectId, dateAsLong)
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

                    // 4. Perform synced update
                    repository.markAttendance(recordIdsToClean, record)

                    // 5. Fire-and-forget notification cleanup in the background
                    launch {
                        withContext(NonCancellable) {
                            try {
                                if (scheduleId.isNotEmpty() && scheduleId != ID_SCHEDULE_MANUAL && scheduleId != ID_SCHEDULE_EXTRA && scheduleId != ID_SCHEDULE_PAST) {
                                    // EARLY MARKER FIX: Cancel pending alarm if marked before end-time
                                    repository.getScheduleByIdLocal(scheduleId)?.let { schedule ->
                                        AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                                    }
                                    NotificationHelper.cancelNotification(getApplication(), scheduleId.hashCode())
                                } else if (scheduleId == ID_SCHEDULE_MANUAL && date == LocalDate.now()) {
                                    // If marking manual for today, cancel any active notifications for this subject's schedules today
                                    val dayOfWeek = (date.dayOfWeek.value % 7) + 1
                                    repository.getSchedulesForDayNowLocal(dayOfWeek).forEach { s ->
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
            val recordToDelete = repository.getAttendanceRecordByIdLocal(recordId)
            if (recordToDelete != null) {
                repository.deleteAttendanceRecord(recordToDelete)
                checkAndTriggerLowAttendanceWarning(subjectId)
            }
        }
    }

    fun deleteAttendanceRecordForDate(subjectId: String, date: LocalDate) {
        viewModelScope.launch {
            val recordsToDelete = repository.getAttendanceRecordsForSubjectOnDateLocal(subjectId, date.toEpochDay())
            repository.deleteAttendanceRecordsForSubjectOnDateLocal(subjectId, date.toEpochDay())
            recordsToDelete.forEach { 
                repository.deleteAttendanceRecord(it) 
            }
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
            val dayRecords = repository.getAllAttendanceRecordsOnDateNowLocal(dateAsLong)
            if (dayRecords.any { it.type == RecordType.HOLIDAY }) {
                _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.error_holiday_manual_mark))
                return@launch
            }

            withContext(NonCancellable) {
                repository.syncMutex.withLock {
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
                    repository.insertAttendanceRecordsLocal(newRecords)
                    // Sync each individually for now or add batch sync to repo
                    newRecords.forEach { repository.markAttendance(emptyList(), it) }
                    
                    _attendanceActionFeedback.emit(getApplication<Application>().getString(R.string.feedback_extra_class_added))
                    checkAndTriggerLowAttendanceWarning(subjectId)
                }
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.syncMutex.withLock {
                    val subjects = allSubjects.value
                    subjects.forEach { subject ->
                        val schedules = repository.getSchedulesForSubjectLocal(subject.id)
                        schedules.forEach { schedule ->
                            AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                        }
                        repository.deleteSubject(subject.id)
                    }
                }
            }
        }
    }

    suspend fun getSubjectById(subjectId: String): Subject? = repository.getSubjectByIdLocal(subjectId)

    fun getSchedulesForSubjectFlow(subjectId: String): Flow<List<ClassSchedule>> = 
        repository.getSchedulesForSubjectFlowLocal(subjectId)

    suspend fun getSchedulesForSubject(subjectId: String): List<ClassSchedule> =
        repository.getSchedulesForSubjectLocal(subjectId)

    fun getAttendanceRecordsForSubject(subjectId: String): Flow<List<AttendanceRecord>> {
        return repository.getAttendanceRecordsForSubjectLocal(subjectId)
    }

    fun getRecordsForDate(date: LocalDate): Flow<List<AttendanceRecordWithSubject>> {
        return repository.getRecordsForDateWithSubjectLocal(date.toEpochDay())
    }

    private suspend fun checkAndTriggerLowAttendanceWarning(subjectId: String) {
        repository.getSubjectByIdLocal(subjectId)?.let { subject ->
            val total = repository.getTotalClassesForSubjectLocal(subjectId)
            val present = repository.getPresentClassesForSubjectLocal(subjectId)
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
            val allRecords = repository.getAllAttendanceRecordsLocal().first()
            val holidayRecord = allRecords.find { it.date == date.toEpochDay() && it.type == RecordType.HOLIDAY }

            if (holidayRecord != null) {
                repository.deleteAttendanceRecord(holidayRecord)
                
                // Reschedule alarms for this day
                val calendarDayOfWeek = (date.dayOfWeek.value % 7) + 1
                val schedulesForDay = repository.getSchedulesForDayNowLocal(calendarDayOfWeek)
                val allSubjectsList = repository.getAllSubjectsLocal().first()
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
                    repository.syncMutex.withLock {
                        val dateAsLong = date.toEpochDay()
                        
                        val allDayRecords = repository.getAllAttendanceRecordsOnDateNowLocal(dateAsLong)
                        val recordIdsToDelete = allDayRecords.map { it.id }
                        
                        val holidayRecord = AttendanceRecord(
                            id = UUID.randomUUID().toString(),
                            subjectId = ID_SUBJECT_HOLIDAY,
                            scheduleId = ID_SCHEDULE_HOLIDAY,
                            date = dateAsLong,
                            isPresent = false,
                            note = "Holiday",
                            type = RecordType.HOLIDAY
                        )
                        
                        repository.markAttendance(recordIdsToDelete, holidayRecord)

                        // 4. Cancel TODAY'S alarms but immediately reschedule for NEXT WEEK
                        val calendarDayOfWeek = (date.dayOfWeek.value % 7) + 1
                        val schedulesForDay = repository.getSchedulesForDayNowLocal(calendarDayOfWeek)
                        val allSubjectsList = repository.getAllSubjectsLocal().first()
                        
                        schedulesForDay.forEach { schedule ->
                            AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                            NotificationHelper.cancelNotification(getApplication(), schedule.id.hashCode())
                            
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
                repository.getSchedulesForDayLocal(todayDayOfWeek),
                repository.getSchedulesForDayLocal(tomorrowDayOfWeek),
                repository.getSchedulesForDayLocal(yesterdayDayOfWeek),
                allSubjects,
                repository.isDateHolidayFlowLocal(todayEpochDay),
                repository.isDateHolidayFlowLocal(tomorrowEpochDay),
                repository.isDateHolidayFlowLocal(yesterdayEpochDay),
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
        return repository.getAllSchedulesLocal().combine(allSubjects) { allSchedules, allSubjects ->
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
            val subjects = repository.getAllSubjectsLocal().first()
            subjects.forEach { subject ->
                val schedules = repository.getSchedulesForSubjectLocal(subject.id)
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
            val result = repository.register(RegisterRequest(email, password, userName.value.ifBlank { "Student" }))
            when (result) {
                is NetworkResult.Success -> {
                    val user = result.data.user
                    preferencesManager.saveUserName(user.name)
                    onComplete(true, null)
                    syncData()
                }
                is NetworkResult.Error -> onComplete(false, result.message)
                is NetworkResult.Exception -> onComplete(false, result.e.message)
            }
        }
    }

    fun loginWithEmail(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.login(LoginRequest(email, password))
            _isSyncing.value = false
            when (result) {
                is NetworkResult.Success -> {
                    val user = result.data.user
                    preferencesManager.saveUserName(user.name)
                    preferencesManager.setOnboardingComplete(user.onboardingComplete)
                    
                    // INSTANT UI FEEDBACK: Dismiss sign-in dialog immediately
                    onComplete(true, null)
                    
                    // Trigger full sync & alarm rescheduling in background
                    syncData()
                    rescheduleAllAlarms()
                }
                is NetworkResult.Error -> onComplete(false, result.message)
                is NetworkResult.Exception -> onComplete(false, result.e.message)
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            // 1. Cancel all active alarms before clearing data
            val subjects = repository.getAllSubjectsLocal().first()
            subjects.forEach { subject ->
                val schedules = repository.getSchedulesForSubjectLocal(subject.id)
                schedules.forEach { schedule ->
                    AlarmScheduler.cancelClassAlarm(getApplication(), schedule)
                }
            }
            
            // 2. Clear local data
            repository.deleteAllSubjectsLocal()
            repository.deleteAllSchedulesLocal()
            repository.deleteAllAttendanceRecordsLocal()
            
            // 3. Clear preferences
            preferencesManager.setOnboardingComplete(false)
            preferencesManager.saveUserName("")

            // 4. Clear internal ViewModel state
            _showHolidayDialog.value = null
            
            // 5. Sign out from Backend Session
            repository.logout()
            
            onComplete()
        }
    }

    fun resetPassword(email: String, newPassword: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.resetPassword(ResetPasswordRequest(email, newPassword))
            when (result) {
                is NetworkResult.Success -> {
                    onComplete(true, result.data["message"] ?: "Password reset successful")
                }
                is NetworkResult.Error -> onComplete(false, result.message ?: "Failed to reset password")
                is NetworkResult.Exception -> onComplete(false, result.e.message ?: "Network error")
            }
        }
    }
}
