package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RecurringCreationRequest
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.UserRole
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng recurring_schedules (lịch trình cố định). */
@Dao
interface RecurringScheduleDao {

    @Query(
        "SELECT * FROM recurring_schedules WHERE familyId = :familyId " +
            "ORDER BY isActive DESC, dueMinuteOfDay ASC, title ASC"
    )
    fun observeByFamily(familyId: String): Flow<List<RecurringSchedule>>

    @Query("SELECT * FROM recurring_schedules WHERE familyId = :familyId")
    suspend fun getByFamilyOnce(familyId: String): List<RecurringSchedule>

    @Query("SELECT * FROM recurring_schedules WHERE isActive = 1")
    suspend fun getActiveSchedules(): List<RecurringSchedule>

    @Query("SELECT * FROM recurring_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecurringSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: RecurringSchedule)

    @Query("SELECT * FROM recurring_creation_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getCreationRequest(requestId: String): RecurringCreationRequest?

    /** Chỉ chèn cùng transaction tạo lịch; xung đột phải rollback, không thay biên nhận cũ. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCreationRequest(request: RecurringCreationRequest)

    @Query("DELETE FROM recurring_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE recurring_schedules SET lastGeneratedEpochDay = :epochDay WHERE id = :id")
    suspend fun markGenerated(id: String, epochDay: Long)

    /**
     * Thêm nhiệm vụ sinh từ lịch trình. IGNORE: nếu id đã tồn tại (đã sinh rồi) thì bỏ qua,
     * KHÔNG ghi đè tiến độ của nhiệm vụ cũ. Trả về -1 khi bị bỏ qua.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskIfAbsent(task: Task): Long

    @Query("SELECT COUNT(*) FROM users WHERE id = :userId AND familyId = :familyId")
    suspend fun countUserInFamily(userId: String, familyId: String): Int

    @Query("SELECT COUNT(*) FROM family_groups WHERE id = :familyId")
    suspend fun countFamily(familyId: String): Int

    @Query("SELECT id FROM users WHERE familyId = :familyId AND role = :role")
    suspend fun getUserIdsByRole(familyId: String, role: UserRole): List<String>

    @Query("SELECT id FROM users WHERE familyId = :familyId")
    suspend fun getUserIdsInFamily(familyId: String): List<String>
}
