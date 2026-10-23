package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.TaskReaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskReactionDao {
    @Query("SELECT * FROM task_reactions WHERE taskId = :taskId")
    fun getReactionsByTask(taskId: String): Flow<List<TaskReaction>>

    @Query("SELECT * FROM task_reactions WHERE taskId = :taskId AND userId = :userId AND emoji = :emoji LIMIT 1")
    suspend fun getUserReaction(taskId: String, userId: String, emoji: String): TaskReaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: TaskReaction)

    @Query("DELETE FROM task_reactions WHERE taskId = :taskId AND userId = :userId AND emoji = :emoji")
    suspend fun deleteReaction(taskId: String, userId: String, emoji: String)
}
