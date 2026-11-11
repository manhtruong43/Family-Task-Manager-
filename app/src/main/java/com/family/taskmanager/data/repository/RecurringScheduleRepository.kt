package com.family.taskmanager.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.RecurringScheduleDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
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
    val dueMinuteOfDay: Int,
    val assignmentMode: RecurringAssignmentMode = RecurringAssignmentMode.SHARED
)

/**
 * Nghiệp vụ "Lịch trình cố định" (Chức năng 6 - Dũng).
 *
 * - Chỉ Admin (Bố/Mẹ) cùng gia đình mới được tạo / sửa / tạm dừng / xóa lịch trình.
 * - generateDueTasks(): với mỗi lịch trình đang bật rơi vào hôm nay mà chưa sinh,
 *   tạo một việc chung hoặc một việc riêng cho từng người hợp lệ (id cố định theo kỳ/người),
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
            if (existing != null && !canManageSchedule(freshActor, existing)) {
                return@withTransaction RecurringResult.INVALID_ASSIGNEE
            }

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
                lastGeneratedEpochDay = existing?.lastGeneratedEpochDay ?: -1L,
                assignmentMode = if (assignee == null) input.assignmentMode else RecurringAssignmentMode.SHARED
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
            if (!canManageSchedule(freshActor, schedule)) return@withTransaction RecurringResult.INVALID_ASSIGNEE

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
            if (!canManageSchedule(freshActor, schedule)) return@withTransaction RecurringResult.INVALID_ASSIGNEE

            dao.deleteById(scheduleId)
            RecurringResult.DELETED
        }

    /** Chỉ gọi trong transaction để quyền và danh sách người thực sự nhận việc không bị lệch lúc ghi. */
    private suspend fun canManageSchedule(actor: User, schedule: RecurringSchedule): Boolean =
        schedule.canBeManagedBy(actor, database.userDao().getUsersByFamilySync(schedule.familyId))

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
            val created = database.withTransaction {
                val schedule = dao.getById(candidate.id) ?: return@withTransaction emptyList<GenerationResult>()
                if (!schedule.isActive) return@withTransaction emptyList()
                if (schedule.lastGeneratedEpochDay >= todayEpoch) return@withTransaction emptyList()
                if (!schedule.occursOn(today)) return@withTransaction emptyList()

                if (dao.countFamily(schedule.familyId) == 0) {
                    dao.upsert(schedule.copy(isActive = false))
                    return@withTransaction emptyList()
                }

                // Chốt danh sách người nhận mới nhất trong cùng transaction với cả bộ nhiệm vụ.
                // Cả nhà: mỗi kỳ lấy lại thành viên; không giao cho người tạo hoặc người không có người duyệt.
                val members = database.userDao().getUsersByFamilySync(schedule.familyId)
                val generated = schedule.tasksForOccurrence(today, members, now, zone).mapNotNull { task ->
                    if (dao.insertTaskIfAbsent(task) == -1L) null
                    else GenerationResult(persistAssigneeNotifications(schedule, task))
                }
                // Kể cả không có người hợp lệ hoặc các ID đã tồn tại, kỳ này vẫn đã được xử lý.
                // Đổi chế độ/sửa lịch sau đó chỉ ảnh hưởng kỳ chưa sinh, không thêm lại bộ việc hôm nay.
                dao.markGenerated(schedule.id, todayEpoch)
                generated
            }
            createdTasks += created
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
