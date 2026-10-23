package com.family.taskmanager.data.repository

import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.local.TaskDao
import com.family.taskmanager.data.local.TaskReactionDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val notificationDao: NotificationDao,
    private val taskReactionDao: TaskReactionDao,
    private val pushNotificationService: PushNotificationService
) {
    fun getTasksByFamily(familyId: String): Flow<List<Task>> =
        taskDao.getTasksByFamily(familyId)

    fun getTasksByUser(userId: String): Flow<List<Task>> =
        taskDao.getTasksByUser(userId)

    suspend fun getTaskById(taskId: String): Task? =
        taskDao.getTaskById(taskId)

    fun getReactionsByTask(taskId: String): Flow<List<TaskReaction>> =
        taskReactionDao.getReactionsByTask(taskId)

    suspend fun toggleReaction(taskId: String, user: User, emoji: String) {
        val existing = taskReactionDao.getUserReaction(taskId, user.id, emoji)
        if (existing != null) {
            taskReactionDao.deleteReaction(taskId, user.id, emoji)
        } else {
            val reaction = TaskReaction(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                userId = user.id,
                userName = user.name,
                emoji = emoji
            )
            taskReactionDao.insertReaction(reaction)
        }
    }

    suspend fun createTask(task: Task) {
        taskDao.insertTask(task)
        if (task.assignedToUserId.isNotEmpty()) {
            val notifTitle = "📋 Nhiệm vụ mới!"
            val notifMessage = "Bạn vừa được giao: ${task.title} (+${task.points} điểm)"
            notificationDao.insertNotification(
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = task.assignedToUserId,
                    title = notifTitle,
                    message = notifMessage
                )
            )
            pushNotificationService.sendTaskNotification(notifTitle, notifMessage)
        }
    }

    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        taskDao.updateTaskStatus(taskId, status)
    }

    suspend fun submitTaskProof(taskId: String, proofUrl: String, note: String, user: User? = null) {
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            val updatedTask = task.copy(
                status = TaskStatus.PENDING_APPROVAL,
                proofImageUrl = proofUrl,
                proofNote = note,
                assignedToUserId = if (task.assignedToUserId.isEmpty() && user != null) user.id else task.assignedToUserId,
                assignedToUserName = if ((task.assignedToUserName.isEmpty() || task.assignedToUserName == "Tất cả") && user != null) user.name else task.assignedToUserName
            )
            taskDao.updateTask(updatedTask)
        } else {
            taskDao.submitTaskProof(taskId, TaskStatus.PENDING_APPROVAL, proofUrl, note)
        }
    }

    suspend fun approveTask(task: Task) {
        val currentTask = taskDao.getTaskById(task.id) ?: task
        // Tránh cộng điểm lặp lại nếu task đã được duyệt trước đó
        if (currentTask.status == TaskStatus.COMPLETED) {
            return
        }
        taskDao.updateTaskStatus(task.id, TaskStatus.COMPLETED)

        // Xác định chính xác thành viên nhận điểm thưởng
        val targetUserId = currentTask.assignedToUserId.ifEmpty { task.assignedToUserId }
        if (targetUserId.isNotEmpty() && currentTask.points > 0) {
            userDao.addPoints(targetUserId, currentTask.points)
            val notifTitle = "✅ Nhiệm vụ được duyệt!"
            val notifMessage = "Chúc mừng! Bạn nhận +${currentTask.points}⭐ điểm cho '${currentTask.title}'"
            notificationDao.insertNotification(
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = targetUserId,
                    title = notifTitle,
                    message = notifMessage
                )
            )
            pushNotificationService.sendTaskNotification(notifTitle, notifMessage)
        }
    }

    suspend fun rejectTask(task: Task) {
        taskDao.updateTaskStatus(task.id, TaskStatus.REJECTED)
        if (task.assignedToUserId.isNotEmpty()) {
            val notifTitle = "❌ Nhiệm vụ bị từ chối"
            val notifMessage = "'${task.title}' chưa đạt yêu cầu, vui lòng thực hiện lại."
            notificationDao.insertNotification(
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = task.assignedToUserId,
                    title = notifTitle,
                    message = notifMessage
                )
            )
            pushNotificationService.sendTaskNotification(notifTitle, notifMessage)
        }
    }

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}
