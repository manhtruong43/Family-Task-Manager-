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

    // ─── Thành viên trong nhóm (chỉ thao tác cột familyId của bảng users) ───

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE familyId = :familyId")
    suspend fun countMembers(familyId: String): Int

    /** Đưa 1 người ra khỏi nhóm gia đình (rời nhóm hoặc bị Admin xóa). */
    @Query("UPDATE users SET familyId = '' WHERE id = :userId")
    suspend fun clearUserFamily(userId: String)
}
