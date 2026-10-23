package com.family.taskmanager.data.repository

import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.local.RewardDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardRepository @Inject constructor(
    private val rewardDao: RewardDao,
    private val userDao: UserDao,
    private val notificationDao: NotificationDao,
    private val pushNotificationService: PushNotificationService
) {
    fun getRewardsByFamily(familyId: String): Flow<List<Reward>> =
        rewardDao.getRewardsByFamily(familyId)

    suspend fun createReward(reward: Reward) = rewardDao.insertReward(reward)

    suspend fun initDefaultRewards(familyId: String) {
        val defaultRewards = listOf(
            Reward(id = UUID.randomUUID().toString(), title = "🎬 1 Buổi xem phim rạp", description = "Được chọn bộ phim hoạt hình/chiếu rạp yêu thích", familyId = familyId, pointsRequired = 100),
            Reward(id = UUID.randomUUID().toString(), title = "🎮 1 Giờ chơi game", description = "Thêm 1 giờ chơi game/PlayStation cuối tuần", familyId = familyId, pointsRequired = 50),
            Reward(id = UUID.randomUUID().toString(), title = "🍦 1 Ly kem siêu ngon", description = "Đổi 1 ly kem vị yêu thích", familyId = familyId, pointsRequired = 30),
            Reward(id = UUID.randomUUID().toString(), title = "🍕 Bữa tối Pizza gia đình", description = "Được chọn vị Pizza cho cả nhà thưởng thức", familyId = familyId, pointsRequired = 150),
            Reward(id = UUID.randomUUID().toString(), title = "⛺ Dã ngoại công viên", description = "Cả nhà cùng đi dã ngoại vào cuối tuần", familyId = familyId, pointsRequired = 200)
        )
        defaultRewards.forEach { rewardDao.insertReward(it) }
    }

    fun getRedemptionsByFamily(familyId: String): Flow<List<RewardRedemption>> =
        rewardDao.getRedemptionsByFamily(familyId)

    suspend fun redeemReward(reward: Reward, user: User): Boolean {
        if (user.points < reward.pointsRequired) return false

        userDao.deductPoints(user.id, reward.pointsRequired)

        val redemption = RewardRedemption(
            id = UUID.randomUUID().toString(),
            rewardId = reward.id,
            rewardTitle = reward.title,
            userId = user.id,
            userName = user.name,
            familyId = user.familyId,
            pointsUsed = reward.pointsRequired,
            status = RedemptionStatus.PENDING
        )
        rewardDao.insertRedemption(redemption)
        return true
    }

    suspend fun approveRedemption(redemption: RewardRedemption) {
        rewardDao.updateRedemptionStatus(redemption.id, RedemptionStatus.APPROVED)
        val notifTitle = "🎉 Phần thưởng đã được trao!"
        val notifMessage = "Phần thưởng '${redemption.rewardTitle}' đã được xác nhận trao cho bạn!"
        notificationDao.insertNotification(
            NotificationItem(
                id = UUID.randomUUID().toString(),
                userId = redemption.userId,
                title = notifTitle,
                message = notifMessage
            )
        )
        pushNotificationService.sendRewardNotification(notifTitle, notifMessage)
    }
}
