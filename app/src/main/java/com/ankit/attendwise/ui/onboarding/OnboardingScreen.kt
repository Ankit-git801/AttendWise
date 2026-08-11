package com.ankit.attendwise.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ankit.attendwise.R
import com.ankit.attendwise.ui.settings.AuthDialog
import com.ankit.attendwise.ui.theme.PoppinsFamily
import com.ankit.attendwise.viewmodel.AppViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

enum class OnboardingStep {
    WELCOME, NAME_INPUT
}

@Composable
fun OnboardingScreen(appViewModel: AppViewModel, onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var name by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val visible = remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var initialIsSignUp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible.value = true
    }

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            appViewModel = appViewModel,
            initialIsSignUp = initialIsSignUp,
            onResult = { success, error ->
                if (success) {
                    showAuthDialog = false
                    if (initialIsSignUp) {
                        currentStep = OnboardingStep.NAME_INPUT
                    } else {
                        // For sign in, we assume name is restored or they can set it later in settings
                        appViewModel.skipOnboarding()
                        onComplete()
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = visible.value,
                enter = fadeIn(tween(1000)) + expandVertically(tween(1000))
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                },
                label = "onboarding_step"
            ) { step ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (step) {
                        OnboardingStep.WELCOME -> {
                            Text(
                                text = stringResource(R.string.welcome_title),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = PoppinsFamily,
                                lineHeight = 40.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.welcome_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(48.dp))

                            Button(
                                onClick = { 
                                    initialIsSignUp = true
                                    showAuthDialog = true 
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(stringResource(R.string.action_sign_up), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = { 
                                    initialIsSignUp = false
                                    showAuthDialog = true 
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_sign_in), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            TextButton(
                                onClick = { currentStep = OnboardingStep.NAME_INPUT },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.action_signup_later), style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        OnboardingStep.NAME_INPUT -> {
                            Text(
                                text = stringResource(R.string.personalize_title),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = PoppinsFamily
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.personalize_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.label_enter_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (name.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        appViewModel.completeOnboarding(name)
                                        onComplete()
                                    }
                                })
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        appViewModel.completeOnboarding(name)
                                        onComplete()
                                    }
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(stringResource(R.string.action_get_started), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
