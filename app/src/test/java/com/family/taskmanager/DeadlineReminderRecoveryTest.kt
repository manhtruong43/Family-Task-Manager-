package com.family.taskmanager

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DeadlineReminderRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: NotificationPreferences
    private lateinit var manager: AlarmManager
    private var now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 86_400_000L
    private val task = Task("t", "Read", familyId = "f", createdByUserId = "mom",
        assignedToUserId = "na", assignedToUserName = "Na", deadlineTimestamp = now + 20 * minute)
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setup() = runBlocking<Unit> {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ReminderAlarmJournal.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.taskDao().insertTask(task)
        manager = context.getSystemService(AlarmManager::class.java)
    }

    @After fun cleanup() {
        runBlocking { worker.coroutineContext[Job]!!.cancelAndJoin() }
        db.close()
    }

    private fun journal() = ReminderAlarmJournal(context)
    private fun scheduler() = DeadlineReminderScheduler(context, db.notificationDao(), prefs,
        DeadlineReminderStore(context, db, prefs), PushNotificationService(context, prefs))
        .also { it.clock = { now }; it.observationScope = worker }
    private suspend fun inbox() = db.notificationDao().getNotificationsByUser("na").first()
    private fun records(kind: ReminderAlarmKind) = journal().read().alarms.values.filter { it.kind == kind }
    private fun next(kind: ReminderAlarmKind) = records(kind).minBy { it.at }
    private fun physical(id: String) = shadowOf(manager).scheduledAlarms.first {
        shadowOf(requireNotNull(it.operation)).savedIntent.getStringExtra(DeadlineReminderScheduler.EXTRA_ALARM_ID) == id
    }

    private suspend fun fire(record: ReminderAlarmRecord, receiver: DeadlineReminderScheduler = scheduler(), at: Long = record.at) {
        val pending = requireNotNull(physical(record.id).operation)
        now = at
        if (record.kind != ReminderAlarmKind.RECOVERY_WATCHDOG) {
            manager.cancel(pending)
            pending.cancel() // simulate one-shot consumption before dispatch
        }
        when (record.kind) {
            ReminderAlarmKind.NORMAL, ReminderAlarmKind.RETRY -> receiver.onAlarmFired(record.id)
            ReminderAlarmKind.RECONCILE -> receiver.onReconciliationRetry(record.attempt)
            ReminderAlarmKind.RECOVERY_WATCHDOG -> receiver.onRecoveryWakeup()
        }
    }

    private fun failWrites() = db.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER fail_delivery BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'test storage failure'); END"
    )
    private fun restoreWrites() = db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delivery")

    private suspend fun assertOverdueOnce() {
        val delivered = inbox().single()
        assertEquals(ServiceLocale.localizedContext(context).getString(R.string.noti_overdue_title), delivered.title)
        assertTrue(db.deadlineReminderDao().getConsumedStages("t", task.deadlineTimestamp, 0).contains("overdue"))
    }

    @Test fun delayedJournaledNormalDeliversOverdueOnce() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        val original = next(ReminderAlarmKind.NORMAL)
        fire(original, scheduler, task.deadlineTimestamp + 10 * minute)
        assertOverdueOnce()
        scheduler().onAlarmFired(original.id)
        assertEquals(1, inbox().size)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun delayedRetryCrossingDeadlineDeliversOverdueOnce() = runBlocking<Unit> {
        val scheduler = scheduler()
        scheduler.rescheduleAll()
        failWrites()
        fire(next(ReminderAlarmKind.NORMAL), scheduler)
        val retry = next(ReminderAlarmKind.RETRY)
        assertEquals(1, retry.attempt)
        restoreWrites()
        fire(retry, scheduler(), task.deadlineTimestamp + 10 * minute)
        assertOverdueOnce()
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun legacyOverdueRetrySurvivesReconciliationUntilDelivery() = runBlocking<Unit> {
        now = task.deadlineTimestamp + minute
        failWrites()
        scheduler().onReminderFired(task.id)
        val retry = next(ReminderAlarmKind.RETRY)
        assertEquals("overdue", retry.occurrence!!.slot)
        scheduler().rescheduleAll()
        assertEquals(retry, next(ReminderAlarmKind.RETRY))
        restoreWrites()
        fire(next(ReminderAlarmKind.RETRY))
        assertOverdueOnce()
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun catchupFiveSecondAlarmPastDeadlineSurvivesReconciliationAndDelivers() = runBlocking<Unit> {
        val near = task.copy(deadlineTimestamp = now + 2_000)
        db.taskDao().updateTask(near)
        scheduler().rescheduleAll()
        val original = next(ReminderAlarmKind.NORMAL)
        assertTrue(original.at > near.deadlineTimestamp)
        now += 3_000
        scheduler().rescheduleAll() // expiry must not erase this already-owed callback
        assertEquals(original.at, next(ReminderAlarmKind.NORMAL).at)
        fire(next(ReminderAlarmKind.NORMAL))
        val delivered = inbox().single()
        assertEquals(ServiceLocale.localizedContext(context).getString(R.string.noti_overdue_title), delivered.title)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun lateCallbackCannotCrossAssignmentGeneration() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        val original = next(ReminderAlarmKind.NORMAL)
        db.taskDao().updateTask(task.copy(reminderGeneration = 1, assignedToUserId = "bin"))
        fire(original, at = task.deadlineTimestamp + minute)
        assertTrue(inbox().isEmpty())
        assertTrue(db.notificationDao().getNotificationsByUser("bin").first().isEmpty())
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun lateCallbackCannotCrossSettingsRevision() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        val original = next(ReminderAlarmKind.NORMAL)
        assertTrue(prefs.setReminderLeadMinutes(60))
        fire(original, at = task.deadlineTimestamp + minute)
        assertTrue(inbox().isEmpty())
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun lateCallbackCannotCrossChangedDeadline() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        val original = next(ReminderAlarmKind.NORMAL)
        val changed = task.copy(deadlineTimestamp = task.deadlineTimestamp + 3_600_000)
        db.taskDao().updateTask(changed)
        fire(original, at = task.deadlineTimestamp + minute)
        assertTrue(inbox().isEmpty())
        assertEquals(changed.deadlineTimestamp, next(ReminderAlarmKind.NORMAL).occurrence!!.deadline)
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun normalCommittedBeforeDeadlineIsNotReplayedAsOverdueAfterInterruptedAcknowledgement() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.afterConsumption = { throw CancellationException("process died after commit") } }
        scheduler.rescheduleAll()
        try { fire(next(ReminderAlarmKind.NORMAL), scheduler); fail("Expected interruption") }
        catch (_: CancellationException) { }
        val delivered = inbox().single()
        db.notificationDao().markAsRead(delivered.id)
        assertTrue(journal().read().attempts.values.single().inFlight)
        now = task.deadlineTimestamp + minute
        fire(next(ReminderAlarmKind.RECOVERY_WATCHDOG), at = now)
        fire(next(ReminderAlarmKind.RETRY))
        assertEquals(listOf(delivered.copy(isRead = true)), inbox())
        assertFalse(db.deadlineReminderDao().getConsumedStages("t", task.deadlineTimestamp, 0).contains("overdue"))
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    private suspend fun tokenWithoutRegistration(recreateToken: Boolean) {
        scheduler().rescheduleAll()
        val record = next(ReminderAlarmKind.NORMAL)
        val pending = requireNotNull(physical(record.id).operation)
        val intent = shadowOf(pending).savedIntent
        manager.cancel(pending) // does not cancel the PendingIntent token
        if (recreateToken) {
            pending.cancel()
            PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        }
        assertNotNull(PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE))
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        scheduler().rescheduleAll()
        assertEquals(record.at, next(ReminderAlarmKind.NORMAL).at)
        assertEquals(record.at, physical(record.id).triggerAtTime)
        fire(next(ReminderAlarmKind.NORMAL))
        assertEquals(1, inbox().size)
    }

    @Test fun existingTokenWithoutAlarmIsReinstalled() = runBlocking<Unit> { tokenWithoutRegistration(true) }
    @Test fun cancellingOnlyAlarmManagerRegistrationIsRepaired() = runBlocking<Unit> { tokenWithoutRegistration(false) }

    @Test fun retryTokenWithoutAlarmKeepsAbsoluteTimeAndBudgetOnRecovery() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        failWrites()
        fire(next(ReminderAlarmKind.NORMAL))
        val retry = next(ReminderAlarmKind.RETRY)
        manager.cancel(requireNotNull(physical(retry.id).operation))
        now += 5_000
        scheduler().rescheduleAll()
        assertEquals(retry.at, physical(retry.id).triggerAtTime)
        assertEquals(1, next(ReminderAlarmKind.RETRY).attempt)
        restoreWrites()
        fire(next(ReminderAlarmKind.RETRY))
        assertEquals(1, inbox().size)
    }

    @Test fun interruptedClaimRecoversByDispatchingSurvivingWakeupWithoutStartup() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.afterClaimAlarm = { throw CancellationException("process died after claim") } }
        scheduler.rescheduleAll()
        try { fire(next(ReminderAlarmKind.NORMAL), scheduler); fail("Expected interruption") }
        catch (_: CancellationException) { }
        assertTrue(inbox().isEmpty())
        fire(next(ReminderAlarmKind.RECOVERY_WATCHDOG), scheduler())
        val retry = next(ReminderAlarmKind.RETRY)
        assertEquals(1, retry.attempt)
        fire(retry, scheduler())
        assertEquals(1, inbox().size)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun finalInterruptedAttemptHasRecoveryWakeupButCannotStartFourthRetry() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        failWrites()
        fire(next(ReminderAlarmKind.NORMAL))
        repeat(2) { fire(next(ReminderAlarmKind.RETRY)) }
        assertEquals(3, next(ReminderAlarmKind.RETRY).attempt)
        val interrupted = scheduler().also { it.afterClaimAlarm = { throw CancellationException("last claim interrupted") } }
        try { fire(next(ReminderAlarmKind.RETRY), interrupted); fail("Expected interruption") }
        catch (_: CancellationException) { }
        fire(next(ReminderAlarmKind.RECOVERY_WATCHDOG), scheduler())
        assertTrue(journal().read().attempts.values.single().exhausted)
        assertEquals(3, journal().read().attempts.values.single().attempt)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertTrue(inbox().isEmpty())
    }

    @Test fun interruptedReconciliationRecoversOnlyThroughItsSurvivingWakeup() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.beforeReconcileAlarms = { throw SQLiteException("read failure") } }
        scheduler.rescheduleAll()
        scheduler.beforeReconcileAlarms = {}
        scheduler.afterReconciliationClaim = { throw CancellationException("process died after reconciliation claim") }
        try { fire(next(ReminderAlarmKind.RECONCILE), scheduler); fail("Expected interruption") }
        catch (_: CancellationException) { }
        assertTrue(journal().read().reconciliationInFlight)
        fire(next(ReminderAlarmKind.RECOVERY_WATCHDOG), scheduler())
        assertEquals(2, next(ReminderAlarmKind.RECONCILE).attempt)
        fire(next(ReminderAlarmKind.RECONCILE), scheduler())
        assertFalse(journal().read().reconciliationInFlight)
        assertTrue(records(ReminderAlarmKind.RECOVERY_WATCHDOG).isEmpty())
        fire(next(ReminderAlarmKind.NORMAL))
        assertEquals(1, inbox().size)
    }

    @Test fun missingGlobalRecoveryRegistrationIsReinstalledWithoutResettingAttempt() = runBlocking<Unit> {
        val scheduler = scheduler().also { it.beforeReconcileAlarms = { throw SQLiteException("read failure") } }
        scheduler.rescheduleAll()
        val original = next(ReminderAlarmKind.RECONCILE)
        manager.cancel(requireNotNull(physical(original.id).operation))
        scheduler.rescheduleAll()
        assertEquals(original.at, physical(original.id).triggerAtTime)
        assertEquals(1, next(ReminderAlarmKind.RECONCILE).attempt)
        scheduler.beforeReconcileAlarms = {}
        fire(next(ReminderAlarmKind.RECONCILE), scheduler)
        assertEquals(1, records(ReminderAlarmKind.NORMAL).size)
    }

    @Test fun oldestWeeklyAlarmUsesLatestDueStageAndConsumesMissedStagesAtomically() = runBlocking<Unit> {
        val deadline = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekly = task.copy(taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3,
            createdAt = deadline - 6 * day, deadlineTimestamp = deadline)
        db.taskDao().updateTask(weekly)
        now = weekly.createdAt
        scheduler().rescheduleAll()
        val oldest = next(ReminderAlarmKind.NORMAL)
        fire(oldest, at = deadline - 2 * 3_600_000)
        assertEquals(setOf("flex_mid_week", "flex_early_pace", "flex_final_day"),
            db.deadlineReminderDao().getConsumedStages(task.id, deadline, 0).toSet())
        val label = SimpleDateFormat("HH:mm dd/MM", ServiceLocale.currentLocale(context)).format(Date(deadline))
        val expected = ServiceLocale.localizedContext(context).getString(R.string.noti_flex_final_day_message,
            weekly.title, weekly.remainingCount, weekly.completedCount, weekly.targetCount, label)
        assertEquals(expected, inbox().single().message)
        assertEquals(deadline - 30 * minute, next(ReminderAlarmKind.NORMAL).at)
        assertEquals(1, records(ReminderAlarmKind.NORMAL).size)
    }

    private suspend fun exhaust(): ReminderAttemptState {
        scheduler().rescheduleAll()
        failWrites()
        fire(next(ReminderAlarmKind.NORMAL))
        repeat(3) { fire(next(ReminderAlarmKind.RETRY)) }
        restoreWrites()
        return journal().read().attempts.values.single().also { assertTrue(it.exhausted) }
    }

    @Test fun deletedPoliciesArePruned() = runBlocking<Unit> {
        exhaust()
        db.taskDao().deleteTask(task)
        scheduler().rescheduleAll()
        assertTrue(journal().read().attempts.isEmpty())
    }

    @Test fun expiredExhaustedPoliciesArePrunedWithoutGeneratingHistoricalReminders() = runBlocking<Unit> {
        exhaust()
        now = task.deadlineTimestamp + minute
        scheduler().rescheduleAll()
        assertTrue(journal().read().attempts.isEmpty())
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertTrue(inbox().isEmpty())
    }

    @Test fun supersededAssignmentPoliciesArePruned() = runBlocking<Unit> {
        val old = exhaust()
        db.taskDao().updateTask(task.copy(reminderGeneration = 1, assignedToUserId = "bin"))
        scheduler().rescheduleAll()
        assertFalse(journal().read().attempts.containsKey(old.occurrence.id))
        assertEquals(1L, next(ReminderAlarmKind.NORMAL).occurrence!!.generation)
    }

    @Test fun supersededDeadlinePoliciesArePruned() = runBlocking<Unit> {
        val old = exhaust()
        db.taskDao().updateTask(task.copy(deadlineTimestamp = task.deadlineTimestamp + day))
        scheduler().rescheduleAll()
        assertFalse(journal().read().attempts.containsKey(old.occurrence.id))
        assertEquals(task.deadlineTimestamp + day, next(ReminderAlarmKind.NORMAL).occurrence!!.deadline)
    }

    @Test fun supersededSettingRevisionPoliciesArePruned() = runBlocking<Unit> {
        val old = exhaust()
        assertTrue(prefs.setReminderLeadMinutes(60))
        scheduler().rescheduleAll()
        assertFalse(journal().read().attempts.containsKey(old.occurrence.id))
        assertEquals(1L, next(ReminderAlarmKind.NORMAL).occurrence!!.settingRevision)
    }

    @Test fun temporaryPendingReviewDoesNotResetExhaustedBudget() = runBlocking<Unit> {
        val exhausted = exhaust()
        db.taskDao().updateTask(task.copy(status = TaskStatus.PENDING_APPROVAL))
        scheduler().rescheduleAll()
        assertEquals(exhausted, journal().read().attempts.values.single())
        db.taskDao().updateTask(task)
        repeat(3) { scheduler().rescheduleAll() }
        assertEquals(exhausted, journal().read().attempts.values.single())
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun queuedRetryDuringTemporaryPauseRetainsItsBudgetWhenWorkResumes() = runBlocking<Unit> {
        scheduler().rescheduleAll()
        failWrites()
        fire(next(ReminderAlarmKind.NORMAL))
        fire(next(ReminderAlarmKind.RETRY))
        val retry = next(ReminderAlarmKind.RETRY)
        assertEquals(2, retry.attempt)
        db.taskDao().updateTask(task.copy(status = TaskStatus.PENDING_APPROVAL))
        fire(retry)
        assertEquals(2, journal().read().attempts.values.single().attempt)
        assertFalse(journal().read().attempts.values.single().inFlight)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        restoreWrites()
        db.taskDao().updateTask(task)
        scheduler().rescheduleAll()
        assertEquals(2, next(ReminderAlarmKind.RETRY).attempt)
        fire(next(ReminderAlarmKind.RETRY), at = now + 1)
        assertEquals(1, inbox().size)
    }

    @Test fun largeDeletedPolicyInventoryIsRemovedInOneReconciliation() = runBlocking<Unit> {
        val policies = (1..900).map { index ->
            val occurrence = ReminderOccurrence("deleted-$index", now + minute, 0, now, "fixed")
            ReminderAttemptState(occurrence, 3, exhausted = true)
        }.associateBy { it.occurrence.id }
        journal().write(ReminderAlarmState(attempts = policies))
        scheduler().rescheduleAll()
        assertTrue(journal().read().attempts.isEmpty())
        assertEquals(1, records(ReminderAlarmKind.NORMAL).size)
    }
}
