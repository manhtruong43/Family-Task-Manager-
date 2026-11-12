package com.family.taskmanager.data.model

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
    val submissionRevision: Int = 0
) {
    fun getProofImageList(): List<String> {
        if (proofImageUrl.isBlank()) return emptyList()
        return proofImageUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Quá hạn khi nhiệm vụ có hạn chót (`deadlineTimestamp > 0`), đã qua thời điểm hạn chót,
     * và vẫn chưa nộp báo cáo chờ duyệt / chưa hoàn thành.
     */
    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        deadlineTimestamp in 1 until nowMillis &&
            status != TaskStatus.COMPLETED &&
            status != TaskStatus.PENDING_APPROVAL
}
