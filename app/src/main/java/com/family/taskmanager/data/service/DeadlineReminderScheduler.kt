package com.family.taskmanager.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.util.Log
import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.repository.deliverCommittedSystemNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Báo thức, retry và giới hạn retry cùng dùng một nhật ký bền và một mutex. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DeadlineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val preferences: NotificationPreferences,
    private val reminderStore: DeadlineReminderStore,
    private val pushNotificationService: PushNotificationService
) {
    companion object {
        const val ACTION_DEADLINE_REMINDER = "com.family.taskmanager.action.DEADLINE_REMINDER"
        const val ACTION_DEADLINE_RETRY = "com.family.taskmanager.action.DEADLINE_RETRY"
        const val ACTION_RECONCILE_RETRY = "com.family.taskmanager.action.RECONCILE_RETRY"
        const val ACTION_RECOVERY_WATCHDOG = "com.family.taskmanager.action.REMINDER_RECOVERY"
        internal const val RECOVERY_INTERVAL_MS = 60_000L
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_RETRY_ATTEMPT = "extra_retry_attempt"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_STAGE_KEY = "extra_stage_key"
        internal val RETRY_DELAYS_MS = longArrayOf(30_000L, 120_000L, 300_000L)
        const val MIN_DELAY_MS = 5_000L
    }

    internal var observationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var clock: () -> Long = System::currentTimeMillis
    internal var beforeReconcileAlarms: suspend () -> Unit = {}
    internal var beforeRetryScheduling: suspend () -> Unit = {}
    internal var beforeClaimAlarm: suspend () -> Unit = {}
    internal var afterClaimAlarm: suspend () -> Unit = {}
    internal var afterConsumption: suspend () -> Unit = {}
    internal var afterReconciliationClaim: suspend () -> Unit = {}
    internal var beforeInstallAlarm: (ReminderAlarmRecord) -> Unit = {}
    internal var afterCancelObsoleteAlarms: () -> Unit = {}
    internal var reminderChanges: () -> Flow<Unit> = {
        combine(notificationDao.observeTasksWithDeadline().distinctUntilChanged(),
            preferences.reminderSettings) { _, _ -> Unit }
    }
    private data class ObservationRequest(val revision: Long = 0, val recoveryAttempt: Int = 0)
    private val observationRequests = MutableStateFlow(ObservationRequest())
    private val observationFailed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val journal = ReminderAlarmJournal(context)
    private val deliveryMutex = Mutex()
    private val alarmsMutex = Mutex()
    private val activeDeliveries = mutableSetOf<String>()
    private val awaitingReconciliation = mutableSetOf<String>()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        observationScope.launch {
            observationRequests.flatMapLatest { request ->
                var attempt = request.recoveryAttempt
                reminderChanges().map {
                    observationFailed.set(false)
                    val current = attempt
                    attempt = 0
                    current
                }.catch { error ->
                    if (error !is SQLiteException) throw error
                    observationFailed.set(true)
                    Log.e("DeadlineReminder", "Observation failed", error)
                    recoverReconciliation(attempt)
                }
            }.onStart { emit(0) }.collect { reconcileWithRecovery(it) }
        }
    }

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) { reconcileWithRecovery(0) }

    suspend fun onReconciliationRetry(completedAttempt: Int) = withContext(Dispatchers.IO) {
        try {
            val accepted = alarmsMutex.withLock {
                val state = journal.read()
                val pending = state.alarms[ReminderAlarmJournal.GLOBAL_ID]
                if (pending == null || pending.attempt != completedAttempt) false
                else {
                    applyStateLocked(state.copy(alarms = state.alarms - ReminderAlarmJournal.GLOBAL_ID,
                        reconciliationAttempt = maxOf(state.reconciliationAttempt, completedAttempt),
                        reconciliationInFlight = true), emptySet())
                    true
                }
            }
            if (accepted) {
                afterReconciliationClaim()
                reconcileWithRecovery(completedAttempt.coerceAtLeast(0))
            }
        } catch (error: ReminderJournalException) {
            Log.e("DeadlineReminder", "Cannot acknowledge reconciliation alarm", error)
        }
    }

    /** Watchdog không phải một lần gửi: chỉ dự trữ lượt thử tiếp theo cho claim đã bị ngắt. */
    suspend fun onRecoveryWakeup() = withContext(Dispatchers.IO) {
        try {
            var recoverGlobal = false
            var globalAttempt = 0
            alarmsMutex.withLock {
                val state = journal.read()
                if (ReminderAlarmJournal.WATCHDOG_ID !in state.alarms) return@withLock
                val abandoned = state.attempts.values.filter { it.inFlight && it.occurrence.id !in activeDeliveries }
                val attempts = state.attempts.toMutableMap()
                var alarms = state.alarms
                val ensure = mutableSetOf<String>()
                abandoned.forEach { policy ->
                    val failed = afterFailure(policy)
                    attempts[policy.occurrence.id] = failed
                    alarms = alarms.filterValues { it.occurrence?.id != policy.occurrence.id }
                    failed.retryAt?.let { at ->
                        val candidate = ReminderOccurrencePlan(policy.occurrence,
                            PlannedReminder(ReminderStage.FIXED_LEAD, policy.stageKey, policy.occurrence.dueAt))
                        val retry = alarmRecord(candidate, ReminderAlarmKind.RETRY, at, failed.attempt)
                        alarms = alarms + (retry.id to retry)
                        ensure.add(retry.id)
                    }
                }
                val live = attempts.values.any { it.inFlight }
                recoverGlobal = state.reconciliationInFlight || (abandoned.isEmpty() && !live)
                globalAttempt = state.reconciliationAttempt
                if (abandoned.isNotEmpty()) {
                    applyStateLocked(state.copy(alarms = alarms, attempts = attempts), ensure)
                }
            }
            // Một retry đã lưu hoặc một global recovery thay thế watchdog trước khi hủy nó.
            if (recoverGlobal) recoverReconciliation(globalAttempt)
        } catch (error: ReminderJournalException) {
            // Watchdog là repeating, nên vẫn còn wake-up nếu ghi nhật ký tiếp tục thất bại.
            Log.e("DeadlineReminder", "Recovery journal unavailable; watchdog remains armed", error)
        }
    }

    private suspend fun reconcileWithRecovery(attempt: Int) {
        try {
            alarmsMutex.withLock { syncAlarmsLocked() }
        } catch (error: SQLiteException) {
            Log.e("DeadlineReminder", "Reconciliation failed", error)
            recoverReconciliation(attempt)
        } catch (error: ReminderJournalException) {
            Log.e("DeadlineReminder", "Alarm journal unavailable", error)
            recoverReconciliation(attempt)
        }
        if (started.get() && observationFailed.getAndSet(false)) {
            observationRequests.update { ObservationRequest(it.revision + 1, attempt) }
        }
    }

    private suspend fun recoverReconciliation(attempt: Int) {
        try {
            alarmsMutex.withLock {
                val state = journal.read()
                val existing = state.alarms[ReminderAlarmJournal.GLOBAL_ID]
                if (existing != null) {
                    applyStateLocked(state.copy(reconciliationInFlight = false), setOf(existing.id))
                    return@withLock
                }
                val completed = maxOf(attempt, state.reconciliationAttempt)
                val delay = RETRY_DELAYS_MS.getOrNull(completed)
                if (delay == null) {
                    applyStateLocked(state.copy(alarms = state.alarms - ReminderAlarmJournal.GLOBAL_ID,
                        reconciliationInFlight = false), emptySet())
                } else {
                    val record = ReminderAlarmRecord(ReminderAlarmJournal.GLOBAL_ID, ReminderAlarmKind.RECONCILE,
                        clock() + delay, completed + 1)
                    applyStateLocked(state.copy(alarms = state.alarms + (record.id to record),
                        reconciliationAttempt = completed + 1, reconciliationInFlight = false), setOf(record.id))
                }
            }
        } catch (error: ReminderJournalException) {
            // Không đặt báo thức mới nếu chưa lưu được danh tính/giới hạn của nó.
            Log.e("DeadlineReminder", "Cannot persist reconciliation recovery", error)
        }
    }

    private suspend fun syncAlarmsLocked() {
        val now = clock()
        val state = journal.read()
        val snapshot = reminderStore.scheduleSnapshot(now, state.attempts, state.alarms.values)
        beforeReconcileAlarms()
        val attempts = state.attempts.filterKeys { it in snapshot.retainedAttempts }.toMutableMap()

        // One-shot đã biến mất nhưng kết quả chưa được xác nhận: tính lượt đó là đã dùng.
        // Bao phủ chết tiến trình trước/sau callback, không phát lại attempt 0 vô hạn.
        state.alarms.values.filter { it.occurrence != null }.forEach { record ->
            val occurrence = requireNotNull(record.occurrence)
            val policy = attempts[occurrence.id]
            if (occurrence.id in snapshot.occurrences && record.at <= now &&
                pendingIntent(record, PendingIntent.FLAG_NO_CREATE) == null &&
                (policy == null || (!policy.exhausted && policy.retryAt != null && policy.attempt == record.attempt))) {
                attempts[occurrence.id] = ReminderAttemptState(occurrence, record.attempt, inFlight = true, stageKey = record.stageKey)
            }
        }
        attempts.values.toList().filter {
            !it.exhausted && (it.occurrence.id !in snapshot.occurrences || it.occurrence.id in awaitingReconciliation)
        }.forEach { policy ->
            // Temporary pause / early callback: preserve the existing budget without
            // treating a no-op as a failed delivery or leaving a live claim behind.
            attempts[policy.occurrence.id] = policy.copy(inFlight = false,
                retryAt = policy.retryAt ?: maxOf(now, policy.occurrence.dueAt))
        }
        attempts.values.toList().filter { it.inFlight && it.occurrence.id !in activeDeliveries && it.occurrence.id in snapshot.occurrences }.forEach { policy ->
            attempts[policy.occurrence.id] = afterFailure(policy)
        }
        val desired = mutableMapOf<String, ReminderAlarmRecord>()
        snapshot.alarms.filter { it.occurrence.id !in attempts }.forEach { candidate ->
            val record = alarmRecord(candidate, ReminderAlarmKind.NORMAL, candidate.plan.triggerAtMillis, 0)
            val existing = state.alarms[record.id]
            // Không lùi báo thức đang chờ thêm 5 giây mỗi lần một hàng DB khác thay đổi.
            desired[record.id] = if (existing != null && existing.at <= record.at) record.copy(at = existing.at) else record
        }
        attempts.values.filter { !it.exhausted && it.retryAt != null }.forEach { policy ->
            snapshot.occurrences[policy.occurrence.id]?.let { candidate ->
                val record = alarmRecord(candidate, ReminderAlarmKind.RETRY, requireNotNull(policy.retryAt), policy.attempt)
                desired[record.id] = record
            }
        }
        // Những ID không còn hợp lệ vẫn có trong state.alarms để hủy được sau process death,
        // kể cả task đã bị xóa hoặc không còn xuất hiện trong truy vấn lập lịch.
        applyStateLocked(ReminderAlarmState(desired, attempts, reconciliationAttempt = 0), desired.keys)
    }

    private fun afterFailure(current: ReminderAttemptState): ReminderAttemptState {
        val delay = RETRY_DELAYS_MS.getOrNull(current.attempt)
        return if (delay == null) current.copy(retryAt = null, exhausted = true, inFlight = false)
        else current.copy(attempt = current.attempt + 1, retryAt = clock() + delay, inFlight = false)
    }

    private fun alarmRecord(plan: ReminderOccurrencePlan, kind: ReminderAlarmKind, at: Long, attempt: Int) =
        ReminderAlarmRecord("${plan.occurrence.id}:${kind.name}", kind, at, attempt, plan.occurrence, plan.plan.stageKey)

    private fun alarmIntent(record: ReminderAlarmRecord): Intent =
        Intent(context, DeadlineReminderReceiver::class.java).apply {
            action = when (record.kind) {
                ReminderAlarmKind.NORMAL -> ACTION_DEADLINE_REMINDER
                ReminderAlarmKind.RETRY -> ACTION_DEADLINE_RETRY
                ReminderAlarmKind.RECONCILE -> ACTION_RECONCILE_RETRY
                ReminderAlarmKind.RECOVERY_WATCHDOG -> ACTION_RECOVERY_WATCHDOG
            }
            data = Uri.Builder().scheme("familytask").authority("alarm").appendPath(record.id).build()
            putExtra(EXTRA_ALARM_ID, record.id)
            putExtra(EXTRA_TASK_ID, record.occurrence?.taskId)
            putExtra(EXTRA_RETRY_ATTEMPT, record.attempt)
            putExtra(EXTRA_STAGE_KEY, record.stageKey)
        }

    private fun pendingIntent(record: ReminderAlarmRecord, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alarmIntent(record), flags or PendingIntent.FLAG_IMMUTABLE or
            if (record.kind == ReminderAlarmKind.RECOVERY_WATCHDOG) 0 else PendingIntent.FLAG_ONE_SHOT)

    private fun cancelSystemAlarm(record: ReminderAlarmRecord) {
        val pending = pendingIntent(record, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /** Write-ahead inventory. Hai lần ghi theo lô, không commit prefs cho từng báo thức. */
    private fun applyStateLocked(next: ReminderAlarmState, ensureInstalled: Set<String>) {
        val old = journal.read()
        val needsRecovery = next.reconciliationInFlight || next.attempts.values.any { it.inFlight }
        val watchdog = old.alarms[ReminderAlarmJournal.WATCHDOG_ID] ?: ReminderAlarmRecord(
            ReminderAlarmJournal.WATCHDOG_ID, ReminderAlarmKind.RECOVERY_WATCHDOG, clock() + RECOVERY_INTERVAL_MS, 0)
        val alarms = if (needsRecovery) next.alarms + (watchdog.id to watchdog)
            else next.alarms - ReminderAlarmJournal.WATCHDOG_ID
        val target = next.copy(alarms = alarms)
        journal.write(target.copy(alarms = old.alarms + alarms))
        // PendingIntent tồn tại không chứng minh AlarmManager đã nhận lệnh.
        // Đặt lại idempotently với thời điểm tuyệt đối và budget đã lưu.
        val ensure = (ensureInstalled + if (needsRecovery) setOf(watchdog.id) else emptySet()).intersect(alarms.keys)
        ensure.forEach { id ->
            val record = alarms.getValue(id)
            beforeInstallAlarm(record)
            setAlarm(record, requireNotNull(pendingIntent(record, PendingIntent.FLAG_UPDATE_CURRENT)))
        }
        // Chuyển giao wake-up trước, rồi mới hủy wake-up cũ.
        (old.alarms.keys - alarms.keys).forEach { cancelSystemAlarm(old.alarms.getValue(it)) }
        afterCancelObsoleteAlarms()
        journal.write(target)
    }

    private fun setAlarm(record: ReminderAlarmRecord, pending: PendingIntent) {
        val at = record.at
        if (record.kind == ReminderAlarmKind.RECOVERY_WATCHDOG) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, RECOVERY_INTERVAL_MS, pending)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun currentAlarm(state: ReminderAlarmState, alarmId: String): ReminderAlarmRecord? =
        state.alarms[alarmId] ?: if (alarmId.endsWith(":NORMAL"))
            state.alarms[alarmId.removeSuffix(":NORMAL") + ":RETRY"] else null

    /** Broadcast mới chỉ được thực hiện nếu danh tính vẫn nằm trong nhật ký hiện tại. */
    suspend fun onAlarmFired(alarmId: String) = withContext(Dispatchers.IO) { deliveryMutex.withLock {
        try {
            val record = alarmsMutex.withLock {
                val state = journal.read()
                currentAlarm(state, alarmId)
            } ?: return@withLock
            if (record.kind == ReminderAlarmKind.RECONCILE || record.kind == ReminderAlarmKind.RECOVERY_WATCHDOG) return@withLock
            executeOccurrence(record, requireInventory = true)
        } catch (error: ReminderJournalException) {
            Log.e("DeadlineReminder", "Cannot claim alarm", error)
            recoverReconciliation(0)
        }
    }

    }

    /** Tương thích callback cũ, đồng thời vẫn tôn trọng exhaustion đã lưu của cùng lượt nhắc. */
    suspend fun onReminderFired(taskId: String, retryAttempt: Int = 0) = withContext(Dispatchers.IO) { deliveryMutex.withLock {
        try {
            val preview = try { reminderStore.previewOccurrence(taskId, clock()) } catch (error: SQLiteException) {
                Log.e("DeadlineReminder", "Cannot resolve legacy callback", error)
                recoverReconciliation(0)
                return@withLock
            }
            if (preview == null) {
                rescheduleAll()
                return@withLock
            }
            executeOccurrence(alarmRecord(preview, if (retryAttempt == 0) ReminderAlarmKind.NORMAL else ReminderAlarmKind.RETRY,
                clock(), retryAttempt.coerceAtLeast(0)), requireInventory = false)
        } catch (error: ReminderJournalException) {
            Log.e("DeadlineReminder", "Cannot claim legacy callback", error)
            recoverReconciliation(0)
        }
    }

    }

    private suspend fun executeOccurrence(record: ReminderAlarmRecord, requireInventory: Boolean) {
        var claimedRecord = record
        var occurrence = requireNotNull(record.occurrence)
        beforeClaimAlarm()
        val blocked = alarmsMutex.withLock {
            val state = journal.read()
            // Nhận danh tính và dự trữ budget trong cùng lượt khóa. Recovery có thể
            // đổi normal thành retry khi callback đang xếp hàng; luôn dùng budget mới nhất.
            if (requireInventory) {
                claimedRecord = currentAlarm(state, record.id) ?: return
                occurrence = requireNotNull(claimedRecord.occurrence)
            }
            val previous = state.attempts[occurrence.id]
            if (previous?.exhausted == true || (previous != null && previous.attempt > claimedRecord.attempt)) return
            val policy = ReminderAttemptState(occurrence, claimedRecord.attempt, inFlight = true, stageKey = claimedRecord.stageKey)
            val remaining = state.alarms.filterValues { it.occurrence?.id != occurrence.id }
            applyStateLocked(state.copy(alarms = remaining, attempts = state.attempts + (occurrence.id to policy)), emptySet())
            activeDeliveries.add(occurrence.id)
            state.attempts.keys - occurrence.id
        }
        try {
        afterClaimAlarm()
        val committed = try {
            reminderStore.consumeDue(occurrence.taskId, clock(), occurrence, blocked)
        } catch (error: SQLiteException) {
            Log.e("DeadlineReminder", "Consumption failed; reserving bounded retry", error)
            beforeRetryScheduling()
            alarmsMutex.withLock {
                val state = journal.read()
                val current = state.attempts[occurrence.id] ?: return@withLock
                val failed = afterFailure(current)
                val candidate = ReminderOccurrencePlan(occurrence,
                    PlannedReminder(ReminderStage.FIXED_LEAD, claimedRecord.stageKey, occurrence.dueAt))
                val retry = failed.retryAt?.let { alarmRecord(candidate, ReminderAlarmKind.RETRY, it, failed.attempt) }
                val remaining = state.alarms.filterValues { it.occurrence?.id != occurrence.id }
                val next = state.copy(alarms = if (retry == null) remaining else remaining + (retry.id to retry),
                    attempts = state.attempts + (occurrence.id to failed))
                applyStateLocked(next, if (retry == null) emptySet() else setOf(retry.id))
            }
            return
        }
        if (committed == null) {
            alarmsMutex.withLock { awaitingReconciliation.add(occurrence.id) }
            // Reconciliation uses fresh metadata to distinguish paused, obsolete,
            // consumed and not-yet-due work before retiring the recovery wake-up.
            rescheduleAll()
            return
        }
        afterConsumption()
        // Sau commit, lỗi đối soát/nhật ký không được đổi thành một lần gửi lại nhiệm vụ.
        try {
            alarmsMutex.withLock {
                val state = journal.read()
                applyStateLocked(state.copy(attempts = state.attempts - occurrence.id), emptySet())
            }
        } catch (error: ReminderJournalException) {
            Log.e("DeadlineReminder", "Consumption committed; journal acknowledgement pending", error)
        }
        if (committed.shouldPost) {
            deliverCommittedSystemNotification(
                post = { pushNotificationService.sendDeadlineReminder(committed.taskId, committed.title, committed.message) },
                onExpectedFailure = { Log.w("DeadlineReminder", "Inbox committed; system notification unavailable", it) },
                onUnexpectedFailure = { Log.e("DeadlineReminder", "Inbox committed; system notification failed", it) }
            )
        }
        rescheduleAll()
        } finally {
            withContext(NonCancellable) { alarmsMutex.withLock {
                activeDeliveries.remove(occurrence.id)
                awaitingReconciliation.remove(occurrence.id)
            } }
        }
    }
}
