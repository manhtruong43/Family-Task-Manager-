package com.family.taskmanager.ui.calendar

import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.TaskPermissions
import java.time.LocalDate
import java.time.ZoneId

/** Dự kiến dùng cùng cách sinh từng nhiệm vụ như repository, sau đó mới lọc theo người xem. */
internal fun projectedRecurringTasks(
    date: LocalDate,
    schedules: List<RecurringSchedule>,
    members: List<User>,
    viewer: User?,
    onlyMine: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): List<Task> = schedules.asSequence()
    .filter { it.isActive && it.occursOn(date) && date.toEpochDay() > it.lastGeneratedEpochDay }
    .flatMap { it.tasksForOccurrence(date, members, generatedAt = 0L, zone = zone).asSequence() }
    .filter { !onlyMine || TaskPermissions.isMine(viewer, it) }
    .sortedWith(compareBy<Task> { it.deadlineTimestamp }.thenBy { it.id })
    .toList()
