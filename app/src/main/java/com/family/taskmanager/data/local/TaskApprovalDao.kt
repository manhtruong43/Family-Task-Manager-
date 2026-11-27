package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.family.taskmanager.data.model.TaskApproval
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskApprovalDao {
    // ABORT nếu trùng phiên bản: cả tiến độ và cộng điểm phải rollback.
    @Insert
    suspend fun insert(approval: TaskApproval)

    @Query("SELECT * FROM task_approvals WHERE familyId = :familyId ORDER BY approvedAt DESC")
    fun observeByFamily(familyId: String): Flow<List<TaskApproval>>

    @Query("SELECT * FROM task_approvals WHERE familyId = :familyId ORDER BY approvedAt DESC")
    suspend fun getByFamily(familyId: String): List<TaskApproval>

    @Query("SELECT EXISTS(SELECT 1 FROM task_approvals WHERE taskId = :taskId)")
    suspend fun hasTaskHistory(taskId: String): Boolean

    @Query("DELETE FROM task_approvals WHERE familyId = :familyId")
    suspend fun deleteByFamily(familyId: String)
}
