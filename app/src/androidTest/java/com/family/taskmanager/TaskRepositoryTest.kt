package com.family.taskmanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskActionResult
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử nghiệp vụ nhiệm vụ trên database thật (Room trong bộ nhớ).
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'TaskRepositoryTest'.
 */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository

    private val family = "family-1"
    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN, familyId = family, isEmailVerified = true)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER, familyId = family, isEmailVerified = true)
    private val na = User(id = "na", name = "Na", email = "na@x.com", role = UserRole.MEMBER, familyId = family, isEmailVerified = true)
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
        listOf(parent, bin, na, otherParent).forEach { db.userDao().insertUser(it) }
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
}
