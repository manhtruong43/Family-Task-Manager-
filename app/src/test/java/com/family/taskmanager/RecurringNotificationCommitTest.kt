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
import kotlinx.coroutines.CancellationException
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
class RecurringNotificationCommitTest {
    private lateinit var db: AppDatabase
    private lateinit var schedules: RecurringScheduleRepository
    private val day = LocalDate.of(2026, 11, 12)
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val each = RecurringSchedule(
        id = "each", familyId = "f", title = "Đọc sách", createdByUserId = mom.id,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )
    private val input = RecurringScheduleInput(
        title = "Đọc sách", description = "", assigneeId = "", priority = TaskPriority.MEDIUM,
        points = 20, repeatType = RepeatType.DAILY, daysOfWeekMask = 0, dueMinuteOfDay = 1200,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    @Before fun setup() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup(id = "f", name = "Nhà thử", inviteCode = "123456", adminId = mom.id))
        listOf(mom, dad, na).forEach { db.userDao().insertUser(it) }
        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        schedules = RecurringScheduleRepository(context, db, notifications)
        schedules.clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @After fun cleanup() { db.close() }

    private suspend fun inbox() = listOf(dad, na).flatMap {
        db.notificationDao().getNotificationsByUser(it.id).first()
    }.sortedBy { it.id }

    private suspend fun assertCommittedAndRetryDoesNotDuplicate(scheduleId: String) {
        val tasks = db.taskDao().getTasksByFamily("f").first().sortedBy { it.id }
        val notifications = inbox()
        assertEquals(setOf(dad.id, na.id), tasks.map { it.assignedToUserId }.toSet())
        assertEquals(2, notifications.size)
        assertEquals(day.toEpochDay(), db.recurringScheduleDao().getById(scheduleId)!!.lastGeneratedEpochDay)
        assertEquals(0, schedules.generateDueTasks())
        assertEquals(tasks, db.taskDao().getTasksByFamily("f").first().sortedBy { it.id })
        assertEquals(notifications, inbox())
    }

    @Test fun save_deliveryRuntimeFailureStillReturnsSavedAndContinuesBatch() = runBlocking<Unit> {
        val attempts = mutableListOf<String>()
        schedules.systemPushSender = { _, message ->
            attempts += message
            if (attempts.size == 1) throw IllegalStateException("System service failed")
        }
        assertEquals(RecurringResult.SAVED, schedules.saveSchedule(mom, null, input))
        assertEquals(2, attempts.size)
        val id = db.recurringScheduleDao().getActiveSchedules().single().id
        assertCommittedAndRetryDoesNotDuplicate(id)
        assertEquals(2, attempts.size)
    }

    @Test fun resume_deliveryRuntimeFailureStillReturnsResumedAndContinuesBatch() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(each.copy(isActive = false))
        var attempts = 0
        schedules.systemPushSender = { _, _ ->
            attempts++
            throw IllegalArgumentException("Unexpected system failure")
        }
        assertEquals(RecurringResult.RESUMED, schedules.setActive(mom, each.id, true))
        assertEquals(2, attempts)
        assertTrue(db.recurringScheduleDao().getById(each.id)!!.isActive)
        assertCommittedAndRetryDoesNotDuplicate(each.id)
        assertEquals(2, attempts)
    }

    @Test fun cancellation_propagatesAfterCommitWithoutDuplicatingOnRetry() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(each.copy(isActive = false))
        val cancelled = CancellationException("Cancelled delivery")
        var attempts = 0
        schedules.systemPushSender = { _, _ -> attempts++; throw cancelled }
        try {
            schedules.setActive(mom, each.id, true)
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
        }
        assertEquals(1, attempts)
        assertCommittedAndRetryDoesNotDuplicate(each.id)
    }

    @Test fun inboxWriteFailure_rollsBackOccurrenceAndRemainsARealGenerationFailure() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(each)
        var attempts = 0
        schedules.systemPushSender = { _, _ -> attempts++ }
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_inbox BEFORE INSERT ON notifications " +
                "BEGIN SELECT RAISE(ABORT, 'inbox failed'); END"
        )
        try {
            schedules.generateDueTasks()
            fail("Transaction failure must propagate")
        } catch (_: SQLiteException) {
            assertTrue(db.taskDao().getTasksByFamily("f").first().isEmpty())
            assertTrue(inbox().isEmpty())
            assertEquals(-1L, db.recurringScheduleDao().getById(each.id)!!.lastGeneratedEpochDay)
            assertEquals(0, attempts)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_inbox")
        }
        assertEquals(2, schedules.generateDueTasks())
        assertCommittedAndRetryDoesNotDuplicate(each.id)
    }
}
