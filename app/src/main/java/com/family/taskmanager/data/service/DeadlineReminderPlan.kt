package com.family.taskmanager.data.service

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import java.util.Calendar

enum class ReminderStage {
    FIXED_LEAD, FLEX_MID_WEEK, FLEX_EARLY_PACE, FLEX_FINAL_DAY, FLEX_LAST_CALL
}

data class PlannedReminder(
    val stage: ReminderStage,
    val stageKey: String,
    val triggerAtMillis: Long,
    /** Mốc nghiệp vụ gốc, không bị đẩy sang now + delay khi bắt kịp mốc đã lỡ. */
    val dueAtMillis: Long = triggerAtMillis,
    /** Các mốc trùng giờ hoặc đã lỡ được xử lý bằng cùng một lần nhắc. */
    val stageKeysToMark: Set<String> = setOf(stageKey),
    /** Các mốc chưa nhắc sau mốc chính; hẹn độc lập để một lỗi không làm mất cả tuần. */
    val futureWakeups: Map<String, Long> = emptyMap()
)

/** Lập lịch nhắc; scheduler truyền khoảng nhắc chung của ứng dụng qua leadMinutesOverride. */
object DeadlineReminderPlanner {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    val REMINDABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)

    fun normalizedLeadMinutes(leadMinutes: Int): Int =
        leadMinutes.takeIf { it in Task.ALLOWED_REMINDER_LEAD_MINUTES }
            ?: Task.DEFAULT_REMINDER_LEAD_MINUTES

    fun nextReminderFor(
        task: Task,
        nowMillis: Long,
        wasReminded: (stageKey: String) -> Boolean,
        minDelayMs: Long = DeadlineReminderScheduler.MIN_DELAY_MS,
        leadMinutesOverride: Int? = null
    ): PlannedReminder? {
        if (task.status !in REMINDABLE_STATUSES || task.deadlineTimestamp <= nowMillis) return null
        val leadMinutes = normalizedLeadMinutes(leadMinutesOverride ?: task.reminderLeadMinutes)
        if (!task.isFlexibleWeekly) {
            val key = "fixed_$leadMinutes"
            if (wasReminded(key)) return null
            val dueAt = task.deadlineTimestamp - leadMinutes * MINUTE_MS
            return PlannedReminder(
                ReminderStage.FIXED_LEAD, key, maxOf(dueAt, nowMillis + minDelayMs), dueAt
            )
        }
        if (task.remainingCount <= 0) return null
        val pending = buildFlexibleCandidates(task, leadMinutes).filterNot { wasReminded(it.stageKey) }
        if (pending.isEmpty()) return null
        val late = pending.filter { it.dueAtMillis <= nowMillis }
        fun followingWakeups(after: Long): Map<String, Long> =
            pending.filter { it.dueAtMillis > after }
                .groupBy { it.dueAtMillis }.map { (time, sameTime) ->
                    sameTime.maxBy { it.stage.ordinal }.stageKey to time
                }.toMap()
        if (late.isNotEmpty()) {
            val latest = late.maxWith(compareBy<PlannedReminder> { it.dueAtMillis }.thenBy { it.stage.ordinal })
            return latest.copy(
                triggerAtMillis = nowMillis + minDelayMs,
                stageKeysToMark = late.map { it.stageKey }.toSet(),
                futureWakeups = followingWakeups(nowMillis)
            )
        }
        val firstTime = pending.minOf { it.dueAtMillis }
        val sameTime = pending.filter { it.dueAtMillis == firstTime }
        return sameTime.maxBy { it.stage.ordinal }.copy(
            stageKeysToMark = sameTime.map { it.stageKey }.toSet(),
            futureWakeups = followingWakeups(firstTime)
        )
    }

    internal fun buildFlexibleCandidates(task: Task, leadMinutes: Int): List<PlannedReminder> {
        val deadline = task.deadlineTimestamp
        val remaining = task.remainingCount
        val candidates = mutableListOf<PlannedReminder>()
        // Tiến độ thay đổi nội dung/thời điểm nhắc, không tạo lại danh tính các mốc đã gửi.
        fun key(stage: ReminderStage) = stage.name.lowercase()
        fun add(stage: ReminderStage, time: Long) {
            candidates += PlannedReminder(stage, key(stage), time)
        }
        val span = deadline - task.createdAt
        if (span >= 2 * DAY_MS) {
            val midpoint = task.createdAt + span / 2
            val evening = timeOfDayMillis(midpoint, 19, 0)
            val trigger = if (evening < deadline - DAY_MS) evening else midpoint
            if (trigger < deadline - DAY_MS) add(ReminderStage.FLEX_MID_WEEK, trigger)
        }
        if (remaining >= 2) {
            val trigger = timeOfDayMillis(deadline - remaining.coerceIn(2, 3) * DAY_MS, 19, 0)
            if (trigger < deadline - 12 * HOUR_MS) add(ReminderStage.FLEX_EARLY_PACE, trigger)
        }
        val morning = timeOfDayMillis(deadline, 9, 0)
        if (deadline - morning >= 2 * HOUR_MS) add(ReminderStage.FLEX_FINAL_DAY, morning)
        add(ReminderStage.FLEX_LAST_CALL, deadline - leadMinutes * MINUTE_MS)
        return candidates.sortedBy { it.dueAtMillis }
    }

    private fun timeOfDayMillis(baseMillis: Long, hourOfDay: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = baseMillis
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
