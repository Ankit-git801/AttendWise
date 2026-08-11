@file:OptIn(ExperimentalMaterial3Api::class)

package com.ankit.attendwise.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.ankit.attendwise.viewmodel.AppViewModel
import androidx.compose.ui.res.stringResource
import com.ankit.attendwise.R
import android.widget.Toast

@Composable
fun SettingsScreen(navController: NavController, appViewModel: AppViewModel) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    val currentTheme by appViewModel.theme.collectAsStateWithLifecycle()
    val userName by appViewModel.userName.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = {
                appViewModel.setTheme(it)
                showThemeDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_delete_all_title)) },
            text = { Text(stringResource(R.string.dialog_delete_all_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.deleteAllData()
                        showDeleteDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.dialog_delete_all_confirm)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { showDeleteDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    if (showNameDialog) {
        UserNameDialog(
            currentName = userName,
            onDismiss = { showNameDialog = false },
            onNameChange = {
                appViewModel.updateUserName(it)
                showNameDialog = false
            }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                SettingsSectionTitle(stringResource(R.string.section_cloud_account))
                CloudSettingsItem(appViewModel)
            }
            item {
                SettingsSectionTitle(stringResource(R.string.section_personalization))
                SettingsItem(
                    title = stringResource(R.string.setting_user_name),
                    subtitle = userName,
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    onClick = { showNameDialog = true }
                )
                SettingsItem(
                    title = stringResource(R.string.setting_theme),
                    subtitle = currentTheme,
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SettingsSectionTitle(stringResource(R.string.section_general))
                SettingsItem(
                    title = stringResource(R.string.setting_weekly_schedule),
                    subtitle = stringResource(R.string.setting_weekly_schedule_subtitle),
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    onClick = { navController.navigate("weekly_schedule") }
                )
                SettingsItem(
                    title = stringResource(R.string.setting_send_feedback),
                    subtitle = stringResource(R.string.setting_send_feedback_subtitle),
                    icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:ak8485332@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "AttendWise Feedback")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, R.string.error_no_email_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item {
                SettingsSectionTitle(stringResource(R.string.section_data_management))
                SettingsItem(
                    title = stringResource(R.string.setting_delete_data),
                    subtitle = stringResource(R.string.setting_delete_data_subtitle),
                    isDestructive = true,
                    icon = {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                        )
                    },
                    onClick = { showDeleteDialog = true }
                )
            }
            item {
                SettingsSectionTitle(stringResource(R.string.section_system_permissions))
                SystemPermissionItem(
                    title = stringResource(R.string.setting_exact_alarms),
                    subtitle = stringResource(R.string.setting_exact_alarms_subtitle),
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            try {
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }
                )
            }
            item {
                SettingsSectionTitle(stringResource(R.string.section_about))
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun UserNameDialog(currentName: String, onDismiss: () -> Unit, onNameChange: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_change_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onNameChange(name) })
            )
        },
        confirmButton = { 
            Button(
                onClick = { onNameChange(name) },
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_save)) } 
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
fun ThemeDialog(currentTheme: String, onDismiss: () -> Unit, onThemeSelected: (String) -> Unit) {
    val themes = listOf(
        stringResource(R.string.theme_system) to "System Default",
        stringResource(R.string.theme_light) to "Light",
        stringResource(R.string.theme_dark) to "Dark"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_choose_theme_title)) },
        text = {
            Column {
                themes.forEach { (displayTheme, themeValue) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(themeValue) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themeValue == currentTheme),
                            onClick = { onThemeSelected(themeValue) }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(displayTheme, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { 
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_cancel)) } 
        }
    )
}

@Composable
fun CloudSettingsItem(appViewModel: AppViewModel) {
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    val isSyncing by appViewModel.isSyncing.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            user = auth.currentUser
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.dialog_sign_out_title)) },
            text = { Text(stringResource(R.string.dialog_sign_out_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        appViewModel.logout()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_sign_out)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { showLogoutDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.action_cancel)) } 
            }
        )
    }

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            appViewModel = appViewModel,
            onResult = { success, error ->
                if (success) {
                    showAuthDialog = false
                    errorMessage = null
                } else {
                    errorMessage = error
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.section_cloud_account), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            
            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(stringResource(R.string.backup_status_syncing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                val displayMessage = when {
                    user != null -> stringResource(R.string.backup_status_account, user?.email ?: "")
                    else -> errorMessage ?: stringResource(R.string.backup_status_no_account)
                }
                Text(
                    text = displayMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            
            if (user == null) {
                Button(
                    onClick = { showAuthDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_connect_account))
                }
            } else {
                OutlinedButton(
                    onClick = { 
                        showLogoutDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }
}

@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    appViewModel: AppViewModel,
    initialIsSignUp: Boolean = false,
    onResult: (Boolean, String?) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(initialIsSignUp) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSignUp) stringResource(R.string.dialog_auth_signup_title) else stringResource(R.string.dialog_auth_signin_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text(stringResource(R.string.label_email)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = icon, contentDescription = description)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                
                TextButton(
                    onClick = { isSignUp = !isSignUp; error = null },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isSignUp) stringResource(R.string.auth_switch_to_signin) else stringResource(R.string.auth_switch_to_signup))
                }

                        if (!isSignUp) {
                    val authEnterEmailReset = stringResource(R.string.auth_enter_email_reset)
                    val authResetSent = stringResource(R.string.auth_reset_sent)
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                error = authEnterEmailReset
                                return@TextButton
                            }
                            appViewModel.resetPassword(email) { success, msg ->
                                error = if (success) authResetSent else msg
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.auth_forgot_password), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            val fillFieldsError = stringResource(R.string.error_fill_fields)
            val wrongPasswordError = stringResource(R.string.error_wrong_password)
            val userNotFoundError = stringResource(R.string.error_user_not_found)
            val authFailedError = stringResource(R.string.error_auth_failed)
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = fillFieldsError
                        return@Button
                    }
                    isLoading = true
                    if (isSignUp) {
                        appViewModel.signUpWithEmail(email, password) { success, msg ->
                            isLoading = false
                            if (success) {
                                onResult(true, null)
                            } else {
                                error = msg
                                onResult(false, msg)
                            }
                        }
                    } else {
                        appViewModel.loginWithEmail(email, password) { success, msg ->
                            isLoading = false
                            if (success) {
                                onResult(true, null)
                            } else {
                                val finalMsg = when {
                                    msg?.contains("password", ignoreCase = true) == true || 
                                    msg?.contains("auth is incorrect", ignoreCase = true) == true ||
                                    msg?.contains("auth credential is incorrect", ignoreCase = true) == true ||
                                    msg?.contains("invalid-credential", ignoreCase = true) == true ||
                                    msg?.contains("invalid credential", ignoreCase = true) == true -> wrongPasswordError
                                    msg?.contains("user-not-found", ignoreCase = true) == true || msg?.contains("no user", ignoreCase = true) == true -> userNotFoundError
                                    else -> msg ?: authFailedError
                                }
                                error = finalMsg
                                onResult(false, finalMsg)
                            }
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (isSignUp) stringResource(R.string.action_sign_up) else stringResource(R.string.action_sign_in))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss, 
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    val subtitleColor = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(modifier = Modifier.size(24.dp)) {
                icon()
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
        }
        if (trailingIcon != null) {
            trailingIcon()
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SystemPermissionItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
