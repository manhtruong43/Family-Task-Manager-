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
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getProofImageList(): List<String> {
        if (proofImageUrl.isBlank()) return emptyList()
        return proofImageUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
