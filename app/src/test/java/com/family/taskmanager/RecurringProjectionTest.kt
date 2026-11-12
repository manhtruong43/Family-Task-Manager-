package com.family.taskmanager

import com.family.taskmanager.data.model.*
import com.family.taskmanager.ui.calendar.projectedRecurringTasks
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringProjectionTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val bin = User("bin", "Bin", "b@example.test", familyId = "f")
    private val members = listOf(mom, dad, na, bin)
    private val shared = RecurringSchedule(id = "s", familyId = "f", title = "Việc", createdByUserId = mom.id)
    private val each = shared.copy(assignmentMode = RecurringAssignmentMode.EACH_MEMBER)

    private fun project(schedule: RecurringSchedule, mine: Boolean, viewer: User = na, people: List<User> = members) =
        projectedRecurringTasks(day, listOf(schedule), people, viewer, mine, ZoneOffset.UTC)

    @Test fun sharedOccurrence_countsOneTask_underBothFilters() {
        for (mine in listOf(false, true)) {
            assertEquals(1, project(shared, mine).size)
            assertTrue(project(shared, mine).single().openToAll)
        }
        assertTrue(project(shared, true, mom).isEmpty())
    }

    @Test fun dedicatedOccurrence_countsOneOnlyForItsAssignee() {
        val direct = shared.copy(assignedToUserId = na.id)
        assertEquals(1, project(direct, false, dad).size)
        assertTrue(project(direct, true, dad).isEmpty())
        assertEquals(na.id, project(direct, true).single().assignedToUserId)
    }

    @Test fun perMemberOccurrence_keepsAllThreeTasks_thenFiltersToViewer() {
        val all = project(each, false)
        assertEquals(3, all.size)
        assertEquals(setOf(dad.id, na.id, bin.id), all.map { it.assignedToUserId }.toSet())
        assertEquals(3, all.map { it.id }.toSet().size)
        assertEquals(na.id, project(each, true).single().assignedToUserId)
        assertEquals(dad.id, project(each, true, dad).single().assignedToUserId)
        assertTrue(project(each, true, mom).isEmpty())
    }

    @Test fun zeroRecipients_projectsNothing_underBothFilters() {
        for (mine in listOf(false, true)) {
            assertTrue(project(each, mine, mom, listOf(mom)).isEmpty())
        }
    }

    @Test fun projectedDate_controlsWeeklyOccurrenceAndEachTaskDeadline() {
        val friday = day.plusDays(1)
        val weekly = each.copy(repeatType = RepeatType.WEEKLY, daysOfWeekMask = 1 shl 4)
        for (mine in listOf(false, true)) {
            assertTrue(project(weekly, mine).isEmpty())
            val next = projectedRecurringTasks(friday, listOf(weekly), members, na, mine, ZoneOffset.UTC)
            assertEquals(if (mine) 1 else 3, next.size)
            assertTrue(next.all { it.deadlineTimestamp == weekly.dueAtMillis(friday, ZoneOffset.UTC) })
            assertEquals(
                weekly.tasksForOccurrence(friday, members, 0L, ZoneOffset.UTC)
                    .filter { !mine || it.assignedToUserId == na.id }.map { it.id }.toSet(),
                next.map { it.id }.toSet()
            )
        }
    }

    @Test fun processedMarkers_areExcludedInBothModesAndFilters() {
        for (mode in RecurringAssignmentMode.entries) {
            for (mine in listOf(false, true)) {
                for (marker in listOf(day.toEpochDay(), day.plusDays(2).toEpochDay())) {
                    val processed = shared.copy(assignmentMode = mode, lastGeneratedEpochDay = marker)
                    assertTrue("mode=$mode mine=$mine marker=$marker", project(processed, mine).isEmpty())
                }
                val upcoming = shared.copy(
                    assignmentMode = mode, lastGeneratedEpochDay = day.minusDays(1).toEpochDay()
                )
                assertEquals(if (mode == RecurringAssignmentMode.EACH_MEMBER && !mine) 3 else 1,
                    project(upcoming, mine).size)
            }
        }
    }

    @Test fun processedEmptyOccurrence_staysEmptyAfterMembersJoinOrModeChanges() {
        val processed = each.copy(lastGeneratedEpochDay = day.toEpochDay())
        for (mine in listOf(false, true)) {
            assertTrue(project(processed, mine, mom, listOf(mom)).isEmpty())
            assertTrue(project(processed, mine).isEmpty())
            assertTrue(project(processed.copy(assignmentMode = RecurringAssignmentMode.SHARED), mine).isEmpty())
        }
    }

    @Test fun mixedSchedules_sumTasks_andOmitPausedOrEmptyOccurrences() {
        val direct = shared.copy(id = "direct", assignedToUserId = na.id)
        val paused = each.copy(id = "paused", isActive = false)
        val all = projectedRecurringTasks(day, listOf(shared, each.copy(id = "each"), direct, paused), members, na, false)
        val mine = projectedRecurringTasks(day, listOf(shared, each.copy(id = "each"), direct, paused), members, na, true)
        assertEquals(5, all.size)
        assertEquals(3, mine.size)
    }
}
