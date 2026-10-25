package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    // ─── Phần thưởng ───

    @Query("SELECT * FROM rewards WHERE familyId = :familyId ORDER BY pointsRequired ASC")
    fun getRewardsByFamily(familyId: String): Flow<List<Reward>>

    @Query("SELECT * FROM rewards WHERE id = :rewardId LIMIT 1")
    suspend fun getRewardById(rewardId: String): Reward?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: Reward)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewards(rewards: List<Reward>)

    @Update
    suspend fun updateReward(reward: Reward)

    @Query("DELETE FROM rewards WHERE id = :rewardId")
    suspend fun deleteRewardById(rewardId: String)

    // ─── Yêu cầu đổi thưởng ───

    @Query("SELECT * FROM reward_redemptions WHERE familyId = :familyId ORDER BY timestamp DESC")
    fun getRedemptionsByFamily(familyId: String): Flow<List<RewardRedemption>>

    @Query("SELECT * FROM reward_redemptions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getRedemptionsByUser(userId: String): Flow<List<RewardRedemption>>

    @Query("SELECT * FROM reward_redemptions WHERE id = :redemptionId LIMIT 1")
    suspend fun getRedemptionById(redemptionId: String): RewardRedemption?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemption(redemption: RewardRedemption)

    @Query("UPDATE reward_redemptions SET status = :status WHERE id = :redemptionId")
    suspend fun updateRedemptionStatus(redemptionId: String, status: RedemptionStatus)

    /**
     * Chỉ đổi trạng thái khi trạng thái hiện tại đúng bằng [expectedStatus].
     * Trả về số dòng bị ảnh hưởng (0 = đã được xử lý trước đó -> chống bấm 2 lần).
     */
    @Query(
        "UPDATE reward_redemptions SET status = :newStatus " +
                "WHERE id = :redemptionId AND status = :expectedStatus"
    )
    suspend fun updateRedemptionStatusIf(
        redemptionId: String,
        expectedStatus: RedemptionStatus,
        newStatus: RedemptionStatus
    ): Int

    // ─── Điểm của thành viên (giao dịch điểm khi đổi thưởng) ───

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    /**
     * Trừ điểm NGUYÊN TỬ: chỉ trừ khi đủ điểm. Trả về 1 nếu trừ thành công, 0 nếu không đủ điểm.
     * Nhờ điều kiện "points >= :cost" ngay trong câu SQL nên điểm không bao giờ bị âm.
     */
    @Query("UPDATE users SET points = points - :cost WHERE id = :userId AND points >= :cost")
    suspend fun deductPointsIfEnough(userId: String, cost: Int): Int

    @Query("UPDATE users SET points = points + :amount WHERE id = :userId")
    suspend fun refundPoints(userId: String, amount: Int)
}
