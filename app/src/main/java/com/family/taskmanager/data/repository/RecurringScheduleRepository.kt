package com.family.taskmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.RecurringScheduleDao
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
    /** Tự giao lịch trình cho chính mình, hoặc người nhận không thuộc gia đình. */
    INVALID_ASSIGNEE
}

/** Dữ liệu form tạo / sửa lịch trình. */
data class RecurringScheduleInput(
    val title: String,
    val description: String,
    val assignee: User?,            // null = cả nhà
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
        if (!canManage(actor)) return RecurringResult.NOT_ALLOWED

        val title = input.title.trim().take(RecurringSchedule.MAX_TITLE_LENGTH)
        if (title.isBlank()) return RecurringResult.INVALID_TITLE
        if (input.points !in 1..RecurringSchedule.MAX_POINTS) return RecurringResult.INVALID_POINTS
        val mask = input.daysOfWeekMask and RecurringSchedule.ALL_DAYS_MASK
        if (input.repeatType == RepeatType.WEEKLY && mask == 0) return RecurringResult.NO_DAYS

        val existing = editingId?.let { dao.getById(it) }
        if (editingId != null && (existing == null || existing.familyId != actor.familyId)) {
            return RecurringResult.NOT_FOUND
        }

        // Cùng quy tắc với giao việc thường: người nhận phải cùng nhà và KHÔNG phải chính người giao
        // (nếu không sẽ tự duyệt việc của mình). Lịch đang giao cho chính mình thì nhờ Bố/Mẹ kia sửa.
        val assignee = input.assignee
        if (assignee != null &&
            (assignee.id == actor.id || dao.countUserInFamily(assignee.id, actor.familyId) == 0)
        ) {
            return RecurringResult.INVALID_ASSIGNEE
        }
        if (existing != null && existing.assignedToUserId == actor.id) return RecurringResult.INVALID_ASSIGNEE

        val draft = RecurringSchedule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            familyId = actor.familyId,
            title = title,
            description = input.description.trim().take(RecurringSchedule.MAX_DESCRIPTION_LENGTH),
            assignedToUserId = input.assignee?.id ?: "",
            assignedToUserName = input.assignee?.name ?: "",
            priority = input.priority,
            points = input.points,
            repeatType = input.repeatType,
            daysOfWeekMask = if (input.repeatType == RepeatType.WEEKLY) mask else 0,
            dueMinuteOfDay = input.dueMinuteOfDay.coerceIn(0, 24 * 60 - 1),
            isActive = existing?.isActive ?: true,
            createdByUserId = existing?.createdByUserId ?: actor.id,
            createdAt = existing?.createdAt ?: nowMillis(),
            lastGeneratedEpochDay = existing?.lastGeneratedEpochDay ?: -1L
        )
        dao.upsert(skipTodayIfAlreadyPastDue(draft))
        generateDueTasks()
        return RecurringResult.SAVED
    }

    suspend fun setActive(actor: User, scheduleId: String, active: Boolean): RecurringResult {
        if (!canManage(actor)) return RecurringResult.NOT_ALLOWED
        val schedule = dao.getById(scheduleId)
        if (schedule == null || schedule.familyId != actor.familyId) return RecurringResult.NOT_FOUND

        val updated = schedule.copy(isActive = active)
        dao.upsert(if (active) skipTodayIfAlreadyPastDue(updated) else updated)
        if (active) generateDueTasks()
        return if (active) RecurringResult.RESUMED else RecurringResult.PAUSED
    }

    /** Xóa lịch trình. Các nhiệm vụ đã sinh trước đó vẫn được giữ lại. */
    suspend fun deleteSchedule(actor: User, scheduleId: String): RecurringResult {
        if (!canManage(actor)) return RecurringResult.NOT_ALLOWED
        val schedule = dao.getById(scheduleId)
        if (schedule == null || schedule.familyId != actor.familyId) return RecurringResult.NOT_FOUND
        dao.deleteById(scheduleId)
        return RecurringResult.DELETED
    }

    private fun canManage(actor: User): Boolean =
        actor.role == UserRole.ADMIN && actor.familyId.isNotBlank()

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
     * Sinh nhiệm vụ của hôm nay cho mọi lịch trình đang bật. Gọi nhiều lần vẫn an toàn:
     * - lastGeneratedEpochDay chặn sinh lại trong cùng ngày;
     * - id nhiệm vụ cố định theo ngày + INSERT IGNORE chặn trùng tuyệt đối.
     *
     * Mỗi lịch trình được xử lý trong MỘT transaction: đọc lại lịch, kiểm tra nhóm và người nhận,
     * thêm nhiệm vụ, đánh dấu đã sinh. Room chỉ chạy 1 transaction ghi tại một thời điểm, nên việc
     * giải tán nhóm / xóa thành viên (FamilyRepository, cũng chạy trong transaction) không thể chen
     * vào giữa -> không sinh việc cho nhóm vừa bị xóa hay giao việc cho người vừa rời nhóm.
     * Thông báo chỉ gửi SAU khi transaction đã hoàn tất.
     * @return số nhiệm vụ mới được tạo.
     */
    suspend fun generateDueTasks(): Int = generateMutex.withLock {
        val zone = zone()
        val today = today()
        val todayEpoch = today.toEpochDay()
        val now = nowMillis()
        val createdTasks = mutableListOf<Pair<RecurringSchedule, Task>>()

        dao.getActiveSchedules().forEach { candidate ->
            val created = database.withTransaction<Pair<RecurringSchedule, Task>?> {
                // Đọc lại trong transaction: danh sách bên ngoài có thể đã cũ
                val schedule = dao.getById(candidate.id) ?: return@withTransaction null
                if (!schedule.isActive) return@withTransaction null
                if (schedule.lastGeneratedEpochDay >= todayEpoch) return@withTransaction null
                if (!schedule.occursOn(today)) return@withTransaction null

                // Nhóm gia đình đã bị giải tán -> tắt lịch trình "mồ côi", không sinh việc nữa
                if (dao.countFamily(schedule.familyId) == 0) {
                    dao.upsert(schedule.copy(isActive = false))
                    return@withTransaction null
                }

                // Người được giao đã rời nhóm -> giao cho cả nhà để việc không bị "mồ côi"
                val assigneeStillInFamily = schedule.assignedToUserId.isBlank() ||
                        dao.countUserInFamily(schedule.assignedToUserId, schedule.familyId) > 0
                val effective = if (assigneeStillInFamily) schedule
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
                    // Lịch giao cho cả nhà -> việc chung "ai làm cũng được" (bị từ chối thì trả lại cả nhà)
                    openToAll = effective.assignedToUserId.isEmpty()
                )
                val rowId = dao.insertTaskIfAbsent(task)
                dao.markGenerated(schedule.id, todayEpoch)
                if (rowId != -1L) effective to task else null
            }
            if (created != null) createdTasks += created
        }

        // Thông báo là việc ngoài DB -> chỉ gửi sau khi các transaction đã commit
        createdTasks.forEach { (schedule, task) -> notifyAssignees(schedule, task) }
        createdTasks.size
    }

    private suspend fun notifyAssignees(schedule: RecurringSchedule, task: Task) {
        val res = ServiceLocale.localizedContext(context)
        // Việc chung: báo cho mọi người có quyền nhận (cả Bố/Mẹ kia), trừ người giao - giống TaskRepository
        val receivers = if (task.assignedToUserId.isNotBlank()) {
            listOf(task.assignedToUserId)
        } else {
            dao.getUserIdsInFamily(schedule.familyId).filter { it != task.createdByUserId }
        }
        if (receivers.isEmpty()) return

        val assignee = schedule.assignedToUserName.ifBlank { res.getString(R.string.rec_everyone) }
        notificationRepository.notifyUsers(
            userIds = receivers,
            title = res.getString(R.string.rec_noti_title),
            message = res.getString(
                R.string.rec_noti_message,
                task.title,
                RecurringSchedule.formatMinuteOfDay(schedule.dueMinuteOfDay),
                assignee,
                task.points
            ),
            kind = NotificationRepository.Kind.TASK
        )
    }
}
