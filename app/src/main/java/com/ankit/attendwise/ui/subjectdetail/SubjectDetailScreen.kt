@file:OptIn(ExperimentalMaterial3Api::class)

package com.ankit.attendwise.ui.subjectdetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ankit.attendwise.data.AttendanceRecord
import com.ankit.attendwise.data.BunkAnalysis
import com.ankit.attendwise.data.RecordType
import com.ankit.attendwise.data.Subject
import com.ankit.attendwise.models.SubjectWithAttendance
import com.ankit.attendwise.ui.theme.ErrorRed
import com.ankit.attendwise.ui.theme.HolidayYellow
import com.ankit.attendwise.ui.theme.PoppinsFamily
import com.ankit.attendwise.ui.theme.SuccessGreen
import com.ankit.attendwise.utils.ColorUtils
import com.ankit.attendwise.viewmodel.AppViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import androidx.compose.ui.res.stringResource
import com.ankit.attendwise.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(subjectId: String, navController: NavController, appViewModel: AppViewModel) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showMarkAttendanceDialog by remember { mutableStateOf<LocalDate?>(null) }
    val haptic = LocalHapticFeedback.current

    val subjectsWithAttendance by appViewModel.subjectsWithAttendance.collectAsStateWithLifecycle()
    val bunkAnalysisMap by appViewModel.bunkAnalysisMap.collectAsStateWithLifecycle()
    val subjectWithAttendance by remember(subjectsWithAttendance, subjectId) {
        derivedStateOf { subjectsWithAttendance.find { it.subject.id == subjectId } }
    }

    val bunkAnalysis = remember(bunkAnalysisMap, subjectId) { bunkAnalysisMap[subjectId] }

    val attendanceRecords by appViewModel.getAttendanceRecordsForSubject(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allAttendanceRecords by appViewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    
    val subjectSchedules by appViewModel.getSchedulesForSubjectFlow(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())

    var recordToDelete by remember { mutableStateOf<String?>(null) }
    var clearAllDateRecords by remember { mutableStateOf<LocalDate?>(null) }

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_record_title)) },
            text = { Text(stringResource(R.string.dialog_delete_record_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.deleteAttendanceRecordById(recordToDelete!!, subjectId)
                        recordToDelete = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { recordToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    if (clearAllDateRecords != null) {
        AlertDialog(
            onDismissRequest = { clearAllDateRecords = null },
            title = { Text(stringResource(R.string.dialog_clear_attendance_title)) },
            text = { Text(stringResource(R.string.dialog_clear_attendance_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.deleteAttendanceRecordForDate(subjectId, clearAllDateRecords!!)
                        clearAllDateRecords = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { clearAllDateRecords = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            subjectName = subjectWithAttendance?.subject?.name ?: "this subject",
            onConfirm = {
                subjectWithAttendance?.subject?.let { appViewModel.deleteSubject(it) }
                navController.popBackStack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showManualAddDialog) {
        ManualAddAttendanceDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { present, absent ->
                appViewModel.addPastRecords(subjectId, present, absent)
                showManualAddDialog = false
            }
        )
    }

    showMarkAttendanceDialog?.let { date ->
        val recordsForSelectedDate = remember(date, attendanceRecords) {
            attendanceRecords.filter { it.date == date.toEpochDay() }
        }
        val isHoliday = allAttendanceRecords.any { (it.date == date.toEpochDay()) && (it.type == RecordType.HOLIDAY) }
        
        // Find schedules for this subject on this specific day
        val calendarDayOfWeek = (date.dayOfWeek.value % 7) + 1
        val schedulesForDay = remember(date, subjectSchedules) {
            subjectSchedules.filter { it.dayOfWeek == calendarDayOfWeek }
        }

        MarkAttendanceDialog(
            date = date,
            recordsForDay = recordsForSelectedDate,
            schedulesForDay = schedulesForDay,
            isHoliday = isHoliday,
            onDismiss = { showMarkAttendanceDialog = null },
            onConfirm = { isPresent, scheduleId ->
                if (scheduleId != null) {
                    if (isPresent) appViewModel.markDateAsPresent(subjectId, scheduleId, date)
                    else appViewModel.markDateAsAbsent(subjectId, scheduleId, date)
                } else {
                    appViewModel.updateAttendanceRecord(subjectId, date, isPresent)
                }
            },
            onConfirmCancelled = { scheduleId ->
                if (scheduleId != null) appViewModel.markDateAsCancelled(subjectId, scheduleId, date)
                else appViewModel.markDateAsCancelled(subjectId, date)
            },
            onToggleHoliday = {
                appViewModel.onHolidayToggleRequested(date)
            },
            onDeleteMain = { clearAllDateRecords = date },
            onDeleteRecord = { recordId -> recordToDelete = recordId },
            onAddExtra = { isPresent -> appViewModel.addExtraClasses(subjectId, date, isPresent, 1) }
        )
    }

    // AUTO-EXIT: Only close if the subject is truly gone (not just loading)
    LaunchedEffect(subjectsWithAttendance) {
        if (subjectsWithAttendance.isNotEmpty() && subjectsWithAttendance.none { it.subject.id == subjectId }) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subjectWithAttendance?.subject?.name ?: stringResource(R.string.subject_details_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showManualAddDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                    }
                    IconButton(onClick = {
                        navController.navigate("edit_subject/$subjectId")
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (subjectWithAttendance == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val currentSubjectWithAttendance = subjectWithAttendance!!
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                AttendanceProgressCard(currentSubjectWithAttendance)
                AttendanceStatsCard(currentSubjectWithAttendance)
                bunkAnalysis?.let {
                    BunkAnalysisCard(it, currentSubjectWithAttendance.subject)
                }

                Text(
                    stringResource(R.string.attendance_history_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                AttendanceCalendar(attendanceRecords, allAttendanceRecords) { date ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMarkAttendanceDialog = date
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(subjectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_subject_title)) },
        text = { Text(stringResource(R.string.dialog_delete_subject_text, subjectName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { 
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_cancel)) } 
        }
    )
}

@Composable
fun ManualAddAttendanceDialog(onDismiss: () -> Unit, onConfirm: (present: Int, absent: Int) -> Unit) {
    var presentCount by remember { mutableStateOf("0") }
    var absentCount by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_manual_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dialog_manual_add_text))
                OutlinedTextField(
                    value = presentCount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) presentCount = it },
                    label = { Text(stringResource(R.string.label_attended_present)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = absentCount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) absentCount = it },
                    label = { Text(stringResource(R.string.label_missed_absent)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(presentCount.toIntOrNull() ?: 0, absentCount.toIntOrNull() ?: 0) },
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_add_records)) }
        },
        dismissButton = { 
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_cancel)) } 
        }
    )
}

@Composable
fun MarkAttendanceDialog(
    date: LocalDate,
    recordsForDay: List<AttendanceRecord>,
    schedulesForDay: List<com.ankit.attendwise.data.ClassSchedule>,
    isHoliday: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String?) -> Unit,
    onConfirmCancelled: (String?) -> Unit,
    onToggleHoliday: () -> Unit,
    onDeleteMain: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    onAddExtra: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.toString(), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isHoliday) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = HolidayYellow.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.holiday_info_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HolidayYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onToggleHoliday(); onDismiss() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HolidayYellow),
                                border = BorderStroke(1.dp, HolidayYellow)
                            ) {
                                Text(stringResource(R.string.action_remove) + " " + stringResource(R.string.mark_holiday))
                            }
                        }
                    }
                }

                if (recordsForDay.isNotEmpty()) {
                    Text(stringResource(R.string.attendance_history_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    recordsForDay.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                val status = when {
                                    record.type == RecordType.CANCELLED -> stringResource(R.string.mark_cancelled)
                                    record.isPresent -> stringResource(R.string.mark_present)
                                    else -> stringResource(R.string.mark_absent)
                                }
                                val color = when {
                                    record.type == RecordType.CANCELLED -> MaterialTheme.colorScheme.outline
                                    record.isPresent -> SuccessGreen
                                    else -> ErrorRed
                                }
                                Text(status, color = color, fontWeight = FontWeight.Bold)
                                if (record.note.isNotEmpty()) {
                                    Text(record.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { onDeleteRecord(record.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                if (!isHoliday) {
                    if (schedulesForDay.isNotEmpty()) {
                        Text(stringResource(R.string.scheduled_sessions_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        schedulesForDay.forEach { schedule ->
                            val timeStr = formatTime(schedule.startHour, schedule.startMinute) + " - " + formatTime(schedule.endHour, schedule.endMinute)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(timeStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { onConfirm(true, schedule.id) }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.mark_present), tint = SuccessGreen)
                                    }
                                    IconButton(onClick = { onConfirm(false, schedule.id) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.mark_absent), tint = ErrorRed)
                                    }
                                    IconButton(onClick = { onConfirmCancelled(schedule.id) }) {
                                        Icon(Icons.Default.EventBusy, contentDescription = stringResource(R.string.mark_cancelled), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.no_schedules_text), style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirm(true, null) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.mark_present))
                            }
                            Button(onClick = { onConfirm(false, null) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.mark_absent))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(stringResource(R.string.quick_actions_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    AttendanceActionRow(Icons.Default.AddCircle, stringResource(R.string.action_add_extra_present), { onAddExtra(true); onDismiss() }, true)
                    AttendanceActionRow(Icons.Default.RemoveCircle, stringResource(R.string.action_add_extra_absent), { onAddExtra(false); onDismiss() }, false)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDeleteMain,
                shape = RoundedCornerShape(12.dp),
                enabled = recordsForDay.isNotEmpty()
            ) { Text(stringResource(R.string.action_clear_day)) }
        },
        dismissButton = { 
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_close)) } 
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}

@Composable
fun AttendanceActionRow(icon: ImageVector, label: String, onClick: () -> Unit, isPositive: Boolean, overrideColor: Color? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseColor = overrideColor ?: if (isPositive) SuccessGreen else ErrorRed
        val tint = if (enabled) baseColor else baseColor.copy(alpha = 0.38f)
        val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

@Composable
fun AttendanceProgressCard(subjectWithAttendance: SubjectWithAttendance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCircularProgress(
                progress = subjectWithAttendance.percentage.toFloat() / 100f,
                color = ColorUtils.safeParseColor(subjectWithAttendance.subject.color),
                size = 100.dp,
                strokeWidth = 10.dp
            )
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    text = subjectWithAttendance.subject.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.label_attendance_target) + ": ${subjectWithAttendance.subject.targetAttendance}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AttendanceStatsCard(subjectWithAttendance: SubjectWithAttendance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(stringResource(R.string.stat_total), subjectWithAttendance.totalClasses.toString(), MaterialTheme.colorScheme.primary)
            StatItem(stringResource(R.string.stat_attended), subjectWithAttendance.presentClasses.toString(), SuccessGreen)
            StatItem(stringResource(R.string.stat_missed), (subjectWithAttendance.totalClasses - subjectWithAttendance.presentClasses).toString(), ErrorRed)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BunkAnalysisCard(analysis: BunkAnalysis, subject: Subject) {
    val (text, color) = when {
        analysis.classesToBunk > 0 -> {
            stringResource(R.string.bunk_analysis_safe_extended, analysis.classesToBunk, subject.targetAttendance) to SuccessGreen
        }
        analysis.classesToAttend > 0 -> {
            stringResource(R.string.bunk_analysis_risk_extended, analysis.classesToAttend, subject.targetAttendance) to ErrorRed
        }
        else -> {
            stringResource(R.string.bunk_analysis_on_track_extended, subject.targetAttendance) to MaterialTheme.colorScheme.primary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.content_desc_bunk_analysis), tint = color)
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AnimatedCircularProgress(
    progress: Float,
    color: Color,
    size: Dp,
    strokeWidth: Dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                color = color.copy(alpha = 0.1f),
                style = Stroke(width = strokeWidth.toPx())
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360 * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun AttendanceCalendar(
    subjectRecords: List<AttendanceRecord>,
    allRecords: List<AttendanceRecord>,
    onDayClick: (LocalDate) -> Unit
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(24) }
    val endMonth = remember { currentMonth.plusMonths(24) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]

    // PERFORMANCE FIX: Pre-process records into a Map for O(1) lookup during day rendering
    val recordsByDate = remember(subjectRecords) {
        subjectRecords.asSequence().groupBy { it.date }
    }
    
    val holidaysByDate = remember(allRecords) {
        allRecords.asSequence().filter { it.type == RecordType.HOLIDAY }.associateBy { it.date }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.minusMonths(1))
                }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
            }

            Text(
                text = state.firstVisibleMonth.yearMonth.month.getDisplayName(TextStyle.FULL, locale) + " " + state.firstVisibleMonth.yearMonth.year,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.plusMonths(1))
                }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
            }
        }
        
        HorizontalCalendar(
            state = state,
            dayContent = { day ->
                val dateAsLong = day.date.toEpochDay()
                val dateRecords = recordsByDate[dateAsLong] ?: emptyList()
                val isHoliday = holidaysByDate.containsKey(dateAsLong)
                Day(day, dateRecords, isHoliday, onDayClick)
            }
        )
    }
}

@Composable
fun Day(
    day: CalendarDay,
    records: List<AttendanceRecord>,
    isHoliday: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val hasRecords = records.any { it.type != RecordType.HOLIDAY }
    val isToday = day.date == LocalDate.now()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isHoliday -> HolidayYellow.copy(alpha = 0.2f)
                    isToday && day.position == DayPosition.MonthDate -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .then(
                if (isToday && day.position == DayPosition.MonthDate) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(enabled = day.position == DayPosition.MonthDate) { onClick(day.date) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday && day.position == DayPosition.MonthDate) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    day.position != DayPosition.MonthDate -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isHoliday -> HolidayYellow
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasRecords) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    records.filter { it.type != RecordType.HOLIDAY }.take(3).forEach { record ->
                        val dotColor = when {
                            record.type == RecordType.CANCELLED -> MaterialTheme.colorScheme.outline
                            record.isPresent -> SuccessGreen
                            else -> ErrorRed
                        }
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }
}
