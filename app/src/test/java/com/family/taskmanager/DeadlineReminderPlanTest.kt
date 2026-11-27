package com.family.taskmanager

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskMode
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.service.DeadlineReminderPlanner
import com.family.taskmanager.data.service.ReminderStage
import com.family.taskmanager.ui.task.DEFAULT_DEADLINE_HOUR
import com.family.taskmanager.ui.task.DEFAULT_DEADLINE_MINUTE
import com.family.taskmanager.ui.task.defaultFlexibleWeeklyDeadlineMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DeadlineReminderPlanTest {

    private val minuteMs = 60_000L
    private val hourMs = 60 * minuteMs
    private val dayMs = 24 * hourMs

    private fun baseTask(
        deadline: Long,
        createdAt: Long = deadline - 6 * dayMs,
        taskMode: TaskMode = TaskMode.FIXED,
        targetCount: Int = 1,
        completedCount: Int = 0,
        reminderLeadMinutes: Int = 30,
        status: TaskStatus = TaskStatus.TODO
    ) = Task(
        id = "t-rem",
        title = "Tập thể dục",
        familyId = "fam-1",
        assignedToUserId = "bin",
        assignedToUserName = "Bin",
        createdByUserId = "mom",
        status = status,
        deadlineTimestamp = deadline,
        createdAt = createdAt,
        taskMode = taskMode,
        targetCount = targetCount,
        completedCount = completedCount,
        reminderLeadMinutes = reminderLeadMinutes
    )

    @Test
    fun fixedTask_respects30m_1h_and_2h_leadTimes() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 27, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val deadline = now + 5 * hourMs // 15:00

        val plan30 = DeadlineReminderPlanner.nextReminderFor(
            baseTask(deadline, reminderLeadMinutes = 30), now, { false }
        )!!
        assertEquals(ReminderStage.FIXED_LEAD, plan30.stage)
        assertEquals(deadline - 30 * minuteMs, plan30.triggerAtMillis)
        assertEquals("fixed_30", plan30.stageKey)

        val plan60 = DeadlineReminderPlanner.nextReminderFor(
            baseTask(deadline, reminderLeadMinutes = 60), now, { false }
        )!!
        assertEquals(deadline - 60 * minuteMs, plan60.triggerAtMillis)
        assertEquals("fixed_60", plan60.stageKey)

        val plan120 = DeadlineReminderPlanner.nextReminderFor(
            baseTask(deadline, reminderLeadMinutes = 120), now, { false }
        )!!
        assertEquals(deadline - 120 * minuteMs, plan120.triggerAtMillis)
        assertEquals("fixed_120", plan120.stageKey)

        // Đã nhắc 30p nhưng đổi sang nhắc 2h -> stageKey khác nên vẫn lên lịch mới
        val planChangedTo120 = DeadlineReminderPlanner.nextReminderFor(
            baseTask(deadline, reminderLeadMinutes = 120), now, { it == "fixed_30" }
        )
        assertNotNull(planChangedTo120)
        assertEquals("fixed_120", planChangedTo120!!.stageKey)
    }

    @Test
    fun flexibleWeeklyTask_schedulesEarlyMultiStageReminders_andAdaptsToProgress() {
        // Thứ 2 08:00 -> Chủ Nhật 20:00 (cần làm 3 lần, nhắc chót trước 1h)
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 23, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sundayDue = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val task0of3 = baseTask(
            deadline = sundayDue,
            createdAt = monday,
            taskMode = TaskMode.FLEXIBLE_WEEKLY,
            targetCount = 3,
            completedCount = 0,
            reminderLeadMinutes = 60
        )

        val reminded = mutableSetOf<String>()
        val stagesObserved = mutableSetOf<String>()
        var cursorNow = monday + hourMs
        while (true) {
            val next = DeadlineReminderPlanner.nextReminderFor(task0of3, cursorNow, { it in reminded })
                ?: break
            stagesObserved.addAll(next.stageKeysToMark)
            reminded.addAll(next.stageKeysToMark)
            cursorNow = maxOf(cursorNow, next.triggerAtMillis) + 1_000L
        }

        // Phải có đủ cả mốc sớm (giữa tuần, cảnh báo theo số lần thiếu, sáng ngày cuối, và nhắc chót)
        assertTrue("flex_mid_week" in stagesObserved)
        assertTrue("flex_early_pace" in stagesObserved)
        assertTrue("flex_final_day" in stagesObserved)
        assertTrue("flex_last_call" in stagesObserved)

        // Khi đã làm 2/3 lần (chỉ còn 1 lần) -> không cần mốc cảnh báo nhiều ngày FLEX_EARLY_PACE
        val task2of3 = task0of3.copy(completedCount = 2)
        val stages2of3 = DeadlineReminderPlanner.buildFlexibleCandidates(task2of3, 60).map { it.stage }
        assertFalse(ReminderStage.FLEX_EARLY_PACE in stages2of3)
        assertTrue(ReminderStage.FLEX_FINAL_DAY in stages2of3)
        assertTrue(ReminderStage.FLEX_LAST_CALL in stages2of3)

        // Khi đã hoàn thành đủ 3/3 -> không đặt báo thức nữa
        val doneTask = task0of3.copy(completedCount = 3, status = TaskStatus.COMPLETED)
        assertNull(DeadlineReminderPlanner.nextReminderFor(doneTask, monday + hourMs, { false }))
    }

    @Test
    fun futureWakeupsExcludeConsumedAndSimultaneousStages() {
        val deadline = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val task = baseTask(deadline, taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3)
        val plan = requireNotNull(DeadlineReminderPlanner.nextReminderFor(task, task.createdAt, { false }))
        assertEquals(setOf("flex_mid_week", "flex_early_pace"), plan.stageKeysToMark)
        assertEquals(setOf("flex_final_day", "flex_last_call"), plan.futureWakeups.keys)
        assertTrue(plan.futureWakeups.values.all { it > plan.dueAtMillis })
        val alreadySent = requireNotNull(DeadlineReminderPlanner.nextReminderFor(
            task, task.createdAt, { it == "flex_last_call" }))
        assertEquals(setOf("flex_final_day"), alreadySent.futureWakeups.keys)
        val fixed = requireNotNull(DeadlineReminderPlanner.nextReminderFor(baseTask(deadline), task.createdAt, { false }))
        assertTrue(fixed.futureWakeups.isEmpty())
    }

    @Test
    fun globalLeadOverridesExistingTaskMetadata() {
        val now = 1_800_000_000_000L
        val task = baseTask(now + 5 * hourMs, reminderLeadMinutes = 30)
        val plan = DeadlineReminderPlanner.nextReminderFor(task, now, { false }, leadMinutesOverride = 120)!!
        assertEquals(task.deadlineTimestamp - 2 * hourMs, plan.triggerAtMillis)
        assertEquals("fixed_120", plan.stageKey)
    }

    @Test
    fun lateCatchup_consumesEarlierStages_andProgressDoesNotRearmThem() {
        val deadline = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val task = baseTask(deadline, taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3)
        val now = deadline - 2 * hourMs
        val plan = DeadlineReminderPlanner.nextReminderFor(task, now, { false })!!
        assertEquals(ReminderStage.FLEX_FINAL_DAY, plan.stage)
        assertTrue(plan.stageKeysToMark.containsAll(setOf("flex_mid_week", "flex_early_pace", "flex_final_day")))
        assertTrue(plan.dueAtMillis < now)
        val consumed = plan.stageKeysToMark
        val next = DeadlineReminderPlanner.nextReminderFor(
            task.copy(completedCount = 1), now + 10_000, { it in consumed }
        )!!
        assertEquals(ReminderStage.FLEX_LAST_CALL, next.stage)
        assertTrue(next.dueAtMillis > now)
    }

    @Test
    fun equalTimeStages_areConsumedTogether() {
        val deadline = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // A six-day span puts both midpoint and pace reminder on Thursday at 19:00.
        val task = baseTask(deadline, createdAt = deadline - 6 * dayMs, taskMode = TaskMode.FLEXIBLE_WEEKLY, targetCount = 3)
        val plan = DeadlineReminderPlanner.nextReminderFor(task, task.createdAt, { false })!!
        assertEquals(setOf("flex_mid_week", "flex_early_pace"), plan.stageKeysToMark)
    }

    @Test
    fun flexibleWeeklyDeadlineDefault_andOverdueRules_workCorrectly() {
        val wednesday = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val defaultSunday = defaultFlexibleWeeklyDeadlineMillis(wednesday)
        val cal = Calendar.getInstance().apply { timeInMillis = defaultSunday }
        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(DEFAULT_DEADLINE_HOUR, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(DEFAULT_DEADLINE_MINUTE, cal.get(Calendar.MINUTE))
        assertTrue(defaultSunday > wednesday)

        val afterDeadline = defaultSunday + hourMs
        val flexPendingRound1 = baseTask(
            deadline = defaultSunday,
            taskMode = TaskMode.FLEXIBLE_WEEKLY,
            targetCount = 3,
            completedCount = 0,
            status = TaskStatus.PENDING_APPROVAL
        )
        // Đã qua hạn mà mới nộp lượt 1/3 -> vẫn coi là quá hạn vì chưa đủ số lần
        assertTrue(flexPendingRound1.isOverdue(afterDeadline))

        val flexPendingFinalRound = flexPendingRound1.copy(completedCount = 2)
        // Đang chờ duyệt lượt cuối 3/3 -> không tô quá hạn
        assertFalse(flexPendingFinalRound.isOverdue(afterDeadline))
    }
}
