package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE familyId = :familyId ORDER BY createdAt DESC")
    fun getTasksByFamily(familyId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE assignedToUserId = :userId ORDER BY createdAt DESC")
    fun getTasksByUser(userId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus)

    @Query("UPDATE tasks SET status = :status, proofImageUrl = :proofUrl, proofNote = :note WHERE id = :taskId")
    suspend fun submitTaskProof(taskId: String, status: TaskStatus, proofUrl: String, note: String)
}
