/*
 * Copyright (c) 2026 Ankit. All rights reserved.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.ankit.attendwise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ankit.attendwise.ui.addsubject.AddSubjectScreen
import com.ankit.attendwise.ui.calendar.CalendarScreen
import com.ankit.attendwise.ui.home.HomeScreen
import com.ankit.attendwise.ui.onboarding.OnboardingScreen
import com.ankit.attendwise.ui.settings.SettingsScreen
import com.ankit.attendwise.ui.statistics.StatisticsScreen
import com.ankit.attendwise.ui.subjectdetail.SubjectDetailScreen
import com.ankit.attendwise.ui.theme.AttendWiseTheme
import com.ankit.attendwise.ui.weeklysched.WeeklyScheduleScreen
import com.ankit.attendwise.utils.NotificationHelper
import com.ankit.attendwise.viewmodel.AppViewModel
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import com.ankit.attendwise.ui.components.RequestAllPermissions

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannel(this)
        
        viewModel = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]

        setContent {
            val appViewModel = viewModel
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                appViewModel.attendanceActionFeedback.collectLatest { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            
            // Keep the splash screen on screen until the onboarding state is loaded
            splashScreen.setKeepOnScreenCondition {
                appViewModel.isOnboardingComplete.value == null
            }

            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            val useDarkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }
            AttendWiseTheme(darkTheme = useDarkTheme) {
                RequestAllPermissions()
                
                val updateAvailable by appViewModel.updateAvailable.collectAsStateWithLifecycle()
                val isForceUpdate by appViewModel.isForceUpdate.collectAsStateWithLifecycle()
                var showUpdateDialog by remember { mutableStateOf(false) }
                
                LaunchedEffect(updateAvailable) {
                    if (updateAvailable) showUpdateDialog = true
                }

                if (showUpdateDialog) {
                    UpdateDialog(
                        isForceUpdate = isForceUpdate,
                        onDismiss = { if (!isForceUpdate) showUpdateDialog = false }
                    )
                }

                AttendWiseApp(appViewModel = appViewModel)
            }
        }
        
        // Initial check for intent when app starts
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val subjectId = intent?.getStringExtra("subject_id")
        if (!subjectId.isNullOrEmpty()) {
            if (::viewModel.isInitialized) {
                viewModel.triggerNavigation(subjectId)
                intent.removeExtra("subject_id")
            }
        }
    }
}

@Composable
fun UpdateDialog(isForceUpdate: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isForceUpdate) stringResource(R.string.update_required_title) else stringResource(R.string.update_available_title)) },
        text = { 
            Text(
                if (isForceUpdate) 
                    stringResource(R.string.update_required_text)
                else 
                    stringResource(R.string.update_available_text)
            ) 
        },
        confirmButton = {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    setPackage("com.android.vending")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                }
            }) {
                Text(stringResource(R.string.action_update_now))
            }
        },
        dismissButton = {
            if (!isForceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_later))
                }
            }
        }
    )
}

@Composable
fun AttendWiseApp(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isOnboardingComplete by appViewModel.isOnboardingComplete.collectAsStateWithLifecycle()

    // ATTENDWISE NAV: Handle navigation from notifications with race condition safety
    LaunchedEffect(isOnboardingComplete) {
        if (isOnboardingComplete == true) {
            appViewModel.navigationEvents.collect { subjectId ->
                try {
                    navController.navigate("subject_detail/$subjectId") {
                        launchSingleTop = true
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Navigation failed: ${e.message}")
                }
            }
        }
    }

    if (isOnboardingComplete == null) return

    val topLevelDestinations = listOf(
        BottomNavItem.Home.route,
        BottomNavItem.Calendar.route,
        BottomNavItem.Statistics.route,
        BottomNavItem.Settings.route
    )
    val showBottomBar = topLevelDestinations.any { it == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar && isOnboardingComplete == true) {
                BottomNavigationBar(navController = navController)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            appViewModel = appViewModel,
            isOnboardingComplete = isOnboardingComplete == true,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    appViewModel: AppViewModel,
    isOnboardingComplete: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) BottomNavItem.Home.route else "onboarding",
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(400)) },
        exitTransition = { fadeOut(animationSpec = tween(400)) }
    ) {
        composable("onboarding") {
            OnboardingScreen(appViewModel = appViewModel, onComplete = {
                navController.navigate(BottomNavItem.Home.route) {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable(BottomNavItem.Home.route) {
            HomeScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(BottomNavItem.Calendar.route) {
            CalendarScreen(appViewModel = appViewModel)
        }
        composable(BottomNavItem.Statistics.route) {
            StatisticsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable("add_subject") {
            AddSubjectScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(
            route = "edit_subject/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
            AddSubjectScreen(
                navController = navController,
                subjectId = subjectId,
                appViewModel = appViewModel
            )
        }
        composable(
            route = "subject_detail/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
            SubjectDetailScreen(subjectId, navController, appViewModel)
        }
        composable("weekly_schedule") {
            WeeklyScheduleScreen(navController = navController, appViewModel = appViewModel)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Calendar,
        BottomNavItem.Statistics,
        BottomNavItem.Settings
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Calendar :
        BottomNavItem("calendar", "Calendar", Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday)

    object Statistics :
        BottomNavItem("statistics", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)

    object Settings :
        BottomNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}
