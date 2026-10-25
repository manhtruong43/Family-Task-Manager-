package com.family.taskmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.RewardDao
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.service.ServiceLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Kết quả khi thành viên gửi yêu cầu đổi quà. */
sealed class RedeemResult {
    data class Success(val redemption: RewardRedemption) : RedeemResult()
    object NotEnoughPoints : RedeemResult()
    object NotAllowed : RedeemResult()
    object RewardNotFound : RedeemResult()
}

/** Kết quả khi Admin xử lý (trao / từ chối) một yêu cầu đổi quà. */
enum class ReviewResult { SUCCESS, ALREADY_HANDLED, NOT_ALLOWED, NOT_FOUND }

@Singleton
class RewardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val rewardDao: RewardDao,
    private val notificationRepository: NotificationRepository
) {
    companion object {
        const val MAX_REWARD_POINTS = 100_000
    }

    // ─────────────────────────── Phần thưởng ───────────────────────────

    fun getRewardsByFamily(familyId: String): Flow<List<Reward>> =
        rewardDao.getRewardsByFamily(familyId)

    /** Giữ lại để tương thích code cũ. */
    suspend fun createReward(reward: Reward) = rewardDao.insertReward(reward)

    suspend fun createReward(
        actor: User,
        title: String,
        description: String,
        pointsRequired: Int
    ): Boolean {
        if (!isAdminOfFamily(actor.id, actor.familyId)) return false
        if (title.isBlank() || pointsRequired !in 1..MAX_REWARD_POINTS) return false

        rewardDao.insertReward(
            Reward(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                familyId = actor.familyId,
                pointsRequired = pointsRequired
            )
        )
        return true
    }

    suspend fun updateReward(
        actor: User,
        reward: Reward,
        title: String,
        description: String,
        pointsRequired: Int
    ): Boolean {
        if (reward.familyId != actor.familyId || !isAdminOfFamily(actor.id, actor.familyId)) return false
        if (title.isBlank() || pointsRequired !in 1..MAX_REWARD_POINTS) return false

        rewardDao.updateReward(
            reward.copy(
                title = title.trim(),
                description = description.trim(),
                pointsRequired = pointsRequired
            )
        )
        return true
    }

    suspend fun deleteReward(actor: User, reward: Reward): Boolean {
        if (reward.familyId != actor.familyId || !isAdminOfFamily(actor.id, actor.familyId)) return false
        rewardDao.deleteRewardById(reward.id)
        return true
    }

    /** Tự động khởi tạo 5 món quà mẫu khi tạo gia đình mới (FamilyRepository gọi hàm này). */
    suspend fun initDefaultRewards(familyId: String) {
        val res = ServiceLocale.localizedContext(context)
        val defaults = listOf(
            Triple(R.string.rw_default_movie_title, R.string.rw_default_movie_desc, 100),
            Triple(R.string.rw_default_game_title, R.string.rw_default_game_desc, 50),
            Triple(R.string.rw_default_icecream_title, R.string.rw_default_icecream_desc, 30),
            Triple(R.string.rw_default_pizza_title, R.string.rw_default_pizza_desc, 150),
            Triple(R.string.rw_default_picnic_title, R.string.rw_default_picnic_desc, 200)
        )
        rewardDao.insertRewards(
            defaults.map { (titleRes, descRes, points) ->
                Reward(
                    id = UUID.randomUUID().toString(),
                    title = res.getString(titleRes),
                    description = res.getString(descRes),
                    familyId = familyId,
                    pointsRequired = points
                )
            }
        )
    }

    // ─────────────────────────── Đổi thưởng ───────────────────────────

    fun getRedemptionsByFamily(familyId: String): Flow<List<RewardRedemption>> =
        rewardDao.getRedemptionsByFamily(familyId)

    fun getRedemptionsByUser(userId: String): Flow<List<RewardRedemption>> =
        rewardDao.getRedemptionsByUser(userId)

    /**
     * Thành viên gửi yêu cầu đổi quà.
     * Toàn bộ chạy trong 1 Room Transaction: đọc lại dữ liệu mới nhất từ DB,
     * trừ điểm có điều kiện (không bao giờ âm) rồi mới tạo yêu cầu.
     */
    suspend fun redeemReward(rewardId: String, userId: String): RedeemResult {
        val result: RedeemResult = database.withTransaction<RedeemResult> {
            val reward = rewardDao.getRewardById(rewardId)
                ?: return@withTransaction RedeemResult.RewardNotFound
            val user = rewardDao.getUserById(userId)
                ?: return@withTransaction RedeemResult.NotAllowed

            // Admin không đổi quà; chỉ được đổi quà của chính gia đình mình
            if (user.role == UserRole.ADMIN || user.familyId != reward.familyId) {
                return@withTransaction RedeemResult.NotAllowed
            }

            val deducted = rewardDao.deductPointsIfEnough(user.id, reward.pointsRequired)
            if (deducted == 0) return@withTransaction RedeemResult.NotEnoughPoints

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
            RedeemResult.Success(redemption)
        }

        if (result is RedeemResult.Success) {
            val r = result.redemption
            val res = ServiceLocale.localizedContext(context)
            notificationRepository.notifyUsers(
                userIds = notificationRepository.getAdminIds(r.familyId),
                title = res.getString(R.string.rw_noti_request_title),
                message = res.getString(R.string.rw_noti_request_message, r.userName, r.rewardTitle, r.pointsUsed),
                kind = NotificationRepository.Kind.REWARD
            )
        }
        return result
    }

    /** Admin xác nhận đã trao quà. */
    suspend fun approveRedemption(redemptionId: String, adminId: String): ReviewResult {
        val redemption = rewardDao.getRedemptionById(redemptionId) ?: return ReviewResult.NOT_FOUND
        if (!isAdminOfFamily(adminId, redemption.familyId)) return ReviewResult.NOT_ALLOWED

        val updated = rewardDao.updateRedemptionStatusIf(
            redemptionId = redemption.id,
            expectedStatus = RedemptionStatus.PENDING,
            newStatus = RedemptionStatus.APPROVED
        )
        if (updated == 0) return ReviewResult.ALREADY_HANDLED

        val res = ServiceLocale.localizedContext(context)
        notificationRepository.notifyUser(
            userId = redemption.userId,
            title = res.getString(R.string.rw_noti_approved_title),
            message = res.getString(R.string.rw_noti_approved_message, redemption.rewardTitle),
            kind = NotificationRepository.Kind.REWARD
        )
        return ReviewResult.SUCCESS
    }

    /** Admin từ chối yêu cầu -> hoàn lại điểm cho thành viên (trong cùng 1 transaction). */
    suspend fun rejectRedemption(redemptionId: String, adminId: String): ReviewResult {
        val redemption = rewardDao.getRedemptionById(redemptionId) ?: return ReviewResult.NOT_FOUND
        if (!isAdminOfFamily(adminId, redemption.familyId)) return ReviewResult.NOT_ALLOWED

        val handled: Boolean = database.withTransaction<Boolean> {
            val updated = rewardDao.updateRedemptionStatusIf(
                redemptionId = redemption.id,
                expectedStatus = RedemptionStatus.PENDING,
                newStatus = RedemptionStatus.REJECTED
            )
            if (updated == 1) {
                rewardDao.refundPoints(redemption.userId, redemption.pointsUsed)
            }
            updated == 1
        }
        if (!handled) return ReviewResult.ALREADY_HANDLED

        val res = ServiceLocale.localizedContext(context)
        notificationRepository.notifyUser(
            userId = redemption.userId,
            title = res.getString(R.string.rw_noti_rejected_title),
            message = res.getString(
                R.string.rw_noti_rejected_message,
                redemption.rewardTitle,
                redemption.pointsUsed
            ),
            kind = NotificationRepository.Kind.REWARD
        )
        return ReviewResult.SUCCESS
    }

    // ─────────────────────────── Phân quyền ───────────────────────────

    /** Kiểm tra quyền dựa trên dữ liệu MỚI NHẤT trong DB, không tin dữ liệu phía UI. */
    private suspend fun isAdminOfFamily(userId: String, familyId: String): Boolean {
        if (familyId.isBlank()) return false
        val user = rewardDao.getUserById(userId) ?: return false
        return user.role == UserRole.ADMIN && user.familyId == familyId
    }
}
