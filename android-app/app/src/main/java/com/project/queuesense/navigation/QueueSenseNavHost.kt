
package com.project.queuesense.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.project.queuesense.ui.accuracy.AccuracyScreen
import com.project.queuesense.ui.checkin.CheckInScreen
import com.project.queuesense.ui.checkin.CheckInViewModel
import com.project.queuesense.ui.components.QueueSenseTopBar
import com.project.queuesense.ui.home.HomeScreen
import com.project.queuesense.ui.login.LoginScreen
import com.project.queuesense.ui.notifications.NotificationsScreen
import com.project.queuesense.ui.prediction.PredictionScreen
import com.project.queuesense.ui.profile.ProfileScreen
import com.project.queuesense.ui.queue.QueueScreen
import com.project.queuesense.ui.session.QueueSessionViewModel
import com.project.queuesense.ui.welcome.WelcomeScreen
import com.project.queuesense.ui.login.AuthViewModel

@Composable
fun QueueSenseNavHost(modifier: Modifier = Modifier) {

    val navController = rememberNavController()
    val session: QueueSessionViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.uiState.collectAsState()

    val currentTicket by session.current.collectAsState()
    val activeTicket = currentTicket?.takeIf {
        it.ticket.status == "waiting" ||
                it.ticket.status == "being_served"
    }
    val notifications by session.notifications.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route


    LaunchedEffect(
        authState.isLoading,
        authState.isAuthenticated
    ) {
        if (!authState.isLoading) {
            if (authState.isAuthenticated) {
                session.restoreActiveTicket()
            } else {
                session.clear()
            }
        }
    }
    LaunchedEffect(
        authState.isLoading,
        authState.isAuthenticated,
        currentRoute
    ) {
        if (
            !authState.isLoading &&
            authState.isAuthenticated &&
            currentRoute == Routes.WELCOME
        ) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.WELCOME) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute != null &&
            currentRoute != Routes.WELCOME &&
            currentRoute != Routes.LOGIN

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
                    onBack = if (showBackButton) {
                        { navController.popBackStack() }
                    } else {
                        null
                    }
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
                            popUpTo(Routes.WELCOME) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(Routes.LOGIN) {
                LaunchedEffect(authState.isAuthenticated) {
                    if (authState.isAuthenticated) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }

                LoginScreen(
                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage,
                    onLogin = { mobile, password ->
                        authViewModel.login(mobile, password)
                    },
                    onRegister = { fullName, mobile, password ->
                        authViewModel.register(fullName, mobile, password)
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    ticket = activeTicket,
                    onCheckIn = {
                        navController.navigate(Routes.CHECK_IN)
                    },
                    onViewQueue = {
                        goToTab(Routes.QUEUE)
                    }
                )
            }

            composable(Routes.CHECK_IN) {

                val checkInViewModel: CheckInViewModel = viewModel()

                val departments by checkInViewModel.departments.collectAsState()
                val isSubmitting by checkInViewModel.isSubmitting.collectAsState()
                val errorMessage by checkInViewModel.errorMessage.collectAsState()
                val createdTicket by checkInViewModel.createdTicket.collectAsState()

                LaunchedEffect(createdTicket) {
                    createdTicket?.let { ticket ->
                        session.setTicket(ticket)
                        checkInViewModel.ticketHandled()
                        goToTab(Routes.QUEUE)
                    }
                }

                CheckInScreen(
                    departments = departments,
                    isSubmitting = isSubmitting,
                    errorMessage = errorMessage,
                    accountName = authState.user?.fullName ?: "QueueSense User",
                    accountMobile = authState.user?.mobile.orEmpty(),
                    onJoinQueue = { fullName, mobile, department ->
                        checkInViewModel.checkIn(
                            fullName,
                            mobile,
                            department
                        )
                    }
                )
            }

            composable(Routes.QUEUE) {
                QueueScreen(
                    ticket = activeTicket,
                    onCheckIn = {
                        navController.navigate(Routes.CHECK_IN)
                    },
                    onViewPrediction = {
                        navController.navigate(Routes.PREDICTION)
                    }
                )
            }

            composable(Routes.PREDICTION) {

                val isRefreshing by session.isRefreshing.collectAsState()
                val refreshError by session.refreshError.collectAsState()

                PredictionScreen(
                    ticket = activeTicket,
                    isRefreshing = isRefreshing,
                    errorMessage = refreshError,
                    onRefresh = {
                        session.refresh()
                    }
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    notifications = notifications
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    ticket = currentTicket,
                    onOpenAccuracy = {
                        navController.navigate(Routes.ACCURACY)
                    },
                    onLogOut = {
                        session.clear()
                        authViewModel.logout()

                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(Routes.ACCURACY) {

                val accuracy by session.accuracy.collectAsState()
                val isLoadingAccuracy by session.isLoadingAccuracy.collectAsState()
                val isSubmittingActual by session.isSubmittingActual.collectAsState()
                val accuracyError by session.accuracyError.collectAsState()

                LaunchedEffect(currentTicket?.ticket?.id) {
                    currentTicket?.ticket?.id?.let { ticketId ->
                        session.loadAccuracy(ticketId)
                    }
                }

                AccuracyScreen(
                    ticket = currentTicket,
                    accuracy = accuracy,
                    isLoading = isLoadingAccuracy,
                    isSubmitting = isSubmittingActual,
                    errorMessage = accuracyError,
                    onSubmit = { actualWait ->
                        session.submitActualWait(actualWait)
                    }
                )
            }
        }
    }
}
