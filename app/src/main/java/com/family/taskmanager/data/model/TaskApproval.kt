package com.family.taskmanager.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Điểm thực nhận của mỗi lượt duyệt, ghi cùng transaction với tiến độ và số dư.
 * Không FK tới task/user: hủy việc hoặc rời nhóm không xóa điểm đã kiếm được.
 * taskCreatedAt giữ quy tắc lọc kỳ thống kê theo ngày tạo nhiệm vụ hiện có.
 */
@Entity(
    tableName = "task_approvals",
    primaryKeys = ["taskId", "submissionRevision"],
    indices = [Index("familyId"), Index("userId")]
)
data class TaskApproval(
    val taskId: String,
    val submissionRevision: Int,
    val familyId: String,
    val userId: String,
    val points: Int,
    val taskCreatedAt: Long,
    val approvedAt: Long,
    /** Biên nhận gộp khi nâng cấp dữ liệu cũ; lượt duyệt mới luôn có giá trị 1. */
    @ColumnInfo(defaultValue = "1") val approvedRounds: Int = 1,
    /** DB 9 chưa lưu điểm từng lượt: số liệu linh hoạt cũ chỉ có thể ước tính. */
    @ColumnInfo(defaultValue = "0") val legacyEstimate: Boolean = false,
    /** Giữ được tên người nhận điểm kể cả khi họ đã rời nhóm. */
    @ColumnInfo(defaultValue = "''") val workerName: String = ""
)
