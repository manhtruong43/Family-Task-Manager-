package com.family.taskmanager.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Kiểu lặp lại của lịch trình cố định. */
enum class RepeatType {
    DAILY,   // Hằng ngày
    WEEKLY   // Hằng tuần vào các thứ được chọn
}

/** Cách giao cho Cả nhà trong mỗi kỳ; giao đích danh vẫn chỉ sinh một việc riêng. */
enum class RecurringAssignmentMode {
    SHARED,      // Một việc chung, người hợp lệ nhận trước sẽ làm
    EACH_MEMBER  // Mỗi thành viên hợp lệ một việc riêng
}

/**
 * Lịch trình cố định (Chức năng 6 - Lập kế hoạch: tạo lịch trình hằng ngày/tuần cố định).
 *
 * Mỗi lịch trình là một "mẫu" nhiệm vụ. Vào mỗi ngày đến lịch, ứng dụng tự sinh
 * một việc chung hoặc các việc riêng từ mẫu này (xem RecurringScheduleRepository.generateDueTasks),
 * nên nhiệm vụ sinh ra đi qua đúng luồng sẵn có: làm -> nộp minh chứng -> duyệt -> cộng điểm.
 */
@Entity(tableName = "recurring_schedules")
data class RecurringSchedule(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val description: String = "",
    /** Rỗng = giao cho cả nhà (giống Task). */
    val assignedToUserId: String = "",
    val assignedToUserName: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val points: Int = 10,
    val repeatType: RepeatType = RepeatType.DAILY,
    /** Bit 0 = Thứ Hai ... bit 6 = Chủ Nhật. Chỉ dùng khi repeatType = WEEKLY. */
    val daysOfWeekMask: Int = 0,
    /** Giờ hết hạn trong ngày, tính bằng phút kể từ 00:00 (vd 20:00 = 1200). */
    val dueMinuteOfDay: Int = DEFAULT_DUE_MINUTE,
    val isActive: Boolean = true,
    /** Người giao/quản lý hiện tại; chuyển khi rời nhóm, không đổi các Task đã sinh. */
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Ngày gần nhất đã sinh nhiệm vụ (LocalDate.toEpochDay theo giờ máy). -1 = chưa sinh lần nào. */
    val lastGeneratedEpochDay: Long = -1L,
    /** Chỉ áp dụng khi assignedToUserId rỗng. Lịch cũ giữ chế độ một việc chung. */
    @ColumnInfo(defaultValue = "'SHARED'")
    val assignmentMode: RecurringAssignmentMode = RecurringAssignmentMode.SHARED
) {
    /** Lịch trình có rơi vào ngày [date] không (không xét bật/tắt). */
    fun occursOn(date: LocalDate): Boolean = when (repeatType) {
        RepeatType.DAILY -> true
        RepeatType.WEEKLY -> isDaySelected(daysOfWeekMask, date.dayOfWeek.value - 1)
    }

    /** Thời điểm hết hạn (epoch millis) của lần lặp vào ngày [date]. */
    fun dueAtMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone)
            .plusMinutes(dueMinuteOfDay.toLong())
            .toInstant()
            .toEpochMilli()

    /** Id cố định cho nhiệm vụ sinh ra vào ngày [date] -> không bao giờ sinh trùng 2 lần. */
    fun taskIdFor(date: LocalDate): String = "rec_${id}_${date.toEpochDay()}"

    /**
     * Dựng nhiệm vụ cho một kỳ từ danh sách thành viên mới nhất.
     * Không mang trạng thái/người đã nhận/minh chứng của kỳ trước sang.
     * Repository gọi trong transaction để cố định thành viên, lưu cả bộ và đánh dấu kỳ nguyên tử.
     */
    fun tasksForOccurrence(
        date: LocalDate,
        members: List<User>,
        generatedAt: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Task> {
        val effective = withCurrentManager(members)
        val assignees = effective.recipientsForOccurrence(members)
        val separateForEveryone = effective.assignedToUserId.isEmpty() &&
            effective.assignmentMode == RecurringAssignmentMode.EACH_MEMBER
        return assignees.map { assignee ->
            Task(
                id = if (separateForEveryone) "${taskIdFor(date)}_each_${assignee!!.id}" else taskIdFor(date),
                title = title,
                description = description,
                familyId = familyId,
                assignedToUserId = assignee?.id.orEmpty(),
                assignedToUserName = assignee?.name.orEmpty(),
                createdByUserId = effective.createdByUserId,
                priority = priority,
                status = TaskStatus.TODO,
                points = points,
                deadlineTimestamp = dueAtMillis(date, zone),
                createdAt = generatedAt,
                openToAll = assignee == null
            )
        }
    }

    /**
     * Cùng quy tắc quản lý ở UI và repository. Repository phải truyền actor/thành viên mới nhất
     * trong transaction. Người tạo vẫn quản lý được lịch; lịch chung nhận trước giữ hành vi cũ.
     */
    fun canBeManagedBy(actor: User?, members: List<User>): Boolean {
        if (actor == null || actor.role != UserRole.ADMIN ||
            actor.familyId.isBlank() || actor.familyId != familyId
        ) return false
        val effective = withCurrentManager(members)
        if (effective.assignedToUserId == actor.id) return false
        if (actor.id == effective.createdByUserId) return true
        return effective.assignedToUserId.isNotEmpty() ||
            effective.assignmentMode != RecurringAssignmentMode.EACH_MEMBER ||
            effective.recipientsForOccurrence(members).none { it?.id == actor.id }
    }

    /**
     * Khôi phục lịch mất người quản lý từ thành viên hiện tại, cùng quy tắc cho UI và DB.
     * Giữ người quản lý hợp lệ đã có; chọn theo ID ổn định khi cần chuyển, không chọn người nhận đích danh.
     * Repository lưu bản sửa trong transaction; bản tính này không tác động các Task đã sinh.
     */
    fun withCurrentManager(members: List<User>): RecurringSchedule {
        val admins = members.filter { it.familyId == familyId && it.role == UserRole.ADMIN }
            .distinctBy { it.id }.sortedBy { it.id }
        if (admins.any { it.id == createdByUserId && it.id != assignedToUserId }) return this
        val manager = admins.firstOrNull { it.id != assignedToUserId }
        if (manager != null) return copy(createdByUserId = manager.id)
        // Dữ liệu cũ giao cho phụ huynh duy nhất: không thể tự duyệt, chuyển về lịch chung trước khi nhận quản lý.
        return if (admins.size == 1) copy(
            createdByUserId = admins.single().id,
            assignedToUserId = "",
            assignedToUserName = "",
            assignmentMode = RecurringAssignmentMode.SHARED
        ) else this
    }

    private fun recipientsForOccurrence(members: List<User>): List<User?> {
        val familyMembers = members.filter { it.familyId == familyId }.distinctBy { it.id }
        fun hasReviewer(worker: User): Boolean = familyMembers.any {
            it.role == UserRole.ADMIN && it.id != worker.id
        }
        return when {
            assignedToUserId.isNotEmpty() -> listOf(
                familyMembers.firstOrNull { it.id == assignedToUserId && hasReviewer(it) }
            )
            assignmentMode == RecurringAssignmentMode.EACH_MEMBER -> familyMembers.filter {
                it.id != createdByUserId && hasReviewer(it)
            }
            else -> listOf(null)
        }
    }

    /**
     * Lịch tuần đã chọn đủ 7 ngày thì coi như hằng ngày (dùng khi hiển thị).
     * Viết dạng hàm (không phải thuộc tính) để Room không hiểu nhầm là một cột.
     */
    fun repeatsEveryDay(): Boolean =
        repeatType == RepeatType.DAILY || (daysOfWeekMask and ALL_DAYS_MASK) == ALL_DAYS_MASK

    companion object {
        const val DEFAULT_DUE_MINUTE = 20 * 60
        const val ALL_DAYS_MASK = 0b111_1111
        const val MAX_POINTS = 1_000
        const val MAX_TITLE_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 150

        /** [dayIndex]: 0 = Thứ Hai ... 6 = Chủ Nhật. */
        fun isDaySelected(mask: Int, dayIndex: Int): Boolean = (mask and (1 shl dayIndex)) != 0

        fun toggleDay(mask: Int, dayIndex: Int): Int = mask xor (1 shl dayIndex)

        fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        fun formatMinuteOfDay(minuteOfDay: Int): String =
            "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }
}
