package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rewards")
data class Reward(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val familyId: String,
    val pointsRequired: Int,
    val iconName: String = "default"
)

enum class RedemptionStatus {
    PENDING,    // Chờ Admin xác nhận trao thưởng
    APPROVED,   // Đã trao
    REJECTED    // Từ chối
}

@Entity(tableName = "reward_redemptions")
data class RewardRedemption(
    @PrimaryKey val id: String,
    val rewardId: String,
    val rewardTitle: String,
    val userId: String,
    val userName: String,
    val familyId: String,
    val pointsUsed: Int,
    val status: RedemptionStatus = RedemptionStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)
