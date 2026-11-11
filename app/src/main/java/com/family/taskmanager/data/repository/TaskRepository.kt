package com.family.taskmanager.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
        private const val TAG = "TaskRepository"
        const val MAX_TASK_POINTS = 100_000
        private val SUBMITTABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)
    }

    private val proofImageStorage = ProofImageStorage(context)

    /** Cho phép bài kiểm thử giả lập việc gửi push hệ thống và quan sát log lỗi sau commit. */
    internal var systemPushSender: (title: String, message: String) -> Unit = { title, message ->
        pushNotificationService.sendTaskNotification(title, message)
    }
    internal var onExpectedPushFailure: (title: String, error: Throwable) -> Unit = { title, error ->
        Log.w(TAG, "Inbox saved; system task notification unavailable (title=$title)", error)
    }
    internal var onUnexpectedPushFailure: (title: String, error: Throwable) -> Unit = { title, error ->
        Log.e(TAG, "Inbox saved; unexpected error sending system task notification after commit (title=$title)", error)
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
        database.withTransaction {
            val freshUser = userDao.getUserByIdSync(user.id) ?: return@withTransaction
            val task = taskDao.getTaskById(taskId) ?: return@withTransaction
            if (!TaskPermissions.sameFamily(freshUser, task)) return@withTransaction

            val existing = taskReactionDao.getUserReaction(taskId, freshUser.id, emoji)
            if (existing != null) {
                taskReactionDao.deleteReaction(taskId, freshUser.id, emoji)
            } else {
                val reaction = TaskReaction(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    userId = freshUser.id,
                    userName = freshUser.name,
                    emoji = emoji
                )
                taskReactionDao.insertReaction(reaction)
            }
        }
    }

    private data class PendingPush(
        val title: String,
        val message: String
    )

    // ─────────────────────────── Tạo nhiệm vụ (Admin) ───────────────────────────

    suspend fun createTask(task: Task, actor: User): TaskActionResult {
        val res = ServiceLocale.localizedContext(context)
        var result = TaskActionResult.SUCCESS
        var pendingPush: PendingPush? = null
        val inserted = database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            if (!TaskPermissions.canCreate(freshActor) || task.familyId != freshActor!!.familyId) {
                result = TaskActionResult.NOT_ALLOWED
                return@withTransaction null
            }
            if (task.title.isBlank() || task.points !in 0..MAX_TASK_POINTS) {
                result = TaskActionResult.INVALID_INPUT
                return@withTransaction null
            }
            // Giao đích danh: người nhận phải cùng nhà, không phải chính người giao, và phải có người duyệt hợp lệ
            val admins = userDao.getUsersByRoleSync(freshActor.familyId, UserRole.ADMIN)
            val assignee = if (task.assignedToUserId.isNotEmpty()) {
                val found = userDao.getUserByIdSync(task.assignedToUserId)
                val hasReviewer = found != null && (found.role != UserRole.ADMIN || admins.any { it.id != found.id })
                if (!TaskPermissions.canAssignTo(freshActor, found, hasReviewer)) {
                    result = TaskActionResult.INVALID_INPUT
                    return@withTransaction null
                }
                found
            } else {
                null
            }

            // Không giao đích danh = việc chung "ai làm cũng được"
            // Luôn lấy createdByUserId và assignedToUserName từ DB để đảm bảo chính xác
            val toInsert = task.copy(
                title = task.title.trim(),
                description = task.description.trim(),
                familyId = freshActor.familyId,
                createdByUserId = freshActor.id,
                assignedToUserId = assignee?.id.orEmpty(),
                assignedToUserName = assignee?.name.orEmpty(),
                openToAll = assignee == null
            )
            taskDao.insertTask(toInsert)
            pendingPush = insertNewTaskNotificationsInTx(toInsert, res)
            toInsert
        } ?: return result

        sendSystemPushSafely(pendingPush)
        return TaskActionResult.SUCCESS
    }

    /**
     * "Mỗi người đều làm": tạo cho mỗi người trong [assigneeIds] một nhiệm vụ riêng từ [template]
     * (cùng tên, mô tả, điểm, hạn). Kiểm tra tất cả và lưu tất cả trong CÙNG 1 transaction:
     * hoặc tạo đủ cho mọi người, hoặc không tạo việc nào.
     */
    suspend fun createTaskForEach(template: Task, assigneeIds: List<String>, actor: User): TaskActionResult {
        val ids = assigneeIds.filter { it.isNotBlank() }.distinct()
        val res = ServiceLocale.localizedContext(context)
        var result = TaskActionResult.SUCCESS
        val pendingPushes = mutableListOf<PendingPush>()
        val createdTasks = database.withTransaction<List<Task>?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            if (!TaskPermissions.canCreate(freshActor) || template.familyId != freshActor!!.familyId) {
                result = TaskActionResult.NOT_ALLOWED
                return@withTransaction null
            }
            if (template.title.isBlank() || template.points !in 0..MAX_TASK_POINTS || ids.isEmpty()) {
                result = TaskActionResult.INVALID_INPUT
                return@withTransaction null
            }
            val admins = userDao.getUsersByRoleSync(freshActor.familyId, UserRole.ADMIN)
            val assignees = ids.map { userDao.getUserByIdSync(it) }
            if (assignees.any { assignee ->
                    val hasReviewer = assignee != null &&
                        (assignee.role != UserRole.ADMIN || admins.any { it.id != assignee.id })
                    !TaskPermissions.canAssignTo(freshActor, assignee, hasReviewer)
                }
            ) {
                result = TaskActionResult.INVALID_INPUT
                return@withTransaction null
            }

            val tasks = assignees.filterNotNull().map { assignee ->
                template.copy(
                    id = UUID.randomUUID().toString(),
                    title = template.title.trim(),
                    description = template.description.trim(),
                    familyId = freshActor.familyId,
                    assignedToUserId = assignee.id,
                    assignedToUserName = assignee.name,
                    createdByUserId = freshActor.id,
                    status = TaskStatus.TODO,
                    openToAll = false
                )
            }
            tasks.forEach { taskItem ->
                taskDao.insertTask(taskItem)
                insertNewTaskNotificationsInTx(taskItem, res)?.let { pendingPushes.add(it) }
            }
            tasks
        } ?: return result

        pendingPushes.forEach { sendSystemPushSafely(it) }
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Người được giao ───────────────────────────

    /**
     * Chuyển nhiệm vụ sang "Đang làm". Chỉ người được giao (hoặc thành viên hợp lệ với việc chung),
     * và chỉ từ TODO / REJECTED.
     * Với việc chung (assignedToUserId đang rỗng), người bấm "Bắt đầu làm" nhận việc ngay trong transaction
     * để không xảy ra cảnh nhiều người cùng làm rồi chỉ người nộp trước được tính.
     */
    suspend fun startTask(taskId: String, actor: User): TaskActionResult =
        database.withTransaction {
            val task = taskDao.getTaskById(taskId) ?: return@withTransaction TaskActionResult.NOT_FOUND
            val freshActor = userDao.getUserByIdSync(actor.id) ?: return@withTransaction TaskActionResult.NOT_ALLOWED
            val hasReviewer = freshActor.role != UserRole.ADMIN ||
                hasOtherAdminInFamily(task.familyId, freshActor.id)
            if (!TaskPermissions.canWork(freshActor, task, hasReviewer)) {
                return@withTransaction TaskActionResult.NOT_ALLOWED
            }
            if (task.status != TaskStatus.TODO && task.status != TaskStatus.REJECTED) {
                return@withTransaction TaskActionResult.INVALID_STATE
            }
            if (task.assignedToUserId.isEmpty()) {
                val claimed = task.copy(
                    status = TaskStatus.IN_PROGRESS,
                    assignedToUserId = freshActor.id,
                    assignedToUserName = freshActor.name,
                    openToAll = true
                )
                taskDao.updateTask(claimed)
                TaskActionResult.SUCCESS
            } else {
                val updated = taskDao.updateTaskStatusConditional(taskId, task.status, TaskStatus.IN_PROGRESS)
                if (updated > 0) TaskActionResult.SUCCESS else TaskActionResult.INVALID_STATE
            }
        }

    /**
     * Đặt lại về "Chưa làm" - chỉ người được giao, chỉ khi đang IN_PROGRESS (trong cùng 1 transaction).
     * Nếu đây là việc chung (openToAll = true) vừa được nhận thì trả lại cho cả nhà.
     */
    suspend fun resetTaskToTodo(taskId: String, actor: User): TaskActionResult =
        database.withTransaction {
            val task = taskDao.getTaskById(taskId) ?: return@withTransaction TaskActionResult.NOT_FOUND
            val freshActor = userDao.getUserByIdSync(actor.id) ?: return@withTransaction TaskActionResult.NOT_ALLOWED
            if (!TaskPermissions.canWork(freshActor, task)) {
                return@withTransaction TaskActionResult.NOT_ALLOWED
            }
            if (task.status != TaskStatus.IN_PROGRESS) {
                return@withTransaction TaskActionResult.INVALID_STATE
            }
            if (task.openToAll) {
                val released = task.copy(
                    status = TaskStatus.TODO,
                    assignedToUserId = "",
                    assignedToUserName = ""
                )
                taskDao.updateTask(released)
                TaskActionResult.SUCCESS
            } else {
                val updated = taskDao.updateTaskStatusConditional(taskId, TaskStatus.IN_PROGRESS, TaskStatus.TODO)
                if (updated > 0) TaskActionResult.SUCCESS else TaskActionResult.INVALID_STATE
            }
        }

    /**
     * Nộp minh chứng. [proofUrl] là danh sách đường dẫn ảnh (đã chép vào bộ nhớ của app),
     * ngăn cách bằng dấu phẩy - bắt buộc có ít nhất 1 ảnh.
     */
    suspend fun submitTaskProof(taskId: String, proofUrl: String, note: String, actor: User): TaskActionResult {
        if (proofUrl.split(",").none { it.isNotBlank() }) return TaskActionResult.NO_PROOF

        // Đọc + kiểm tra quyền + tăng submissionRevision + ghi thông báo hộp thư trong cùng một transaction:
        // 2 lần bấm "Gửi" liên tiếp không tạo 2 lượt nộp, và không ai chen vào giữa lúc kiểm tra và lúc ghi.
        val res = ServiceLocale.localizedContext(context)
        var result = TaskActionResult.SUCCESS
        var oldProofImages: List<String> = emptyList()
        var pendingPush: PendingPush? = null
        val updatedTask = database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            if (freshActor == null) {
                result = TaskActionResult.NOT_ALLOWED
                return@withTransaction null
            }
            val task = taskDao.getTaskById(taskId)
            val hasReviewer = task != null && (
                freshActor.role != UserRole.ADMIN || hasOtherAdminInFamily(task.familyId, freshActor.id)
                )
            when {
                task == null -> {
                    result = TaskActionResult.NOT_FOUND
                    null
                }
                !TaskPermissions.canWork(freshActor, task, hasReviewer) -> {
                    result = TaskActionResult.NOT_ALLOWED
                    null
                }
                task.status !in SUBMITTABLE_STATUSES -> {
                    result = TaskActionResult.INVALID_STATE
                    null
                }
                else -> {
                    oldProofImages = task.getProofImageList()
                    // Việc của cả nhà: người nộp trở thành người thực hiện (và là người nhận điểm)
                    val isUnassigned = task.assignedToUserId.isEmpty()
                    val updated = task.copy(
                        status = TaskStatus.PENDING_APPROVAL,
                        proofImageUrl = proofUrl,
                        proofNote = note.trim(),
                        assignedToUserId = if (isUnassigned) freshActor.id else task.assignedToUserId,
                        assignedToUserName = if (isUnassigned) freshActor.name else task.assignedToUserName,
                        openToAll = task.openToAll || isUnassigned,
                        submissionRevision = task.submissionRevision + 1
                    )
                    taskDao.updateTask(updated)

                    val recipients = userDao.getUsersByRoleSync(updated.familyId, UserRole.ADMIN)
                        .map { it.id }
                        .toMutableSet()
                    if (updated.createdByUserId.isNotEmpty()) recipients.add(updated.createdByUserId)
                    recipients.remove(freshActor.id)

                    pendingPush = insertNotificationsInTx(
                        recipients.toList(),
                        res.getString(R.string.tk_noti_proof_title),
                        res.getString(R.string.tk_noti_proof_message, freshActor.name, updated.title)
                    )
                    updated
                }
            }
        } ?: return result

        // Nếu đây là lần nộp lại sau khi bị từ chối, dọn các file ảnh minh chứng cũ không còn dùng
        val newProofSet = updatedTask.getProofImageList().toSet()
        val staleProofs = oldProofImages.filter { it !in newProofSet }
        if (staleProofs.isNotEmpty()) {
            deleteProofFilesSafely(staleProofs)
        }

        sendSystemPushSafely(pendingPush)
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Bố/Mẹ duyệt ───────────────────────────

    suspend fun approveTask(task: Task, actor: User): TaskActionResult {
        // Kiểm tra quyền + submissionRevision + đổi trạng thái + cộng điểm + ghi thông báo trong CÙNG một transaction:
        // quyền được xét trên lượt nộp ĐANG có trong DB (không phải bản cũ trên màn hình), và
        // lỗi ở bất kỳ bước nào thì rollback toàn bộ, không có chuyện "đã duyệt nhưng chưa cộng điểm".
        val res = ServiceLocale.localizedContext(context)
        var denied: TaskActionResult? = null
        var pendingPush: PendingPush? = null
        database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            denied = reviewError(freshActor, taskDao.getTaskById(task.id), task)
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
                pendingPush = insertNotificationsInTx(
                    listOf(targetUserId),
                    res.getString(R.string.tk_noti_approved_title),
                    res.getString(R.string.tk_noti_approved_message, currentTask.points, currentTask.title)
                )
            }
            currentTask
        } ?: return denied ?: TaskActionResult.INVALID_STATE

        // Thông báo đẩy hệ thống là việc ngoài DB nên chỉ gửi sau khi transaction đã commit
        sendSystemPushSafely(pendingPush)
        return TaskActionResult.SUCCESS
    }

    suspend fun rejectTask(task: Task, actor: User): TaskActionResult {
        // Kiểm tra quyền + submissionRevision + đổi trạng thái + lưu thông báo hộp thư trong cùng 1 transaction.
        // Chỉ từ chối khi đang PENDING_APPROVAL. Việc chung ("ai làm cũng được") bị từ chối
        // thì trả lại cho cả nhà - ai cũng làm lại được, không chỉ người vừa nộp.
        val res = ServiceLocale.localizedContext(context)
        var denied: TaskActionResult? = null
        var pendingPush: PendingPush? = null
        database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            denied = reviewError(freshActor, taskDao.getTaskById(task.id), task)
            if (denied != null) return@withTransaction null
            val rowsUpdated = taskDao.updateTaskStatusConditional(
                taskId = task.id,
                expectedStatus = TaskStatus.PENDING_APPROVAL,
                newStatus = TaskStatus.REJECTED
            )
            if (rowsUpdated <= 0) return@withTransaction null
            val current = taskDao.getTaskById(task.id) ?: return@withTransaction null
            val submitterId = current.assignedToUserId
            val rejectedTask = if (current.openToAll) {
                val released = current.copy(assignedToUserId = "", assignedToUserName = "")
                taskDao.updateTask(released)
                released
            } else {
                current
            }
            if (submitterId.isNotEmpty()) {
                pendingPush = insertNotificationsInTx(
                    listOf(submitterId),
                    res.getString(R.string.tk_noti_rejected_title),
                    res.getString(R.string.tk_noti_rejected_message, rejectedTask.title)
                )
            }
            rejectedTask
        } ?: return denied ?: TaskActionResult.INVALID_STATE

        sendSystemPushSafely(pendingPush)
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Bố/Mẹ sửa / hủy việc giao nhầm ───────────────────────────

    /**
     * Sửa nhiệm vụ khi chưa ai nộp minh chứng (Chưa làm / Đang làm / Bị từ chối).
     * - [assigneeId] rỗng / null = giao cho cả nhà (`openToAll = true`).
     * - Khi [assigneeId] không đổi so với `current.assignedToUserId` và [openToAll] không truyền (`null`),
     *   giữ nguyên `current.openToAll` để việc chung đã có người bấm "Bắt đầu" không bị biến thành việc riêng
     *   chỉ vì Bố/Mẹ chỉnh sửa tiêu đề, mô tả, điểm hoặc hạn chót.
     * - Khi muốn chủ động chuyển việc chung đang do 1 người giữ thành việc riêng của chính người đó,
     *   truyền `openToAll = false`.
     * - Đổi sang người làm khác (hoặc đổi từ có người nhận về cả nhà) -> việc về lại "Chưa làm", xóa minh chứng cũ,
     *   báo cho người mới và người cũ.
     */
    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeId: String?,
        priority: TaskPriority,
        points: Int,
        deadlineTimestamp: Long,
        actor: User,
        openToAll: Boolean? = null
    ): TaskActionResult {
        val newAssigneeId = assigneeId.orEmpty()

        // Đọc + kiểm tra + ghi + tạo thông báo hộp thư trong 1 transaction: không sửa chồng lên lúc con vừa nộp minh chứng.
        // Thứ tự kiểm tra: có việc không -> có quyền sửa không -> còn sửa được không -> dữ liệu mới có hợp lệ không
        // (người không có quyền luôn nhận NOT_ALLOWED, không bị lộ lỗi dữ liệu).
        val res = ServiceLocale.localizedContext(context)
        var result = TaskActionResult.SUCCESS
        var before: Task? = null
        var newAssignee: User? = null
        val pendingPushes = mutableListOf<PendingPush>()
        val after = database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            if (freshActor == null) {
                result = TaskActionResult.NOT_ALLOWED
                return@withTransaction null
            }
            val task = taskDao.getTaskById(taskId)
            if (newAssigneeId.isNotEmpty()) newAssignee = userDao.getUserByIdSync(newAssigneeId)
            val hasReviewerForNewAssignee = newAssignee != null && (
                newAssignee!!.role != UserRole.ADMIN || hasOtherAdminInFamily(freshActor.familyId, newAssignee!!.id)
                )
            val error = when {
                task == null -> TaskActionResult.NOT_FOUND
                !TaskPermissions.canManage(freshActor, task) -> TaskActionResult.NOT_ALLOWED
                task.status !in TaskPermissions.EDITABLE_STATUSES -> TaskActionResult.INVALID_STATE
                title.isBlank() || points !in 0..MAX_TASK_POINTS -> TaskActionResult.INVALID_INPUT
                newAssigneeId.isNotEmpty() &&
                    !TaskPermissions.canAssignTo(freshActor, newAssignee, hasReviewerForNewAssignee) ->
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
            val nextOpenToAll = when {
                newAssigneeId.isEmpty() -> true
                openToAll != null -> openToAll
                !assigneeChanged -> current.openToAll
                else -> false
            }
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
                openToAll = nextOpenToAll
            )
            taskDao.updateTask(updated)

            if (assigneeChanged) {
                val newReceivers = receiversOf(updated).filter { it != freshActor.id }
                insertNotificationsInTx(
                    newReceivers,
                    res.getString(R.string.tk_noti_new_title),
                    res.getString(
                        if (updated.assignedToUserId.isNotEmpty()) R.string.tk_noti_new_message else R.string.tk_noti_new_all_message,
                        updated.title,
                        updated.points
                    )
                )?.let { pendingPushes.add(it) }

                val oldReceivers = receiversOf(current).filter { it != freshActor.id && it !in newReceivers }
                insertNotificationsInTx(
                    oldReceivers,
                    res.getString(R.string.tk_noti_cancelled_title),
                    res.getString(R.string.tk_noti_reassigned_message, current.title)
                )?.let { pendingPushes.add(it) }
            }
            updated
        } ?: return result

        // Dọn ảnh minh chứng cũ và gửi thông báo đẩy sau khi transaction đã commit
        val old = before
        if (old != null && old.assignedToUserId != after.assignedToUserId) {
            val oldProofs = old.getProofImageList()
            if (oldProofs.isNotEmpty()) {
                deleteProofFilesSafely(oldProofs)
            }
        }
        pendingPushes.forEach { sendSystemPushSafely(it) }
        return TaskActionResult.SUCCESS
    }

    /**
     * Hủy nhiệm vụ chưa hoàn thành: xóa nhiệm vụ + cảm xúc + ảnh minh chứng của nó, báo cho người được giao.
     * Báo thức nhắc hạn tự hủy (DeadlineReminderScheduler theo dõi bảng tasks).
     */
    suspend fun cancelTask(taskId: String, actor: User): TaskActionResult {
        val res = ServiceLocale.localizedContext(context)
        var result = TaskActionResult.SUCCESS
        var pendingPush: PendingPush? = null
        val cancelled = database.withTransaction<Task?> {
            val freshActor = userDao.getUserByIdSync(actor.id)
            if (freshActor == null) {
                result = TaskActionResult.NOT_ALLOWED
                return@withTransaction null
            }
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
            val receivers = receiversOf(current).filter { it != freshActor.id }
            taskReactionDao.deleteReactionsOfTask(current.id)
            taskDao.deleteTask(current)
            pendingPush = insertNotificationsInTx(
                receivers,
                res.getString(R.string.tk_noti_cancelled_title),
                res.getString(R.string.tk_noti_cancelled_message, current.title)
            )
            current
        } ?: return result

        // Xóa đúng bộ ảnh minh chứng gắn với bản ghi vừa xóa trong transaction
        val proofPaths = cancelled.getProofImageList()
        if (proofPaths.isNotEmpty()) {
            deleteProofFilesSafely(proofPaths)
        }

        sendSystemPushSafely(pendingPush)
        return TaskActionResult.SUCCESS
    }

    // ─────────────────────────── Tiện ích ───────────────────────────

    private suspend fun hasOtherAdminInFamily(familyId: String, excludeUserId: String): Boolean =
        userDao.getUsersByRoleSync(familyId, UserRole.ADMIN).any { it.id != excludeUserId }

    /**
     * Lỗi khi duyệt / từ chối [task] (đọc trong transaction), hoặc null nếu được phép.
     * So sánh [Task.submissionRevision] (và người thực hiện / đường dẫn ảnh) trên [screenTask]
     * với bản ghi hiện tại trong DB: nếu đã có lượt nộp mới xen giữa (kể cả khi dùng lại cùng đường dẫn ảnh)
     * hoặc [screenTask] là bản chụp từ trước lúc nộp (`submissionRevision == 0`), trả về INVALID_STATE
     * để tránh duyệt/từ chối nhầm lượt nộp chưa xem.
     */
    private fun reviewError(actor: User?, task: Task?, screenTask: Task): TaskActionResult? = when {
        actor == null -> TaskActionResult.NOT_ALLOWED
        task == null -> TaskActionResult.NOT_FOUND
        !TaskPermissions.canReview(actor, task) -> TaskActionResult.NOT_ALLOWED
        task.status != TaskStatus.PENDING_APPROVAL -> TaskActionResult.INVALID_STATE
        task.submissionRevision <= 0 || screenTask.submissionRevision != task.submissionRevision ->
            TaskActionResult.INVALID_STATE
        screenTask.assignedToUserId != task.assignedToUserId ->
            TaskActionResult.INVALID_STATE
        screenTask.proofImageUrl != task.proofImageUrl ->
            TaskActionResult.INVALID_STATE
        else -> null
    }

    /** Người liên quan tới 1 việc: người được giao, hoặc cả nhà trừ người giao nếu là việc chung. */
    private suspend fun receiversOf(task: Task): List<String> =
        if (task.assignedToUserId.isNotEmpty()) listOf(task.assignedToUserId)
        else userDao.getUsersByFamilySync(task.familyId).map { it.id }.filter { it != task.createdByUserId }

    /** Lưu thông báo "nhiệm vụ mới" vào hộp thư trong transaction và trả về thông tin push hệ thống. */
    private suspend fun insertNewTaskNotificationsInTx(task: Task, res: Context): PendingPush? =
        insertNotificationsInTx(
            receiversOf(task),
            res.getString(R.string.tk_noti_new_title),
            res.getString(
                if (task.assignedToUserId.isNotEmpty()) R.string.tk_noti_new_message else R.string.tk_noti_new_all_message,
                task.title,
                task.points
            )
        )

    /** Lưu thông báo vào bảng notifications (gọi bên trong transaction) và trả về thông tin push. */
    private suspend fun insertNotificationsInTx(userIds: List<String>, title: String, message: String): PendingPush? {
        val receivers = userIds.filter { it.isNotBlank() }.distinct()
        if (receivers.isEmpty()) return null
        val now = System.currentTimeMillis()
        notificationDao.insertNotifications(
            receivers.map { userId ->
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    message = message,
                    timestamp = now
                )
            }
        )
        return PendingPush(title, message)
    }

    /** Gửi thông báo đẩy hệ thống sau khi DB đã commit; phân tách lỗi dự kiến và lỗi bất ngờ, có ghi log rõ ràng. */
    private fun sendSystemPushSafely(pendingPush: PendingPush?) {
        if (pendingPush == null) return
        deliverCommittedSystemNotification(
            post = { systemPushSender(pendingPush.title, pendingPush.message) },
            onExpectedFailure = { error -> onExpectedPushFailure(pendingPush.title, error) },
            onUnexpectedFailure = { error -> onUnexpectedPushFailure(pendingPush.title, error) }
        )
    }

    /** Xóa file ảnh minh chứng cũ sau khi DB đã commit, không để việc hủy coroutine hay lỗi I/O làm hỏng kết quả. */
    private suspend fun deleteProofFilesSafely(paths: List<String>) {
        if (paths.isEmpty()) return
        withContext(NonCancellable) {
            try {
                proofImageStorage.delete(paths)
            } catch (error: Exception) {
                Log.w(TAG, "Proof files cleanup failed after DB commit", error)
            }
        }
    }
}
