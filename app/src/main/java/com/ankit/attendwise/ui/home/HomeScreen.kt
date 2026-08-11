package com.ankit.attendwise.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ankit.attendwise.data.BunkAnalysis
import com.ankit.attendwise.data.RecordType
import com.ankit.attendwise.data.Subject
import com.ankit.attendwise.models.ScheduleWithSubject
import com.ankit.attendwise.models.SubjectWithAttendance
import com.ankit.attendwise.ui.theme.ErrorRed
import com.ankit.attendwise.ui.theme.PoppinsFamily
import com.ankit.attendwise.ui.theme.SuccessGreen
import com.ankit.attendwise.utils.ColorUtils
import com.ankit.attendwise.utils.Constants.ID_SCHEDULE_EXTRA
import com.ankit.attendwise.viewmodel.AppViewModel
import androidx.compose.ui.res.stringResource
import com.ankit.attendwise.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class GreetingInfo(
    val greetingResId: Int,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

fun getGreetingInfo(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): GreetingInfo {
    return when (hour) {
        // Morning: 4:00 AM - 11:59 AM
        in 4..11 -> GreetingInfo(
            R.string.greeting_morning,
            Icons.Default.WbSunny,
            listOf(Color(0xFFFE8C00), Color(0xFFF83600))
        )
        // Afternoon: 12:00 PM - 4:59 PM
        in 12..16 -> GreetingInfo(
            R.string.greeting_afternoon,
            Icons.Default.WbCloudy,
            listOf(Color(0xFF89F7FE), Color(0xFF66A6FF))
        )
        // Evening: 5:00 PM - 7:59 PM
        in 17..19 -> GreetingInfo(
            R.string.greeting_evening,
            Icons.Default.WbTwilight,
            listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))
        )
        // Night: 8:00 PM - 3:59 AM
        else -> GreetingInfo(
            R.string.greeting_evening,
            Icons.Default.NightsStay,
            listOf(Color(0xFF232526), Color(0xFF414345))
        )
    }
}

@Composable
fun HomeScreen(navController: NavController, appViewModel: AppViewModel) {
    val subjectsWithAttendance by appViewModel.subjectsWithAttendance.collectAsStateWithLifecycle()
    val dashboardData by appViewModel.dashboardScheduleWithSubjects.collectAsStateWithLifecycle()
    val (isTomorrow, displaySchedule) = dashboardData
    
    val userName by appViewModel.userName.collectAsStateWithLifecycle()
    val bunkAnalysisMap by appViewModel.bunkAnalysisMap.collectAsStateWithLifecycle()
    val currentDate by appViewModel.currentDate.collectAsStateWithLifecycle()
    
    var showExtraClassDialog by remember { mutableStateOf(false) }
    
    val haptic = LocalHapticFeedback.current

    val allSubjects by appViewModel.allSubjects.collectAsStateWithLifecycle()
    val isTodayHoliday by appViewModel.isTodayHoliday.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GreetingCard(userName, currentDate)
        }

        item {
            QuickActions(
                onNewSubjectClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate("add_subject") 
                },
                onExtraClassClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showExtraClassDialog = true 
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isTomorrow) stringResource(R.string.section_tomorrows_classes) else stringResource(R.string.section_todays_classes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                TextButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    appViewModel.toggleTomorrowPreview() 
                }) {
                    Text(if (isTomorrow) stringResource(R.string.action_show_today) else "Next Day")
                }
            }
        }

        if (isTodayHoliday && !isTomorrow) {
            item {
                EmptyState(
                    icon = Icons.Default.WbSunny,
                    title = stringResource(R.string.holiday_title),
                    subtitle = stringResource(R.string.holiday_subtitle),
                    actionLabel = stringResource(R.string.action_remove) + " " + stringResource(R.string.mark_holiday),
                    onActionClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        appViewModel.onHolidayToggleRequested(LocalDate.now()) 
                    }
                )
            }
        } else if (displaySchedule.isEmpty()) {
            item {
                val isTomorrowHoliday by appViewModel.allAttendanceRecords.collectAsStateWithLifecycle()
                val tomorrowDate = currentDate.plusDays(1)
                val isHoliday = isTomorrow && isTomorrowHoliday.any { it.date == tomorrowDate.toEpochDay() && it.type == RecordType.HOLIDAY }

                EmptyState(
                    icon = if (isHoliday) Icons.Default.WbSunny else Icons.Default.CalendarToday,
                    title = stringResource(
                        if (isHoliday) R.string.holiday_title 
                        else if (isTomorrow) R.string.no_classes_tomorrow_title 
                        else R.string.no_classes_title
                    ),
                    subtitle = stringResource(
                        if (isHoliday) R.string.holiday_subtitle 
                        else if (isTomorrow) R.string.no_classes_tomorrow_subtitle 
                        else R.string.no_classes_subtitle
                    )
                )
            }
        } else {
            items(displaySchedule, key = { it.schedule.id }) { schedule ->
                val markingDate = schedule.effectiveDate ?: (if (isTomorrow) currentDate.plusDays(1) else currentDate)
                TodayScheduleCard(
                    scheduleWithSubject = schedule,
                    appViewModel = appViewModel,
                    date = markingDate,
                    onClick = { navController.navigate("subject_detail/${schedule.subject.id}") }
                )
            }
        }

        item {
            Text(
                stringResource(R.string.section_all_subjects),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (subjectsWithAttendance.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.no_subjects_title),
                    subtitle = stringResource(R.string.no_subjects_subtitle),
                    actionLabel = stringResource(R.string.action_add_subject),
                    onActionClick = { navController.navigate("add_subject") }
                )
            }
        } else {
            items(subjectsWithAttendance, key = { it.subject.id }) { subjectWithAttendance ->
                SubjectCard(
                    subjectWithAttendance = subjectWithAttendance,
                    bunkAnalysis = bunkAnalysisMap[subjectWithAttendance.subject.id],
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.navigate("subject_detail/${subjectWithAttendance.subject.id}") 
                    }
                )
            }
        }
    }

    if (showExtraClassDialog) {
        var pendingConfirmationData by remember { mutableStateOf<Triple<String, Boolean, Int>?>(null) }

        if (pendingConfirmationData != null) {
            val (subId, present, count) = pendingConfirmationData!!
            val subjectName = allSubjects.find { it.id == subId }?.name ?: "Subject"
            
            AlertDialog(
                onDismissRequest = { pendingConfirmationData = null },
                title = { Text(stringResource(R.string.dialog_extra_class_confirm_title)) },
                text = { Text(stringResource(R.string.dialog_extra_class_confirm_text, count, subjectName)) },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val markingDate = if (isTomorrow) currentDate.plusDays(1) else currentDate
                            appViewModel.addExtraClasses(subId, markingDate, present, count)
                            pendingConfirmationData = null
                            showExtraClassDialog = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirmationData = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        ExtraClassDialog(
            subjects = allSubjects,
            onDismiss = { showExtraClassDialog = false },
            onConfirm = { subjectId, isPresent, count ->
                pendingConfirmationData = Triple(subjectId, isPresent, count)
            }
        )
    }
}

@Composable
fun ExtraClassDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (subjectId: String, isPresent: Boolean, count: Int) -> Unit
) {
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var isPresent by remember { mutableStateOf(true) }
    var count by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extra_class_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (subjects.isEmpty()) {
                    Text(stringResource(R.string.extra_class_no_subjects))
                } else {
                    Text(stringResource(R.string.extra_class_subject), fontWeight = FontWeight.Bold)
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subjects.forEach { subject ->
                            FilterChip(
                                selected = selectedSubjectId == subject.id,
                                onClick = { selectedSubjectId = subject.id },
                                label = { Text(subject.name) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.extra_class_status), fontWeight = FontWeight.Bold)
                        Row {
                            FilterChip(
                                selected = isPresent,
                                onClick = { isPresent = true },
                                label = { Text(stringResource(R.string.mark_present)) },
                                leadingIcon = if (isPresent) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                } else null,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = !isPresent,
                                onClick = { isPresent = false },
                                label = { Text(stringResource(R.string.mark_absent)) },
                                leadingIcon = if (!isPresent) {
                                    { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }
                                } else null,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.extra_class_count), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (count > 1) count-- }) {
                                Icon(Icons.Default.Remove, null)
                            }
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { count++ }) {
                                Icon(Icons.Default.Add, null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSubjectId, isPresent, count) },
                enabled = subjects.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_add)) }
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
fun GreetingCard(userName: String, currentDate: LocalDate) {
    // REACIVE HOUR: Re-calculated whenever the 30-second heartbeat fires
    val currentHour = remember(currentDate) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingInfo = getGreetingInfo(currentHour)
    
    val date = remember(currentDate) { 
        currentDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) 
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = greetingInfo.gradientColors))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = greetingInfo.icon,
                    contentDescription = stringResource(R.string.content_desc_greeting_icon),
                    modifier = Modifier.size(80.dp),
                    tint = Color.White.copy(alpha = 0.9f)
                )
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column {
                    Text(
                        text = "${stringResource(greetingInfo.greetingResId)}, ${userName.ifEmpty { stringResource(R.string.greeting_student) }} !",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = PoppinsFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun QuickActions(onNewSubjectClick: () -> Unit, onExtraClassClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            title = stringResource(R.string.extra_class_dialog_title),
            onClick = onExtraClassClick
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Add,
            title = stringResource(R.string.action_add_subject),
            onClick = onNewSubjectClick
        )
    }
}

@Composable
fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TodayScheduleCard(
    scheduleWithSubject: ScheduleWithSubject,
    appViewModel: AppViewModel,
    date: LocalDate,
    onClick: () -> Unit
) {
    val record = scheduleWithSubject.attendanceRecord
    val haptic = LocalHapticFeedback.current
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "card_scale")

    // Use derivedStateOf to avoid UI flickering during recomposition
    val attendanceState by remember(record) {
        derivedStateOf {
            Triple(record != null, record?.type, record?.isPresent)
        }
    }
    val (isAlreadyMarked, recordType, wasPresent) = attendanceState

    val subject = scheduleWithSubject.subject
    val schedule = scheduleWithSubject.schedule
    val startTime = remember(schedule.startHour, schedule.startMinute) { formatTime(schedule.startHour, schedule.startMinute) }
    val endTime = remember(schedule.endHour, schedule.endMinute) { formatTime(schedule.endHour, schedule.endMinute) }
    val subjectColor = remember(subject.color) { ColorUtils.safeParseColor(subject.color) }
    val isLive = scheduleWithSubject.isLive
    val isExtra = record?.scheduleId == ID_SCHEDULE_EXTRA

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(subjectColor)
                )
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = if (isExtra) stringResource(R.string.extra_session_label) else "$startTime - $endTime",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isLive && !isAlreadyMarked && !isExtra) {
                            LiveBadge()
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = isAlreadyMarked,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300)) using
                                    SizeTransform(clip = false)
                        },
                        modifier = Modifier.fillMaxWidth(), label = "attendance_buttons"
                    ) { marked ->
                        if (marked) {
                            val (icon, text, color) = when (recordType) {
                                RecordType.CANCELLED -> Triple(Icons.Filled.EventBusy, stringResource(R.string.class_cancelled), MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> if (wasPresent == true) Triple(Icons.Filled.CheckCircle, stringResource(R.string.marked_as_present), SuccessGreen) else Triple(Icons.Filled.Cancel, stringResource(R.string.marked_as_absent), ErrorRed)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, tint = color)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        appViewModel.markDateAsPresent(subject.id, schedule.id, date) 
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.mark_present), style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        appViewModel.markDateAsAbsent(subject.id, schedule.id, date) 
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.mark_absent), style = MaterialTheme.typography.labelLarge)
                                }
                                IconButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        appViewModel.markDateAsCancelled(subject.id, schedule.id, date) 
                                    },
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.mark_cancelled)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
            Text(
                stringResource(R.string.live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}

@Composable
fun SubjectCard(subjectWithAttendance: SubjectWithAttendance, bunkAnalysis: BunkAnalysis?, onClick: () -> Unit) {
    val subject = subjectWithAttendance.subject
    val percentage = subjectWithAttendance.percentage
    val subjectColor = remember(subject.color) { ColorUtils.safeParseColor(subject.color) }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "card_scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(subjectColor, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PoppinsFamily
                    )
                }
                
                CircularProgressIndicator(
                    progress = { (percentage / 100f).toFloat() },
                    modifier = Modifier.size(48.dp),
                    color = subjectColor,
                    strokeWidth = 6.dp,
                    trackColor = subjectColor.copy(alpha = 0.2f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "%.1f%%".format(percentage),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (percentage < subject.targetAttendance) ErrorRed else SuccessGreen
                    )
                    Text(
                        text = stringResource(R.string.attendance_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (bunkAnalysis != null) {
                    BunkAnalysisText(bunkAnalysis)
                }
            }
        }
    }
}

@Composable
fun BunkAnalysisText(analysis: BunkAnalysis) {
    val text = when {
        analysis.classesToBunk > 0 -> stringResource(R.string.bunk_safe, analysis.classesToBunk)
        analysis.classesToAttend > 0 -> stringResource(R.string.bunk_risk, analysis.classesToAttend)
        else -> stringResource(R.string.bunk_on_track)
    }
    val color = if (analysis.classesToBunk > 0) SuccessGreen else if (analysis.classesToAttend > 0) ErrorRed else MaterialTheme.colorScheme.primary

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
