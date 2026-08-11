package com.ankit.attendwise.ui.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ankit.attendwise.R
import com.ankit.attendwise.models.AttendanceStatistics
import com.ankit.attendwise.models.SubjectWithAttendance
import com.ankit.attendwise.ui.theme.ErrorRed
import com.ankit.attendwise.ui.theme.SuccessGreen
import com.ankit.attendwise.utils.ColorUtils
import com.ankit.attendwise.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController, appViewModel: AppViewModel) {
    val subjectsWithAttendance by appViewModel.subjectsWithAttendance.collectAsStateWithLifecycle()
    val stats by appViewModel.overallStatistics.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_stats), fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        if (subjectsWithAttendance.isEmpty()) {
            EmptyState(Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val averageTarget = remember(subjectsWithAttendance) {
                        if (subjectsWithAttendance.isEmpty()) 75f 
                        else subjectsWithAttendance.map { it.subject.targetAttendance }.average().toFloat()
                    }
                    OverallPerformanceCard(stats, averageTarget)
                }

                item {
                    Text(
                        stringResource(R.string.section_subject_breakdown),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp)
                    )
                }

                items(subjectsWithAttendance, key = { it.subject.id }) { subjectWithAttendance ->
                    SubjectStatCard(
                        subjectWithAttendance = subjectWithAttendance,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("subject_detail/${subjectWithAttendance.subject.id}")
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.no_stats_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_stats_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverallPerformanceCard(stats: AttendanceStatistics, target: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.section_overall_performance),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    percentage = stats.overallPercentage.toFloat(),
                    target = target
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    label = stringResource(R.string.stat_total),
                    value = stats.totalClasses.toString(),
                    icon = Icons.Default.Functions
                )
                StatItem(
                    label = stringResource(R.string.mark_present),
                    value = stats.totalPresent.toString(),
                    color = SuccessGreen,
                    icon = Icons.Default.CheckCircle
                )
                StatItem(
                    label = stringResource(R.string.mark_absent),
                    value = stats.totalAbsent.toString(),
                    color = ErrorRed,
                    icon = Icons.Default.Cancel
                )
            }
        }
    }
}

@Composable
private fun DonutChart(
    percentage: Float,
    target: Float,
    radius: Dp = 80.dp,
    strokeWidth: Dp = 16.dp
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 1000),
        label = "donut_chart_progress"
    )
    
    val chartColor = if (animatedPercentage >= target) SuccessGreen else ErrorRed

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(radius * 2)) {
        Canvas(modifier = Modifier.size(radius * 2)) {
            val sweepAngle = (animatedPercentage / 100) * 360f
            drawArc(
                color = chartColor.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = chartColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${"%.1f".format(animatedPercentage)}%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.mark_present), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) {
            Icon(icon, null, tint = color)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubjectStatCard(
    subjectWithAttendance: SubjectWithAttendance,
    onClick: () -> Unit
) {
    val subject = subjectWithAttendance.subject
    val percentage = subjectWithAttendance.percentage

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(
                        ColorUtils.safeParseColor(subject.color),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.label_attendance_target) + ": ${subject.targetAttendance}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${subjectWithAttendance.presentClasses}/${subjectWithAttendance.totalClasses}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )

            val color = if (percentage >= subject.targetAttendance) SuccessGreen else ErrorRed
            Row(
                modifier = Modifier.width(70.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "%.1f".format(percentage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .padding(start = 2.dp, bottom = 2.dp)
                        .alignByBaseline()
                )
            }
        }
    }
}
