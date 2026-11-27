package com.family.taskmanager

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import com.family.taskmanager.ui.statistics.earningsByTask
import com.family.taskmanager.ui.statistics.earnedPointEntries
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FlexibleAssignmentTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var families: FamilyRepository
    private val mom = User("mom", "Mom", "m@test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@test", familyId = "f")
    private val bin = User("bin", "Bin", "b@test", familyId = "f")
    private val template = Task(
        id = "flex", title = "Read", familyId = "f", createdByUserId = mom.id,
        points = 10, deadlineTimestamp = System.currentTimeMillis() + 7 * 86_400_000L,
        taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3
    )

    @Before fun setup() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup("f", "Family", "123456", mom.id))
        listOf(mom, na, bin).forEach { db.userDao().insertUser(it) }
        val prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        repository = TaskRepository(
            context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(),
            PushNotificationService(context, prefs)
        )
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, prefs))
        families = FamilyRepository(db, db.familyDao(), db.userDao(), RewardRepository(context, db, db.rewardDao(), notifications))
    }

    @After fun cleanup() { db.close() }

    private suspend fun fresh(id: String = template.id) = requireNotNull(db.taskDao().getTaskById(id))

    private suspend fun submitAndApprove(worker: User): Task {
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(template.id, "proof.jpg", "", worker))
        val pending = fresh()
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(pending, mom))
        return pending
    }

    @Test fun eachApprovalRecordsActualPoints_notCurrentRewardTimesCount() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(template.copy(assignedToUserId = na.id), mom))
        val firstSubmission = submitAndApprove(na)
        assertEquals(1, fresh().completedCount)
        assertEquals(TaskStatus.TODO, fresh().status)
        assertEquals(10, db.taskApprovalDao().getByFamily("f").sumOf { it.points })
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(firstSubmission, mom))
        assertEquals(TaskActionResult.SUCCESS, repository.updateTask(
            taskId = template.id, actor = mom, title = "Read", description = "",
            assigneeId = na.id, priority = TaskPriority.MEDIUM, points = 20,
            deadlineTimestamp = template.deadlineTimestamp
        ))
        submitAndApprove(na)
        assertEquals(30, db.taskApprovalDao().getByFamily("f").sumOf { it.points })
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(template.id, "round-3.jpg", "", na))
        val rejected = fresh()
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(rejected, mom))
        assertEquals(2, db.taskApprovalDao().getByFamily("f").size)
        assertEquals(2, fresh().completedCount)
        val lastSubmission = submitAndApprove(na)
        assertEquals(TaskStatus.COMPLETED, fresh().status)
        assertEquals(3, fresh().completedCount)
        assertEquals(50, db.userDao().getUserByIdSync(na.id)!!.points)
        assertEquals(listOf(10, 20, 20), db.taskApprovalDao().getByFamily("f").sortedBy { it.submissionRevision }.map { it.points })
        assertEquals(TaskActionResult.INVALID_STATE, repository.approveTask(lastSubmission, mom))
        assertEquals(50, db.taskApprovalDao().getByFamily("f").sumOf { it.points })
    }

    @Test fun cancellingIncompleteGoal_keepsAlreadyEarnedPointsInHistory() = runBlocking<Unit> {
        repository.createTask(template.copy(assignedToUserId = na.id), mom)
        submitAndApprove(na)
        assertTrue(families.leaveFamily(na.id) is FamilyResult.Success)
        assertEquals(TaskActionResult.SUCCESS, repository.cancelTask(template.id, mom))
        assertNull(db.taskDao().getTaskById(template.id))
        assertEquals(10, db.userDao().getUserByIdSync(na.id)!!.points)
        val awards = db.taskApprovalDao().getByFamily("f")
        assertEquals(10, awards.single().points)
        val members = db.userDao().getUsersByFamilySync("f")
        assertFalse(members.any { it.id == na.id })
        val retained = earnedPointEntries(members, awards).single { it.userId == na.id }
        assertEquals("Na", retained.name)
        assertEquals(10, retained.pointsEarned)
        assertFalse(retained.isCurrentMember)
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(template.copy(assignedToUserId = bin.id), mom))
        assertNull(db.taskDao().getTaskById(template.id))
    }

    @Test fun departureThenClaim_resetsProgressAndKeepsAwardsWithOriginalWorkers() = runBlocking<Unit> {
        repository.createTask(template.copy(assignedToUserId = na.id), mom)
        submitAndApprove(na)
        assertTrue(families.leaveFamily(na.id) is FamilyResult.Success)
        assertEquals("", fresh().assignedToUserId)
        assertEquals(0, fresh().completedCount)
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(template.id, bin))
        assertFalse(fresh().openToAll)
        submitAndApprove(bin)
        assertEquals(bin.id, fresh().assignedToUserId)
        assertEquals(1, fresh().completedCount)
        assertEquals(3, fresh().targetCount)
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(template.id, na))
        val points = db.taskApprovalDao().getByFamily("f").groupBy { it.userId }.mapValues { (_, rounds) -> rounds.sumOf { it.points } }
        assertEquals(mapOf(na.id to 10, bin.id to 10), points)
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(template.id, bin))
        assertEquals(TaskActionResult.SUCCESS, repository.resetTaskToTodo(template.id, bin))
        assertEquals(bin.id, fresh().assignedToUserId)
        assertEquals(1, fresh().completedCount)
        repeat(2) { submitAndApprove(bin) }
        assertEquals(TaskStatus.COMPLETED, fresh().status)
        assertEquals(2L, fresh().reminderGeneration) // claim changes owner; later approvals keep that identity
        val history = earningsByTask(db.taskApprovalDao().getByFamily("f")).getValue(template.id)
        assertEquals(10, history.single { it.userId == na.id }.points)
        assertEquals("Na", history.single { it.userId == na.id }.name)
        assertEquals(30, history.single { it.userId == bin.id }.points)
        assertEquals("Bin", history.single { it.userId == bin.id }.name)
    }

    @Test fun directSubmissionAfterDeparture_alsoBindsGoalOnRejection() = runBlocking<Unit> {
        repository.createTask(template.copy(assignedToUserId = na.id), mom)
        submitAndApprove(na)
        families.leaveFamily(na.id)
        assertEquals(TaskActionResult.SUCCESS, repository.submitTaskProof(template.id, "bin.jpg", "", bin))
        assertFalse(fresh().openToAll)
        assertEquals(TaskActionResult.SUCCESS, repository.rejectTask(fresh(), mom))
        assertEquals(bin.id, fresh().assignedToUserId)
        assertEquals(0, fresh().completedCount)
        assertFalse(fresh().openToAll)
    }

    @Test fun failedApprovalReceipt_rollsBackProgressBalanceAndAllowsOneRetry() = runBlocking<Unit> {
        repository.createTask(template.copy(assignedToUserId = na.id), mom)
        repository.submitTaskProof(template.id, "proof.jpg", "", na)
        val pending = fresh()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_award BEFORE INSERT ON task_approvals BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        try {
            repository.approveTask(pending, mom)
            fail("Expected persistence failure")
        } catch (_: SQLiteException) { }
        assertEquals(pending, fresh())
        assertEquals(0, db.userDao().getUserByIdSync(na.id)!!.points)
        assertTrue(db.taskApprovalDao().getByFamily("f").isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_award")
        assertEquals(TaskActionResult.SUCCESS, repository.approveTask(pending, mom))
        assertEquals(10, db.userDao().getUserByIdSync(na.id)!!.points)
        assertEquals(1, db.taskApprovalDao().getByFamily("f").size)
    }

    @Test fun dissolvingOneFamily_cleansOnlyItsOwnApprovalHistory() = runBlocking<Unit> {
        repository.createTask(template.copy(assignedToUserId = na.id), mom)
        submitAndApprove(na)
        val other = mom.copy(id = "other-mom", familyId = "g")
        val otherChild = bin.copy(id = "other-child", familyId = "g")
        db.familyDao().insertFamily(FamilyGroup("g", "Other family", "654321", other.id))
        listOf(other, otherChild).forEach { db.userDao().insertUser(it) }
        val otherTask = template.copy(id = "other-task", familyId = "g", createdByUserId = other.id,
            assignedToUserId = otherChild.id, taskMode = TaskMode.FIXED, targetCount = 1)
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(otherTask, other))
        repository.submitTaskProof(otherTask.id, "other.jpg", "", otherChild)
        repository.approveTask(db.taskDao().getTaskById(otherTask.id)!!, other)
        families.leaveFamily(na.id)
        families.leaveFamily(bin.id)
        assertTrue(families.leaveFamily(mom.id) is FamilyResult.Success)
        assertTrue(db.taskApprovalDao().getByFamily("f").isEmpty())
        assertEquals(10, db.taskApprovalDao().getByFamily("g").single().points)
        assertNotNull(db.familyDao().getFamilyByIdOnce("g"))
    }

    @Test fun flexibleSharedCreationIsRejectedButFixedSharedWorkIsPreserved() = runBlocking<Unit> {
        assertEquals(TaskActionResult.INVALID_INPUT, repository.createTask(template, mom))
        assertTrue(db.taskDao().getTasksByFamily("f").first().isEmpty())
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(template.copy(taskMode = TaskMode.FIXED, targetCount = 1), mom))
        assertTrue(db.taskDao().getTaskById(template.id)!!.openToAll)
    }

    @Test fun eachRecipientStartsAtZeroWithIndependentProgressAndProof() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.createTaskForEach(
            template.copy(completedCount = 2, proofImageUrl = "stale.jpg", proofNote = "old", submissionRevision = 5),
            listOf(na.id, bin.id), mom
        ))
        val tasks = db.taskDao().getTasksByFamily("f").first()
        assertEquals(setOf(na.id, bin.id), tasks.map { it.assignedToUserId }.toSet())
        assertEquals(2, tasks.map { it.id }.distinct().size)
        tasks.forEach {
            assertFalse(it.openToAll)
            assertEquals(3, it.targetCount)
            assertEquals(0, it.completedCount)
            assertEquals("", it.proofImageUrl)
            assertEquals(0, it.submissionRevision)
        }
        val naTask = tasks.single { it.assignedToUserId == na.id }
        assertEquals(TaskActionResult.SUCCESS, repository.startTask(naTask.id, na))
        assertEquals(TaskActionResult.NOT_ALLOWED, repository.startTask(naTask.id, bin))
        assertEquals(TaskStatus.TODO, db.taskDao().getTaskById(tasks.single { it.assignedToUserId == bin.id }.id)!!.status)
    }

    @Test fun dedicatedFlexibleGoalCannotBeEditedIntoSharedProgress() = runBlocking<Unit> {
        assertEquals(TaskActionResult.SUCCESS, repository.createTask(template.copy(assignedToUserId = na.id), mom))
        assertEquals(TaskActionResult.INVALID_INPUT, repository.updateTask(
            taskId = template.id, actor = mom, title = template.title, description = "",
            priority = TaskPriority.MEDIUM, points = 10, deadlineTimestamp = template.deadlineTimestamp,
            assigneeId = null, openToAll = true
        ))
        val stored = db.taskDao().getTaskById(template.id)!!
        assertEquals(na.id, stored.assignedToUserId)
        assertFalse(stored.openToAll)
    }
}
