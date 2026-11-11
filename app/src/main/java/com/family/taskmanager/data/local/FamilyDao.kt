package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {

    // ─── Nhóm gia đình ───

    @Query("SELECT * FROM family_groups WHERE id = :familyId")
    fun getFamilyById(familyId: String): Flow<FamilyGroup?>

    @Query("SELECT * FROM family_groups WHERE id = :familyId LIMIT 1")
    suspend fun getFamilyByIdOnce(familyId: String): FamilyGroup?

    @Query("SELECT * FROM family_groups WHERE inviteCode = :code LIMIT 1")
    suspend fun getFamilyByInviteCode(code: String): FamilyGroup?

    @Query("SELECT COUNT(*) FROM family_groups WHERE inviteCode = :code")
    suspend fun countInviteCode(code: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamily(familyGroup: FamilyGroup)

    @Query("UPDATE family_groups SET inviteCode = :newCode WHERE id = :familyId")
    suspend fun updateInviteCode(familyId: String, newCode: String)

    @Query("DELETE FROM family_groups WHERE id = :familyId")
    suspend fun deleteFamily(familyId: String)

    /** Chuyển quyền chủ nhóm (người tạo nhóm) sang một Bố/Mẹ khác. */
    @Query("UPDATE family_groups SET adminId = :newAdminId WHERE id = :familyId")
    suspend fun updateFamilyOwner(familyId: String, newAdminId: String)

    // ─── Thành viên trong nhóm (chỉ thao tác cột familyId của bảng users) ───

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE familyId = :familyId")
    suspend fun countMembers(familyId: String): Int

    /** Một Bố/Mẹ khác (không phải [excludeUserId]) đang ở trong nhóm, nếu có. */
    @Query("SELECT * FROM users WHERE familyId = :familyId AND role = 'ADMIN' AND id != :excludeUserId LIMIT 1")
    suspend fun getOtherParent(familyId: String, excludeUserId: String): User?

    /** Đếm số Bố/Mẹ khác (không phải [excludeUserId]) đang ở trong nhóm. */
    @Query("SELECT COUNT(*) FROM users WHERE familyId = :familyId AND role = 'ADMIN' AND id != :excludeUserId")
    suspend fun countOtherParents(familyId: String, excludeUserId: String): Int

    /** Đếm số nhiệm vụ chưa hoàn thành đang giao cho [userId] trong gia đình [familyId]. */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE familyId = :familyId AND assignedToUserId = :userId " +
            "AND status IN ('TODO', 'IN_PROGRESS', 'REJECTED', 'PENDING_APPROVAL')"
    )
    suspend fun countUnfinishedTasksOfUser(familyId: String, userId: String): Int

    /** Đưa 1 người ra khỏi nhóm gia đình (rời nhóm hoặc bị Admin xóa). */
    @Query("UPDATE users SET familyId = '' WHERE id = :userId")
    suspend fun clearUserFamily(userId: String)

    // ─── Dọn dữ liệu khi thành viên rời nhóm ───

    /**
     * Việc CHƯA hoàn thành của người rời nhóm -> trả về "Chưa làm" và giao cho cả nhà,
     * để không bị kẹt (người cũ đã rời, người khác lại không có quyền làm việc của họ).
     * Việc đã hoàn thành giữ nguyên để thống kê / lịch sử vẫn đúng.
     */
    @Query(
        "UPDATE tasks SET assignedToUserId = '', assignedToUserName = '', status = 'TODO', " +
            "proofImageUrl = '', proofNote = '', openToAll = 1 " +
            "WHERE familyId = :familyId AND assignedToUserId = :userId " +
            "AND status IN ('TODO', 'IN_PROGRESS', 'REJECTED', 'PENDING_APPROVAL')"
    )
    suspend fun releaseUnfinishedTasks(familyId: String, userId: String): Int

    /** Lịch trình cố định đang giao cho người rời nhóm -> giao cho cả nhà. */
    @Query(
        "UPDATE recurring_schedules SET assignedToUserId = '', assignedToUserName = '' " +
            "WHERE familyId = :familyId AND assignedToUserId = :userId"
    )
    suspend fun releaseSchedules(familyId: String, userId: String): Int

    // ─── Dọn dữ liệu khi giải tán nhóm ───

    @Query("DELETE FROM recurring_schedules WHERE familyId = :familyId")
    suspend fun deleteSchedulesOfFamily(familyId: String)

    @Query("DELETE FROM task_reactions WHERE taskId IN (SELECT id FROM tasks WHERE familyId = :familyId)")
    suspend fun deleteReactionsOfFamily(familyId: String)

    @Query("DELETE FROM tasks WHERE familyId = :familyId")
    suspend fun deleteTasksOfFamily(familyId: String)

    @Query("DELETE FROM reward_redemptions WHERE familyId = :familyId")
    suspend fun deleteRedemptionsOfFamily(familyId: String)

    @Query("DELETE FROM rewards WHERE familyId = :familyId")
    suspend fun deleteRewardsOfFamily(familyId: String)
}
