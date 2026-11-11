package com.family.taskmanager

import com.family.taskmanager.data.model.*
import org.junit.Assert.*
import org.junit.Test

class RecurringManagementTest {
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val members = listOf(mom, dad, na)
    private val each = RecurringSchedule(
        id = "each", familyId = "f", title = "Dọn nhà", createdByUserId = mom.id,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    @Test fun includedParent_cannotManagePerMemberSchedule() {
        assertFalse(each.canBeManagedBy(dad, members))
    }

    @Test fun creator_keepsManagementAccess() {
        assertTrue(each.canBeManagedBy(mom, members))
    }

    @Test fun sharedClaim_preservesManagementByOtherParents() {
        val shared = each.copy(assignmentMode = RecurringAssignmentMode.SHARED)
        assertTrue(shared.canBeManagedBy(dad, members))
        assertTrue(shared.canBeManagedBy(mom, members))
    }

    @Test fun dedicatedSchedule_keepsNoSelfManagementRule() {
        val direct = each.copy(assignedToUserId = dad.id)
        assertFalse(direct.canBeManagedBy(dad, members))
        assertTrue(direct.canBeManagedBy(mom, members))
    }

    @Test fun recipientStatus_usesCurrentMembers_andReviewerEligibility() {
        // Mẹ đã rời: Bố không thể nhận việc riêng vì không có người duyệt, nên không bị coi là người nhận.
        assertTrue(each.canBeManagedBy(dad, listOf(dad, na)))
        // Khi có phụ huynh khác vào, Bố lại là người nhận thực sự và phải bị chặn quản lý.
        val other = mom.copy(id = "other")
        assertFalse(each.canBeManagedBy(dad, listOf(dad, na, other)))
    }

    @Test fun missingChildOrForeignActor_cannotManage() {
        assertFalse(each.canBeManagedBy(null, members))
        assertFalse(each.canBeManagedBy(na, members))
        assertFalse(each.canBeManagedBy(dad.copy(familyId = "other"), members))
    }
}
