package com.project.queuesense.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.project.queuesense.ui.accuracy.AccuracyScreen
import com.project.queuesense.ui.checkin.CheckInScreen
import com.project.queuesense.ui.home.HomeScreen
import com.project.queuesense.ui.login.LoginScreen
import com.project.queuesense.ui.notifications.NotificationsScreen
import com.project.queuesense.ui.prediction.PredictionScreen
import com.project.queuesense.ui.profile.ProfileScreen
import com.project.queuesense.ui.queue.QueueScreen
import com.project.queuesense.ui.welcome.WelcomeScreen
import com.project.queuesense.ui.components.QueueSenseTopBar
import com.project.queuesense.data.SampleData

@Composable
fun QueueSenseNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar is shown everywhere except before login.
    val showBottomBar = currentRoute != null &&
            currentRoute != Routes.WELCOME &&
            currentRoute != Routes.LOGIN

    // Sub-screens highlight the tab they belong to.
    val selectedTab = when (currentRoute) {
        Routes.CHECK_IN -> Routes.HOME
        Routes.PREDICTION -> Routes.QUEUE
        Routes.ACCURACY -> Routes.PROFILE
        else -> currentRoute
    }

    val goToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }
    val title = when (currentRoute) {
        Routes.HOME -> "QueueSense"
        Routes.CHECK_IN -> "Check In"
        Routes.QUEUE -> "My Queue"
        Routes.PREDICTION -> "Wait Prediction"
        Routes.NOTIFICATIONS -> "Notifications"
        Routes.ACCURACY -> "Prediction Accuracy"
        Routes.PROFILE -> "Profile"
        else -> ""
    }
    val showBackButton = showBottomBar && currentRoute != Routes.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showBottomBar) {
                QueueSenseTopBar(
                    title = title,
                    onBack = if (showBackButton) ({ navController.popBackStack() }) else null
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                QueueSenseBottomBar(
                    currentRoute = selectedTab,
                    onTabSelected = goToTab
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WELCOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onGetStarted = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLogin = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    summary = SampleData.queueSummary,
                    onCheckIn = { navController.navigate(Routes.CHECK_IN) },
                    onViewQueue = { goToTab(Routes.QUEUE) }
                )
            }
            composable(Routes.CHECK_IN) {
                CheckInScreen(
                    departments = SampleData.departments,
                    onJoinQueue = { goToTab(Routes.QUEUE) }
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    onViewPrediction = { navController.navigate(Routes.PREDICTION) }
                )
            }
            composable(Routes.PREDICTION) { PredictionScreen() }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenAccuracy = { navController.navigate(Routes.ACCURACY) },
                    onLogOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.ACCURACY) { AccuracyScreen() }
        }
    }
}