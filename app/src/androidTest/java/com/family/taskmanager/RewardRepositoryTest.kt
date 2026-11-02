package com.family.taskmanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.NotificationRepository
import com.family.taskmanager.data.repository.RedeemResult
import com.family.taskmanager.data.repository.ReviewResult
import com.family.taskmanager.data.repository.RewardRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử đổi quà: không bao giờ âm điểm, hoàn điểm đúng 1 lần, duyệt đúng 1 lần, đúng người.
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'RewardRepositoryTest'.
 */
@RunWith(AndroidJUnit4::class)
class RewardRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RewardRepository

    private val family = "family-1"
    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN, familyId = family)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER, familyId = family, points = 100)
    private val otherParent = User(id = "admin2", name = "Bố nhà khác", email = "b@x.com", role = UserRole.ADMIN, familyId = "family-2")
    private val movie = Reward(id = "r-movie", title = "Xem phim", familyId = family, pointsRequired = 60)

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val push = PushNotificationService(context, NotificationPreferences(context))
        val notifications = NotificationRepository(db.notificationDao(), push)
        repository = RewardRepository(context, db, db.rewardDao(), notifications)

        listOf(parent, bin, otherParent).forEach { db.userDao().insertUser(it) }
        db.rewardDao().insertReward(movie)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun points(userId: String) = db.userDao().getUserByIdSync(userId)!!.points

    private suspend fun redeemOk(): String {
        val result = repository.redeemReward(movie.id, bin.id)
        assertTrue(result is RedeemResult.Success)
        return (result as RedeemResult.Success).redemption.id
    }

    @Test
    fun redeem_deductsPoints() = runBlocking<Unit> {
        redeemOk()
        assertEquals(40, points(bin.id))
    }

    @Test
    fun redeem_withoutEnoughPoints_isRefused_andNeverNegative() = runBlocking<Unit> {
        redeemOk()                                     // 100 -> 40
        val second = repository.redeemReward(movie.id, bin.id)
        assertEquals(RedeemResult.NotEnoughPoints, second)
        assertEquals(40, points(bin.id))
    }

    @Test
    fun parent_cannotRedeem() = runBlocking<Unit> {
        assertEquals(RedeemResult.NotAllowed, repository.redeemReward(movie.id, parent.id))
    }

    @Test
    fun reject_refundsPointsExactlyOnce() = runBlocking<Unit> {
        val redemptionId = redeemOk()

        assertEquals(ReviewResult.SUCCESS, repository.rejectRedemption(redemptionId, parent.id))
        assertEquals(ReviewResult.ALREADY_HANDLED, repository.rejectRedemption(redemptionId, parent.id))
        assertEquals(100, points(bin.id))
        assertEquals(RedemptionStatus.REJECTED, db.rewardDao().getRedemptionById(redemptionId)!!.status)
    }

    @Test
    fun approve_onlyOnce_andCannotRejectAfterwards() = runBlocking<Unit> {
        val redemptionId = redeemOk()

        assertEquals(ReviewResult.SUCCESS, repository.approveRedemption(redemptionId, parent.id))
        assertEquals(ReviewResult.ALREADY_HANDLED, repository.approveRedemption(redemptionId, parent.id))
        assertEquals(ReviewResult.ALREADY_HANDLED, repository.rejectRedemption(redemptionId, parent.id))
        assertEquals(40, points(bin.id))   // không được hoàn điểm sau khi đã trao quà
    }

    @Test
    fun onlyParentOfSameFamily_canReview() = runBlocking<Unit> {
        val redemptionId = redeemOk()

        assertEquals(ReviewResult.NOT_ALLOWED, repository.approveRedemption(redemptionId, bin.id))
        assertEquals(ReviewResult.NOT_ALLOWED, repository.rejectRedemption(redemptionId, otherParent.id))
        assertEquals(RedemptionStatus.PENDING, db.rewardDao().getRedemptionById(redemptionId)!!.status)
    }
}
