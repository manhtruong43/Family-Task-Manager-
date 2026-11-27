package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.AppMigrations
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.*
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
import java.util.Calendar
import java.util.UUID
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DeadlineReminderStoreTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: NotificationPreferences
    private lateinit var store: DeadlineReminderStore
    private val databaseName = "rem.db"
    private val recordQueries = AtomicBoolean(false)
    private val queries = Collections.synchronizedList(mutableListOf<String>())
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val due = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val now = due - 2 * hour
    private val mom = User("mom", "Mom", "m@test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@test", familyId = "f")
    private val bin = User("bin", "Bin", "b@test", familyId = "f")
    private val task = Task(
        "t", "Read", familyId = "f", createdByUserId = mom.id,
        assignedToUserId = na.id, assignedToUserName = na.name,
        createdAt = due - 6 * day, deadlineTimestamp = due,
        taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3
    )

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .setQueryCallback(object : RoomDatabase.QueryCallback {
            override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                if (recordQueries.get()) queries.add(sqlQuery)
            }
        }, Executor { it.run() })
        .addMigrations(*AppMigrations.ALL).allowMainThreadQueries().build()

    @Before fun setup() = runBlocking<Unit> {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ReminderAlarmJournal.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        db = open()
        db.familyDao().insertFamily(FamilyGroup("f", "Family", "123456", mom.id))
        listOf(mom, na, bin).forEach { db.userDao().insertUser(it) }
        db.taskDao().insertTask(task)
        store = DeadlineReminderStore(context, db, prefs)
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun reopen() {
        db.close()
        db = open()
        prefs = NotificationPreferences(context)
        store = DeadlineReminderStore(context, db, prefs)
    }

    private suspend fun inbox(userId: String = na.id) = db.notificationDao().getNotificationsByUser(userId).first()

    @Test fun interruptedAfterCommit_restartDoesNotReplayGroupedStagesOrResetReadState() = runBlocking<Unit> {
        val delivered = store.consumeDue(task.id, now)!!
        assertTrue(delivered.shouldPost)
        assertEquals(setOf("flex_mid_week", "flex_early_pace", "flex_final_day"),
            db.deadlineReminderDao().getConsumedStages(task.id, due, 0).toSet())
        val original = inbox().single()
        db.notificationDao().markAsRead(original.id)
        try { throw CancellationException("Process ended before system posting") } catch (_: CancellationException) { }
        reopen()
        assertNull(store.consumeDue(task.id, now + 1000))
        assertEquals(original.copy(isRead = true), inbox().single())
        val next = store.currentPlans(now + 1000).single().second
        assertEquals("flex_last_call", next.stageKey)
    }

    private suspend fun verifyRollback(table: String) {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delivery BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        try {
            store.consumeDue(task.id, now)
            fail("Expected persistence failure")
        } catch (_: SQLiteException) { }
        assertTrue(inbox().isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")
        assertNotNull(store.consumeDue(task.id, now))
        assertEquals(1, inbox().size)
        assertEquals(3, db.deadlineReminderDao().getAll().size)
    }

    @Test fun inboxFailureRollsBackStageConsumption() = runBlocking<Unit> { verifyRollback("notifications") }
    @Test fun receiptFailureRollsBackInbox() = runBlocking<Unit> { verifyRollback("deadline_reminder_receipts") }

    @Test fun legacyOverdueCallbackReplay_preservesRandomIdInboxWithoutSendingAgain() = runBlocking<Unit> {
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("reminded_tasks", setOf("${task.id}@$due#overdue_c0")).commit()
        val existing = NotificationItem(UUID.randomUUID().toString(), na.id, "Old overdue", "Already sent",
            timestamp = due + 1, isRead = true)
        db.notificationDao().insertNotification(existing)
        suspend fun replay() {
            val scheduler = DeadlineReminderScheduler(context, db.notificationDao(), prefs, store,
                PushNotificationService(context, prefs)).also { it.clock = { due + 1000 } }
            scheduler.onReminderFired(task.id)
        }
        replay()
        assertEquals(listOf(existing), inbox())
        assertEquals(listOf("overdue"), db.deadlineReminderDao().getConsumedStages(task.id, due, 0))
        reopen()
        replay()
        assertEquals(listOf(existing), inbox())
        assertEquals(task, db.taskDao().getTaskById(task.id))
    }

    @Test fun legacyFixed60And120DoNotReplayUnderUnsetGlobal30_evenAfterRestart() = runBlocking<Unit> {
        val fixed = listOf(60, 120).map { lead ->
            task.copy(id = "legacy-$lead", taskMode = TaskMode.FIXED, targetCount = 1, reminderLeadMinutes = lead)
        }
        fixed.forEach {
            db.taskDao().insertTask(it)
            prefs.markRemindedStages(it.id, due, setOf("fixed_${it.reminderLeadMinutes}"))
            db.notificationDao().insertNotification(NotificationItem(UUID.randomUUID().toString(), na.id,
                "Already reminded", it.id, timestamp = due - hour, isRead = true))
        }
        val before = inbox().toSet()
        val at = due - 15 * 60_000
        repeat(2) {
            assertEquals(30, prefs.reminderLeadMinutes.value)
            assertEquals(0L, prefs.reminderSettings.value.revision)
            assertTrue(store.currentPlans(at).none { row -> row.first.id.startsWith("legacy-") })
            fixed.forEach { assertNull(store.consumeDue(it.id, at)) }
            assertEquals(before, inbox().toSet())
            reopen()
        }
        // Explicit changes are a new configuration occurrence, unlike upgrading to the default.
        prefs.setReminderLeadMinutes(60)
        assertEquals(1L, prefs.reminderSettings.value.revision)
        fixed.forEach { assertNotNull(store.consumeDue(it.id, at)) }
        assertEquals(before.size + 2, inbox().size)
    }

    @Test fun legacyFixedMarksCannotCrossAssignmentGenerationsOrDeadlines() = runBlocking<Unit> {
        val fixed = task.copy(taskMode = TaskMode.FIXED, targetCount = 1)
        db.taskDao().updateTask(fixed)
        prefs.markRemindedStages(fixed.id, due, setOf("fixed_120"))
        val at = due - 15 * 60_000
        assertNull(store.consumeDue(fixed.id, at))
        val assignedAgain = fixed.copy(reminderGeneration = 1)
        db.taskDao().updateTask(assignedAgain)
        assertNotNull(store.consumeDue(fixed.id, at))
        val changedDeadline = assignedAgain.copy(deadlineTimestamp = due + day)
        db.taskDao().updateTask(changedDeadline)
        assertEquals(fixed.id, store.currentPlans(at).single().first.id)
        assertNull(store.consumeDue(fixed.id, at)) // new deadline's reminder is not due yet
    }

    @Test fun existingInboxRowIsNeverReplacedDuringLegacyRecovery() = runBlocking<Unit> {
        val id = UUID.nameUUIDFromBytes("${task.id}@$due#flex_final_day#${na.id}".toByteArray(Charsets.UTF_8)).toString()
        val existing = NotificationItem(id, na.id, "Old title", "Old message", timestamp = now - hour, isRead = true)
        db.notificationDao().insertNotification(existing)
        val result = store.consumeDue(task.id, now)!!
        assertFalse(result.shouldPost)
        assertEquals(existing, inbox().single())
        assertEquals(3, db.deadlineReminderDao().getAll().size)
        reopen()
        assertNull(store.consumeDue(task.id, now + hour / 2))
        assertEquals(existing, inbox().single())
    }

    @Test fun progressKeepsConsumedStages_butDepartureAndReclaimRearmForNewWorker() = runBlocking<Unit> {
        store.consumeDue(task.id, now)
        db.taskDao().updateTask(task.copy(completedCount = 1))
        assertNull(store.consumeDue(task.id, now + 1000))
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, prefs))
        val families = FamilyRepository(db, db.familyDao(), db.userDao(), RewardRepository(context, db, db.rewardDao(), notifications))
        assertTrue(families.leaveFamily(na.id) is FamilyResult.Success)
        val released = db.taskDao().getTaskById(task.id)!!
        assertEquals(1L, released.reminderGeneration)
        assertEquals(0, released.completedCount)
        val tasks = TaskRepository(context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(),
            PushNotificationService(context, prefs))
        assertEquals(TaskActionResult.SUCCESS, tasks.startTask(task.id, bin))
        assertEquals(2L, db.taskDao().getTaskById(task.id)!!.reminderGeneration)
        assertNotNull(store.consumeDue(task.id, now + 2000))
        assertEquals(1, inbox(bin.id).size)
        assertEquals(1, inbox(na.id).size)
        assertEquals(3, db.deadlineReminderDao().getConsumedStages(task.id, due, 2).size)
        reopen()
        assertNull(store.consumeDue(task.id, now + 3000))
    }

    @Test fun reconciliationFiltersHistory_batchesReceipts_andReadsLegacyPreferencesOnce() = runBlocking<Unit> {
        val active = (1..24).map { task.copy(id = "active-$it") }
        val inactive = (1..1200).map { index ->
            when (index % 4) {
                0 -> task.copy(id = "history-$index", status = TaskStatus.COMPLETED)
                1 -> task.copy(id = "history-$index", status = TaskStatus.PENDING_APPROVAL)
                2 -> task.copy(id = "history-$index", deadlineTimestamp = now - 1)
                else -> task.copy(id = "history-$index", completedCount = task.targetCount)
            }
        }
        db.withTransaction { (active + inactive).forEach { db.taskDao().insertTask(it) } }
        val preferenceReads = AtomicInteger()
        val countedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val delegate = super.getSharedPreferences(name, mode)
                if (name != NotificationPreferences.PREFS_NAME) return delegate
                return object : SharedPreferences by delegate {
                    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
                        preferenceReads.incrementAndGet()
                        return delegate.getStringSet(key, defValues)
                    }
                }
            }
        }
        val countedPreferences = NotificationPreferences(countedContext)
        countedPreferences.markRemindedStages(task.id, due,
            setOf("flex_mid_week", "flex_early_pace", "flex_final_day"))
        val countedStore = DeadlineReminderStore(context, db, countedPreferences)
        preferenceReads.set(0)
        queries.clear()
        recordQueries.set(true)
        val plans = try { countedStore.currentPlans(now) } finally { recordQueries.set(false) }
        assertEquals((active.map { it.id } + task.id).toSet(), plans.map { it.first.id }.toSet())
        assertEquals("flex_last_call", plans.single { it.first.id == task.id }.second.stageKey)
        assertEquals(1, queries.count {
            it.startsWith("SELECT", ignoreCase = true) && it.contains("FROM deadline_reminder_receipts", ignoreCase = true)
        })
        assertEquals(1, preferenceReads.get())
        assertEquals(3, db.deadlineReminderDao().getConsumedStages(task.id, due, 0).size)
        assertTrue(db.deadlineReminderDao().getAll().all { it.taskId == task.id })

        // With only inactive history left, neither receipts nor legacy preferences need reading.
        db.withTransaction {
            (active + task).forEach { db.taskDao().updateTask(it.copy(status = TaskStatus.COMPLETED)) }
        }
        queries.clear()
        preferenceReads.set(0)
        recordQueries.set(true)
        val emptyPlans = try { countedStore.currentPlans(now) } finally { recordQueries.set(false) }
        assertTrue(emptyPlans.isEmpty())
        assertFalse(queries.any { it.contains("FROM deadline_reminder_receipts", ignoreCase = true) })
        assertEquals(0, preferenceReads.get())
    }

    @Test fun oldPreferenceMarksAreImportedOnlyForTheirOriginalGeneration() = runBlocking<Unit> {
        prefs.markRemindedStages(task.id, due, setOf("flex_mid_week", "flex_early_pace", "flex_final_day"))
        store.currentPlans(now)
        assertEquals(3, db.deadlineReminderDao().getAll().size)
        assertNull(store.consumeDue(task.id, now))
        db.taskDao().updateTask(task.copy(reminderGeneration = 1, assignedToUserId = bin.id, assignedToUserName = bin.name))
        assertNotNull(store.consumeDue(task.id, now))
        assertEquals(1, inbox(bin.id).size)
    }

    @Test fun directReassignmentStartsNewGeneration_butContentAndProgressDoNot() = runBlocking<Unit> {
        val tasks = TaskRepository(context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(),
            PushNotificationService(context, prefs))
        store.consumeDue(task.id, now)
        assertEquals(TaskActionResult.SUCCESS, tasks.updateTask(task.id, "Read more", "", bin.id, TaskPriority.MEDIUM,
            10, due, mom))
        val changed = db.taskDao().getTaskById(task.id)!!
        assertEquals(1L, changed.reminderGeneration)
        assertNotNull(store.consumeDue(task.id, now))
        assertEquals(TaskActionResult.SUCCESS, tasks.updateTask(task.id, "Read again", "", bin.id, TaskPriority.HIGH,
            20, due, mom))
        assertEquals(1L, db.taskDao().getTaskById(task.id)!!.reminderGeneration)
        assertNull(store.consumeDue(task.id, now))
    }

    @Test fun deletedTaskCascadesReceipts_andCannotProduceAnotherInboxEntry() = runBlocking<Unit> {
        store.consumeDue(task.id, now)
        db.taskDao().deleteTask(task)
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        assertNull(store.consumeDue(task.id, now + 1000))
        assertEquals(1, inbox().size)
    }
    private fun taskActions() = TaskRepository(
        context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(),
        PushNotificationService(context, prefs)
    )

    private suspend fun currentTask() = requireNotNull(db.taskDao().getTaskById(task.id))

    @Test fun sharedReturnAndReclaim_rearmEvenWhenUnassignedHouseholdAlreadyReceivedReminder() = runBlocking<Unit> {
        db.taskDao().updateTask(task.copy(taskMode = TaskMode.FIXED, targetCount = 1,
            assignedToUserId = "", assignedToUserName = "", openToAll = true))
        val actions = taskActions()
        val at = due - 15 * 60_000L
        assertEquals(TaskActionResult.SUCCESS, actions.startTask(task.id, na))
        assertEquals(1L, currentTask().reminderGeneration)
        assertNotNull(store.consumeDue(task.id, at))
        assertEquals(TaskActionResult.SUCCESS, actions.resetTaskToTodo(task.id, na))
        assertEquals(2L, currentTask().reminderGeneration)
        assertNotNull(store.consumeDue(task.id, at + 1000)) // notification while released
        assertEquals(TaskActionResult.SUCCESS, actions.startTask(task.id, bin))
        assertEquals(3L, currentTask().reminderGeneration)
        assertNotNull(store.consumeDue(task.id, at + 2000))
        assertEquals(2, inbox(bin.id).size) // shared reminder + reminder of Bin's own claim
        assertNull(store.consumeDue(task.id, at + 3000))
    }

    @Test fun rejectionAndDirectSubmission_advanceOwnershipGenerationWithoutRearmingSameOwner() = runBlocking<Unit> {
        db.taskDao().updateTask(task.copy(taskMode = TaskMode.FIXED, targetCount = 1,
            assignedToUserId = "", assignedToUserName = "", openToAll = true))
        val actions = taskActions()
        val at = due - 15 * 60_000L
        actions.startTask(task.id, na)
        store.consumeDue(task.id, at)
        actions.submitTaskProof(task.id, "na.jpg", "", na)
        assertEquals(1L, currentTask().reminderGeneration) // same owner
        assertEquals(TaskActionResult.SUCCESS, actions.rejectTask(currentTask(), mom))
        assertEquals(2L, currentTask().reminderGeneration)
        assertEquals(TaskActionResult.SUCCESS, actions.submitTaskProof(task.id, "bin.jpg", "", bin))
        assertEquals(3L, currentTask().reminderGeneration) // direct claim through submission
        assertEquals(bin.id, currentTask().assignedToUserId)
        assertEquals(TaskActionResult.SUCCESS, actions.rejectTask(currentTask(), mom))
        assertEquals(4L, currentTask().reminderGeneration)
        actions.startTask(task.id, bin)
        assertEquals(5L, currentTask().reminderGeneration)
        assertNotNull(store.consumeDue(task.id, at + 1000))
    }
}
