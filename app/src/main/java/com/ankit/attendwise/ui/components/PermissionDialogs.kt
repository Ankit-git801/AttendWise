package com.ankit.attendwise.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.ankit.attendwise.R

@Composable
fun RequestAllPermissions() {
    RequestNotificationPermission()
    RequestExactAlarmPermission()
}

@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PermissionChecker.PERMISSION_GRANTED
            )
        }
        var showRationale by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                hasPermission = isGranted
            }
        )

        LaunchedEffect(hasPermission) {
            if (!hasPermission) {
                showRationale = true
            }
        }

        if (showRationale) {
            AlertDialog(
                onDismissRequest = { showRationale = false },
                title = { Text(stringResource(R.string.perm_notifications_rationale_title)) },
                text = { Text(stringResource(R.string.perm_notifications_rationale_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRationale = false
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRationale = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun RequestExactAlarmPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            var showDialog by remember { mutableStateOf(true) }
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(stringResource(R.string.perm_exact_alarm_title)) },
                    text = { Text(stringResource(R.string.perm_exact_alarm_text)) },
                    confirmButton = {
                        Button(onClick = {
                            showDialog = false
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                                context.startActivity(this)
                            }
                        }) { Text(stringResource(R.string.action_open_settings)) }
                    }
                )
            }
        }
    }
}
