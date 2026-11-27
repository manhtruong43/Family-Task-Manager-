package com.family.taskmanager.data.service

import android.content.Context
import androidx.room.withTransaction
import com.family.taskmanager.R
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.DeadlineReminderReceipt
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class CommittedDeadlineReminder(
    val taskId: String,
    val title: String,
    val message: String,
    val shouldPost: Boolean
)

/** Chỉ trả kết quả sau khi cả dấu đã nhắc và thông báo hộp thư đã commit. */
@Singleton
class DeadlineReminderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val preferences: NotificationPreferences
) {
    private val notifications = database.notificationDao()
    private val receipts = database.deadlineReminderDao()

    internal suspend fun currentPlans(now: Long = System.currentTimeMillis()): List<Pair<Task, PlannedReminder>> =
        database.withTransaction {
            val settings = preferences.reminderSettings.value
            readTasksInTx(now, settings).mapNotNull { (task, consumed) ->
                if ("overdue" in consumed) return@mapNotNull null
                DeadlineReminderPlanner.nextReminderFor(task, now,
                    { settings.receiptKey(it) in consumed }, leadMinutesOverride = settings.leadMinutes)
                    ?.let { task to it }
            }
        }

    internal suspend fun scheduleSnapshot(
        now: Long,
        attempts: Map<String, ReminderAttemptState>,
        installed: Collection<ReminderAlarmRecord>
    ): ReminderScheduleSnapshot = database.withTransaction {
        val settings = preferences.reminderSettings.value
        val references = attempts.values.map { it.occurrence } + installed.mapNotNull { it.occurrence }
        val all = mutableMapOf<String, ReminderOccurrencePlan>()
        val alarms = mutableListOf<ReminderOccurrencePlan>()
        val retained = mutableSetOf<String>()
        readTasksInTx(now, settings, references.map { it.taskId }.toSet()).forEach { (task, consumed) ->
            if (task.status == TaskStatus.COMPLETED || (task.isFlexibleWeekly && task.remainingCount == 0)) return@forEach
            val legacyOverdue = references.filter {
                it.taskId == task.id && it.slot == "overdue" && it.deadline == task.deadlineTimestamp &&
                    it.generation == task.reminderGeneration && it.dueAt == task.deadlineTimestamp &&
                    now >= task.deadlineTimestamp && "overdue" !in consumed
            }.distinctBy { it.id }.map {
                ReminderOccurrencePlan(it, PlannedReminder(ReminderStage.FIXED_LEAD, "overdue", it.dueAt))
            }
            val groups = occurrencePlans(task, settings, consumed) + legacyOverdue
            val ids = groups.map { it.occurrence.id }.toSet()
            val owed = installed.mapNotNull { it.occurrence }.filter {
                it.taskId == task.id && attempts[it.id]?.exhausted != true
            }.map { it.id }.toSet() + attempts.values.filter {
                it.occurrence.taskId == task.id && !it.exhausted && (it.inFlight || it.retryAt != null)
            }.map { it.occurrence.id }
            attempts.values.filter { it.occurrence.id in ids }.forEach {
                // Pending approval is temporary: retain its budget. Expired exhausted work,
                // deleted tasks, old deadlines/generations/revisions and consumed stages are pruned.
                if (task.deadlineTimestamp > now || it.occurrence.id in owed) retained.add(it.occurrence.id)
            }
            if (task.status !in DeadlineReminderPlanner.REMINDABLE_STATUSES) return@forEach
            val eligible = if (task.deadlineTimestamp > now) groups else groups.filter { it.occurrence.id in owed }
            eligible.forEach { all[it.occurrence.id] = it }
            val blockedStages = groups.filter { it.occurrence.id in retained }
                .flatMap { it.plan.stageKeysToMark }.toSet()
            if (task.deadlineTimestamp > now) {
                val next = DeadlineReminderPlanner.nextReminderFor(task, now,
                    { settings.receiptKey(it) in consumed || it in blockedStages },
                    leadMinutesOverride = settings.leadMinutes)
                if (next != null) {
                    groups.firstOrNull { it.plan.dueAtMillis == next.dueAtMillis }?.let { alarms += it.copy(plan = next) }
                    next.futureWakeups.values.forEach { time ->
                        groups.firstOrNull { it.plan.dueAtMillis == time }?.let { alarms += it }
                    }
                }
            } else {
                // Preserve an owed late callback/retry; do not generate fresh reminders for history.
                val due = eligible.filter { it.occurrence.id !in retained }
                due.maxByOrNull { it.plan.dueAtMillis }?.let {
                    alarms += it.copy(plan = it.plan.copy(triggerAtMillis = now + DeadlineReminderScheduler.MIN_DELAY_MS,
                        stageKeysToMark = due.flatMap { row -> row.plan.stageKeysToMark }.toSet()))
                }
            }
        }
        ReminderScheduleSnapshot(all, alarms.distinctBy { it.occurrence.id }, retained)
    }

    /** Đọc theo lô; không nhập lại prefs cho lịch sử hay cho phiên giao việc khác. */
    private suspend fun readTasksInTx(
        now: Long, settings: ReminderSettings, referencedIds: Set<String> = emptySet()
    ): List<Pair<Task, Set<String>>> {
        val active = notifications.getReminderCandidates(now)
        val activeIds = active.map { it.id }.toSet()
        val extra = (referencedIds - activeIds).toList().chunked(400).flatMap { notifications.getTasksByIds(it) }
        val tasks = active + extra
        if (tasks.isEmpty()) return emptyList()
        val receiptRows = (if (active.isEmpty()) emptyList() else receipts.getForReminderCandidates(now)) +
            extra.filter { it.status != TaskStatus.COMPLETED && !(it.isFlexibleWeekly && it.remainingCount == 0) }
                .map { it.id }.chunked(400).flatMap { receipts.getForTaskIds(it) }
        val stored = receiptRows.groupBy { it.taskId }
        val legacy = if (tasks.any { it.reminderGeneration == 0L }) preferences.legacyReminderSnapshot() else null
        val imports = mutableListOf<DeadlineReminderReceipt>()
        val rows = tasks.map { task ->
            val stages = stored[task.id].orEmpty().filter {
                it.deadlineTimestamp == task.deadlineTimestamp && it.generation == task.reminderGeneration
            }.map { it.stageKey }.toSet()
            val old = if (task.reminderGeneration == 0L)
                legacy?.consumedStages(task.id, task.deadlineTimestamp).orEmpty() else emptySet()
            val consumed = normalizeLegacyFixed(task, settings, stages + old)
            if (task.status in DeadlineReminderPlanner.REMINDABLE_STATUSES &&
                !(task.isFlexibleWeekly && task.remainingCount == 0)) {
                imports += (consumed - stages).map { receipt(task, it) }
            }
            task to consumed
        }
        if (imports.isNotEmpty()) receipts.insert(imports)
        return rows
    }

    private fun normalizeLegacyFixed(task: Task, settings: ReminderSettings, stages: Set<String>): Set<String> {
        val fixed = setOf("fixed_30", "fixed_60", "fixed_120")
        // Revision 0 là cấu hình được nâng cấp; đổi lựa chọn chủ động có revision riêng.
        return if (!task.isFlexibleWeekly && settings.revision == 0L && stages.any { it in fixed })
            stages + settings.receiptKey("fixed_${settings.leadMinutes}") else stages
    }

    private fun occurrencePlans(task: Task, settings: ReminderSettings, consumed: Set<String>): List<ReminderOccurrencePlan> {
        if ("overdue" in consumed || task.deadlineTimestamp <= 0) return emptyList()
        val candidates = if (task.isFlexibleWeekly) DeadlineReminderPlanner.buildFlexibleCandidates(task, settings.leadMinutes)
        else listOf(PlannedReminder(ReminderStage.FIXED_LEAD, "fixed_${settings.leadMinutes}",
            task.deadlineTimestamp - settings.leadMinutes * 60_000L))
        return candidates.filterNot { settings.receiptKey(it.stageKey) in consumed }
            .groupBy { it.dueAtMillis }.values.map { sameTime ->
                val plan = sameTime.maxBy { it.stage.ordinal }.copy(
                    stageKeysToMark = sameTime.flatMap { it.stageKeysToMark }.toSet())
                ReminderOccurrencePlan(ReminderOccurrence(task.id, task.deadlineTimestamp, task.reminderGeneration,
                    plan.dueAtMillis, if (task.isFlexibleWeekly) "flex" else "fixed",
                    if (task.isFlexibleWeekly) 0 else settings.revision), plan)
            }
    }

    /** Chỉ đọc để hỗ trợ callback cũ; broadcast mới dùng danh tính đã ghi trong nhật ký. */
    internal suspend fun previewOccurrence(taskId: String, now: Long): ReminderOccurrencePlan? = database.withTransaction {
        val task = notifications.getTaskById(taskId) ?: return@withTransaction null
        if (task.status !in DeadlineReminderPlanner.REMINDABLE_STATUSES || task.deadlineTimestamp <= 0) return@withTransaction null
        val settings = preferences.reminderSettings.value
        if (task.deadlineTimestamp <= now) return@withTransaction ReminderOccurrencePlan(
            ReminderOccurrence(task.id, task.deadlineTimestamp, task.reminderGeneration, task.deadlineTimestamp, "overdue"),
            PlannedReminder(ReminderStage.FIXED_LEAD, "overdue", task.deadlineTimestamp))
        val stored = receipts.getConsumedStages(task.id, task.deadlineTimestamp, task.reminderGeneration).toSet()
        val old = if (task.reminderGeneration == 0L) preferences.legacyReminderSnapshot()
            .consumedStages(task.id, task.deadlineTimestamp) else emptySet()
        val consumed = normalizeLegacyFixed(task, settings, stored + old)
        val next = DeadlineReminderPlanner.nextReminderFor(task, now,
            { settings.receiptKey(it) in consumed }, leadMinutesOverride = settings.leadMinutes) ?: return@withTransaction null
        occurrencePlans(task, settings, consumed).firstOrNull { it.plan.dueAtMillis == next.dueAtMillis }
            ?.copy(plan = next)
    }

    internal suspend fun consumeDue(
        taskId: String,
        now: Long = System.currentTimeMillis(),
        expected: ReminderOccurrence? = null,
        blocked: Set<String> = emptySet()
    ): CommittedDeadlineReminder? =
        database.withTransaction {
            // Cùng transaction với việc đổi người nhận/rời nhóm/hủy việc.
            val task = notifications.getTaskById(taskId) ?: return@withTransaction null
            if (task.status !in DeadlineReminderPlanner.REMINDABLE_STATUSES || task.deadlineTimestamp <= 0 ||
                (task.isFlexibleWeekly && task.remainingCount == 0)) {
                return@withTransaction null
            }
            val settings = preferences.reminderSettings.value
            if (expected != null && (expected.taskId != task.id || expected.deadline != task.deadlineTimestamp ||
                    expected.generation != task.reminderGeneration ||
                    (expected.slot == "fixed" && expected.settingRevision != settings.revision))) return@withTransaction null
            val consumed = consumedStagesInTx(task, settings)
            val lead = settings.leadMinutes
            val overdue = task.deadlineTimestamp <= now
            if ("overdue" in consumed) return@withTransaction null
            val groups = occurrencePlans(task, settings, consumed)
            if (expected != null && expected.slot != "overdue") {
                val target = groups.firstOrNull { it.occurrence.id == expected.id } ?: return@withTransaction null
                if (target.plan.dueAtMillis > now) return@withTransaction null
            } else if (expected?.slot == "overdue" && !overdue) return@withTransaction null
            val due = groups.filter {
                it.plan.dueAtMillis <= now && (it.occurrence.id !in blocked || it.occurrence.id == expected?.id)
            }
            val plan = due.maxWithOrNull(compareBy<ReminderOccurrencePlan> { it.plan.dueAtMillis }
                .thenBy { it.plan.stage.ordinal })?.plan?.copy(
                stageKeysToMark = due.flatMap { it.plan.stageKeysToMark }.toSet())
            if (!overdue && (plan == null || plan.dueAtMillis > now)) return@withTransaction null
            val stage = plan?.stageKey ?: "overdue"
            if (settings.receiptKey(stage) in consumed) return@withTransaction null
            val stages = if (overdue) {
                setOf("overdue") + due.flatMap { it.plan.stageKeysToMark }
            } else requireNotNull(plan).stageKeysToMark
            val (title, message) = messageFor(task, plan, overdue, lead)
            val receivers = if (task.assignedToUserId.isNotBlank()) listOf(task.assignedToUserId)
                else notifications.getFamilyMemberIds(task.familyId)
            receipts.insert(stages.map { receipt(task, settings.receiptKey(it)) })
            val inserted = notifications.insertDeadlineNotifications(receivers.map { userId ->
                NotificationItem(
                    id = notificationId(task, settings.receiptKey(stage), userId),
                    userId = userId, title = title, message = message, timestamp = now
                )
            })
            CommittedDeadlineReminder(task.id, title, message, inserted.any { it != -1L })
        }

    private fun receipt(task: Task, stage: String) = DeadlineReminderReceipt(
        task.id, task.deadlineTimestamp, task.reminderGeneration, stage
    )

    private suspend fun consumedStagesInTx(task: Task, settings: ReminderSettings): Set<String> {
        val stored = receipts.getConsumedStages(task.id, task.deadlineTimestamp, task.reminderGeneration).toSet()
        // Chỉ nhập dấu từ phiên bản cũ, không ghi thêm vào SharedPreferences.
        // Lần giao việc mới không được kế thừa dấu của người nhận trước.
        val legacy = if (task.reminderGeneration == 0L) preferences.legacyReminderSnapshot()
            .consumedStages(task.id, task.deadlineTimestamp) else emptySet()
        val normalized = normalizeLegacyFixed(task, settings, stored + legacy)
        val imports = normalized - stored
        if (imports.isNotEmpty()) receipts.insert(imports.map { receipt(task, it) })
        return normalized
    }

    private fun notificationId(task: Task, stage: String, userId: String): String {
        // Generation 0 giữ ID cũ để thử lại không ghi đè thông báo đã đọc từ bản trước.
        val generation = if (task.reminderGeneration == 0L) "" else "g${task.reminderGeneration}#"
        return UUID.nameUUIDFromBytes(
            "${task.id}@${task.deadlineTimestamp}#$generation$stage#$userId".toByteArray(Charsets.UTF_8)
        ).toString()
    }

    private fun messageFor(task: Task, plan: PlannedReminder?, overdue: Boolean, lead: Int): Pair<String, String> {
        val res = ServiceLocale.localizedContext(context)
        val deadline = SimpleDateFormat("HH:mm dd/MM", ServiceLocale.currentLocale(context)).format(Date(task.deadlineTimestamp))
        val assignee = task.assignedToUserName.takeIf { task.assignedToUserId.isNotBlank() }
            ?: res.getString(R.string.noti_everyone)
        val leadLabel = res.getString(when (lead) {
            60 -> R.string.noti_lead_1h
            120 -> R.string.noti_lead_2h
            else -> R.string.noti_lead_30m
        })
        return when {
            overdue -> res.getString(R.string.noti_overdue_title) to res.getString(
                R.string.noti_overdue_message, task.title, deadline, assignee
            )
            !task.isFlexibleWeekly -> res.getString(R.string.noti_reminder_title) to res.getString(
                R.string.noti_reminder_lead_message, leadLabel, task.title, deadline, assignee
            )
            plan?.stage == ReminderStage.FLEX_LAST_CALL -> res.getString(R.string.noti_flex_reminder_title) to res.getString(
                R.string.noti_flex_last_call_message, leadLabel, task.title, task.completedCount, task.targetCount
            )
            plan?.stage == ReminderStage.FLEX_FINAL_DAY -> res.getString(R.string.noti_flex_reminder_title) to res.getString(
                R.string.noti_flex_final_day_message, task.title, task.remainingCount, task.completedCount, task.targetCount, deadline
            )
            else -> res.getString(R.string.noti_flex_reminder_title) to res.getString(
                R.string.noti_flex_early_message, task.title, task.completedCount, task.targetCount, task.remainingCount, deadline
            )
        }
    }

}
