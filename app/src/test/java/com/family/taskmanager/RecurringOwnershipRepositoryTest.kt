package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecurringOwnershipRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var schedules: RecurringScheduleRepository
    private lateinit var families: FamilyRepository
    private val day = LocalDate.of(2026, 11, 12)
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val other = User("other", "Phụ huynh khác", "o@example.test", role = UserRole.ADMIN)
    private val each = RecurringSchedule(
        id = "each", familyId = "f", title = "Đọc sách", createdByUserId = mom.id,
        isActive = false, assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    private fun input(points: Int = 20) = RecurringScheduleInput(
        title = "Đọc sách", description = "", assigneeId = "", priority = TaskPriority.MEDIUM,
        points = points, repeatType = RepeatType.DAILY, daysOfWeekMask = 0, dueMinuteOfDay = 1200,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    @Before fun setup() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup(id = "f", name = "Nhà thử", inviteCode = "123456", adminId = mom.id))
        listOf(mom, dad, na).forEach { db.userDao().insertUser(it) }
        db.recurringScheduleDao().upsert(each)
        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        schedules = RecurringScheduleRepository(context, db, notifications)
        families = FamilyRepository(db, db.familyDao(), db.userDao(), RewardRepository(context, db, db.rewardDao(), notifications))
        schedules.clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @After fun cleanup() { db.close() }

    private suspend fun members() = db.userDao().getUsersByFamilySync("f")
    private suspend fun current() = db.recurringScheduleDao().getById(each.id)!!

    @Test fun departureThenParentJoin_keepsSafeManagerAndExcludesTheirFutureWork() = runBlocking<Unit> {
        assertTrue(families.leaveFamily(mom.id) is FamilyResult.Success)
        assertEquals(dad.id, current().createdByUserId)
        val arriving = other.copy(id = "aaa-new") // ID nhỏ hơn Bố không được giành lại lịch đã chuyển quản lý.
        db.userDao().insertUser(arriving)
        assertTrue(families.joinFamilyByCode("123456", arriving) is FamilyResult.Success)
        val joined = db.userDao().getUserByIdSync(arriving.id)!!
        assertEquals(dad.id, current().createdByUserId)
        assertTrue(current().canBeManagedBy(dad, members()))
        assertFalse(current().canBeManagedBy(joined, members()))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, schedules.saveSchedule(joined, each.id, input(99)))
        assertEquals(RecurringResult.RESUMED, schedules.setActive(dad, each.id, true))
        assertEquals(setOf(na.id, joined.id), db.taskDao().getTasksByFamily("f").first().map { it.assignedToUserId }.toSet())
        assertEquals(RecurringResult.SAVED, schedules.saveSchedule(dad, each.id, input(50)))
        schedules.clock = Clock.fixed(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(2, schedules.generateDueTasks())
        val next = db.taskDao().getTasksByFamily("f").first().filter { it.points == 50 }
        assertEquals(2, next.size)
        assertTrue(next.none { it.assignedToUserId == dad.id })
        assertTrue(next.all { it.createdByUserId == dad.id })

        val departedMom = db.userDao().getUserByIdSync(mom.id)!!
        assertTrue(families.joinFamilyByCode("123456", departedMom) is FamilyResult.Success)
        assertEquals(dad.id, current().createdByUserId)
        assertFalse(current().canBeManagedBy(db.userDao().getUserByIdSync(mom.id)!!, members()))
    }

    @Test fun lastReviewer_cannotLeaveWhileRemainingParentHasPendingWork() = runBlocking<Unit> {
        val pending = Task(
            id = "dad-pending", title = "Đang chờ duyệt", familyId = "f", assignedToUserId = dad.id,
            createdByUserId = mom.id, status = TaskStatus.PENDING_APPROVAL, proofImageUrl = "pending.jpg"
        )
        db.taskDao().insertTask(pending)
        assertEquals(FamilyResult.Failure(FamilyError.ADMIN_MUST_STAY), families.leaveFamily(mom.id))
        assertEquals("f", db.userDao().getUserByIdSync(mom.id)!!.familyId)
        assertEquals(mom.id, current().createdByUserId)
        assertEquals(pending, db.taskDao().getTaskById(pending.id))
    }

    @Test fun departureWithTwoOtherParents_keepsManagerAndExistingSubmissions() = runBlocking<Unit> {
        db.userDao().insertUser(other.copy(familyId = "f"))
        val old = Task(
            id = "existing-dad", title = "Việc đã nộp", familyId = "f", assignedToUserId = dad.id,
            createdByUserId = mom.id, status = TaskStatus.PENDING_APPROVAL,
            points = 30, proofImageUrl = "old.jpg", submissionRevision = 1
        )
        db.taskDao().insertTask(old)
        assertTrue(families.leaveFamily(mom.id) is FamilyResult.Success)
        assertEquals(dad.id, current().createdByUserId)
        assertTrue(current().canBeManagedBy(dad, members()))
        assertFalse(current().canBeManagedBy(other.copy(familyId = "f"), members()))
        assertEquals(RecurringResult.SAVED, schedules.saveSchedule(dad, each.id, input(90)))
        assertEquals(old, db.taskDao().getTaskById(old.id))
        assertFalse(TaskPermissions.canReview(dad, old))
        assertTrue(TaskPermissions.canReview(other.copy(familyId = "f"), old))
    }

    @Test fun existingOrphans_recoverForActiveAndPausedSchedules_withoutResettingMarkers() = runBlocking<Unit> {
        db.userDao().updateUser(mom.copy(familyId = ""))
        db.userDao().insertUser(other.copy(familyId = "f"))
        db.recurringScheduleDao().upsert(each.copy(isActive = true, lastGeneratedEpochDay = day.toEpochDay()))
        val paused = each.copy(id = "paused")
        db.recurringScheduleDao().upsert(paused)
        assertTrue(current().canBeManagedBy(dad, members()))
        assertEquals(0, schedules.generateDueTasks())
        assertEquals(dad.id, current().createdByUserId)
        assertEquals(day.toEpochDay(), current().lastGeneratedEpochDay)
        assertEquals(RecurringResult.PAUSED, schedules.setActive(dad, paused.id, false))
        assertEquals(dad.id, db.recurringScheduleDao().getById(paused.id)!!.createdByUserId)
        schedules.clock = Clock.fixed(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(2, schedules.generateDueTasks())
        assertTrue(db.taskDao().getTasksByFamily("f").first().none { it.assignedToUserId == dad.id })
    }

    @Test fun transferFailure_rollsBackDepartureAndFamilyOwnership() = runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_owner BEFORE INSERT ON recurring_schedules " +
                "WHEN NEW.createdByUserId != \'mom\' " +
                "BEGIN SELECT RAISE(ABORT, 'transfer failed'); END"
        )
        try {
            families.leaveFamily(mom.id)
            fail("Schedule transfer and departure must be atomic")
        } catch (_: SQLiteException) {
            assertEquals("f", db.userDao().getUserByIdSync(mom.id)!!.familyId)
            assertEquals(mom.id, current().createdByUserId)
            assertEquals(mom.id, db.familyDao().getFamilyByIdOnce("f")!!.adminId)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_owner")
        }
    }

    @Test fun joiningRepairsLegacyOrphan_beforeAddingNewParent() = runBlocking<Unit> {
        db.userDao().updateUser(mom.copy(familyId = ""))
        val arriving = other.copy(id = "aaa-new")
        db.userDao().insertUser(arriving)
        assertTrue(families.joinFamilyByCode("123456", arriving) is FamilyResult.Success)
        assertEquals(dad.id, current().createdByUserId)
        assertTrue(current().canBeManagedBy(dad, members()))
        assertFalse(current().canBeManagedBy(db.userDao().getUserByIdSync(arriving.id)!!, members()))
    }

    @Test fun legacyDedicatedToSoleParent_canRecoverSafelyAsShared() = runBlocking<Unit> {
        db.userDao().updateUser(mom.copy(familyId = ""))
        db.recurringScheduleDao().upsert(each.copy(
            assignedToUserId = dad.id, assignedToUserName = dad.name, assignmentMode = RecurringAssignmentMode.SHARED
        ))
        assertTrue(current().canBeManagedBy(dad, members()))
        assertEquals(RecurringResult.PAUSED, schedules.setActive(dad, each.id, false))
        assertEquals(dad.id, current().createdByUserId)
        assertEquals("", current().assignedToUserId)
        assertEquals(RecurringAssignmentMode.SHARED, current().assignmentMode)
    }
}
