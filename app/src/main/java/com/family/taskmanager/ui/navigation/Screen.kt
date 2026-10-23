package com.family.taskmanager.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object FamilySetup : Screen("family_setup")
    object Home : Screen("home")
    object CreateTask : Screen("create_task")
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    object Rewards : Screen("rewards")
    object Calendar : Screen("calendar")
    object Statistics : Screen("statistics")
    object Settings : Screen("settings")
}
