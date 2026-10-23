package com.family.taskmanager.data.repository

import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.local.TaskDao
import com.family.taskmanager.data.local.TaskReactionDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val database: AppDatabase,
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

    /**
     * Chuyển nhiệm vụ sang "Đang làm".
     * Chỉ hợp lệ khi task đang ở TODO hoặc REJECTED. Mỗi lệnh UPDATE đều có điều kiện
     * nên không thể vô tình kéo một task đã PENDING_APPROVAL / COMPLETED quay về IN_PROGRESS.
     * @return true nếu trạng thái thực sự được thay đổi.
     */
    suspend fun startTask(taskId: String): Boolean {
        val fromTodo = taskDao.updateTaskStatusConditional(taskId, TaskStatus.TODO, TaskStatus.IN_PROGRESS)
        if (fromTodo > 0) return true
        val fromRejected = taskDao.updateTaskStatusConditional(taskId, TaskStatus.REJECTED, TaskStatus.IN_PROGRESS)
        return fromRejected > 0
    }

    /**
     * Đặt lại nhiệm vụ về "Chưa làm" – chỉ hợp lệ khi task đang IN_PROGRESS.
     * @return true nếu trạng thái thực sự được thay đổi.
     */
    suspend fun resetTaskToTodo(taskId: String): Boolean =
        taskDao.updateTaskStatusConditional(taskId, TaskStatus.IN_PROGRESS, TaskStatus.TODO) > 0

    suspend fun submitTaskProof(taskId: String, proofUrl: String, note: String, user: User? = null) {
        // Đọc + ghi trong cùng một transaction để 2 lần bấm "Gửi" liên tiếp không tạo 2 lượt thông báo
        val updatedTask = database.withTransaction {
            val task = taskDao.getTaskById(taskId) ?: return@withTransaction null
            // Chỉ cho nộp minh chứng khi task chưa nộp / đang làm / bị từ chối
            if (task.status !in SUBMITTABLE_STATUSES) return@withTransaction null

            // Task giao cho "Tất cả" được nhận diện bằng assignedToUserId rỗng (không so chuỗi hiển thị)
            val isUnassigned = task.assignedToUserId.isEmpty()
            task.copy(
                status = TaskStatus.PENDING_APPROVAL,
                proofImageUrl = proofUrl,
                proofNote = note,
                assignedToUserId = if (isUnassigned && user != null) user.id else task.assignedToUserId,
                assignedToUserName = if (isUnassigned && user != null) user.name else task.assignedToUserName
            ).also { taskDao.updateTask(it) }
        } ?: return

        val submitterName = user?.name ?: updatedTask.assignedToUserName.ifEmpty { "Thành viên" }
        val submitterId = user?.id ?: updatedTask.assignedToUserId

        // Báo cho Admin (Bố/Mẹ) khi Member hoàn thành việc và nộp minh chứng
        val familyId = updatedTask.familyId
        val taskTitle = updatedTask.title
        val notifTitle = "📸 Minh chứng mới cần duyệt"
        val notifMessage = "$submitterName đã nộp minh chứng cho việc: '$taskTitle'. Vui lòng kiểm tra và phê duyệt!"

        val admins = if (familyId.isNotEmpty()) {
            userDao.getUsersByRoleSync(familyId, UserRole.ADMIN)
        } else emptyList()

        val recipientAdminIds = admins.map { it.id }.toMutableSet()
        val creatorId = updatedTask.createdByUserId
        if (creatorId.isNotEmpty()) {
            recipientAdminIds.add(creatorId)
        }
        if (submitterId.isNotEmpty()) {
            recipientAdminIds.remove(submitterId)
        }

        recipientAdminIds.forEach { adminId ->
            notificationDao.insertNotification(
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = adminId,
                    title = notifTitle,
                    message = notifMessage
                )
            )
        }

        if (recipientAdminIds.isNotEmpty()) {
            pushNotificationService.sendTaskNotification(notifTitle, notifMessage)
        }
    }

    suspend fun approveTask(task: Task) {
        // Đổi trạng thái + cộng điểm + ghi thông báo trong CÙNG một transaction:
        // nếu bất kỳ bước nào lỗi thì toàn bộ được rollback, không có trường hợp "đã duyệt nhưng chưa cộng điểm".
        val approvedTask = database.withTransaction {
            // Cập nhật có điều kiện: chỉ chuyển sang COMPLETED khi trạng thái hiện tại là PENDING_APPROVAL
            val rowsUpdated = taskDao.updateTaskStatusConditional(
                taskId = task.id,
                expectedStatus = TaskStatus.PENDING_APPROVAL,
                newStatus = TaskStatus.COMPLETED
            )
            // Nếu không có dòng nào được cập nhật (ví dụ bấm nhanh 2 lần hoặc task chưa nộp PENDING_APPROVAL), dừng lại
            if (rowsUpdated <= 0) return@withTransaction null

            val currentTask = taskDao.getTaskById(task.id) ?: task
            // Xác định chính xác thành viên nhận điểm thưởng
            val targetUserId = currentTask.assignedToUserId.ifEmpty { task.assignedToUserId }
            if (targetUserId.isNotEmpty() && currentTask.points > 0) {
                userDao.addPoints(targetUserId, currentTask.points)
                notificationDao.insertNotification(
                    NotificationItem(
                        id = UUID.randomUUID().toString(),
                        userId = targetUserId,
                        title = APPROVED_TITLE,
                        message = approvedMessage(currentTask)
                    )
                )
                currentTask
            } else null
        } ?: return

        // Push notification là side-effect ngoài DB nên chỉ gửi sau khi transaction đã commit thành công
        pushNotificationService.sendTaskNotification(APPROVED_TITLE, approvedMessage(approvedTask))
    }

    private fun approvedMessage(task: Task) =
        "Chúc mừng! Bạn nhận +${task.points}⭐ điểm cho '${task.title}'"

    suspend fun rejectTask(task: Task) {
        // Cập nhật có điều kiện: chỉ từ chối khi task đang ở trạng thái PENDING_APPROVAL
        val rowsUpdated = taskDao.updateTaskStatusConditional(
            taskId = task.id,
            expectedStatus = TaskStatus.PENDING_APPROVAL,
            newStatus = TaskStatus.REJECTED
        )
        if (rowsUpdated <= 0) {
            return
        }

        val currentTask = taskDao.getTaskById(task.id) ?: task
        val targetUserId = currentTask.assignedToUserId.ifEmpty { task.assignedToUserId }
        if (targetUserId.isNotEmpty()) {
            val notifTitle = "❌ Nhiệm vụ bị từ chối"
            val notifMessage = "'${currentTask.title}' chưa đạt yêu cầu, vui lòng thực hiện lại."
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

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    private companion object {
        const val APPROVED_TITLE = "✅ Nhiệm vụ được duyệt!"
        val SUBMITTABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)
    }
}
