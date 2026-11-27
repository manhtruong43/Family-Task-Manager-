package com.family.taskmanager.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority {
    LOW, MEDIUM, HIGH
}

enum class TaskStatus {
    TODO,               // Chưa làm
    IN_PROGRESS,        // Đang làm
    PENDING_APPROVAL,   // Chờ duyệt (đã gửi báo cáo + ảnh)
    COMPLETED,          // Đã hoàn thành (đã duyệt + cộng điểm)
    REJECTED            // Bị từ chối (cần làm lại)
}

/** Chế độ thực hiện nhiệm vụ: cố định 1 lần hoặc linh hoạt đủ số lần trong tuần. */
enum class TaskMode {
    FIXED,
    FLEXIBLE_WEEKLY
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val familyId: String,
    val assignedToUserId: String = "",
    val assignedToUserName: String = "",
    val createdByUserId: String,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val points: Int = 10,
    val deadlineTimestamp: Long = 0L,
    val proofImageUrl: String = "",
    val proofNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Việc "ai làm cũng được" (giao cho cả nhà). Khi có người nhận/nộp, assignedToUserId được gán
     * cho người đó; cờ này giúp app nhớ việc vốn là việc chung để trả lại cho cả nhà khi bị từ chối hoặc trả việc.
     * Thêm ở phiên bản DB 5 (MIGRATION_4_5).
     */
    val openToAll: Boolean = false,
    /**
     * Phiên bản lượt nộp minh chứng (tăng thêm 1 sau mỗi lần nộp thành công).
     * Dùng để kiểm tra màn hình duyệt có đang nhìn đúng lượt nộp hiện tại trong DB hay không.
     * Thêm ở phiên bản DB 6 (MIGRATION_5_6).
     */
    val submissionRevision: Int = 0,
    /**
     * Chế độ nhiệm vụ: FIXED (một lần / sinh từ lịch cố định) hoặc FLEXIBLE_WEEKLY (linh hoạt đủ số lần trong tuần).
     * Thêm ở phiên bản DB 9 (MIGRATION_8_9).
     */
    @ColumnInfo(defaultValue = "'FIXED'")
    val taskMode: TaskMode = TaskMode.FIXED,
    /** Số lần cần hoàn thành trong kỳ (mặc định 1; việc linh hoạt từ 2..7). Thêm ở DB 9. */
    @ColumnInfo(defaultValue = "1")
    val targetCount: Int = 1,
    /** Số lần đã được Admin duyệt hoàn thành. Thêm ở DB 9. */
    @ColumnInfo(defaultValue = "0")
    val completedCount: Int = 0,
    /** Dữ liệu cũ DB 9; scheduler dùng cấu hình chung từ NotificationPreferences thay cho giá trị này. */
    @ColumnInfo(defaultValue = "30")
    val reminderLeadMinutes: Int = DEFAULT_REMINDER_LEAD_MINUTES,
    /** Tăng khi đổi người nhận hoặc trả mục tiêu về nhóm; không đổi khi duyệt thêm một lượt. */
    @ColumnInfo(defaultValue = "0")
    val reminderGeneration: Long = 0L
) {
    val isFlexibleWeekly: Boolean
        get() = taskMode == TaskMode.FLEXIBLE_WEEKLY && targetCount > 1

    val remainingCount: Int
        get() = (targetCount - completedCount).coerceAtLeast(0)

    fun getProofImageList(): List<String> {
        if (proofImageUrl.isBlank()) return emptyList()
        return proofImageUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Quá hạn khi nhiệm vụ có hạn chót (`deadlineTimestamp > 0`), đã qua thời điểm hạn chót,
     * và vẫn chưa hoàn thành (hoặc chưa nộp lượt cuối cùng chờ duyệt).
     */
    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (deadlineTimestamp !in 1 until nowMillis) return false
        if (status == TaskStatus.COMPLETED) return false
        if (status == TaskStatus.PENDING_APPROVAL) {
            return isFlexibleWeekly && (completedCount + 1 < targetCount)
        }
        return true
    }

    companion object {
        const val DEFAULT_REMINDER_LEAD_MINUTES = 30
        val ALLOWED_REMINDER_LEAD_MINUTES = listOf(30, 60, 120)
        const val MIN_FLEXIBLE_TARGET_COUNT = 2
        const val MAX_FLEXIBLE_TARGET_COUNT = 7
    }
}
