package com.family.taskmanager

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskPermissions
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
    fun everyoneTask_onlyChildrenCanWork() {
        assertTrue(TaskPermissions.canWork(bin, taskForEveryone))
        assertTrue(TaskPermissions.canWork(na, taskForEveryone))
        assertFalse(TaskPermissions.canWork(parent, taskForEveryone))
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

    @Test
    fun isMine_includesEveryoneTasksForChildren() {
        assertTrue(TaskPermissions.isMine(bin, taskForBin))
        assertTrue(TaskPermissions.isMine(na, taskForEveryone))
        assertFalse(TaskPermissions.isMine(na, taskForBin))
    }
}
