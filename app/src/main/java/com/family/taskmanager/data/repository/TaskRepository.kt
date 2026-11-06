package com.family.taskmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.local.TaskDao
import com.family.taskmanager.data.local.TaskReactionDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.service.PushNotificationService
import com.family.taskmanager.data.service.ServiceLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nghiệp vụ nhiệm vụ: tạo, bắt đầu, nộp minh chứng, duyệt, từ chối.
 *
 * Mọi thao tác thay đổi dữ liệu đều nhận [User] đang thao tác (actor) và kiểm tra quyền
 * bằng [TaskPermissions] trên dữ liệu MỚI NHẤT trong DB - không tin việc giao diện đã ẩn nút.
 */
@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val notificationDao: NotificationDao,
    private val taskReactionDao: TaskReactionDao,
    private val pushNotificationService: PushNotificationService
) {
    companion object {
        const val MAX_TASK_POINTS = 100_000
        private val SUBMITTABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)
    }

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

    // ─────────────────────────── Tạo nhiệm vụ (Admin) ───────────────────────────

    suspend fun createTask(task: Task, actor: User): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED
        if (!TaskPermissions.canCreate(freshActor) || task.familyId != freshActor.familyId) {
            return TaskActionResult.NOT_ALLOWED
        }
        if (task.title.isBlank() || task.points !in 0..MAX_TASK_POINTS) {
            return TaskActionResult.INVALID_INPUT
        }
        // Giao đích danh: người nhận phải cùng nhà và không phải chính người giao
        if (task.assignedToUserId.isNotEmpty()) {
            val assignee = userDao.getUserByIdSync(task.assignedToUserId)
            if (!TaskPermissions.canAssignTo(freshActor, assignee)) return TaskActionResult.INVALID_INPUT
        }

        // Không giao đích danh = việc chung "ai làm cũng được"
        val toInsert = task.copy(openToAll = task.assignedToUserId.isEmpty())
        taskDao.insertTask(toInsert)

        notifyNewTask(toInsert)
        return TaskActionResult.SUCCESS
    }

    /**
     * "Mỗi người đều làm": tạo cho mỗi người trong [assigneeIds] một nhiệm vụ riêng từ [template]
     * (cùng tên, mô tả, điểm, hạn). Kiểm tra tất cả trước, rồi lưu tất cả trong 1 transaction:
     * hoặc tạo đủ cho mọi người, hoặc không tạo việc nào.
     */
    suspend fun createTaskForEach(template: Task, assigneeIds: List<String>, actor: User): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED
        if (!TaskPermissions.canCreate(freshActor) || template.familyId != freshActor.familyId) {
            return TaskActionResult.NOT_ALLOWED
        }
        val ids = assigneeIds.filter { it.isNotBlank() }.distinct()
        if (template.title.isBlank() || template.points !in 0..MAX_TASK_POINTS || ids.isEmpty()) {
            return TaskActionResult.INVALID_INPUT
        }
        val assignees = ids.map { userDao.getUserByIdSync(it) }
        if (assignees.any { !TaskPermissions.canAssignTo(freshActor, it) }) return TaskActionResult.INVALID_INPUT

        val tasks = assignees.filterNotNull().map { assignee ->
            template.copy(
                id = UUID.randomUUID().toString(),
                assignedToUserId = assignee.id,
                assignedToUserName = assignee.name,
                createdByUserId = freshActor.id,
                status = TaskStatus.TODO,
                openToAll = false
            )
        }
        database.withTransaction { tasks.forEach { taskDao.insertTask(it) } }
        tasks.forEach { notifyNewTask(it) }
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Người được giao ───────────────────────────

    /**
     * Chuyển nhiệm vụ sang "Đang làm". Chỉ người được giao, và chỉ từ TODO / REJECTED.
     * Lệnh UPDATE có điều kiện nên không thể kéo một task đã chờ duyệt / hoàn thành quay lại.
     */
    suspend fun startTask(taskId: String, actor: User): TaskActionResult {
        val check = checkWorker(taskId, actor)
        if (check != TaskActionResult.SUCCESS) return check

        val fromTodo = taskDao.updateTaskStatusConditional(taskId, TaskStatus.TODO, TaskStatus.IN_PROGRESS)
        if (fromTodo > 0) return TaskActionResult.SUCCESS
        val fromRejected = taskDao.updateTaskStatusConditional(taskId, TaskStatus.REJECTED, TaskStatus.IN_PROGRESS)
        return if (fromRejected > 0) TaskActionResult.SUCCESS else TaskActionResult.INVALID_STATE
    }

    /** Đặt lại về "Chưa làm" - chỉ người được giao, chỉ khi đang IN_PROGRESS. */
    suspend fun resetTaskToTodo(taskId: String, actor: User): TaskActionResult {
        val check = checkWorker(taskId, actor)
        if (check != TaskActionResult.SUCCESS) return check

        val updated = taskDao.updateTaskStatusConditional(taskId, TaskStatus.IN_PROGRESS, TaskStatus.TODO)
        return if (updated > 0) TaskActionResult.SUCCESS else TaskActionResult.INVALID_STATE
    }

    /**
     * Nộp minh chứng. [proofUrl] là danh sách đường dẫn ảnh (đã chép vào bộ nhớ của app),
     * ngăn cách bằng dấu phẩy - bắt buộc có ít nhất 1 ảnh.
     */
    suspend fun submitTaskProof(taskId: String, proofUrl: String, note: String, actor: User): TaskActionResult {
        if (proofUrl.split(",").none { it.isNotBlank() }) return TaskActionResult.NO_PROOF
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED

        // Đọc + kiểm tra quyền + ghi trong cùng một transaction: 2 lần bấm "Gửi" liên tiếp
        // không tạo 2 lượt nộp, và không ai chen vào giữa lúc kiểm tra và lúc ghi.
        var result = TaskActionResult.SUCCESS
        val updatedTask = database.withTransaction<Task?> {
            val task = taskDao.getTaskById(taskId)
            when {
                task == null -> {
                    result = TaskActionResult.NOT_FOUND
                    null
                }
                !TaskPermissions.canWork(freshActor, task) -> {
                    result = TaskActionResult.NOT_ALLOWED
                    null
                }
                task.status !in SUBMITTABLE_STATUSES -> {
                    result = TaskActionResult.INVALID_STATE
                    null
                }
                else -> {
                    // Việc của cả nhà: người nộp trở thành người thực hiện (và là người nhận điểm)
                    val isUnassigned = task.assignedToUserId.isEmpty()
                    val updated = task.copy(
                        status = TaskStatus.PENDING_APPROVAL,
                        proofImageUrl = proofUrl,
                        proofNote = note.trim(),
                        assignedToUserId = if (isUnassigned) freshActor.id else task.assignedToUserId,
                        assignedToUserName = if (isUnassigned) freshActor.name else task.assignedToUserName
                    )
                    taskDao.updateTask(updated)
                    updated
                }
            }
        }
        if (updatedTask == null) return result

        // Báo cho Bố/Mẹ (và người tạo việc) có minh chứng mới cần duyệt
        val res = ServiceLocale.localizedContext(context)
        val recipients = userDao.getUsersByRoleSync(updatedTask.familyId, UserRole.ADMIN)
            .map { it.id }
            .toMutableSet()
        if (updatedTask.createdByUserId.isNotEmpty()) recipients.add(updatedTask.createdByUserId)
        recipients.remove(freshActor.id)

        notifyUsers(
            recipients.toList(),
            res.getString(R.string.tk_noti_proof_title),
            res.getString(R.string.tk_noti_proof_message, freshActor.name, updatedTask.title)
        )
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Bố/Mẹ duyệt ───────────────────────────

    suspend fun approveTask(task: Task, actor: User): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED

        // Kiểm tra quyền + đổi trạng thái + cộng điểm + ghi thông báo trong CÙNG một transaction:
        // quyền được xét trên lượt nộp ĐANG có trong DB (không phải bản cũ trên màn hình), và
        // lỗi ở bất kỳ bước nào thì rollback toàn bộ, không có chuyện "đã duyệt nhưng chưa cộng điểm".
        val res = ServiceLocale.localizedContext(context)
        var denied: TaskActionResult? = null
        val approvedTask = database.withTransaction<Task?> {
            denied = reviewError(freshActor, taskDao.getTaskById(task.id))
            if (denied != null) return@withTransaction null
            // Chỉ chuyển sang COMPLETED khi đang PENDING_APPROVAL -> bấm 2 lần chỉ cộng điểm 1 lần
            val rowsUpdated = taskDao.updateTaskStatusConditional(
                taskId = task.id,
                expectedStatus = TaskStatus.PENDING_APPROVAL,
                newStatus = TaskStatus.COMPLETED
            )
            if (rowsUpdated <= 0) return@withTransaction null

            val currentTask = taskDao.getTaskById(task.id) ?: return@withTransaction null
            val targetUserId = currentTask.assignedToUserId
            if (targetUserId.isNotEmpty() && currentTask.points > 0) {
                userDao.addPoints(targetUserId, currentTask.points)
                notificationDao.insertNotification(
                    NotificationItem(
                        id = UUID.randomUUID().toString(),
                        userId = targetUserId,
                        title = res.getString(R.string.tk_noti_approved_title),
                        message = res.getString(R.string.tk_noti_approved_message, currentTask.points, currentTask.title)
                    )
                )
            }
            currentTask
        } ?: return denied ?: TaskActionResult.INVALID_STATE

        // Thông báo đẩy là việc ngoài DB nên chỉ gửi sau khi transaction đã commit
        if (approvedTask.assignedToUserId.isNotEmpty() && approvedTask.points > 0) {
            pushNotificationService.sendTaskNotification(
                res.getString(R.string.tk_noti_approved_title),
                res.getString(R.string.tk_noti_approved_message, approvedTask.points, approvedTask.title)
            )
        }
        return TaskActionResult.SUCCESS
    }

    suspend fun rejectTask(task: Task, actor: User): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED

        // Kiểm tra quyền + đổi trạng thái trong cùng 1 transaction (như approveTask).
        // Chỉ từ chối khi đang PENDING_APPROVAL. Việc chung ("ai làm cũng được") bị từ chối
        // thì trả lại cho cả nhà - ai cũng làm lại được, không chỉ người vừa nộp.
        var submitterId = ""
        var denied: TaskActionResult? = null
        val rejected = database.withTransaction<Task?> {
            denied = reviewError(freshActor, taskDao.getTaskById(task.id))
            if (denied != null) return@withTransaction null
            val rowsUpdated = taskDao.updateTaskStatusConditional(
                taskId = task.id,
                expectedStatus = TaskStatus.PENDING_APPROVAL,
                newStatus = TaskStatus.REJECTED
            )
            if (rowsUpdated <= 0) return@withTransaction null
            val current = taskDao.getTaskById(task.id) ?: return@withTransaction null
            submitterId = current.assignedToUserId
            if (current.openToAll) {
                val released = current.copy(assignedToUserId = "", assignedToUserName = "")
                taskDao.updateTask(released)
                released
            } else {
                current
            }
        } ?: return denied ?: TaskActionResult.INVALID_STATE

        if (submitterId.isNotEmpty()) {
            val res = ServiceLocale.localizedContext(context)
            notifyUsers(
                listOf(submitterId),
                res.getString(R.string.tk_noti_rejected_title),
                res.getString(R.string.tk_noti_rejected_message, rejected.title)
            )
        }
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Bố/Mẹ sửa / hủy việc giao nhầm ───────────────────────────

    /**
     * Sửa nhiệm vụ khi chưa ai nộp minh chứng (Chưa làm / Đang làm / Bị từ chối).
     * Đổi người làm -> việc về lại "Chưa làm", xóa minh chứng cũ, báo cho người mới và người cũ.
     * [assigneeId] rỗng / null = giao cho cả nhà.
     */
    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeId: String?,
        priority: TaskPriority,
        points: Int,
        deadlineTimestamp: Long,
        actor: User
    ): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED
        val newAssigneeId = assigneeId.orEmpty()

        // Đọc + kiểm tra + ghi trong 1 transaction: không sửa chồng lên lúc con vừa nộp minh chứng.
        // Thứ tự kiểm tra: có việc không -> có quyền sửa không -> còn sửa được không -> dữ liệu mới có hợp lệ không
        // (người không có quyền luôn nhận NOT_ALLOWED, không bị lộ lỗi dữ liệu).
        var result = TaskActionResult.SUCCESS
        var before: Task? = null
        var newAssignee: User? = null
        val after = database.withTransaction<Task?> {
            val task = taskDao.getTaskById(taskId)
            if (newAssigneeId.isNotEmpty()) newAssignee = userDao.getUserByIdSync(newAssigneeId)
            val error = when {
                task == null -> TaskActionResult.NOT_FOUND
                !TaskPermissions.canManage(freshActor, task) -> TaskActionResult.NOT_ALLOWED
                task.status !in TaskPermissions.EDITABLE_STATUSES -> TaskActionResult.INVALID_STATE
                title.isBlank() || points !in 0..MAX_TASK_POINTS -> TaskActionResult.INVALID_INPUT
                newAssigneeId.isNotEmpty() && !TaskPermissions.canAssignTo(freshActor, newAssignee) ->
                    TaskActionResult.INVALID_INPUT
                else -> null
            }
            if (error != null) {
                result = error
                return@withTransaction null
            }
            val current = task!!
            before = current
            val assigneeChanged = newAssigneeId != current.assignedToUserId
            val updated = current.copy(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                points = points,
                deadlineTimestamp = deadlineTimestamp,
                assignedToUserId = newAssigneeId,
                assignedToUserName = newAssignee?.name.orEmpty(),
                status = if (assigneeChanged) TaskStatus.TODO else current.status,
                proofImageUrl = if (assigneeChanged) "" else current.proofImageUrl,
                proofNote = if (assigneeChanged) "" else current.proofNote,
                openToAll = newAssigneeId.isEmpty()
            )
            taskDao.updateTask(updated)
            updated
        } ?: return result

        // Thông báo chỉ gửi sau khi transaction đã commit
        val old = before
        if (old != null && old.assignedToUserId != after.assignedToUserId) {
            val res = ServiceLocale.localizedContext(context)
            val newReceivers = receiversOf(after).filter { it != freshActor.id }
            notifyUsers(
                newReceivers,
                res.getString(R.string.tk_noti_new_title),
                res.getString(
                    if (after.assignedToUserId.isNotEmpty()) R.string.tk_noti_new_message else R.string.tk_noti_new_all_message,
                    after.title,
                    after.points
                )
            )
            val oldReceivers = receiversOf(old).filter { it != freshActor.id && it !in newReceivers }
            notifyUsers(
                oldReceivers,
                res.getString(R.string.tk_noti_cancelled_title),
                res.getString(R.string.tk_noti_reassigned_message, old.title)
            )
        }
        return TaskActionResult.SUCCESS
    }

    /**
     * Hủy nhiệm vụ chưa hoàn thành: xóa nhiệm vụ + cảm xúc của nó, báo cho người được giao.
     * Báo thức nhắc hạn tự hủy (DeadlineReminderScheduler theo dõi bảng tasks).
     * Ảnh minh chứng do HomeViewModel xóa sau khi hủy thành công.
     */
    suspend fun cancelTask(taskId: String, actor: User): TaskActionResult {
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED

        var result = TaskActionResult.SUCCESS
        val cancelled = database.withTransaction<Task?> {
            val task = taskDao.getTaskById(taskId)
            val error = when {
                task == null -> TaskActionResult.NOT_FOUND
                !TaskPermissions.canManage(freshActor, task) -> TaskActionResult.NOT_ALLOWED
                task.status !in TaskPermissions.CANCELLABLE_STATUSES -> TaskActionResult.INVALID_STATE
                else -> null
            }
            if (error != null) {
                result = error
                return@withTransaction null
            }
            val current = task!!
            taskReactionDao.deleteReactionsOfTask(current.id)
            taskDao.deleteTask(current)
            current
        } ?: return result

        val res = ServiceLocale.localizedContext(context)
        notifyUsers(
            receiversOf(cancelled).filter { it != freshActor.id },
            res.getString(R.string.tk_noti_cancelled_title),
            res.getString(R.string.tk_noti_cancelled_message, cancelled.title)
        )
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Tiện ích ───────────────────────────

    private suspend fun checkWorker(taskId: String, actor: User): TaskActionResult {
        val task = taskDao.getTaskById(taskId) ?: return TaskActionResult.NOT_FOUND
        val freshActor = userDao.getUserByIdSync(actor.id) ?: return TaskActionResult.NOT_ALLOWED
        return if (TaskPermissions.canWork(freshActor, task)) TaskActionResult.SUCCESS
        else TaskActionResult.NOT_ALLOWED
    }

    /** Lỗi khi duyệt / từ chối [task] (đọc trong transaction), hoặc null nếu được phép. */
    private fun reviewError(actor: User, task: Task?): TaskActionResult? = when {
        task == null -> TaskActionResult.NOT_FOUND
        !TaskPermissions.canReview(actor, task) -> TaskActionResult.NOT_ALLOWED
        else -> null
    }

    /** Người liên quan tới 1 việc: người được giao, hoặc cả nhà trừ người giao nếu là việc chung. */
    private suspend fun receiversOf(task: Task): List<String> =
        if (task.assignedToUserId.isNotEmpty()) listOf(task.assignedToUserId)
        else userDao.getUsersByFamilySync(task.familyId).map { it.id }.filter { it != task.createdByUserId }

    /** Báo "nhiệm vụ mới" cho người được giao (hoặc cả nhà nếu là việc chung). */
    private suspend fun notifyNewTask(task: Task) {
        val res = ServiceLocale.localizedContext(context)
        notifyUsers(
            receiversOf(task),
            res.getString(R.string.tk_noti_new_title),
            res.getString(
                if (task.assignedToUserId.isNotEmpty()) R.string.tk_noti_new_message else R.string.tk_noti_new_all_message,
                task.title,
                task.points
            )
        )
    }

    /** Lưu thông báo vào hộp thư từng người nhận rồi bắn 1 thông báo đẩy. */
    private suspend fun notifyUsers(userIds: List<String>, title: String, message: String) {
        val receivers = userIds.filter { it.isNotBlank() }.distinct()
        if (receivers.isEmpty()) return
        val now = System.currentTimeMillis()
        receivers.forEach { userId ->
            notificationDao.insertNotification(
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    message = message,
                    timestamp = now
                )
            )
        }
        pushNotificationService.sendTaskNotification(title, message)
    }
}
