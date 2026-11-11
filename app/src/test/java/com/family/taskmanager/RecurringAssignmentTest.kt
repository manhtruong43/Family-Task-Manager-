package com.family.taskmanager

import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskPermissions
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Quy tắc sinh nhiệm vụ mỗi kỳ; chạy JVM, không cần thiết bị. */
class RecurringAssignmentTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val mom = User("mom", "Mẹ", "mom@example.test", role = UserRole.ADMIN, familyId = "home")
    private val dad = User("dad", "Bố", "dad@example.test", role = UserRole.ADMIN, familyId = "home")
    private val na = User("na", "Na", "na@example.test", familyId = "home")
    private val bin = User("bin", "Bin", "bin@example.test", familyId = "home")
    private val members = listOf(mom, dad, na, bin)
    private val schedule = RecurringSchedule(
        id = "routine", familyId = "home", title = "Dọn phòng", createdByUserId = mom.id,
        points = 15, dueMinuteOfDay = 20 * 60
    )

    @Test
    fun legacyEveryone_createsOneUnclaimedSharedTask() {
        val task = schedule.tasksForOccurrence(day, members, 123L).single()
        assertEquals(RecurringAssignmentMode.SHARED, schedule.assignmentMode)
        assertEquals(schedule.taskIdFor(day), task.id)
        assertEquals("", task.assignedToUserId)
        assertTrue(task.openToAll)
        assertEquals(TaskStatus.TODO, task.status)
    }

    @Test
    fun eachMember_createsIndependentWorkAndPoints_excludingCreatorAndOtherFamilies() {
        val outsider = bin.copy(id = "outsider", familyId = "elsewhere")
        val tasks = schedule.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)
            .tasksForOccurrence(day, members + outsider + na, 123L)
        assertEquals(setOf(dad.id, na.id, bin.id), tasks.map { it.assignedToUserId }.toSet())
        assertEquals(3, tasks.size)
        assertEquals(3, tasks.map { it.id }.toSet().size)
        assertTrue(tasks.all { !it.openToAll && it.points == 15 && it.status == TaskStatus.TODO })
        val naTask = tasks.single { it.assignedToUserId == na.id }
        val binTask = tasks.single { it.assignedToUserId == bin.id }
        assertTrue(TaskPermissions.canWork(na, naTask))
        assertFalse(TaskPermissions.canWork(bin, naTask))
        assertTrue(TaskPermissions.canWork(bin, binTask))
    }

    @Test
    fun eachOccurrence_usesCurrentMembershipAndNames() {
        val each = schedule.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)
        val oldTasks = each.tasksForOccurrence(day, members, 123L)
        val newMember = bin.copy(id = "new", name = "An")
        val nextMembers = listOf(mom, na.copy(name = "Na mới"), newMember)
        val next = each.tasksForOccurrence(day.plusDays(1), nextMembers, 456L)
        assertEquals(setOf(na.id, newMember.id), next.map { it.assignedToUserId }.toSet())
        assertEquals("Na mới", next.single { it.assignedToUserId == na.id }.assignedToUserName)
        assertEquals("Na", oldTasks.single { it.assignedToUserId == na.id }.assignedToUserName)
        assertTrue(next.all { it.createdAt == 456L })
    }

    @Test
    fun eachMember_excludesSoleParentWhoCannotBeReviewed_butKeepsChildren() {
        val each = schedule.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)
        val remaining = listOf(dad, na, bin) // Người giao đã rời, Bố là phụ huynh duy nhất
        val tasks = each.tasksForOccurrence(day, remaining, 123L)
        assertEquals(setOf(na.id, bin.id), tasks.map { it.assignedToUserId }.toSet())
        assertTrue(tasks.all { TaskPermissions.canReview(dad, it) })
    }

    @Test
    fun dedicatedSchedule_staysOneTask_andUnavailableAssigneeFallsBackToShared() {
        val dedicated = schedule.copy(
            assignedToUserId = dad.id, assignedToUserName = "Tên cũ",
            assignmentMode = RecurringAssignmentMode.EACH_MEMBER
        )
        val task = dedicated.tasksForOccurrence(day, members, 123L).single()
        assertEquals(dad.id, task.assignedToUserId)
        assertEquals(dad.name, task.assignedToUserName)
        assertFalse(task.openToAll)
        assertEquals(schedule.taskIdFor(day), task.id)

        val fallback = dedicated.tasksForOccurrence(day, listOf(dad, na), 123L).single()
        assertTrue(fallback.openToAll)
        assertEquals("", fallback.assignedToUserId)
        assertEquals("", fallback.assignedToUserName)
    }

    @Test
    fun occurrenceIds_areStableAcrossRetriesAndMemberOrdering_butNewDayStartsFresh() {
        val each = schedule.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)
        val first = each.tasksForOccurrence(day, members, 123L)
        val retry = each.tasksForOccurrence(day, members.reversed(), 456L)
        assertEquals(first.map { it.id }.toSet(), retry.map { it.id }.toSet())
        val submitted = first.first().copy(status = TaskStatus.COMPLETED, proofImageUrl = "old.jpg")
        val next = each.tasksForOccurrence(day.plusDays(1), members, 789L)
        assertTrue(next.none { fresh -> first.any { it.id == fresh.id } })
        assertTrue(next.all { it.status == TaskStatus.TODO && it.proofImageUrl.isEmpty() && it.submissionRevision == 0 })
        assertEquals(TaskStatus.COMPLETED, submitted.status)
    }

    @Test
    fun deadline_usesRequestedGenerationZone() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val task = schedule.tasksForOccurrence(day, members, 123L, zone).single()
        assertEquals(day.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(), task.deadlineTimestamp)
        assertNotEquals(schedule.dueAtMillis(day, ZoneOffset.UTC), task.deadlineTimestamp)
    }

    @Test
    fun eachMember_withNobodyEligible_doesNotCreateAnAccidentalSharedTask() {
        val tasks = schedule.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)
            .tasksForOccurrence(day, listOf(mom), 123L)
        assertTrue(tasks.isEmpty())
    }
}
