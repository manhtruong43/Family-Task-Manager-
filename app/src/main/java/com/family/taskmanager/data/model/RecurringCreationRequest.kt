package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Biên nhận tạo lịch, lưu nguyên tử cùng lịch để thử lại sau khi tiến trình bị tắt.
 * Không khóa ngoại tới lịch: xóa lịch vẫn giữ biên nhận, không cho yêu cầu cũ tạo lại nó.
 * Khi giải tán gia đình, biên nhận được dọn theo gia đình.
 */
@Entity(
    tableName = "recurring_creation_requests",
    foreignKeys = [ForeignKey(
        entity = FamilyGroup::class,
        parentColumns = ["id"],
        childColumns = ["familyId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("familyId")]
)
data class RecurringCreationRequest(
    @PrimaryKey val requestId: String,
    val actorId: String,
    val familyId: String,
    val scheduleId: String
)
