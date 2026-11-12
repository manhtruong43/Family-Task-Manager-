package com.family.taskmanager

import com.family.taskmanager.data.model.*
import com.family.taskmanager.ui.calendar.prepareRecurringEditor
import org.junit.Assert.*
import org.junit.Test

class RecurringEditorStateTest {
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val legacy = RecurringSchedule(
        id = "legacy", familyId = "f", title = "Lịch cũ", createdByUserId = mom.id,
        assignedToUserId = dad.id, assignedToUserName = dad.name, isActive = false,
        lastGeneratedEpochDay = 123L
    )

    @Test fun legacyPausedSelfAssignment_recoversBeforeFilteringEditorChoices() {
        val state = prepareRecurringEditor(legacy, listOf(dad, na), dad.id)
        val initial = state.initial!!
        assertEquals(dad.id, initial.createdByUserId)
        assertEquals("", initial.assignedToUserId)
        assertEquals("", initial.assignedToUserName)
        assertEquals(RecurringAssignmentMode.SHARED, initial.assignmentMode)
        assertFalse(initial.isActive)
        assertEquals(123L, initial.lastGeneratedEpochDay)
        assertEquals(listOf(na), state.assignableMembers)
    }

    @Test fun validExistingAssignment_preservesValuesAndFullMembershipContext() {
        val valid = legacy.copy(assignedToUserId = na.id, assignedToUserName = na.name)
        val state = prepareRecurringEditor(valid, listOf(mom, dad, na), dad.id)
        assertEquals(valid, state.initial)
        assertEquals(listOf(mom, na), state.assignableMembers)
    }

    @Test fun newSchedule_keepsEmptyInitialAndExcludesCurrentUserFromChoices() {
        val state = prepareRecurringEditor(null, listOf(mom, dad, na), dad.id)
        assertNull(state.initial)
        assertEquals(listOf(mom, na), state.assignableMembers)
    }
}
