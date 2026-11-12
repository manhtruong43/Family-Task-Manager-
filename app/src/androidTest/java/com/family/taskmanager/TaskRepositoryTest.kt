package com.family.taskmanager

import android.database.sqlite.SQLiteException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private suspend fun fresh(taskId: String) = db.taskDao().getTaskById(taskId)!!
    private suspend fun status(taskId: String) = fresh(taskId).status
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

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(taskForBin.id), parent))
        assertEquals(TaskStatus.COMPLETED, status(taskForBin.id))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun approveTwice_addsPointsOnlyOnce() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        val submitted = fresh(taskForBin.id)

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(submitted, parent))
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(submitted, parent))
        assertEquals(TaskActionResult.INVALID_STATE, repository.rejectTask(submitted, parent))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun onlyParentOfSameFamily_canApprove() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        val submitted = fresh(taskForBin.id)

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(submitted, bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(submitted, otherParent))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(taskForBin.id))
        assertEquals(0, points(bin.id))
    }

    @Test
    fun reject_thenRedo_thenApprove() = runBlocking<Unit> {
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(fresh(taskForBin.id), parent))
        assertEquals(TaskStatus.REJECTED, status(taskForBin.id))
        assertEquals(0, points(bin.id))

        assertEquals(TaskActionResult.SUCCESS, repository.startTask(taskForBin.id, bin))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "làm lại", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(taskForBin.id), parent))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun everyoneTask_submitterBecomesAssignee_andGetsPoints() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForEveryone.id, proof, "", na))
        assertEquals(na.id, fresh(taskForEveryone.id).assignedToUserId)

        // Bin không nộp chồng lên được nữa
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForEveryone.id, proof, "", bin))

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(taskForEveryone.id), parent))
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
        val submittedCook = fresh(cook.id)

        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(submittedCook, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(cook.id))

        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(submittedCook, parent))
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

        val edited = fresh(taskForBin.id)
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
        assertEquals(bin.id, fresh(taskForBin.id).assignedToUserId)
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
        repository.approveTask(fresh(taskForEveryone.id), parent)
        assertEquals(TaskActionResult.INVALID_STATE, repository.cancelTask(taskForEveryone.id, parent))
        assertEquals(5, points(na.id))
    }

    // ─── Việc chung / Mỗi người đều làm ───

    @Test
    fun everyoneTask_otherParentCanClaim_butNotTheAssigner() = runBlocking<Unit> {
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(taskForEveryone.id, proof, "", parent))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForEveryone.id, proof, "", dad))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(taskForEveryone.id), parent))
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
        repository.rejectTask(fresh(binRoom.id), parent)
        repository.approveTask(fresh(naRoom.id), parent)
        assertEquals(20, points(na.id))
        assertEquals(0, points(bin.id))
        assertEquals(bin.id, fresh(binRoom.id).assignedToUserId)
    }

    @Test
    fun openTask_rejected_goesBackToWholeFamily() = runBlocking<Unit> {
        val dishes = Task(id = "t-dishes", title = "Rửa bát", familyId = family, createdByUserId = parent.id, points = 15)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(dishes, parent))
        assertTrue(fresh(dishes.id).openToAll)

        repository.submitTaskProof(dishes.id, proof, "", na)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(fresh(dishes.id), parent))

        val released = fresh(dishes.id)
        assertEquals(TaskStatus.REJECTED, released.status)
        assertEquals("", released.assignedToUserId)          // trả lại cả nhà

        // Lần này Bin làm lại và nhận điểm
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(dishes.id, proof, "làm lại", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(dishes.id), parent))
        assertEquals(15, points(bin.id))
        assertEquals(0, points(na.id))

        // Việc giao đích danh bị từ chối thì vẫn thuộc người đó
        repository.submitTaskProof(taskForBin.id, proof, "", bin)
        repository.rejectTask(fresh(taskForBin.id), parent)
        assertEquals(bin.id, fresh(taskForBin.id).assignedToUserId)
        assertFalse(fresh(taskForBin.id).openToAll)
    }

    @Test
    fun review_isCheckedAgainstCurrentSubmission_notTheOldScreen() = runBlocking<Unit> {
        val wipe = Task(id = "t-wipe", title = "Lau bàn", familyId = family, createdByUserId = parent.id, points = 5)
        repository.createTask(wipe, parent)
        repository.submitTaskProof(wipe.id, proof, "", na)
        val seenByDad = fresh(wipe.id)                                  // Bố mở màn duyệt bài của Na

        repository.rejectTask(seenByDad, parent)                        // Mẹ từ chối trước -> trả về cả nhà
        repository.submitTaskProof(wipe.id, proof, "", dad)             // Bố tự làm và nộp

        // Yêu cầu duyệt cũ của Bố không được áp lên lượt nộp mới của chính Bố
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.approveTask(seenByDad, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(wipe.id))
        assertEquals(0, points(dad.id))

        // Mẹ cũng không thể duyệt bằng màn hình cũ của Na, phải tải lại màn hình lượt nộp mới của Bố
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(seenByDad, parent))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(wipe.id), parent))
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
        val saved = fresh("t-spoof")
        assertEquals(parent.id, saved.createdByUserId)
        assertEquals(bin.name, saved.assignedToUserName)
    }

    @Test
    fun review_refusesStaleScreenWhenNewProofSubmitted_evenWithSamePhotoPath() = runBlocking<Unit> {
        // Bản chụp trước khi nộp (submissionRevision == 0) không được dùng để duyệt lượt mới
        val preSubmitSnapshot = fresh(taskForBin.id)
        assertEquals(0, preSubmitSnapshot.submissionRevision)
        repository.submitTaskProof(taskForBin.id, proof, "lần 1", bin)
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(preSubmitSnapshot, parent))
        assertEquals(TaskActionResult.INVALID_STATE, repository.rejectTask(preSubmitSnapshot, parent))

        // Lượt nộp 1 (submissionRevision == 1)
        val firstSubmissionScreen = fresh(taskForBin.id)
        assertEquals(1, firstSubmissionScreen.submissionRevision)

        // Mẹ từ chối, Bin nộp lại dùng CÙNG đường dẫn ảnh (submissionRevision tăng lên 2)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(firstSubmissionScreen, parent))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "lần 2 cùng đường dẫn ảnh", bin))
        val secondSubmissionScreen = fresh(taskForBin.id)
        assertEquals(2, secondSubmissionScreen.submissionRevision)

        // Bố vẫn nhìn màn hình từ lượt nộp 1 -> bị chặn dù đường dẫn ảnh giống hệt
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(firstSubmissionScreen, dad))
        assertEquals(TaskActionResult.INVALID_STATE, repository.rejectTask(firstSubmissionScreen, dad))
        assertEquals(TaskStatus.PENDING_APPROVAL, status(taskForBin.id))
        assertEquals(0, points(bin.id))

        // Khi tải lại màn hình lượt nộp 2 -> duyệt thành công
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(secondSubmissionScreen, dad))
        assertEquals(10, points(bin.id))
    }

    @Test
    fun everyoneTask_startTask_claimsImmediately_andResetReleasesBackToFamily() = runBlocking<Unit> {
        val trash = Task(id = "t-trash", title = "Đổ rác", familyId = family, createdByUserId = parent.id, points = 10)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(trash, parent))

        // Na bấm "Bắt đầu làm" -> nhận việc ngay lập tức
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(trash.id, na))
        val claimedByNa = fresh(trash.id)
        assertEquals(TaskStatus.IN_PROGRESS, claimedByNa.status)
        assertEquals(na.id, claimedByNa.assignedToUserId)
        assertEquals(na.name, claimedByNa.assignedToUserName)
        assertTrue(claimedByNa.openToAll)

        // Người thứ hai (Bin hoặc Bố) bấm "Bắt đầu", "Nộp" hay "Đặt lại" đều bị từ chối
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(trash.id, bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(trash.id, dad))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(trash.id, proof, "", bin))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.resetTaskToTodo(trash.id, bin))

        // Nếu Na bận và bấm đặt lại về TODO -> việc được trả lại cho cả nhà
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(trash.id, na))
        val released = fresh(trash.id)
        assertEquals(TaskStatus.TODO, released.status)
        assertEquals("", released.assignedToUserId)
        assertEquals("", released.assignedToUserName)
        assertTrue(released.openToAll)

        // Bây giờ Bin có thể bấm "Bắt đầu làm", nộp minh chứng và nhận điểm
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(trash.id, bin))
        assertEquals(bin.id, fresh(trash.id).assignedToUserId)
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(trash.id, proof, "", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(trash.id), parent))
        assertEquals(10, points(bin.id))
        assertEquals(0, points(na.id))
    }

    // ─── Các kiểm thử bổ sung sau PR #18 (Mục 1, 2, 3) ───

    @Test
    fun editContent_ofClaimedSharedTask_preservesOpenToAll_soResetAndRejectStillReleaseToFamily() = runBlocking<Unit> {
        // 1. Chung -> Na nhận -> Mẹ chỉ sửa tiêu đề/mô tả/điểm/hạn -> Na trả việc -> Bin nhận được
        val futureDeadline = System.currentTimeMillis() + 3_600_000L
        val shared = Task(id = "t-shared-edit", title = "Quét nhà", familyId = family, createdByUserId = parent.id, points = 10)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(shared, parent))
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(shared.id, na))
        assertTrue(fresh(shared.id).openToAll)

        // Mẹ mở sửa việc, chỉ đổi tiêu đề / mô tả / điểm / hạn chót (assigneeId vẫn là na.id đang giữ việc)
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.updateTask(
                taskId = shared.id,
                title = "Quét nhà sạch",
                description = "Quét cả phòng khách",
                assigneeId = na.id,
                priority = TaskPriority.HIGH,
                points = 20,
                deadlineTimestamp = futureDeadline,
                actor = parent
            )
        )
        val afterContentEdit = fresh(shared.id)
        assertEquals("Quét nhà sạch", afterContentEdit.title)
        assertEquals(na.id, afterContentEdit.assignedToUserId)
        assertEquals(TaskStatus.IN_PROGRESS, afterContentEdit.status)
        assertTrue(afterContentEdit.openToAll) // Không bị mất cờ việc chung!

        // Na đặt lại về TODO -> việc trở về cả nhà, Bin nhận được
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(shared.id, na))
        assertEquals("", fresh(shared.id).assignedToUserId)
        assertTrue(fresh(shared.id).openToAll)
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(shared.id, bin))
        assertEquals(bin.id, fresh(shared.id).assignedToUserId)

        // 2. Chung (đang do Bin giữ) -> Mẹ sửa nội dung -> Bin nộp -> Mẹ từ chối -> việc trở về cả nhà
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.updateTask(
                taskId = shared.id,
                title = "Quét nhà kỹ hơn",
                description = "Mô tả mới",
                assigneeId = bin.id,
                priority = TaskPriority.HIGH,
                points = 20,
                deadlineTimestamp = futureDeadline,
                actor = parent
            )
        )
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(shared.id, proof, "đã quét", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(fresh(shared.id), parent))
        val afterReject = fresh(shared.id)
        assertEquals(TaskStatus.REJECTED, afterReject.status)
        assertEquals("", afterReject.assignedToUserId)
        assertTrue(afterReject.openToAll)

        // 3. Giao riêng Na -> sửa tiêu đề -> Na đặt lại -> vẫn giao riêng Na, không mở cho Bin
        val dedicatedForNa = Task(
            id = "t-dedicated-na", title = "Học bài", familyId = family,
            assignedToUserId = na.id, assignedToUserName = na.name, createdByUserId = parent.id, points = 15
        )
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(dedicatedForNa, parent))
        assertFalse(fresh(dedicatedForNa.id).openToAll)
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(dedicatedForNa.id, na))
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.updateTask(
                taskId = dedicatedForNa.id,
                title = "Học bài Toán",
                description = "",
                assigneeId = na.id,
                priority = TaskPriority.MEDIUM,
                points = 15,
                deadlineTimestamp = 0L,
                actor = parent
            )
        )
        assertFalse(fresh(dedicatedForNa.id).openToAll)
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(dedicatedForNa.id, na))
        assertEquals(na.id, fresh(dedicatedForNa.id).assignedToUserId)
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(dedicatedForNa.id, bin))

        // 4. Chủ động chuyển việc chung đang do Na giữ thành việc riêng của chính Na (openToAll = false)
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(shared.id, na))
        assertTrue(fresh(shared.id).openToAll)
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.updateTask(
                taskId = shared.id,
                title = "Quét nhà giao riêng Na",
                description = "",
                assigneeId = na.id,
                priority = TaskPriority.HIGH,
                points = 20,
                deadlineTimestamp = 0L,
                actor = parent,
                openToAll = false
            )
        )
        assertFalse(fresh(shared.id).openToAll)
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(shared.id, na))
        assertEquals(na.id, fresh(shared.id).assignedToUserId)
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(shared.id, bin))
    }

    @Test
    fun pushAndInboxErrorHandling_logsExpectedAndUnexpectedPostCommitErrors_andRollsBackOnInboxFailure() = runBlocking<Unit> {
        var expectedLogged: Throwable? = null
        var unexpectedLogged: Throwable? = null
        repository.onExpectedPushFailure = { _, err -> expectedLogged = err }
        repository.onUnexpectedPushFailure = { _, err -> unexpectedLogged = err }

        // 1. Lỗi gửi dự kiến sau commit (SecurityException) -> nhiệm vụ + hộp thư vẫn lưu đúng, có ghi nhận expected
        val securityErr = SecurityException("POST_NOTIFICATIONS revoked")
        repository.systemPushSender = { _, _ -> throw securityErr }
        val t1 = taskForBin.copy(id = "t-push-sec", title = "Việc 1")
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(t1, parent))
        assertSame(securityErr, expectedLogged)
        assertNull(unexpectedLogged)
        assertEquals("Việc 1", fresh(t1.id).title)
        assertTrue(db.notificationDao().getNotificationsByUser(bin.id).first().isNotEmpty())

        // 2. Lỗi bất ngờ sau commit (IllegalStateException) -> nhiệm vụ + hộp thư đã commit vẫn trả về SUCCESS, ghi nhận unexpected
        expectedLogged = null
        val unexpectedErr = IllegalStateException("Channel misconfigured")
        repository.systemPushSender = { _, _ -> throw unexpectedErr }
        val t2 = taskForBin.copy(id = "t-push-unexpected", title = "Việc 2")
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(t2, parent))
        assertNull(expectedLogged)
        assertSame(unexpectedErr, unexpectedLogged)
        assertEquals("Việc 2", fresh(t2.id).title)

        // 3. CancellationException không bao giờ bị nuốt
        val cancelErr = CancellationException("Cancelled")
        repository.systemPushSender = { _, _ -> throw cancelErr }
        val t3 = taskForBin.copy(id = "t-push-cancel", title = "Việc 3")
        try {
            repository.createTask(t3, parent)
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancelErr, actual)
        }

        // 4. Lỗi ghi inbox trong transaction -> rollback toàn bộ (nhiệm vụ không chuyển trạng thái, điểm không cộng)
        var pushCalled = false
        repository.systemPushSender = { _, _ -> pushCalled = true }
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(taskForBin.id, proof, "", bin))
        val submitted = fresh(taskForBin.id)
        pushCalled = false

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_task_inbox BEFORE INSERT ON notifications " +
                "BEGIN SELECT RAISE(ABORT, 'injected inbox failure'); END"
        )
        try {
            repository.approveTask(submitted, parent)
            fail("Inbox write failure inside transaction must throw and roll back")
        } catch (_: SQLiteException) {
            assertEquals(TaskStatus.PENDING_APPROVAL, status(taskForBin.id))
            assertEquals(0, points(bin.id))
            assertFalse(pushCalled)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_task_inbox")
        }
    }

    @Test
    fun soleRemainingParent_cannotClaimOrSubmitSharedTaskWithoutReviewer_whileChildrenStillCan() = runBlocking<Unit> {
        val shared = Task(id = "t-sole-parent", title = "Lau cầu thang", familyId = family, createdByUserId = parent.id, points = 15)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(shared, parent))

        // Khi Mẹ vẫn trong gia đình, Bố có Mẹ duyệt nên nhận và đặt lại việc chung bình thường
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(shared.id, dad))
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(shared.id, dad))

        // Mẹ rời gia đình -> Bố là phụ huynh duy nhất còn lại
        db.userDao().updateUser(parent.copy(familyId = ""))

        // Bố không được nhận hoặc nộp việc chung nữa (vì nếu Bố làm sẽ không còn ai đủ quyền duyệt)
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(shared.id, dad))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.submitTaskProof(shared.id, proof, "", dad))

        // Các con vẫn nhận, nộp và được Bố duyệt bình thường
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(shared.id, bin))
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(shared.id, proof, "", bin))
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(fresh(shared.id), dad))
        assertEquals(15, points(bin.id))
    }

    @Test
    fun deadlineValidation_rejectsPastOnCreateAndChangedEdit_allowsFutureAndUnchangedOverdueEdit() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val past = now - 60_000L
        val anotherPast = now - 120_000L
        val future = now + 3_600_000L

        // 1. Tạo việc với hạn ở quá khứ -> bị từ chối (INVALID_INPUT)
        val pastSingle = taskForBin.copy(id = "t-past-single", deadlineTimestamp = past)
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(pastSingle, parent))
        assertNull(db.taskDao().getTaskById("t-past-single"))

        val pastTemplate = taskForEveryone.copy(id = "t-past-each", title = "Việc quá khứ", deadlineTimestamp = past)
        assertEquals(
            TaskActionResult.INVALID_INPUT,
            repository.createTaskForEach(pastTemplate, listOf(bin.id, na.id), parent)
        )

        // 2. Tạo việc với hạn ở tương lai -> thành công (SUCCESS)
        val futureSingle = taskForBin.copy(id = "t-future-single", deadlineTimestamp = future)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(futureSingle, parent))
        assertEquals(future, fresh("t-future-single").deadlineTimestamp)

        val futureTemplate = taskForEveryone.copy(id = "t-future-each", title = "Việc tương lai", deadlineTimestamp = future)
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.createTaskForEach(futureTemplate, listOf(bin.id, na.id), parent)
        )

        // 3. Sửa tên một việc đã quá hạn mà giữ nguyên hạn -> vẫn lưu được (SUCCESS)
        val alreadyOverdue = taskForBin.copy(id = "t-already-overdue", title = "Việc cũ quá hạn", deadlineTimestamp = past)
        db.taskDao().insertTask(alreadyOverdue)
        assertEquals(
            TaskActionResult.SUCCESS,
            repository.updateTask(
                taskId = alreadyOverdue.id,
                title = "Việc cũ quá hạn (đổi tên)",
                description = "Giữ nguyên hạn cũ",
                assigneeId = bin.id,
                priority = TaskPriority.MEDIUM,
                points = 10,
                deadlineTimestamp = past, // giữ nguyên hạn cũ
                actor = parent
            )
        )
        assertEquals("Việc cũ quá hạn (đổi tên)", fresh(alreadyOverdue.id).title)

        // 4. Sửa sang hạn khác ở quá khứ -> bị từ chối (INVALID_INPUT)
        assertEquals(
            TaskActionResult.INVALID_INPUT,
            repository.updateTask(
                taskId = alreadyOverdue.id,
                title = "Đổi sang hạn quá khứ khác",
                description = "",
                assigneeId = bin.id,
                priority = TaskPriority.MEDIUM,
                points = 10,
                deadlineTimestamp = anotherPast,
                actor = parent
            )
        )
        assertEquals(
            TaskActionResult.INVALID_INPUT,
            repository.updateTask(
                taskId = futureSingle.id,
                title = "Đổi từ tương lai về quá khứ",
                description = "",
                assigneeId = bin.id,
                priority = TaskPriority.MEDIUM,
                points = 10,
                deadlineTimestamp = past,
                actor = parent
            )
        )
    }
}
