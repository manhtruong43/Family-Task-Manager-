package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.AppMigrations
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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

/** Đóng/mở DB trên đĩa và tạo repository mới; không dựa vào bộ nhớ ViewModel hay singleton cũ. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecurringCreationPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: RecurringScheduleRepository
    private val dbName = "rq.db"
    private val day = LocalDate.of(2026, 11, 12)
    private val mom = User("mom", "Mom", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Dad", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val token = "stable-create-request"
    private val input = RecurringScheduleInput(
        title = "Read", description = "", assigneeId = "", priority = TaskPriority.MEDIUM,
        points = 20, repeatType = RepeatType.DAILY, daysOfWeekMask = 0, dueMinuteOfDay = 1200,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    private fun openDb() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(*AppMigrations.ALL).allowMainThreadQueries().build()

    private fun newRepository(): RecurringScheduleRepository {
        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        return RecurringScheduleRepository(context, db, notifications).also {
            it.clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        }
    }

    @Before fun setup() = runBlocking<Unit> {
        context = RuntimeEnvironment.getApplication()
        db = openDb()
        db.familyDao().insertFamily(FamilyGroup(id = "f", name = "Test", inviteCode = "123456", adminId = mom.id))
        listOf(mom, dad, na).forEach { db.userDao().insertUser(it) }
        repository = newRepository()
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun reopen() {
        db.close()
        db = openDb()
        repository = newRepository()
    }

    private data class Snapshot(
        val schedules: List<RecurringSchedule>,
        val tasks: List<Task>,
        val notifications: List<NotificationItem>
    )

    private suspend fun snapshot() = Snapshot(
        db.recurringScheduleDao().getByFamilyOnce("f").sortedBy { it.id },
        db.taskDao().getTasksByFamily("f").first().sortedBy { it.id },
        listOf(mom, dad, na).flatMap { db.notificationDao().getNotificationsByUser(it.id).first() }.sortedBy { it.id }
    )

    private suspend fun interruptedCommit(mode: RecurringAssignmentMode) {
        val form = input.copy(assignmentMode = mode)
        val interrupted = CancellationException("Process ended after commit, before acknowledgement")
        repository.systemPushSender = { _, _ -> throw interrupted }
        try {
            repository.saveSchedule(mom, null, form, token)
            fail("Completion should be interrupted after persistence")
        } catch (error: CancellationException) {
            assertSame(interrupted, error)
        }
        val committed = snapshot()
        assertEquals(1, committed.schedules.size)
        assertEquals(if (mode == RecurringAssignmentMode.SHARED) 1 else 2, committed.tasks.size)
        assertEquals(2, committed.notifications.size)
        val receipt = requireNotNull(db.recurringScheduleDao().getCreationRequest(token))
        assertEquals(committed.schedules.single().id, receipt.scheduleId)

        reopen()
        var pushAttempts = 0
        repository.systemPushSender = { _, _ -> pushAttempts++ }
        assertEquals(RecurringResult.ALREADY_CREATED, repository.saveSchedule(mom, null, form, token))
        assertEquals(receipt, db.recurringScheduleDao().getCreationRequest(token))
        assertEquals(committed, snapshot())
        assertEquals(0, pushAttempts)
    }

    @Test fun reopenShared() = runBlocking<Unit> {
        interruptedCommit(RecurringAssignmentMode.SHARED)
    }

    @Test fun reopenEach() = runBlocking<Unit> {
        interruptedCommit(RecurringAssignmentMode.EACH_MEMBER)
    }

    @Test fun replayUnchanged() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        val saved = snapshot().schedules.single()
        db.recurringScheduleDao().upsert(saved.copy(title = "Changed later", points = 31, isActive = false))
        val before = snapshot()
        reopen()
        // Một yêu cầu tạo đã thành công chỉ nhận lại kết quả, không được ghi đè thay đổi về sau.
        val staleForm = input.copy(title = "", points = 0, assigneeId = "departed")
        assertEquals(RecurringResult.ALREADY_CREATED, repository.saveSchedule(mom, null, staleForm, token))
        assertEquals(before, snapshot())
    }

    @Test fun deletedNotRecreated() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        val receipt = requireNotNull(db.recurringScheduleDao().getCreationRequest(token))
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(mom, receipt.scheduleId))
        val afterDelete = snapshot()
        reopen()
        assertEquals(RecurringResult.NOT_FOUND, repository.saveSchedule(mom, null, input, token))
        assertEquals(receipt, db.recurringScheduleDao().getCreationRequest(token))
        assertEquals(afterDelete, snapshot())
    }

    @Test fun identityScope() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        val before = snapshot()
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(dad, null, input, token))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(na, null, input, token))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(mom, null, input, " "))
        db.userDao().updateUser(mom.copy(familyId = ""))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(mom, null, input, token))
        db.familyDao().insertFamily(FamilyGroup(id = "g", name = "Other", inviteCode = "654321", adminId = mom.id))
        val moved = mom.copy(familyId = "g")
        db.userDao().updateUser(moved)
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(moved, null, input, token))
        assertTrue(db.recurringScheduleDao().getByFamilyOnce("g").isEmpty())
        assertEquals(before, snapshot())
    }

    @Test fun concurrentRetry() = runBlocking<Unit> {
        val otherRepository = newRepository()
        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) { repository.saveSchedule(mom, null, input, token) },
                async(Dispatchers.IO) { otherRepository.saveSchedule(mom, null, input, token) }
            ).awaitAll()
        }
        assertEquals(setOf(RecurringResult.SAVED, RecurringResult.ALREADY_CREATED), results.toSet())
        val data = snapshot()
        assertEquals(1, data.schedules.size)
        assertEquals(2, data.tasks.size)
        assertEquals(2, data.notifications.size)
    }

    @Test fun receiptRollback() = runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_receipt BEFORE INSERT ON recurring_creation_requests " +
                "BEGIN SELECT RAISE(ABORT, 'receipt failed'); END"
        )
        try {
            repository.saveSchedule(mom, null, input, token)
            fail("Schedule and receipt must commit atomically")
        } catch (_: SQLiteException) {
            assertEquals(Snapshot(emptyList(), emptyList(), emptyList()), snapshot())
            assertNull(db.recurringScheduleDao().getCreationRequest(token))
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receipt")
        }
        reopen()
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        assertEquals(1, snapshot().schedules.size)
    }

    @Test fun lookupValidatesSessionAndDistinguishesMissing() = runBlocking<Unit> {
        assertEquals(CreationRequestStatus.NEW, repository.resolveCreationRequest(mom, token).status)
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        val found = repository.resolveCreationRequest(mom, token)
        assertEquals(CreationRequestStatus.COMMITTED, found.status)
        assertEquals(snapshot().schedules.single().id, found.schedule!!.id)
        assertEquals(CreationRequestStatus.NOT_ALLOWED, repository.resolveCreationRequest(dad, token).status)
        assertEquals(CreationRequestStatus.NOT_ALLOWED, repository.resolveCreationRequest(na, token).status)
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(mom, found.schedule.id))
        assertEquals(CreationRequestStatus.NOT_FOUND, repository.resolveCreationRequest(mom, token).status)
        db.userDao().updateUser(mom.copy(familyId = ""))
        assertEquals(CreationRequestStatus.NOT_ALLOWED, repository.resolveCreationRequest(mom, token).status)
    }

    @Test fun newRequestCreatesNew() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, token))
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, "another-request"))
        assertEquals(2, snapshot().schedules.size)
        assertNotEquals(
            db.recurringScheduleDao().getCreationRequest(token)!!.scheduleId,
            db.recurringScheduleDao().getCreationRequest("another-request")!!.scheduleId
        )
    }
}
