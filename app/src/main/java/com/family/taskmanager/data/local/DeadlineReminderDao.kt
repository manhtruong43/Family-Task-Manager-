package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.DeadlineReminderReceipt

internal const val REMINDER_CANDIDATE_CONDITION =
    "t.deadlineTimestamp > :now AND t.status IN ('TODO', 'IN_PROGRESS', 'REJECTED') " +
        "AND (t.taskMode != 'FLEXIBLE_WEEKLY' OR t.targetCount <= 1 OR t.completedCount < t.targetCount)"

@Dao
interface DeadlineReminderDao {
    @Query("SELECT * FROM deadline_reminder_receipts")
    suspend fun getAll(): List<DeadlineReminderReceipt>

    @Query("SELECT stageKey FROM deadline_reminder_receipts WHERE taskId = :taskId AND deadlineTimestamp = :deadline AND generation = :generation")
    suspend fun getConsumedStages(taskId: String, deadline: Long, generation: Long): List<String>

    @Query("SELECT r.* FROM deadline_reminder_receipts r JOIN tasks t ON t.id = r.taskId " +
        "AND t.deadlineTimestamp = r.deadlineTimestamp AND t.reminderGeneration = r.generation " +
        "WHERE " + REMINDER_CANDIDATE_CONDITION)
    suspend fun getForReminderCandidates(now: Long): List<DeadlineReminderReceipt>

    @Query("SELECT * FROM deadline_reminder_receipts WHERE taskId IN (:taskIds)")
    suspend fun getForTaskIds(taskIds: List<String>): List<DeadlineReminderReceipt>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipts: List<DeadlineReminderReceipt>)
}
