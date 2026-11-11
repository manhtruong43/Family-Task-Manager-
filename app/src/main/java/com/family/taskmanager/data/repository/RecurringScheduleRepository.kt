package com.family.taskmanager.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.RecurringScheduleDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.service.ServiceLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Kết quả các thao tác trên lịch trình (UI dịch sang thông báo tương ứng). */
enum class RecurringResult {
    SAVED, DELETED, PAUSED, RESUMED,
    NOT_ALLOWED, INVALID_TITLE, INVALID_POINTS, NO_DAYS, NOT_FOUND,
    /** Người nhận không hợp lệ, hoặc người được giao tự quản lý lịch trình của mình. */
    INVALID_ASSIGNEE
}

/** Dữ liệu form tạo / sửa lịch trình. */
data class RecurringScheduleInput(
    val title: String,
    val description: String,
    val assigneeId: String,         // chỉ chuỗi rỗng mới là lựa chọn "Cả nhà"
    val priority: TaskPriority,
    val points: Int,
    val repeatType: RepeatType,
    val daysOfWeekMask: Int,
    val dueMinuteOfDay: Int
)

/**
 * Nghiệp vụ "Lịch trình cố định" (Chức năng 6 - Dũng).
 *
 * - Chỉ Admin (Bố/Mẹ) cùng gia đình mới được tạo / sửa / tạm dừng / xóa lịch trình.
 * - generateDueTasks(): với mỗi lịch trình đang bật rơi vào hôm nay mà chưa sinh,
 *   tạo 1 Task bình thường (id cố định theo ngày nên không bao giờ trùng),
 *   rồi báo cho người được giao. Được gọi khi mở app và lúc 00:01 mỗi đêm.
 * - Chỉ sinh cho HÔM NAY, không sinh bù những ngày điện thoại không mở app.
 */
@Singleton
class RecurringScheduleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val notificationRepository: NotificationRepository
) {
    private val dao: RecurringScheduleDao = database.recurringScheduleDao()
    private val generateMutex = Mutex()

    /**
     * Đồng hồ dùng để xác định "hôm nay" và "bây giờ". Mặc định là giờ của máy;
     * test gán một đồng hồ cố định để kết quả không phụ thuộc lúc chạy (vd: gần nửa đêm).
     */
    internal var clock: Clock = Clock.systemDefaultZone()

    private fun zone(): ZoneId = clock.zone
    private fun today(): LocalDate = LocalDate.now(clock)
    private fun nowMillis(): Long = clock.millis()

    fun observeSchedules(familyId: String): Flow<List<RecurringSchedule>> =
        dao.observeByFamily(familyId)

    // ─────────────────────────── Quản lý lịch trình (Admin) ───────────────────────────

    suspend fun saveSchedule(actor: User, editingId: String?, input: RecurringScheduleInput): RecurringResult {
        val result = database.withTransaction {
            val freshActor = freshManager(actor) ?: return@withTransaction RecurringResult.NOT_ALLOWED

            val title = input.title.trim().take(RecurringSchedule.MAX_TITLE_LENGTH)
            if (title.isBlank()) return@withTransaction RecurringResult.INVALID_TITLE
            if (input.points !in 1..RecurringSchedule.MAX_POINTS) return@withTransaction RecurringResult.INVALID_POINTS
            val mask = input.daysOfWeekMask and RecurringSchedule.ALL_DAYS_MASK
            if (input.repeatType == RepeatType.WEEKLY && mask == 0) return@withTransaction RecurringResult.NO_DAYS

            val existing = editingId?.let { dao.getById(it) }
            if (editingId != null && (existing == null || existing.familyId != freshActor.familyId)) {
                return@withTransaction RecurringResult.NOT_FOUND
            }
            // Người được giao không tự sửa, bật/tắt hoặc xóa lịch trình của mình.
            if (existing?.assignedToUserId == freshActor.id) return@withTransaction RecurringResult.INVALID_ASSIGNEE

            // Đọc lại người nhận trong cùng transaction; không dùng tên/gia đình từ form đã cũ.
            val assignee = input.assigneeId.takeIf { it.isNotEmpty() }?.let { selectedId ->
                val freshAssignee = database.userDao().getUserByIdSync(selectedId)
                if (freshAssignee == null || freshAssignee.id == freshActor.id ||
                    freshAssignee.familyId != freshActor.familyId
                ) {
                    return@withTransaction RecurringResult.INVALID_ASSIGNEE
                }
                freshAssignee
            }
            val draft = RecurringSchedule(
                id = existing?.id ?: UUID.randomUUID().toString(),
                familyId = freshActor.familyId,
                title = title,
                description = input.description.trim().take(RecurringSchedule.MAX_DESCRIPTION_LENGTH),
                assignedToUserId = assignee?.id.orEmpty(),
                assignedToUserName = assignee?.name.orEmpty(),
                priority = input.priority,
                points = input.points,
                repeatType = input.repeatType,
                daysOfWeekMask = if (input.repeatType == RepeatType.WEEKLY) mask else 0,
                dueMinuteOfDay = input.dueMinuteOfDay.coerceIn(0, 24 * 60 - 1),
                isActive = existing?.isActive ?: true,
                createdByUserId = existing?.createdByUserId ?: freshActor.id,
                createdAt = existing?.createdAt ?: nowMillis(),
                lastGeneratedEpochDay = existing?.lastGeneratedEpochDay ?: -1L
            )
            dao.upsert(skipTodayIfAlreadyPastDue(draft))
            RecurringResult.SAVED
        }
        // Sinh nhiệm vụ và gửi thông báo sau khi transaction lưu lịch đã hoàn tất.
        if (result == RecurringResult.SAVED) generateAfterSave()
        return result
    }

    suspend fun setActive(actor: User, scheduleId: String, active: Boolean): RecurringResult {
        val result = database.withTransaction {
            val freshActor = freshManager(actor) ?: return@withTransaction RecurringResult.NOT_ALLOWED
            val schedule = dao.getById(scheduleId)
            if (schedule == null || schedule.familyId != freshActor.familyId) {
                return@withTransaction RecurringResult.NOT_FOUND
            }
            if (schedule.assignedToUserId == freshActor.id) return@withTransaction RecurringResult.INVALID_ASSIGNEE

            val updated = schedule.copy(isActive = active)
            dao.upsert(if (active) skipTodayIfAlreadyPastDue(updated) else updated)
            if (active) RecurringResult.RESUMED else RecurringResult.PAUSED
        }
        if (result == RecurringResult.RESUMED) generateAfterSave()
        return result
    }

    /** Xóa lịch trình. Các nhiệm vụ đã sinh trước đó vẫn được giữ lại. */
    suspend fun deleteSchedule(actor: User, scheduleId: String): RecurringResult =
        database.withTransaction {
            val freshActor = freshManager(actor) ?: return@withTransaction RecurringResult.NOT_ALLOWED
            val schedule = dao.getById(scheduleId)
            if (schedule == null || schedule.familyId != freshActor.familyId) {
                return@withTransaction RecurringResult.NOT_FOUND
            }
            if (schedule.assignedToUserId == freshActor.id) return@withTransaction RecurringResult.INVALID_ASSIGNEE

            dao.deleteById(scheduleId)
            RecurringResult.DELETED
        }

    /** Chỉ gọi trong transaction quản lý lịch; phiên trên UI có thể đã cũ. */
    private suspend fun freshManager(actor: User): User? {
        val freshActor = database.userDao().getUserByIdSync(actor.id) ?: return null
        return freshActor.takeIf {
            it.role == UserRole.ADMIN && it.familyId.isNotBlank() &&
                it.familyId == actor.familyId && dao.countFamily(it.familyId) > 0
        }
    }

    /** Lưu lịch đã thành công; lỗi DB khi sinh việc sẽ được thử lại ở lần chạy sau. */
    private suspend fun generateAfterSave() {
        try {
            generateDueTasks()
        } catch (exception: SQLiteException) {
            // Không bắt CancellationException: coroutine vẫn hủy bình thường.
            Log.w("RecurringScheduleRepo", "Schedule saved; task generation deferred", exception)
        }
    }

    /**
     * Tạo mới / sửa / bật lại lịch trình SAU giờ hạn của hôm nay -> không sinh việc hôm nay
     * (tránh vừa tạo xong đã có một nhiệm vụ quá hạn). Lần đầu sẽ là kỳ kế tiếp.
     */
    private fun skipTodayIfAlreadyPastDue(schedule: RecurringSchedule): RecurringSchedule {
        val zone = zone()
        val today = today()
        val todayEpoch = today.toEpochDay()
        val alreadyPast = schedule.occursOn(today) &&
                schedule.dueAtMillis(today, zone) <= nowMillis()
        return if (alreadyPast && schedule.lastGeneratedEpochDay < todayEpoch) {
            schedule.copy(lastGeneratedEpochDay = todayEpoch)
        } else {
            schedule
        }
    }

    // ─────────────────────────── Sinh nhiệm vụ tự động ───────────────────────────

    /**
     * Mỗi lần sinh lưu nhiệm vụ, thông báo trong app và dấu đã sinh trong cùng transaction.
     * Nếu lưu thông báo lỗi thì rollback cả nhiệm vụ để lần chạy sau có thể thử lại.
     * Thông báo hệ thống chỉ phát sau khi commit; gọi lại không tạo trùng nhiệm vụ/hộp thư.
     */
    suspend fun generateDueTasks(): Int = generateMutex.withLock {
        val zone = zone()
        val today = today()
        val todayEpoch = today.toEpochDay()
        val now = nowMillis()
        val createdTasks = mutableListOf<GenerationResult>()

        dao.getActiveSchedules().forEach { candidate ->
            val created = database.withTransaction<GenerationResult?> {
                val schedule = dao.getById(candidate.id) ?: return@withTransaction null
                if (!schedule.isActive) return@withTransaction null
                if (schedule.lastGeneratedEpochDay >= todayEpoch) return@withTransaction null
                if (!schedule.occursOn(today)) return@withTransaction null

                if (dao.countFamily(schedule.familyId) == 0) {
                    dao.upsert(schedule.copy(isActive = false))
                    return@withTransaction null
                }

                val assigneeStillInFamily = schedule.assignedToUserId.isBlank() ||
                        dao.countUserInFamily(schedule.assignedToUserId, schedule.familyId) > 0
                val adminIds = dao.getUserIdsByRole(schedule.familyId, UserRole.ADMIN)
                val assigneeHasReviewer = schedule.assignedToUserId.isBlank() ||
                        schedule.assignedToUserId !in adminIds ||
                        adminIds.any { it != schedule.assignedToUserId }
                val effective = if (assigneeStillInFamily && assigneeHasReviewer) schedule
                else schedule.copy(assignedToUserId = "", assignedToUserName = "")

                val task = Task(
                    id = schedule.taskIdFor(today),
                    title = schedule.title,
                    description = schedule.description,
                    familyId = schedule.familyId,
                    assignedToUserId = effective.assignedToUserId,
                    assignedToUserName = effective.assignedToUserName,
                    createdByUserId = schedule.createdByUserId,
                    priority = schedule.priority,
                    status = TaskStatus.TODO,
                    points = schedule.points,
                    deadlineTimestamp = schedule.dueAtMillis(today, zone),
                    createdAt = now,
                    openToAll = effective.assignedToUserId.isEmpty()
                )
                val rowId = dao.insertTaskIfAbsent(task)
                if (rowId == -1L) {
                    dao.markGenerated(schedule.id, todayEpoch)
                    null
                } else {
                    val notification = persistAssigneeNotifications(effective, task)
                    dao.markGenerated(schedule.id, todayEpoch)
                    GenerationResult(notification)
                }
            }
            if (created != null) createdTasks += created
        }

        createdTasks.mapNotNull { it.notification }.forEach { notification ->
            notificationRepository.postNotification(
                notification.title, notification.message, NotificationRepository.Kind.TASK
            )
        }
        createdTasks.size
    }

    private data class TaskNotification(val title: String, val message: String)
    private data class GenerationResult(val notification: TaskNotification?)

    /** Chỉ gọi trong transaction sinh nhiệm vụ, trước khi đánh dấu đã sinh. */
    private suspend fun persistAssigneeNotifications(schedule: RecurringSchedule, task: Task): TaskNotification? {
        val receivers = if (task.assignedToUserId.isNotBlank()) {
            listOf(task.assignedToUserId)
        } else {
            dao.getUserIdsInFamily(schedule.familyId).filter { it != task.createdByUserId }
        }
        if (receivers.isEmpty()) return null

        val res = ServiceLocale.localizedContext(context)
        val assignee = task.assignedToUserName.ifBlank { res.getString(R.string.rec_everyone) }
        val notification = TaskNotification(
            title = res.getString(R.string.rec_noti_title),
            message = res.getString(
                R.string.rec_noti_message,
                task.title,
                RecurringSchedule.formatMinuteOfDay(schedule.dueMinuteOfDay),
                assignee,
                task.points
            )
        )
        database.notificationDao().insertNotifications(
            receivers.map { userId ->
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = notification.title,
                    message = notification.message,
                    timestamp = nowMillis()
                )
            }
        )
        return notification
    }

}
