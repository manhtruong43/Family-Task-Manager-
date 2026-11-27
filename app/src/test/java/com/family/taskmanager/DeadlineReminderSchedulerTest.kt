package com.family.taskmanager

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DeadlineReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: NotificationPreferences
    private lateinit var alarms: AlarmManager
    private var now = 1_800_000_000_000L
    private val task = Task("t", "Read", familyId = "f", createdByUserId = "mom",
        assignedToUserId = "na", assignedToUserName = "Na", deadlineTimestamp = now + 20 * 60_000L)
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gate: CompletableDeferred<Unit>? = null
    private var lock: Job? = null

    @Before fun setup() = runBlocking<Unit> {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ReminderAlarmJournal.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.taskDao().insertTask(task)
        prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        alarms = context.getSystemService(AlarmManager::class.java)
    }

    @After fun cleanup() {
        gate?.complete(Unit)
        runBlocking { lock?.join() }
        runBlocking { worker.coroutineContext[Job]!!.cancelAndJoin() }
        db.close()
    }

    private fun scheduler() = DeadlineReminderScheduler(
        context, db.notificationDao(), prefs, DeadlineReminderStore(context, db, prefs),
        PushNotificationService(context, prefs)
    ).also { it.clock = { now }; it.observationScope = worker }

    private fun failWrites() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delivery BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
    }

    private suspend fun fireNextRetry(scheduler: DeadlineReminderScheduler): Int {
        val alarm = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        val operation = requireNotNull(alarm.operation)
        val intent = shadowOf(operation).savedIntent
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_RETRY, intent.action)
        val attempt = intent.getIntExtra(DeadlineReminderScheduler.EXTRA_RETRY_ATTEMPT, -1)
        now = alarm.triggerAtTime
        // AlarmManager consumes a FLAG_ONE_SHOT PendingIntent before dispatching the receiver.
        // Dispatch the same extras directly, without requiring a Hilt Application in this JVM fixture.
        operation.cancel()
        scheduler.onAlarmFired(requireNotNull(intent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID)))
        return attempt
    }

    @Test fun writeFailureSchedulesDurableRetry_storageRecoveryDeliversOnceWithoutTaskUpdate() = runBlocking<Unit> {
        failWrites()
        scheduler().onReminderFired(task.id)
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        val retry = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(now + 30_000L, retry.triggerAtTime)

        // A new scheduler represents a new process. A successful DB observation cannot replace its retry.
        val restarted = scheduler()
        restarted.rescheduleAll()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(retry.triggerAtTime, shadowOf(alarms).scheduledAlarms.single().triggerAtTime)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")
        assertEquals(1, fireNextRetry(restarted))
        val delivered = db.notificationDao().getNotificationsByUser("na").first().single()
        db.notificationDao().markAsRead(delivered.id)
        restarted.onReminderFired(task.id, 1) // duplicate receiver dispatch
        assertEquals(delivered.copy(isRead = true), db.notificationDao().getNotificationsByUser("na").first().single())
        assertEquals(task, db.taskDao().getTaskById(task.id))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun exhaustedMidweekRetries_keepFutureStagesAcrossProcessRestartWithoutTaskUpdates() = runBlocking<Unit> {
        val day = 86_400_000L
        val deadline = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 29, 20, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val flexible = task.copy(taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3,
            createdAt = deadline - 6 * day, deadlineTimestamp = deadline)
        db.taskDao().updateTask(flexible)
        val firstPlan = requireNotNull(DeadlineReminderPlanner.nextReminderFor(flexible, flexible.createdAt, { false }))
        now = firstPlan.dueAtMillis - 10_000
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        assertEquals(3, shadowOf(alarms).scheduledAlarms.size) // grouped midweek, final day, last call
        failWrites()
        val firstAlarm = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        requireNotNull(firstAlarm.operation).cancel()
        now = firstAlarm.triggerAtTime
        scheduler.onReminderFired(task.id)
        scheduler.rescheduleAll() // a pending retry must retain the independent future alarms too
        assertEquals(3, shadowOf(alarms).scheduledAlarms.size)
        repeat(3) { assertEquals(it + 1, fireNextRetry(scheduler)) }
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertTrue(shadowOf(alarms).scheduledAlarms.all {
            actionOf(it) == DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER && it.triggerAtTime > now
        })
        assertTrue(db.deadlineReminderDao().getAll().isEmpty()) // failed stages have not been consumed
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")

        val restarted = scheduler()
        repeat(3) { scheduler().rescheduleAll() }
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        val nextStage = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        val pending = requireNotNull(nextStage.operation)
        assertEquals("flex_final_day", shadowOf(pending).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_STAGE_KEY))
        pending.cancel()
        now = nextStage.triggerAtTime
        restarted.onAlarmFired(requireNotNull(shadowOf(pending).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID)))
        val firstInbox = db.notificationDao().getNotificationsByUser("na").first().single()
        assertEquals(setOf("flex_final_day"),
            db.deadlineReminderDao().getConsumedStages(task.id, deadline, 0).toSet()) // exhausted stages remain unconsumed
        // The backup for last call is replaced by the primary alarm, not duplicated.
        val lastStage = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(deadline - 30 * 60_000L, lastStage.triggerAtTime)
        requireNotNull(shadowOf(alarms).nextScheduledAlarm!!.operation).cancel()
        now = lastStage.triggerAtTime
        restarted.onReminderFired(task.id)
        assertEquals(2, db.notificationDao().getNotificationsByUser("na").first().size)
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().contains(firstInbox))
        assertEquals(flexible, db.taskDao().getTaskById(task.id))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun completedFlexibleTask_cancelsItsPrimaryAndFutureWakeups() = runBlocking<Unit> {
        val deadline = now + 7 * 86_400_000L
        val flexible = task.copy(taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3,
            createdAt = now, deadlineTimestamp = deadline)
        db.taskDao().updateTask(flexible)
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        assertTrue(shadowOf(alarms).scheduledAlarms.size > 1)
        db.taskDao().updateTask(flexible.copy(status = TaskStatus.COMPLETED, completedCount = 3))
        scheduler.rescheduleAll()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun persistentFailureStopsAfterThreeRetries_andKeepsTransactionRolledBack() = runBlocking<Unit> {
        failWrites()
        val scheduler = scheduler()
        scheduler.onReminderFired(task.id)
        val attempts = mutableListOf<Int>()
        repeat(3) { attempts += fireNextRetry(scheduler) }
        assertEquals(listOf(1, 2, 3), attempts)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
        assertEquals(task, db.taskDao().getTaskById(task.id))
    }

    @Test fun reconciliationAndFailedDelivery_leaveOnlyRetryWithItsOriginalAttempt() = runBlocking<Unit> {
        failWrites()
        val scheduler = scheduler()
        val reconciliationRead = CompletableDeferred<Unit>()
        val releaseReconciliation = CompletableDeferred<Unit>()
        val retryReached = CompletableDeferred<Unit>()
        scheduler.beforeReconcileAlarms = {
            reconciliationRead.complete(Unit)
            releaseReconciliation.await()
        }
        scheduler.beforeClaimAlarm = { retryReached.complete(Unit) }
        val reconciliation = worker.async { scheduler.rescheduleAll() }
        var delivery: Deferred<Unit>? = null
        try {
            withTimeout(5_000) { reconciliationRead.await() }
            // Reconciliation has checked that no retry exists, but has not installed its normal alarm.
            // Same event loop: awaiting retryReached resumes only after delivery suspends on the mutex.
            delivery = async(start = CoroutineStart.UNDISPATCHED) { scheduler.onReminderFired(task.id, retryAttempt = 1) }
            withTimeout(5_000) { retryReached.await() }
        } finally {
            releaseReconciliation.complete(Unit)
        }
        withTimeout(5_000) { reconciliation.await(); delivery?.await() }
        val onlyAlarm = shadowOf(alarms).scheduledAlarms.single()
        val intent = shadowOf(requireNotNull(onlyAlarm.operation)).savedIntent
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_RETRY, intent.action)
        assertEquals(2, intent.getIntExtra(DeadlineReminderScheduler.EXTRA_RETRY_ATTEMPT, -1))
        assertEquals(now + DeadlineReminderScheduler.RETRY_DELAYS_MS[1], onlyAlarm.triggerAtTime)
        scheduler.rescheduleAll()
        val reinstalled = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(onlyAlarm.triggerAtTime, reinstalled.triggerAtTime)
        assertEquals(2, shadowOf(requireNotNull(reinstalled.operation)).savedIntent
            .getIntExtra(DeadlineReminderScheduler.EXTRA_RETRY_ATTEMPT, -1))
    }

    private fun actionOf(alarm: org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm): String? =
        shadowOf(requireNotNull(alarm.operation)).savedIntent.action

    private suspend fun awaitReconciliationAlarm() = withTimeout(5_000) {
        while (shadowOf(alarms).scheduledAlarms.none { actionOf(it) == DeadlineReminderScheduler.ACTION_RECONCILE_RETRY }) {
            delay(10)
        }
    }

    private suspend fun fireReconciliationRetry(scheduler: DeadlineReminderScheduler): Int {
        val alarm = shadowOf(alarms).scheduledAlarms.single {
            actionOf(it) == DeadlineReminderScheduler.ACTION_RECONCILE_RETRY
        }
        val pending = requireNotNull(alarm.operation)
        val intent = shadowOf(pending).savedIntent
        val attempt = intent.getIntExtra(DeadlineReminderScheduler.EXTRA_RETRY_ATTEMPT, -1)
        now = alarm.triggerAtTime
        alarms.cancel(pending)
        pending.cancel() // simulate one-shot consumption, without a Hilt receiver in this fixture
        scheduler.onReconciliationRetry(attempt)
        return attempt
    }

    private fun failLegacyImport() {
        prefs.markRemindedStages(task.id, task.deadlineTimestamp, setOf("flex_mid_week"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON deadline_reminder_receipts BEGIN SELECT RAISE(ABORT, 'test import failure'); END"
        )
    }

    @Test fun failedManualReconciliation_recoversAcrossSchedulerRestartWithoutTaskOrSettingUpdate() = runBlocking<Unit> {
        failLegacyImport()
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        scheduler.rescheduleAll() // an overlapping request must keep the same pending attempt
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        val first = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(DeadlineReminderScheduler.ACTION_RECONCILE_RETRY, actionOf(first))
        assertEquals(now + 30_000, first.triggerAtTime)
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        assertEquals(1, fireReconciliationRetry(scheduler()))
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
        assertEquals(task, db.taskDao().getTaskById(task.id))
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
    }

    @Test fun startupReconciliation_recoversWithoutAnotherObservationEmission() = runBlocking<Unit> {
        failLegacyImport()
        val scheduler = scheduler().also {
            it.reminderChanges = { flow { awaitCancellation() } }
        }
        scheduler.start()
        awaitReconciliationAlarm()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        fireReconciliationRetry(scheduler)
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
    }

    @Test fun observationFailure_restartsSubscriptionThroughReconciliationRecovery() = runBlocking<Unit> {
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val scheduler = scheduler().also {
            it.reminderChanges = {
                flow {
                    if (attempts.incrementAndGet() == 1) throw android.database.sqlite.SQLiteException("read failure")
                    emit(Unit)
                    awaitCancellation()
                }
            }
        }
        scheduler.start()
        awaitReconciliationAlarm()
        fireReconciliationRetry(scheduler)
        withTimeout(5_000) { while (attempts.get() < 2) delay(10) }
        assertEquals(2, attempts.get())
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
        assertEquals(task, db.taskDao().getTaskById(task.id))
    }

    @Test fun reconciliationRetriesAreBoundedAndDoNotOverlap() = runBlocking<Unit> {
        failLegacyImport()
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        repeat(3) { index ->
            assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
            scheduler.rescheduleAll()
            assertEquals(index + 1, fireReconciliationRetry(scheduler()))
        }
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
    }

    @Test fun postCommitReconciliationFailure_neverRetriesDeliveryOrCreatesOverdueInbox() = runBlocking<Unit> {
        now = task.deadlineTimestamp - 10_000
        val scheduler = scheduler().also {
            it.beforeReconcileAlarms = { throw android.database.sqlite.SQLiteException("post-commit read failure") }
        }
        scheduler.onReminderFired(task.id)
        val delivered = db.notificationDao().getNotificationsByUser("na").first().single()
        assertEquals(listOf("fixed_30@r0"), db.deadlineReminderDao().getConsumedStages(task.id, task.deadlineTimestamp, 0))
        assertEquals(DeadlineReminderScheduler.ACTION_RECONCILE_RETRY,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
        scheduler.beforeReconcileAlarms = {}
        assertEquals(1, fireReconciliationRetry(scheduler)) // now is past the deadline
        assertTrue(now > task.deadlineTimestamp)
        assertEquals(listOf(delivered), db.notificationDao().getNotificationsByUser("na").first())
        assertFalse(db.deadlineReminderDao().getConsumedStages(task.id, task.deadlineTimestamp, 0).contains("overdue"))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    private suspend fun fireNormal(scheduler: DeadlineReminderScheduler) {
        val alarm = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER, actionOf(alarm))
        val pending = requireNotNull(alarm.operation)
        val id = requireNotNull(shadowOf(pending).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID))
        now = alarm.triggerAtTime
        pending.cancel()
        scheduler.onAlarmFired(id)
    }

    private suspend fun assertChangedOccurrence(change: (Task, Long) -> Task) {
        failWrites()
        val original = scheduler()
        original.onReminderFired(task.id)
        repeat(2) { fireNextRetry(original) }
        val old = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(now + 300_000, old.triggerAtTime)
        val oldId = requireNotNull(shadowOf(requireNotNull(old.operation)).savedIntent
            .getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID))
        val changed = change(task, now)
        db.taskDao().updateTask(changed)
        val restarted = scheduler()
        restarted.rescheduleAll()
        val current = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER, actionOf(current))
        val record = ReminderAlarmJournal(context).read().alarms.values.single()
        assertEquals(changed.deadlineTimestamp, record.occurrence!!.deadline)
        assertEquals(changed.reminderGeneration, record.occurrence.generation)
        assertNotEquals(oldId, record.id)
        assertTrue(current.triggerAtTime < old.triggerAtTime)
        restarted.onAlarmFired(oldId) // queued broadcast of the obsolete retry must do nothing
        assertTrue(db.notificationDao().getNotificationsByUser(changed.assignedToUserId).first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")
        fireNormal(restarted)
        assertEquals(1, db.notificationDao().getNotificationsByUser(changed.assignedToUserId).first().size)
        assertEquals(changed, db.taskDao().getTaskById(task.id))
    }

    @Test fun shorteningDeadlineReplacesPendingRetryWithCurrentOccurrence() = runBlocking<Unit> {
        assertChangedOccurrence { old, time -> old.copy(deadlineTimestamp = time + 120_000) }
    }

    @Test fun reassignmentReplacesPendingRetryAndDeliversOnlyToNewWorker() = runBlocking<Unit> {
        assertChangedOccurrence { old, _ -> old.copy(assignedToUserId = "bin", assignedToUserName = "Bin",
            reminderGeneration = old.reminderGeneration + 1) }
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
    }

    @Test fun exhaustedOccurrenceIsNotRearmedByReconciliationRestartOrLegacyCallback() = runBlocking<Unit> {
        failWrites()
        val scheduler = scheduler()
        scheduler.onReminderFired(task.id)
        repeat(3) { fireNextRetry(scheduler) }
        assertTrue(ReminderAlarmJournal(context).read().attempts.values.single().exhausted)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")
        repeat(3) { scheduler().rescheduleAll() }
        scheduler().onReminderFired(task.id)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
        assertTrue(db.notificationDao().getNotificationsByUser("na").first().isEmpty())
        db.taskDao().updateTask(task.copy(deadlineTimestamp = now + 3_600_000))
        scheduler().rescheduleAll()
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
    }

    @Test fun freshSchedulerCancelsCompletedDeletedExpiredPendingAndFinishedGoalAlarms() = runBlocking<Unit> {
        val mutations: List<(Task) -> Task?> = listOf(
            { it.copy(status = TaskStatus.COMPLETED) },
            { null },
            { it.copy(deadlineTimestamp = now - 1) },
            { it.copy(status = TaskStatus.PENDING_APPROVAL) },
            { it.copy(completedCount = it.targetCount) }
        )
        mutations.forEachIndexed { index, mutate ->
            val flexible = task.copy(taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3,
                createdAt = now, deadlineTimestamp = now + 7 * 86_400_000, reminderGeneration = index.toLong())
            db.taskDao().insertTask(flexible)
            scheduler().rescheduleAll()
            assertTrue(shadowOf(alarms).scheduledAlarms.size > 1)
            val updated = mutate(flexible)
            if (updated == null) db.taskDao().deleteTask(flexible) else db.taskDao().updateTask(updated)
            // No observer reconciliation in the old process.
            assertFalse(ReminderAlarmJournal(context).read().alarms.isEmpty())
            scheduler().rescheduleAll()
            assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
            assertTrue(ReminderAlarmJournal(context).read().alarms.isEmpty())
        }
    }

    @Test fun freshSchedulerCancelsDeletedTaskRetryAndAllFutureAlarms() = runBlocking<Unit> {
        val flexible = task.copy(taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3,
            createdAt = now, deadlineTimestamp = now + 7 * 86_400_000)
        db.taskDao().updateTask(flexible)
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        failWrites()
        fireNormal(scheduler)
        assertTrue(shadowOf(alarms).scheduledAlarms.any { actionOf(it) == DeadlineReminderScheduler.ACTION_DEADLINE_RETRY })
        db.taskDao().deleteTask(flexible)
        assertNull(db.taskDao().getTaskById(task.id))
        scheduler().rescheduleAll()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(ReminderAlarmJournal(context).read().alarms.isEmpty())
    }

    @Test fun writeAheadInventoryRecoversInterruptionBeforeAlarmInstallation() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.beforeInstallAlarm = { throw CancellationException("process ended") } }
        try { scheduler.rescheduleAll(); fail("Expected interruption") } catch (_: CancellationException) { }
        assertFalse(ReminderAlarmJournal(context).read().alarms.isEmpty())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        scheduler().rescheduleAll()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test fun cancelledIdentitiesRemainDurableUntilRemovalIsAcknowledged() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        db.taskDao().deleteTask(task)
        scheduler.afterCancelObsoleteAlarms = { throw CancellationException("process ended before journal ack") }
        try { scheduler.rescheduleAll(); fail("Expected interruption") } catch (_: CancellationException) { }
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertFalse(ReminderAlarmJournal(context).read().alarms.isEmpty())
        scheduler().rescheduleAll()
        assertTrue(ReminderAlarmJournal(context).read().alarms.isEmpty())
    }

    @Test fun interruptedClaimConsumesItsBudgetAndRecoversWithoutRepeatingAttemptZero() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.afterClaimAlarm = { throw CancellationException("process ended") } }
        scheduler.rescheduleAll()
        try { fireNormal(scheduler); fail("Expected interruption") } catch (_: CancellationException) { }
        assertTrue(ReminderAlarmJournal(context).read().attempts.values.single().inFlight)
        val restarted = scheduler()
        restarted.rescheduleAll()
        repeat(3) { scheduler().rescheduleAll() }
        val retry = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(1, shadowOf(requireNotNull(retry.operation)).savedIntent
            .getIntExtra(DeadlineReminderScheduler.EXTRA_RETRY_ATTEMPT, -1))
        fireNextRetry(restarted)
        assertEquals(1, db.notificationDao().getNotificationsByUser("na").first().size)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun queuedPrimaryCallbackAfterRecoveryUsesReservedBudgetAndStillDelivers() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        val fired = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        val pending = requireNotNull(fired.operation)
        val id = requireNotNull(shadowOf(pending).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID))
        now = fired.triggerAtTime
        pending.cancel() // OS consumed the one-shot; receiver has not claimed its journal entry yet.
        val restarted = scheduler()
        restarted.rescheduleAll()
        assertEquals(1, ReminderAlarmJournal(context).read().attempts.values.single().attempt)
        assertEquals(DeadlineReminderScheduler.ACTION_DEADLINE_RETRY,
            actionOf(shadowOf(alarms).scheduledAlarms.single()))
        restarted.onAlarmFired(id) // queued original callback arrives after recovery reserved a retry
        assertEquals(1, db.notificationDao().getNotificationsByUser("na").first().size)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        restarted.onAlarmFired(id)
        assertEquals(1, db.notificationDao().getNotificationsByUser("na").first().size)
    }

    @Test fun recoveryBetweenCallbackLookupAndClaimKeepsItsCurrentBudget() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        val fired = requireNotNull(shadowOf(alarms).nextScheduledAlarm)
        val pending = requireNotNull(fired.operation)
        val id = requireNotNull(shadowOf(pending).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID))
        now = fired.triggerAtTime
        pending.cancel()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        scheduler.beforeClaimAlarm = { entered.complete(Unit); release.await() }
        val callback = async { scheduler.onAlarmFired(id) }
        try {
            withTimeout(5_000) { entered.await() }
            scheduler.rescheduleAll()
            assertEquals(1, ReminderAlarmJournal(context).read().attempts.values.single().attempt)
        } finally { release.complete(Unit) }
        withTimeout(5_000) { callback.await() }
        assertEquals(1, db.notificationDao().getNotificationsByUser("na").first().size)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun reconciliationDoesNotSpendRetryBudgetOfLiveDelivery() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        scheduler.afterClaimAlarm = { entered.complete(Unit); release.await() }
        val callback = async { fireNormal(scheduler) }
        try {
            withTimeout(5_000) { entered.await() }
            scheduler.rescheduleAll()
            val attempt = ReminderAlarmJournal(context).read().attempts.values.single()
            assertTrue(attempt.inFlight)
            assertEquals(0, attempt.attempt)
            assertEquals(DeadlineReminderScheduler.ACTION_RECOVERY_WATCHDOG,
                actionOf(shadowOf(alarms).scheduledAlarms.single()))
        } finally { release.complete(Unit) }
        withTimeout(5_000) { callback.await() }
        assertEquals(1, db.notificationDao().getNotificationsByUser("na").first().size)
        assertTrue(ReminderAlarmJournal(context).read().attempts.isEmpty())
    }

    @Test fun unrelatedReconciliationDoesNotPostponeAlreadyArmedCatchup() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        val originalTime = shadowOf(alarms).scheduledAlarms.single().triggerAtTime
        repeat(3) {
            now += 1000
            scheduler().rescheduleAll()
            assertEquals(originalTime, shadowOf(alarms).scheduledAlarms.single().triggerAtTime)
        }
    }

    @Test fun cancellationDuringReconciliationPropagatesWithoutRecoveryAlarm() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val scheduler = scheduler().also {
            it.beforeReconcileAlarms = { entered.complete(Unit); awaitCancellation() }
        }
        val job = worker.launch { scheduler.rescheduleAll() }
        withTimeout(5_000) { entered.await() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun cancellationPropagatesWithoutSchedulingPersistenceRetry() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        gate = release
        lock = worker.launch {
            db.withTransaction { entered.countDown(); release.await() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val delivery = worker.launch { scheduler().onReminderFired(task.id) }
        delay(50)
        delivery.cancelAndJoin()
        release.complete(Unit)
        lock!!.join()
        assertTrue(delivery.isCancelled)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(db.deadlineReminderDao().getAll().isEmpty())
    }
}
