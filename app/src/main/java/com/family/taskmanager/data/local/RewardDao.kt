package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards WHERE familyId = :familyId")
    fun getRewardsByFamily(familyId: String): Flow<List<Reward>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: Reward)

    @Query("SELECT * FROM reward_redemptions WHERE familyId = :familyId ORDER BY timestamp DESC")
    fun getRedemptionsByFamily(familyId: String): Flow<List<RewardRedemption>>

    @Query("SELECT * FROM reward_redemptions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getRedemptionsByUser(userId: String): Flow<List<RewardRedemption>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemption(redemption: RewardRedemption)

    @Query("UPDATE reward_redemptions SET status = :status WHERE id = :redemptionId")
    suspend fun updateRedemptionStatus(redemptionId: String, status: RedemptionStatus)
}
