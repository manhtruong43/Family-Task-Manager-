package com.family.taskmanager

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử quy tắc phân quyền nhiệm vụ (unit test JVM - chạy nhanh, không cần máy ảo).
 * Chạy: chuột phải thư mục app/src/test -> Run 'Tests in ...'.
 */
class TaskPermissionsTest {

    private val family = "family-1"
    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN, familyId = family)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER, familyId = family)
    private val na = User(id = "na", name = "Na", email = "na@x.com", role = UserRole.MEMBER, familyId = family)
    private val dad = User(id = "dad", name = "Bố", email = "bo@x.com", role = UserRole.ADMIN, familyId = family)
    private val outsider = User(id = "out", name = "Khách", email = "o@x.com", role = UserRole.ADMIN, familyId = "family-2")

    private val taskForBin = Task(
        id = "t1", title = "Dọn phòng", familyId = family,
        assignedToUserId = bin.id, assignedToUserName = bin.name, createdByUserId = parent.id
    )
    private val taskForEveryone = Task(
        id = "t2", title = "Rửa bát", familyId = family, createdByUserId = parent.id
    )

    @Test
    fun assignee_canWork_onOwnTask() {
        assertTrue(TaskPermissions.canWork(bin, taskForBin))
    }

    @Test
    fun otherMember_cannotWork_onSomeoneElsesTask() {
        assertFalse(TaskPermissions.canWork(na, taskForBin))
    }

    @Test
    fun parent_cannotWork_onTaskAssignedToChild() {
        assertFalse(TaskPermissions.canWork(parent, taskForBin))
    }

    @Test
    fun everyoneTask_anyoneExceptAssignerCanWork() {
        assertTrue(TaskPermissions.canWork(bin, taskForEveryone))
        assertTrue(TaskPermissions.canWork(na, taskForEveryone))
        assertTrue(TaskPermissions.canWork(dad, taskForEveryone))      // Bố/Mẹ kia cũng nhận được việc chung
        assertFalse(TaskPermissions.canWork(parent, taskForEveryone))  // người giao thì không
    }

    @Test
    fun userFromAnotherFamily_cannotDoAnything() {
        assertFalse(TaskPermissions.canWork(outsider, taskForEveryone))
        assertFalse(TaskPermissions.canReview(outsider, taskForBin))
    }

    @Test
    fun noUser_cannotDoAnything() {
        assertFalse(TaskPermissions.canWork(null, taskForBin))
        assertFalse(TaskPermissions.canReview(null, taskForBin))
        assertFalse(TaskPermissions.canCreate(null))
    }

    @Test
    fun onlyParentOfSameFamily_canReview() {
        assertTrue(TaskPermissions.canReview(parent, taskForBin))
        assertFalse(TaskPermissions.canReview(bin, taskForBin))
    }

    @Test
    fun onlyParentWithFamily_canCreate() {
        assertTrue(TaskPermissions.canCreate(parent))
        assertFalse(TaskPermissions.canCreate(bin))
        assertFalse(TaskPermissions.canCreate(parent.copy(familyId = "")))
    }

    // ─── Không tự duyệt, không tự giao, sửa / hủy ───

    @Test
    fun parent_cannotReview_taskAssignedToThemself() {
        val taskForDad = taskForBin.copy(assignedToUserId = dad.id, assignedToUserName = dad.name)
        assertFalse(TaskPermissions.canReview(dad, taskForDad))     // Bố không tự duyệt
        assertTrue(TaskPermissions.canReview(parent, taskForDad))   // Mẹ duyệt được
        assertTrue(TaskPermissions.canWork(dad, taskForDad))        // Bố được làm việc giao cho mình
    }

    @Test
    fun canAssignTo_familyMembersExceptYourself() {
        assertTrue(TaskPermissions.canAssignTo(parent, bin))
        assertTrue(TaskPermissions.canAssignTo(parent, dad))
        assertFalse(TaskPermissions.canAssignTo(parent, parent))    // không tự giao cho mình
        assertFalse(TaskPermissions.canAssignTo(parent, outsider))  // người nhà khác
        assertFalse(TaskPermissions.canAssignTo(bin, na))           // Con không giao việc
    }

    @Test
    fun editAndCancel_rules() {
        assertTrue(TaskPermissions.canEdit(parent, taskForBin))
        assertTrue(TaskPermissions.canCancel(dad, taskForBin))
        assertFalse(TaskPermissions.canEdit(bin, taskForBin))
        assertFalse(TaskPermissions.canCancel(outsider, taskForBin))

        val pending = taskForBin.copy(status = TaskStatus.PENDING_APPROVAL)
        assertFalse(TaskPermissions.canEdit(parent, pending))       // đã nộp thì không sửa
        assertTrue(TaskPermissions.canCancel(parent, pending))      // nhưng vẫn hủy được

        val done = taskForBin.copy(status = TaskStatus.COMPLETED)
        assertFalse(TaskPermissions.canEdit(parent, done))
        assertFalse(TaskPermissions.canCancel(parent, done))

        val taskForDad = taskForBin.copy(assignedToUserId = dad.id)
        assertFalse(TaskPermissions.canCancel(dad, taskForDad))     // không tự hủy việc của mình
    }

    @Test
    fun isMine_includesEveryoneTasksForChildren() {
        assertTrue(TaskPermissions.isMine(bin, taskForBin))
        assertTrue(TaskPermissions.isMine(na, taskForEveryone))
        assertFalse(TaskPermissions.isMine(na, taskForBin))
    }

    @Test
    fun displayRoleRes_usesRelationLabel_notAvatarEmoji() {
        // Mẹ đổi avatar sang bất kỳ icon nào thì nhãn vẫn giữ là Mẹ
        val momCustomAvatar = parent.copy(relationLabel = User.RELATION_MOTHER, avatarUrl = "🧔")
        // Bố dùng cùng avatar với Mẹ thì nhãn vẫn phân biệt rõ là Bố
        val dadSameAvatar = dad.copy(relationLabel = User.RELATION_FATHER, avatarUrl = "🧔")
        // Tài khoản cũ chưa có relationLabel thì hiển thị nhãn trung tính "Bố/Mẹ"
        val legacyParent = parent.copy(relationLabel = "", avatarUrl = "🧔")

        assertEquals(R.string.role_mother_short, momCustomAvatar.displayRoleRes())
        assertEquals(R.string.role_father_short, dadSameAvatar.displayRoleRes())
        assertEquals(R.string.role_parent, legacyParent.displayRoleRes())
        assertEquals(R.string.role_child, bin.displayRoleRes())

        assertEquals(R.string.role_mother_short, momCustomAvatar.displayRoleRes(homeStyle = true))
        assertEquals(R.string.role_father_short, dadSameAvatar.displayRoleRes(homeStyle = true))
        assertEquals(R.string.home_role_parent, legacyParent.displayRoleRes(homeStyle = true))
        assertEquals(R.string.home_role_child, bin.displayRoleRes(homeStyle = true))
    }

    @Test
    fun parentWorker_requiresAnotherAdminInFamily_toHaveValidReviewer() {
        // Còn phụ huynh khác trong nhóm -> Bố được nhận việc chung và Mẹ được giao việc cho Bố
        assertTrue(TaskPermissions.canWork(dad, taskForEveryone, hasOtherAdminInFamily = true))
        assertTrue(TaskPermissions.canAssignTo(parent, dad, hasOtherAdminForAssignee = true))

        // Nếu Mẹ đã rời nhóm (Bố là phụ huynh duy nhất còn lại) -> Bố không nhận việc chung để tránh kẹt người duyệt,
        // trong khi các con vẫn làm việc chung bình thường và Bố là người duyệt.
        assertFalse(TaskPermissions.canWork(dad, taskForEveryone, hasOtherAdminInFamily = false))
        assertFalse(TaskPermissions.canAssignTo(parent, dad, hasOtherAdminForAssignee = false))
        assertTrue(TaskPermissions.canWork(bin, taskForEveryone, hasOtherAdminInFamily = false))
        assertTrue(TaskPermissions.canWork(na, taskForEveryone, hasOtherAdminInFamily = false))
    }
}
