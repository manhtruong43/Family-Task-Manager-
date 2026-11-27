package com.family.taskmanager.data.service

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import java.util.Calendar

enum class ReminderStage {
    FIXED_LEAD,
    FLEX_MID_WEEK,
    FLEX_EARLY_PACE,
    FLEX_FINAL_DAY,
    FLEX_LAST_CALL
}

data class PlannedReminder(
    val stage: ReminderStage,
    val stageKey: String,
    val triggerAtMillis: Long
)

/**
 * Bộ lập lịch mốc nhắc việc thuần logic (test trực tiếp trên JVM không cần AlarmManager):
 * - Việc cố định (FIXED): nhắc trước hạn chót đúng [Task.reminderLeadMinutes] (30 / 60 / 120 phút).
 * - Việc linh hoạt trong tuần (FLEXIBLE_WEEKLY): nhắc sớm theo 4 mốc dựa trên số lần còn thiếu
 *   (giữa kỳ -> cảnh báo sớm theo số lần còn thiếu -> sáng ngày cuối -> nhắc chót trước hạn).
 */
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
        minDelayMs: Long = DeadlineReminderScheduler.MIN_DELAY_MS
    ): PlannedReminder? {
        if (task.status !in REMINDABLE_STATUSES) return null
        if (task.deadlineTimestamp <= nowMillis) return null

        val leadMinutes = normalizedLeadMinutes(task.reminderLeadMinutes)
        if (!task.isFlexibleWeekly) {
            val stageKey = "fixed_$leadMinutes"
            if (wasReminded(stageKey)) return null
            val triggerAt = maxOf(task.deadlineTimestamp - leadMinutes * MINUTE_MS, nowMillis + minDelayMs)
            return PlannedReminder(ReminderStage.FIXED_LEAD, stageKey, triggerAt)
        }

        val remaining = task.remainingCount
        if (remaining <= 0) return null

        val candidates = buildFlexibleCandidates(task, leadMinutes)
            .filterNot { wasReminded(it.stageKey) }
        if (candidates.isEmpty()) return null

        // Nếu nhiều mốc cũ đã qua mà chưa kịp bắn (vd: tắt máy lâu), chỉ bắn mốc gần hiện tại nhất để tránh spam.
        val pastDue = candidates.filter { it.triggerAtMillis <= nowMillis }
        if (pastDue.isNotEmpty()) {
            val latest = pastDue.maxBy { it.triggerAtMillis }
            return latest.copy(triggerAtMillis = nowMillis + minDelayMs)
        }
        return candidates.minBy { it.triggerAtMillis }
    }

    internal fun buildFlexibleCandidates(task: Task, leadMinutes: Int): List<PlannedReminder> {
        val deadline = task.deadlineTimestamp
        val remaining = task.remainingCount
        val list = mutableListOf<PlannedReminder>()

        fun keyOf(stage: ReminderStage) = "${stage.name.lowercase()}_c${task.completedCount}_l$leadMinutes"

        // 1. Mốc giữa kỳ (50% thời gian từ lúc tạo đến hạn cuối, ưu tiên 19:00 tối)
        val totalSpan = deadline - task.createdAt
        if (totalSpan >= 2 * DAY_MS) {
            val rawMid = task.createdAt + totalSpan / 2
            val midEvening = timeOfDayMillis(rawMid, 19, 0)
            val midTrigger = if (midEvening < deadline - DAY_MS) midEvening else rawMid
            if (midTrigger < deadline - DAY_MS) {
                list.add(PlannedReminder(ReminderStage.FLEX_MID_WEEK, keyOf(ReminderStage.FLEX_MID_WEEK), midTrigger))
            }
        }

        // 2. Mốc nhắc sớm theo số lần còn thiếu (còn 2 lần -> trước 2 ngày; còn >=3 lần -> trước 3 ngày, lúc 19:00)
        if (remaining >= 2) {
            val daysBefore = remaining.coerceIn(2, 3)
            val paceTrigger = timeOfDayMillis(deadline - daysBefore * DAY_MS, 19, 0)
            if (paceTrigger < deadline - 12 * HOUR_MS) {
                list.add(PlannedReminder(ReminderStage.FLEX_EARLY_PACE, keyOf(ReminderStage.FLEX_EARLY_PACE), paceTrigger))
            }
        }

        // 3. Mốc sáng ngày cuối hạn (09:00 sáng) nếu hạn sau 11:00
        val finalMorning = timeOfDayMillis(deadline, 9, 0)
        if (deadline - finalMorning >= 2 * HOUR_MS) {
            list.add(PlannedReminder(ReminderStage.FLEX_FINAL_DAY, keyOf(ReminderStage.FLEX_FINAL_DAY), finalMorning))
        }

        // 4. Mốc nhắc chót trước hạn theo reminderLeadMinutes (30p / 1h / 2h)
        val lastCallTrigger = deadline - leadMinutes * MINUTE_MS
        list.add(PlannedReminder(ReminderStage.FLEX_LAST_CALL, keyOf(ReminderStage.FLEX_LAST_CALL), lastCallTrigger))

        return list.sortedBy { it.triggerAtMillis }
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
