package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.FamilyGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM family_groups WHERE id = :familyId")
    fun getFamilyById(familyId: String): Flow<FamilyGroup?>

    @Query("SELECT * FROM family_groups WHERE inviteCode = :code LIMIT 1")
    suspend fun getFamilyByInviteCode(code: String): FamilyGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamily(familyGroup: FamilyGroup)
}
