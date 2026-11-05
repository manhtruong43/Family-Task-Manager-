package com.family.taskmanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.FamilyError
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.data.repository.FamilyResult
import com.family.taskmanager.data.repository.NotificationRepository
import com.family.taskmanager.data.repository.RewardRepository
import com.family.taskmanager.data.repository.TaskPermissions
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử nhóm gia đình: tạo / tham gia / mã mời / xóa thành viên / rời nhóm.
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'FamilyRepositoryTest'.
 */
@RunWith(AndroidJUnit4::class)
class FamilyRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FamilyRepository

    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER)
    private val na = User(id = "na", name = "Na", email = "na@x.com", role = UserRole.MEMBER)
    private val dad = User(id = "dad", name = "Bố", email = "bo@x.com", role = UserRole.ADMIN)

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val push = PushNotificationService(context, NotificationPreferences(context))
        val rewards = RewardRepository(context, db, db.rewardDao(), NotificationRepository(db.notificationDao(), push))
        repository = FamilyRepository(db, db.familyDao(), db.userDao(), rewards)
        listOf(parent, bin, na, dad).forEach { db.userDao().insertUser(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun user(id: String) = db.userDao().getUserByIdSync(id)!!

    private suspend fun createFamily(): FamilyGroup {
        val result = repository.createFamily("Gia đình Yêu Thương", parent)
        assertTrue(result is FamilyResult.Success)
        return (result as FamilyResult.Success).data
    }

    private fun errorOf(result: FamilyResult<*>): FamilyError? = (result as? FamilyResult.Failure)?.error

    @Test
    fun parent_createsFamily_withSixDigitCode_andDefaultRewards() = runBlocking<Unit> {
        val family = createFamily()

        assertTrue(family.inviteCode.matches(Regex("\\d{6}")))
        assertEquals(family.id, user(parent.id).familyId)
        assertTrue(db.rewardDao().getRewardsByFamily(family.id).first().isNotEmpty())
    }

    @Test
    fun createFamily_rules() = runBlocking<Unit> {
        assertEquals(FamilyError.EMPTY_NAME, errorOf(repository.createFamily("   ", parent)))
        assertEquals(FamilyError.ONLY_ADMIN_CAN_CREATE, errorOf(repository.createFamily("Nhà Bin", bin)))

        createFamily()
        assertEquals(FamilyError.ALREADY_IN_FAMILY, errorOf(repository.createFamily("Nhà thứ hai", parent)))
    }

    @Test
    fun member_joinsWithCode() = runBlocking<Unit> {
        val family = createFamily()

        assertTrue(repository.joinFamilyByCode(family.inviteCode, bin) is FamilyResult.Success)
        assertEquals(family.id, user(bin.id).familyId)
        assertEquals(FamilyError.ALREADY_IN_FAMILY, errorOf(repository.joinFamilyByCode(family.inviteCode, bin)))
    }

    @Test
    fun joinFamily_rules() = runBlocking<Unit> {
        val family = createFamily()
        val wrongCode = if (family.inviteCode == "000000") "111111" else "000000"

        assertEquals(FamilyError.INVALID_CODE, errorOf(repository.joinFamilyByCode("123", bin)))
        assertEquals(FamilyError.CODE_NOT_FOUND, errorOf(repository.joinFamilyByCode(wrongCode, bin)))
    }

    // ─── Hai Bố/Mẹ cùng một gia đình ───

    @Test
    fun secondParent_joinsWithCode_andKeepsAdminRights() = runBlocking<Unit> {
        val family = createFamily()

        assertTrue(repository.joinFamilyByCode(family.inviteCode, dad) is FamilyResult.Success)
        val joinedDad = user(dad.id)
        assertEquals(family.id, joinedDad.familyId)
        assertEquals(UserRole.ADMIN, joinedDad.role)                  // không bị hạ xuống Thành viên
        assertEquals(parent.id, db.familyDao().getFamilyByIdOnce(family.id)!!.adminId)

        // Bố cũng giao việc, duyệt việc và đổi mã mời được như Mẹ
        val task = Task(id = "t", title = "Rửa bát", familyId = family.id, createdByUserId = parent.id)
        assertTrue(TaskPermissions.canCreate(joinedDad))
        assertTrue(TaskPermissions.canReview(joinedDad, task))
        assertTrue(repository.regenerateInviteCode(dad.id) is FamilyResult.Success)

        // Hai Bố/Mẹ không xóa được nhau
        assertEquals(FamilyError.CANNOT_REMOVE_ADMIN, errorOf(repository.removeMember(dad.id, parent.id)))
    }

    @Test
    fun ownerLeaving_whileOtherParentStays_handsOverFamily() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, dad)
        repository.joinFamilyByCode(family.inviteCode, bin)

        assertTrue(repository.leaveFamily(parent.id) is FamilyResult.Success)

        assertEquals("", user(parent.id).familyId)
        val kept = db.familyDao().getFamilyByIdOnce(family.id)!!      // nhóm KHÔNG bị giải tán
        assertEquals(dad.id, kept.adminId)                            // Bố thành chủ nhóm
        assertEquals(family.id, user(bin.id).familyId)
        assertTrue(db.rewardDao().getRewardsByFamily(family.id).first().isNotEmpty())
    }

    @Test
    fun lastParent_mustStay_whileChildrenRemain() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, dad)
        repository.joinFamilyByCode(family.inviteCode, bin)

        assertTrue(repository.leaveFamily(dad.id) is FamilyResult.Success)
        assertEquals(parent.id, db.familyDao().getFamilyByIdOnce(family.id)!!.adminId)

        assertEquals(FamilyError.ADMIN_MUST_STAY, errorOf(repository.leaveFamily(parent.id)))
        assertEquals(family.id, user(parent.id).familyId)
    }

    @Test
    fun regenerateCode_oldCodeStopsWorking() = runBlocking<Unit> {
        val family = createFamily()

        val result = repository.regenerateInviteCode(parent.id)
        assertTrue(result is FamilyResult.Success)
        val newCode = (result as FamilyResult.Success).data
        assertNotEquals(family.inviteCode, newCode)

        assertEquals(FamilyError.CODE_NOT_FOUND, errorOf(repository.joinFamilyByCode(family.inviteCode, bin)))
        assertTrue(repository.joinFamilyByCode(newCode, bin) is FamilyResult.Success)
        assertEquals(FamilyError.NOT_ALLOWED, errorOf(repository.regenerateInviteCode(bin.id)))
    }

    @Test
    fun removeMember_rules() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, bin)
        repository.joinFamilyByCode(family.inviteCode, na)

        assertEquals(FamilyError.NOT_ALLOWED, errorOf(repository.removeMember(bin.id, na.id)))
        assertEquals(FamilyError.CANNOT_REMOVE_ADMIN, errorOf(repository.removeMember(parent.id, parent.id)))

        assertTrue(repository.removeMember(parent.id, bin.id) is FamilyResult.Success)
        assertEquals("", user(bin.id).familyId)
        assertEquals(family.id, user(na.id).familyId)
    }

    @Test
    fun leaveFamily_parentMustBeLast_thenFamilyIsDeleted() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, bin)

        assertEquals(FamilyError.ADMIN_MUST_STAY, errorOf(repository.leaveFamily(parent.id)))

        assertTrue(repository.leaveFamily(bin.id) is FamilyResult.Success)
        assertEquals("", user(bin.id).familyId)

        assertTrue(repository.leaveFamily(parent.id) is FamilyResult.Success)
        assertNull(db.familyDao().getFamilyByIdOnce(family.id))
    }

    // ─── Rời nhóm / bị xóa: việc dang dở không bị kẹt ───

    private fun task(id: String, familyId: String, assignee: User, status: TaskStatus) = Task(
        id = id, title = "Việc $id", familyId = familyId,
        assignedToUserId = assignee.id, assignedToUserName = assignee.name,
        createdByUserId = parent.id, status = status,
        proofImageUrl = if (status == TaskStatus.PENDING_APPROVAL) "file:///proof.jpg" else ""
    )

    @Test
    fun removedMember_unfinishedTasksGoBackToEveryone_completedTasksKept() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, bin)
        db.taskDao().insertTask(task("todo", family.id, bin, TaskStatus.IN_PROGRESS))
        db.taskDao().insertTask(task("pending", family.id, bin, TaskStatus.PENDING_APPROVAL))
        db.taskDao().insertTask(task("done", family.id, bin, TaskStatus.COMPLETED))
        db.recurringScheduleDao().upsert(
            RecurringSchedule(
                id = "rec", familyId = family.id, title = "Rửa bát",
                assignedToUserId = bin.id, assignedToUserName = bin.name, createdByUserId = parent.id
            )
        )

        assertTrue(repository.removeMember(parent.id, bin.id) is FamilyResult.Success)

        listOf("todo", "pending").forEach { id ->
            val released = db.taskDao().getTaskById(id)!!
            assertEquals("", released.assignedToUserId)
            assertEquals(TaskStatus.TODO, released.status)
            assertEquals("", released.proofImageUrl)
        }
        val done = db.taskDao().getTaskById("done")!!
        assertEquals(bin.id, done.assignedToUserId)                 // lịch sử giữ nguyên
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertEquals("", db.recurringScheduleDao().getById("rec")!!.assignedToUserId)
    }

    @Test
    fun memberLeaving_releasesTheirTasksToo() = runBlocking<Unit> {
        val family = createFamily()
        repository.joinFamilyByCode(family.inviteCode, na)
        db.taskDao().insertTask(task("na-todo", family.id, na, TaskStatus.TODO))

        assertTrue(repository.leaveFamily(na.id) is FamilyResult.Success)
        assertEquals("", db.taskDao().getTaskById("na-todo")!!.assignedToUserId)
    }

    @Test
    fun dissolvingFamily_removesItsSchedulesTasksAndRewards() = runBlocking<Unit> {
        val family = createFamily()
        db.taskDao().insertTask(task("t", family.id, parent, TaskStatus.TODO))
        db.recurringScheduleDao().upsert(
            RecurringSchedule(id = "rec", familyId = family.id, title = "Đổ rác", createdByUserId = parent.id)
        )

        assertTrue(repository.leaveFamily(parent.id) is FamilyResult.Success)

        assertNull(db.familyDao().getFamilyByIdOnce(family.id))
        assertNull(db.recurringScheduleDao().getById("rec"))
        assertTrue(db.taskDao().getTasksByFamily(family.id).first().isEmpty())
        assertTrue(db.rewardDao().getRewardsByFamily(family.id).first().isEmpty())
    }
}
