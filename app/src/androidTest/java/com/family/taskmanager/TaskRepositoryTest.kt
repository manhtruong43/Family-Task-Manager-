package com.family.taskmanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskActionResult
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử nghiệp vụ nhiệm vụ trên database thật (Room trong bộ nhớ).
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'TaskRepositoryTest'.
 */
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository

    private val family = "family-1"
    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN, familyId = family, isEmailVerified = true)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER, familyId = family, isEmailVerified = true)
    private val na = User(id = "na", name = "Na", email = "na@x.com", role = UserRole.MEMBER, familyId = family, isEmailVerified = true)
    private val dad = User(id = "dad", name = "Bố", email = "bo@x.com", role = UserRole.ADMIN, familyId = family, isEmailVerified = true)
    private val otherParent = User(id = "admin2", name = "Bố nhà khác", email = "b@x.com", role = UserRole.ADMIN, familyId = "family-2", isEmailVerified = true)

    private val taskForBin = Task(
        id = "t-bin", title = "Dọn phòng", familyId = family,
        assignedToUserId = bin.id, assignedToUserName = bin.name,
        createdByUserId = parent.id, points = 10
    )
    private val taskForEveryone = Task(
        id = "t-all", title = "Rửa bát", familyId = family,
        createdByUserId = parent.id, points = 5
    )
    private val proof = "file:///data/proof.jpg"

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val push = PushNotificationService(context, NotificationPreferences(context))
        repository = TaskRepository(
            context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(), push
        )
        listOf(parent, bin, na, dad, otherParent).forEach { db.userDao().insertUser(it) }
        db.taskDao().insertTask(taskForBin)
        db.taskDao().insertTask(taskForEveryone)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun status(taskId: String) = db.taskDao().getTaskById(taskId)!!.status
    private suspend fun points(userId: String) = db.userDao().getUserByIdSync(userId)!!.points

    @Test
    fun otherMember_cannotStartOrSubmit_someoneElsesTask() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(taskForBin.id, na))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForBin.id, proof, "", na))
        assertEquals(TaskStatus.TODO, status(taskForBin.id))
    }

    @Test
    fun parent_cannotSubmitProof_forChild() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForBin.id, proof, "", parent))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForEveryone.id, proof, "", parent))
    }

    @Test
    fun submitWithoutPhoto_isRefused() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NO_PROOF, repository.submitTaskProof(taskForBin.id, "", "xong", bin))
        assertEquals(TaskActionResult.NO_PROOF, repository.submitTaskProof(taskForBin.id, " , ", "xong", bin))
        assertEquals(TaskStatus.TODO, status(taskForBin.id))
    }

    @Test
    fun assignee_fullFlow_startSubmitApprove() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(taskForBin.id, bin))
        assertEquals(TaskStatus.IN_PROGRESS, status(taskForBin.id))

        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "đã dọn", bin))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(taskForBin.id))

        // Nộp lần 2 khi đang chờ duyệt -> sai trạng thái
        assertEquals(TaskActionResult.INVALID_STATE, repository.submitTaskProof(taskForBin.id, proof, "", bin))

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(taskForBin, parent))
        assertEquals(TaskStatus.COMPLETED, status(taskForBin.id))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun approveTwice_addsPointsOnlyOnce() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(taskForBin, parent))
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(taskForBin, parent))
        assertEquals(TaskActionResult.INVALID_STATE, repository.rejectTask(taskForBin, parent))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun onlyParentOfSameFamily_canApprove() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(taskForBin, bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(taskForBin, otherParent))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(taskForBin.id))
        assertEquals(0, points(bin.id))
    }

    @Test
    fun reject_thenRedo_thenApprove() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(taskForBin, parent))
        assertEquals(TaskStatus.REJECTED, status(taskForBin.id))
        assertEquals(0, points(bin.id))

        assertEquals(TaskActionResult.SUCCESS, repository.startTask(taskForBin.id, bin))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "làm lại", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(taskForBin, parent))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun everyoneTask_submitterBecomesAssignee_andGetsPoints() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForEveryone.id, proof, "", na))
        assertEquals(na.id, db.taskDao().getTaskById(taskForEveryone.id)!!.assignedToUserId)

        // Bin không nộp chồng lên được nữa
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForEveryone.id, proof, "", bin))

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(taskForEveryone, parent))
        assertEquals(5, points(na.id))
        assertEquals(0, points(bin.id))
    }

    @Test
    fun onlyParent_canCreateTask() = runBlocking<Unit> {
        val newTask = taskForBin.copy(id = "t-new", title = "Tưới cây")
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.createTask(newTask, bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.createTask(newTask, otherParent))
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(newTask.copy(title = "  "), parent))
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(newTask, parent))
    }

    // ─── Không tự duyệt / không tự giao ───

    @Test
    fun parent_cannotApprove_ownTask_otherParentCan() = runBlocking<Unit> {
        val cook = Task(
            id = "t-cook", title = "Nấu cơm", familyId = family,
            assignedToUserId = dad.id, assignedToUserName = dad.name,
            createdByUserId = parent.id, points = 20
        )
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(cook, parent))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(cook.id, proof, "", dad))

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(cook, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(cook.id))

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(cook, parent))
        assertEquals(20, points(dad.id))
    }

    @Test
    fun cannotAssign_toYourself_orOutsideFamily() = runBlocking<Unit> {
        val selfTask = taskForBin.copy(id = "t-self", assignedToUserId = parent.id, assignedToUserName = parent.name)
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(selfTask, parent))

        val outsiderTask = taskForBin.copy(id = "t-out", assignedToUserId = otherParent.id)
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(outsiderTask, parent))
    }

    // ─── Sửa / hủy việc giao nhầm ───

    private suspend fun edit(taskId: String, assigneeId: String?, actor: User, title: String = "Dọn phòng") =
        repository.updateTask(taskId, title, "", assigneeId, TaskPriority.HIGH, 15, 0L, actor)

    @Test
    fun edit_changingAssignee_resetsTaskForNewPerson() = runBlocking<Unit> {
        repository.startTask(taskForBin.id, bin)
        assertEquals(TaskActionResult.SUCCESS, edit(taskForBin.id, na.id, parent))

        val edited = db.taskDao().getTaskById(taskForBin.id)!!
        assertEquals(na.id, edited.assignedToUserId)
        assertEquals(TaskStatus.TODO, edited.status)
        assertEquals(15, edited.points)
        assertEquals(TaskPriority.HIGH, edited.priority)

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForBin.id, proof, "", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "", na))
    }

    @Test
    fun edit_rules() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NOT_ALLOWED, edit(taskForBin.id, na.id, bin))            // Con không sửa
        assertEquals(TaskActionResult.NOT_ALLOWED, edit(taskForBin.id, na.id, otherParent))    // nhà khác
        assertEquals(TaskActionResult.INVALID_INPUT, edit(taskForBin.id, parent.id, parent))   // tự giao cho mình
        assertEquals(TaskActionResult.INVALID_INPUT, edit(taskForBin.id, na.id, parent, title = " "))

        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        assertEquals(TaskActionResult.INVALID_STATE, edit(taskForBin.id, na.id, parent))       // đã nộp
        assertEquals(bin.id, db.taskDao().getTaskById(taskForBin.id)!!.assignedToUserId)
    }

    @Test
    fun cancel_removesTaskAndReactions_onlyBeforeCompleted() = runBlocking<Unit> {
        db.taskReactionDao().insertReaction(
            TaskReaction(id = "r1", taskId = taskForBin.id, userId = na.id, userName = na.name, emoji = "👍")
        )
        repository.submitTaskProof(taskForBin.id, proof, "", bin)

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.cancelTask(taskForBin.id, bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.cancelTask(taskForBin.id, otherParent))

        assertEquals(TaskActionResult.SUCCESS, repository.cancelTask(taskForBin.id, dad))      // đang chờ duyệt vẫn hủy được
        assertNull(db.taskDao().getTaskById(taskForBin.id))
        assertNull(db.taskReactionDao().getUserReaction(taskForBin.id, na.id, "👍"))
        assertEquals(TaskActionResult.NOT_FOUND, repository.cancelTask(taskForBin.id, parent))

        // Việc đã hoàn thành: không hủy được, điểm giữ nguyên
        repository.submitTaskProof(taskForEveryone.id, proof, "", na)
        repository.approveTask(taskForEveryone, parent)
        assertEquals(TaskActionResult.INVALID_STATE, repository.cancelTask(taskForEveryone.id, parent))
        assertEquals(5, points(na.id))
    }

    // ─── Việc chung / Mỗi người đều làm ───

    @Test
    fun everyoneTask_otherParentCanClaim_butNotTheAssigner() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForEveryone.id, proof, "", parent))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForEveryone.id, proof, "", dad))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(taskForEveryone, parent))
        assertEquals(5, points(dad.id))
    }

    @Test
    fun createForEach_givesEveryoneTheirOwnTask() = runBlocking<Unit> {
        val template = Task(id = "tpl", title = "Dọn phòng riêng", familyId = family, createdByUserId = parent.id, points = 20)

        // Có cả chính mình trong danh sách -> từ chối, không tạo việc nào
        assertEquals(
            TaskActionResult.INVALID_INPUT,
            repository.createTaskForEach(template, listOf(bin.id, parent.id), parent)
        )
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.createTaskForEach(template, listOf(na.id), bin))

        assertEquals(
            TaskActionResult.SUCCESS,
            repository.createTaskForEach(template, listOf(dad.id, bin.id, na.id), parent)
        )
        val rooms = db.taskDao().getTasksByFamily(family).first().filter { it.title == "Dọn phòng riêng" }
        assertEquals(3, rooms.size)
        assertEquals(setOf(dad.id, bin.id, na.id), rooms.map { it.assignedToUserId }.toSet())
        assertTrue(rooms.none { it.openToAll })

        // Mỗi bản được duyệt riêng: Bin bị từ chối không ảnh hưởng Na
        val binRoom = rooms.first { it.assignedToUserId == bin.id }
        val naRoom = rooms.first { it.assignedToUserId == na.id }
        repository.submitTaskProof(binRoom.id, proof, "", bin)
        repository.submitTaskProof(naRoom.id, proof, "", na)
        repository.rejectTask(binRoom, parent)
        repository.approveTask(naRoom, parent)
        assertEquals(20, points(na.id))
        assertEquals(0, points(bin.id))
        assertEquals(bin.id, db.taskDao().getTaskById(binRoom.id)!!.assignedToUserId)
    }

    @Test
    fun openTask_rejected_goesBackToWholeFamily() = runBlocking<Unit> {
        val dishes = Task(id = "t-dishes", title = "Rửa bát", familyId = family, createdByUserId = parent.id, points = 15)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(dishes, parent))
        assertTrue(db.taskDao().getTaskById(dishes.id)!!.openToAll)

        repository.submitTaskProof(dishes.id, proof, "", na)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(dishes, parent))

        val released = db.taskDao().getTaskById(dishes.id)!!
        assertEquals(TaskStatus.REJECTED, released.status)
        assertEquals("", released.assignedToUserId)          // trả lại cả nhà

        // Lần này Bin làm lại và nhận điểm
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(dishes.id, proof, "làm lại", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(dishes, parent))
        assertEquals(15, points(bin.id))
        assertEquals(0, points(na.id))

        // Việc giao đích danh bị từ chối thì vẫn thuộc người đó
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        repository.rejectTask(taskForBin, parent)
        assertEquals(bin.id, db.taskDao().getTaskById(taskForBin.id)!!.assignedToUserId)
        assertFalse(db.taskDao().getTaskById(taskForBin.id)!!.openToAll)
    }

    @Test
    fun review_isCheckedAgainstCurrentSubmission_notTheOldScreen() = runBlocking<Unit> {
        val wipe = Task(id = "t-wipe", title = "Lau bàn", familyId = family, createdByUserId = parent.id, points = 5)
        repository.createTask(wipe, parent)
        repository.submitTaskProof(wipe.id, proof, "", na)
        val seenByDad = db.taskDao().getTaskById(wipe.id)!!            // Bố mở màn duyệt bài của Na

        repository.rejectTask(seenByDad, parent)                        // Mẹ từ chối trước -> trả về cả nhà
        repository.submitTaskProof(wipe.id, proof, "", dad)             // Bố tự làm và nộp

        // Yêu cầu duyệt cũ của Bố không được áp lên lượt nộp mới của chính Bố
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(seenByDad, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(wipe.id))
        assertEquals(0, points(dad.id))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(seenByDad, parent))
        assertEquals(5, points(dad.id))
    }

    @Test
    fun toggleReaction_ignoresDeletedOrOtherFamilyTask() = runBlocking<Unit> {
        repository.toggleReaction("khong-ton-tai", bin, "👍")
        assertNull(db.taskReactionDao().getUserReaction("khong-ton-tai", bin.id, "👍"))

        repository.toggleReaction(taskForBin.id, otherParent, "👍")
        assertNull(db.taskReactionDao().getUserReaction(taskForBin.id, otherParent.id, "👍"))
    }

    @Test
    fun createTask_enforcesCreatorIdAndAssigneeNameFromDb() = runBlocking<Unit> {
        val spoofed = Task(
            id = "t-spoof",
            title = "Quét sân",
            familyId = family,
            assignedToUserId = bin.id,
            assignedToUserName = "Tên giả",
            createdByUserId = bin.id, // truyền sai người tạo
            points = 10
        )
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(spoofed, parent))
        val saved = db.taskDao().getTaskById("t-spoof")!!
        assertEquals(parent.id, saved.createdByUserId)
        assertEquals(bin.name, saved.assignedToUserName)
    }

    @Test
    fun review_refusesStaleScreenWhenNewProofSubmitted() = runBlocking<Unit> {
        val wipe = Task(id = "t-stale", title = "Lau kính", familyId = family, createdByUserId = parent.id, points = 10)
        repository.createTask(wipe, parent)
        repository.submitTaskProof(wipe.id, "file:///data/na_photo.jpg", "", na)
        val staleScreen = db.taskDao().getTaskById(wipe.id)!!

        // Mẹ từ chối bài của Na, sau đó Bin nộp bộ ảnh mới khác
        repository.rejectTask(staleScreen, parent)
        repository.submitTaskProof(wipe.id, "file:///data/bin_new_photo.jpg", "", bin)

        // Bố vẫn đang nhìn màn hình cũ của Na (staleScreen) -> chặn duyệt nhầm ảnh chưa xem
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(staleScreen, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(wipe.id))
        assertEquals(0, points(bin.id))

        // Khi tải lại màn hình mới nhất của Bin -> Bố duyệt thành công
        val freshScreen = db.taskDao().getTaskById(wipe.id)!!
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(freshScreen, dad))
        assertEquals(10, points(bin.id))
    }
}
