package com.ankit.attendwise.ui.addsubject

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ankit.attendwise.R
import com.ankit.attendwise.data.ClassSchedule
import com.ankit.attendwise.data.Subject
import com.ankit.attendwise.utils.ColorUtils
import com.ankit.attendwise.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

data class UiClassSchedule(
    val schedule: ClassSchedule,
    val localId: UUID = UUID.randomUUID()
)

val predefinedColors = listOf(
    "#FF9800", "#FFB74D", "#E91E63", "#F06292", "#9C27B0",
    "#BA68C8", "#2196F3", "#64B5F6", "#4CAF50", "#81C784"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubjectScreen(
    navController: NavController,
    subjectId: String = "",
    appViewModel: AppViewModel
) {
    var subjectName by remember { mutableStateOf("") }
    var subjectColor by remember { mutableStateOf(predefinedColors.first()) }
    var attendanceTarget by remember { mutableIntStateOf(75) }
    var schedules by remember { mutableStateOf<List<UiClassSchedule>>(emptyList()) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    var pastPresent by remember { mutableStateOf("0") }
    var pastAbsent by remember { mutableStateOf("0") }
    
    val haptic = LocalHapticFeedback.current

    var hasChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val onBackRequest = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(onBack = { onBackRequest() })

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_changes_title)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        navController.popBackStack()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { 
                TextButton({ showDiscardDialog = false }) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    val isEditMode = subjectId.isNotEmpty()
    val allExistingSchedules by appViewModel.allSchedules.collectAsStateWithLifecycle()

    val hasInvalidSchedules = schedules.any { 
        it.schedule.startHour == it.schedule.endHour && it.schedule.startMinute == it.schedule.endMinute
    }
    
    val hasOverlappingSchedules = remember(schedules, allExistingSchedules, subjectId) {
        val otherSubjectSchedules = allExistingSchedules.filter { it.subjectId != subjectId }
        val currentSubjectSchedules = schedules.map { it.schedule }
        val allSchedulesList = currentSubjectSchedules + otherSubjectSchedules

        // Normalize each schedule into [start, end] intervals in minutes since Sunday 00:00.
        val weekIntervals = allSchedulesList.flatMap { schedule ->
            val startMins = ((schedule.dayOfWeek - 1) * 1440) + (schedule.startHour * 60) + schedule.startMinute
            val duration = if (schedule.endHour * 60 + schedule.endMinute <= schedule.startHour * 60 + schedule.startMinute) {
                // If it ends exactly at start, it's 0 duration (handled by hasInvalidSchedules)
                // Otherwise it's overnight
                if (schedule.endHour == schedule.startHour && schedule.endMinute == schedule.startMinute) 0
                else (1440 - (schedule.startHour * 60 + schedule.startMinute)) + (schedule.endHour * 60 + schedule.endMinute)
            } else {
                (schedule.endHour * 60 + schedule.endMinute) - (schedule.startHour * 60 + schedule.startMinute)
            }
            
            val endMins = startMins + duration
            val totalWeekMins = 7 * 1440
            
            if (endMins > totalWeekMins) {
                listOf(startMins to totalWeekMins, 0 to (endMins % totalWeekMins))
            } else {
                listOf(startMins to endMins)
            }
        }.filter { it.first < it.second }.sortedBy { it.first }

        for (i in 0 until weekIntervals.size - 1) {
            // Strictly greater than (>) allows back-to-back classes
            if (weekIntervals[i].second > weekIntervals[i+1].first) return@remember true
        }
        
        false
    }

    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(subjectId) {
        if (isEditMode) {
            appViewModel.getSubjectById(subjectId)?.let { subject ->
                subjectName = subject.name
                subjectColor = subject.color
                attendanceTarget = subject.targetAttendance
            }
            schedules = appViewModel.getSchedulesForSubject(subjectId).map { UiClassSchedule(it) }
        }
        isInitialized = true
        hasChanges = false
    }

    var scheduleToDelete by remember { mutableStateOf<UiClassSchedule?>(null) }

    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(stringResource(R.string.dialog_remove_schedule_title)) },
            text = { Text(stringResource(R.string.dialog_remove_schedule_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        schedules = schedules.filter { it.localId != scheduleToDelete!!.localId }
                        scheduleToDelete = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton({ scheduleToDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) stringResource(R.string.edit_subject_title) else stringResource(R.string.add_subject_title)) },
                navigationIcon = {
                    IconButton(onClick = { onBackRequest() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newSubject = Subject(
                                id = if (isEditMode) subjectId else "",
                                name = subjectName,
                                color = subjectColor,
                                targetAttendance = attendanceTarget
                            )
                            val schedulesList = schedules.map { it.schedule }
                            appViewModel.addOrUpdateSubject(
                                newSubject,
                                schedulesList,
                                pastPresent.toIntOrNull() ?: 0,
                                pastAbsent.toIntOrNull() ?: 0
                            )
                            hasChanges = false
                            navController.popBackStack()
                        },
                        enabled = subjectName.isNotBlank() && schedules.isNotEmpty() && !hasInvalidSchedules && !hasOverlappingSchedules
                    ) {
                        Text(if (isEditMode) stringResource(R.string.action_update) else stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { paddingValues ->
        LaunchedEffect(subjectName, subjectColor, attendanceTarget, schedules, pastPresent, pastAbsent) {
            if (isInitialized) {
                hasChanges = true
            }
        }
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasInvalidSchedules || hasOverlappingSchedules) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (hasInvalidSchedules) stringResource(R.string.error_invalid_schedules) else stringResource(R.string.error_overlapping_schedules),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text(stringResource(R.string.label_subject_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Book, null) }
                )
            }
            item { ColorPicker(selectedColor = subjectColor, onColorSelected = { subjectColor = it }) }
            item { AttendanceTargetSlider(attendanceTarget, onTargetChange = { attendanceTarget = it }) }

            if (!isEditMode) {
                item {
                    PastAttendanceInput(
                        presentCount = pastPresent,
                        absentCount = pastAbsent,
                        onPresentChange = { pastPresent = it },
                        onAbsentChange = { pastAbsent = it }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_class_schedule), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { showAddScheduleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
            if (schedules.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.no_schedules_text),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(schedules, key = { it.localId }) { uiSchedule ->
                    ScheduleCard(uiSchedule.schedule) {
                        scheduleToDelete = uiSchedule
                    }
                }
            }
        }
    }

    if (showAddScheduleDialog) {
        AddScheduleDialog(
            onDismiss = { showAddScheduleDialog = false },
            onAddSchedule = { newSchedule -> schedules = schedules + UiClassSchedule(newSchedule) }
        )
    }
}

@Composable
private fun ColorPicker(selectedColor: String, onColorSelected: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.label_subject_color),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(predefinedColors) { colorHex ->
                    val isSelected = colorHex.equals(selectedColor, ignoreCase = true)
                    val color = ColorUtils.safeParseColor(colorHex)
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(colorHex) }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.content_desc_selected),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceTargetSlider(target: Int, onTargetChange: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(stringResource(R.string.label_attendance_target), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$target%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = target.toFloat(),
                onValueChange = { onTargetChange(it.toInt()) },
                valueRange = 50f..100f,
                steps = 9
            )
        }
    }
}

@Composable
private fun PastAttendanceInput(
    presentCount: String,
    absentCount: String,
    onPresentChange: (String) -> Unit,
    onAbsentChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.label_past_attendance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.label_past_attendance_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = presentCount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) onPresentChange(it) },
                    label = { Text(stringResource(R.string.stat_attended)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(
                    value = absentCount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) onAbsentChange(it) },
                    label = { Text(stringResource(R.string.stat_missed)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(schedule: ClassSchedule, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(getDayName(schedule.dayOfWeek), fontWeight = FontWeight.Bold)
                Text("${formatTime(schedule.startHour, schedule.startMinute)} - ${formatTime(schedule.endHour, schedule.endMinute)}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onAddSchedule: (ClassSchedule) -> Unit) {
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }

    val calendar = Calendar.getInstance()
    var startHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    calendar.add(Calendar.HOUR_OF_DAY, 1)
    var endHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var endMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    val context = LocalContext.current

    val startTimePickerDialog = TimePickerDialog(context, { _, h, m -> startHour = h; startMinute = m }, startHour, startMinute, false)
    val endTimePickerDialog = TimePickerDialog(context, { _, h, m -> endHour = h; endMinute = m }, endHour, endMinute, false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_add_schedule_title))
            }
        },
        text = {
            Column {
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                DaySelector(selectedDay) { selectedDay = it }
                Spacer(Modifier.height(16.dp))
                TimeSelector(stringResource(R.string.label_start_time), startHour, startMinute) { startTimePickerDialog.show() }
                Spacer(Modifier.height(8.dp))
                TimeSelector(stringResource(R.string.label_end_time), endHour, endMinute) { endTimePickerDialog.show() }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddSchedule(ClassSchedule(id = "", subjectId = "", dayOfWeek = selectedDay, startHour = startHour, startMinute = startMinute, endHour = endHour, endMinute = endMinute))
                    onDismiss()
                },
                enabled = startHour != endHour || startMinute != endMinute
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySelector(selectedDay: Int, onDaySelected: (Int) -> Unit) {
    val days = listOf(
        stringResource(R.string.day_mon) to Calendar.MONDAY, 
        stringResource(R.string.day_tue) to Calendar.TUESDAY, 
        stringResource(R.string.day_wed) to Calendar.WEDNESDAY,
        stringResource(R.string.day_thu) to Calendar.THURSDAY, 
        stringResource(R.string.day_fri) to Calendar.FRIDAY, 
        stringResource(R.string.day_sat) to Calendar.SATURDAY, 
        stringResource(R.string.day_sun) to Calendar.SUNDAY
    )
    Column {
        Text(stringResource(R.string.label_day_of_week), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { (dayName, dayConstant) ->
                FilterChip(
                    selected = selectedDay == dayConstant,
                    onClick = { onDaySelected(dayConstant) },
                    label = { Text(dayName) }
                )
            }
        }
    }
}

@Composable
private fun TimeSelector(label: String, hour: Int, minute: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onClick) {
            Text(formatTime(hour, minute))
        }
    }
}

private fun getDayName(dayOfWeek: Int): String {
    val calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }
    return SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
}

private fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}
