package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

/** Cùng transaction với thông báo hộp thư; không phụ thuộc bộ nhớ hay SharedPreferences. */
@Entity(
    tableName = "deadline_reminder_receipts",
    primaryKeys = ["taskId", "deadlineTimestamp", "generation", "stageKey"],
    foreignKeys = [ForeignKey(
        entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DeadlineReminderReceipt(
    val taskId: String,
    val deadlineTimestamp: Long,
    val generation: Long,
    val stageKey: String
)
