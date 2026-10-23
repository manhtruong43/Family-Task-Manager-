package com.family.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.auth.LoginScreen
import com.family.taskmanager.ui.auth.LoginViewModel
import com.family.taskmanager.ui.calendar.CalendarScreen
import com.family.taskmanager.ui.family.FamilySetupScreen
import com.family.taskmanager.ui.family.FamilyViewModel
import com.family.taskmanager.ui.home.HomeScreen
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.navigation.Screen
import com.family.taskmanager.ui.rewards.RewardsScreen
import com.family.taskmanager.ui.rewards.RewardsViewModel
import com.family.taskmanager.ui.settings.SettingsScreen
import com.family.taskmanager.ui.statistics.StatisticsScreen
import com.family.taskmanager.ui.task.CreateEditTaskScreen
import com.family.taskmanager.ui.task.TaskDetailScreen
import com.family.taskmanager.ui.theme.FamilyTaskManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyTaskManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FamilyTaskManagerApp()
                }
            }
        }
    }
}

@Composable
fun FamilyTaskManagerApp() {
    val navController = rememberNavController()
    // Lưu userId qua rememberSaveable để không bị mất khi xoay màn hình (orientation change)
    var loggedInUserId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Sử dụng instance HomeViewModel chung để quản lý trạng thái phiên đăng nhập nhất quán
    val homeViewModel: HomeViewModel = hiltViewModel()
    val currentUser by homeViewModel.currentUser.collectAsState()

    // Đồng bộ userId vào ViewModel khi khởi tạo hoặc sau khi xoay màn hình
    LaunchedEffect(loggedInUserId) {
        if (loggedInUserId != null) {
            homeViewModel.setCurrentUserId(loggedInUserId)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = { user ->
                    loggedInUserId = user.id
                    homeViewModel.setCurrentUser(user)
                    if (user.familyId.isEmpty()) {
                        navController.navigate(Screen.FamilySetup.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.FamilySetup.route) {
            val familyViewModel: FamilyViewModel = hiltViewModel()
            val user = currentUser
            if (user != null) {
                FamilySetupScreen(
                    currentUser = user,
                    viewModel = familyViewModel,
                    onSuccess = { _ ->
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.FamilySetup.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onCreateTaskClick = { navController.navigate(Screen.CreateTask.route) },
                onTaskClick = { taskId -> navController.navigate(Screen.TaskDetail.createRoute(taskId)) },
                onRewardsClick = { navController.navigate(Screen.Rewards.route) },
                onCalendarClick = { navController.navigate(Screen.Calendar.route) },
                onStatisticsClick = { navController.navigate(Screen.Statistics.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.CreateTask.route) {
            CreateEditTaskScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""

            TaskDetailScreen(
                taskId = taskId,
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Rewards.route) {
            val rewardsViewModel: RewardsViewModel = hiltViewModel()
            currentUser?.let { rewardsViewModel.setCurrentUser(it) }

            RewardsScreen(
                viewModel = rewardsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    loggedInUserId = null
                    homeViewModel.setCurrentUserId(null)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
