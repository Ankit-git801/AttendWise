@file:OptIn(ExperimentalMaterial3Api::class)

package com.ankit.attendwise.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ankit.attendwise.data.AttendanceRecord
import com.ankit.attendwise.data.RecordType
import com.ankit.attendwise.models.AttendanceRecordWithSubject
import com.ankit.attendwise.ui.theme.ErrorRed
import com.ankit.attendwise.ui.theme.HolidayYellow
import com.ankit.attendwise.ui.theme.SuccessGreen
import com.ankit.attendwise.viewmodel.AppViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import androidx.compose.ui.res.stringResource
import com.ankit.attendwise.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(appViewModel: AppViewModel) {
    val allRecords by appViewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val allSubjects by appViewModel.allSubjects.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }
    var showHolidayConfirmation by remember { mutableStateOf(false) }

    val recordsForSelectedDate by remember(selectedDate) {
        if (selectedDate != null) {
            appViewModel.getRecordsForDate(selectedDate!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    if (showDeleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text(stringResource(R.string.dialog_delete_record_title)) },
            text = { Text(stringResource(R.string.dialog_delete_record_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        val recordId = showDeleteConfirmation!!
                        val subjectId = recordsForSelectedDate.find { it.attendanceRecord.id == recordId }?.attendanceRecord?.subjectId ?: ""
                        appViewModel.deleteAttendanceRecordById(recordId, subjectId)
                        showDeleteConfirmation = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { showDeleteConfirmation = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    if (showHolidayConfirmation && selectedDate != null) {
        HolidayConfirmationDialog(
            onConfirm = {
                appViewModel.onHolidayToggleConfirmed()
                showHolidayConfirmation = false
                showDialog = false
            },
            onDismiss = { showHolidayConfirmation = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_calendar)) })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AttendanceCalendar(
                allRecords = allRecords,
                onDayClick = { date ->
                    selectedDate = date
                    showDialog = true
                }
            )

            if (showDialog && selectedDate != null) {
                val holiday = allRecords.find { it.date == selectedDate!!.toEpochDay() && it.type == RecordType.HOLIDAY }
                val isCurrentlyHoliday = holiday != null

                DayDetailDialog(
                    date = selectedDate!!,
                    records = recordsForSelectedDate,
                    allSubjects = allSubjects,
                    isHoliday = isCurrentlyHoliday,
                    onDismiss = { showDialog = false },
                    onDeleteRecord = { recordId ->
                        showDeleteConfirmation = recordId
                    },
                    onToggleHoliday = {
                        if (isCurrentlyHoliday) {
                            appViewModel.onHolidayToggleRequested(selectedDate!!)
                            showDialog = false
                        } else {
                            appViewModel.onHolidayToggleRequested(selectedDate!!) // This sets the _showHolidayDialog value
                            showHolidayConfirmation = true
                        }
                    },
                    onConfirmCancelled = { subjectId ->
                        appViewModel.markDateAsCancelled(subjectId, selectedDate!!)
                    }
                )
            }
        }
    }
}

@Composable
fun DayDetailDialog(
    date: LocalDate,
    records: List<AttendanceRecordWithSubject>,
    allSubjects: List<com.ankit.attendwise.data.Subject>,
    isHoliday: Boolean,
    onDismiss: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    onToggleHoliday: () -> Unit,
    onConfirmCancelled: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = date.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isHoliday) {
                    Text(
                        stringResource(R.string.holiday_title),
                        color = HolidayYellow,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (records.isEmpty() && !isHoliday) {
                    Text(stringResource(R.string.no_records_day))
                }

                if (!isHoliday) {
                    var showCancelMenu by remember { mutableStateOf(false) }

                    Box {
                        TextButton(
                            onClick = { showCancelMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mark_cancelled))
                        }

                        DropdownMenu(
                            expanded = showCancelMenu,
                            onDismissRequest = { showCancelMenu = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            if (allSubjects.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.error_no_subjects)) },
                                    onClick = { showCancelMenu = false }
                                )
                            } else {
                                Text(
                                    stringResource(R.string.label_select_subject),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                allSubjects.forEach { subject ->
                                    DropdownMenuItem(
                                        text = { Text(subject.name) },
                                        onClick = {
                                            onConfirmCancelled(subject.id)
                                            showCancelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                records.forEach { recordWithSubject ->
                    val record = recordWithSubject.attendanceRecord
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recordWithSubject.subjectName ?: stringResource(R.string.label_unknown),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                val statusText = when {
                                    record.type == RecordType.CANCELLED -> stringResource(R.string.mark_cancelled)
                                    record.isPresent -> stringResource(R.string.mark_present)
                                    else -> stringResource(R.string.mark_absent)
                                }
                                val statusColor = when {
                                    record.type == RecordType.CANCELLED -> MaterialTheme.colorScheme.outline
                                    record.isPresent -> SuccessGreen
                                    else -> ErrorRed
                                }
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (record.note.isNotEmpty()) {
                                    Text(
                                        text = record.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteRecord(record.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onToggleHoliday,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isHoliday) stringResource(R.string.action_remove) + " " + stringResource(R.string.mark_holiday) else stringResource(R.string.action_add) + " " + stringResource(R.string.mark_holiday))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun HolidayConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_mark_holiday_title)) },
        text = { Text(stringResource(R.string.dialog_mark_holiday_text)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_proceed))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun AttendanceCalendar(
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

    val recordsByDate = remember(allRecords) {
        allRecords.groupBy { it.date }
    }

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]

    Column {
        val visibleMonth = state.firstVisibleMonth.yearMonth
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.minusMonths(1))
                }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.content_desc_prev_month))
            }

            Text(
                text = "${visibleMonth.month.getDisplayName(TextStyle.FULL, locale)} ${visibleMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            IconButton(onClick = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.plusMonths(1))
                }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.content_desc_next_month))
            }
        }
        
        HorizontalCalendar(
            state = state,
            dayContent = { day ->
                val dateAsLong = day.date.toEpochDay()
                val dayRecords = recordsByDate[dateAsLong] ?: emptyList()
                Day(day.date, dayRecords, onDayClick)
            }
        )
    }
}

@Composable
fun Day(
    date: LocalDate,
    records: List<AttendanceRecord>,
    onClick: (LocalDate) -> Unit
) {
    val isHoliday = records.any { it.type == RecordType.HOLIDAY }
    val hasRecords = records.any { it.type != RecordType.HOLIDAY }
    val isToday = date == LocalDate.now()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isHoliday -> HolidayYellow.copy(alpha = 0.2f)
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .then(
                if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
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
