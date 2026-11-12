package com.family.taskmanager

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskPermissions
import com.family.taskmanager.ui.task.DEFAULT_DEADLINE_HOUR
import com.family.taskmanager.ui.task.DEFAULT_DEADLINE_MINUTE
import com.family.taskmanager.ui.task.applySelectedDatePreservingTime
import com.family.taskmanager.ui.task.applySelectedTimePreservingDate
import com.family.taskmanager.ui.task.defaultTaskDeadlineMillis
import com.family.taskmanager.ui.task.formatTaskDeadlineDetail
import com.family.taskmanager.ui.task.formatTaskDeadlineForm
import com.family.taskmanager.ui.task.formatTaskShortDeadline
import com.family.taskmanager.ui.task.isDeadlineSelectionValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

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

    // ─── Chọn giờ hạn chót & hiển thị hạn chót ───

    @Test
    fun defaultDeadline_isTomorrowAt2000_andChangingDatePreservesSelectedTime() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val defaultDeadline = defaultTaskDeadlineMillis(now)
        val defaultCal = Calendar.getInstance().apply { timeInMillis = defaultDeadline }
        assertEquals(2026, defaultCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, defaultCal.get(Calendar.MONTH))
        assertEquals(13, defaultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(DEFAULT_DEADLINE_HOUR, defaultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(DEFAULT_DEADLINE_MINUTE, defaultCal.get(Calendar.MINUTE))

        // Đổi giờ sang 18:45 trên ngày 13/11
        val withCustomTime = applySelectedTimePreservingDate(defaultDeadline, 18, 45, now)
        val timeCal = Calendar.getInstance().apply { timeInMillis = withCustomTime }
        assertEquals(13, timeCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, timeCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, timeCal.get(Calendar.MINUTE))

        // Đổi ngày sang 20/11 -> phải giữ nguyên giờ 18:45 (không bị ép về 23:59)
        val withNewDate = applySelectedDatePreservingTime(withCustomTime, 2026, Calendar.NOVEMBER, 20)
        val dateCal = Calendar.getInstance().apply { timeInMillis = withNewDate }
        assertEquals(20, dateCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, dateCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, dateCal.get(Calendar.MINUTE))

        // Nếu việc cũ không có hạn (0L) mà Bố/Mẹ chọn ngày mới -> đặt giờ mặc định 20:00
        val fromNoDeadline = applySelectedDatePreservingTime(0L, 2026, Calendar.NOVEMBER, 25)
        val fromNoDeadlineCal = Calendar.getInstance().apply { timeInMillis = fromNoDeadline }
        assertEquals(25, fromNoDeadlineCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(DEFAULT_DEADLINE_HOUR, fromNoDeadlineCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(DEFAULT_DEADLINE_MINUTE, fromNoDeadlineCal.get(Calendar.MINUTE))
    }

    @Test
    fun deadlineValidation_blocksPastSelection_butAllowsUnchangedOverdueWhenEditing() {
        val now = 1_700_000_000_000L
        val past = now - 60_000L
        val future = now + 60_000L

        // Tạo mới: chặn quá khứ, cho phép tương lai hoặc không đặt hạn (0L)
        assertFalse(isDeadlineSelectionValid(past, isEditing = false, deadlineChangedByUser = false, nowMillis = now))
        assertTrue(isDeadlineSelectionValid(future, isEditing = false, deadlineChangedByUser = false, nowMillis = now))
        assertTrue(isDeadlineSelectionValid(0L, isEditing = false, deadlineChangedByUser = false, nowMillis = now))

        // Sửa việc đã quá hạn mà giữ nguyên hạn cũ (deadlineChangedByUser = false) -> cho phép lưu
        assertTrue(isDeadlineSelectionValid(past, isEditing = true, deadlineChangedByUser = false, nowMillis = now))
        // Sửa việc mà đổi sang hạn ở quá khứ (deadlineChangedByUser = true) -> chặn
        assertFalse(isDeadlineSelectionValid(past, isEditing = true, deadlineChangedByUser = true, nowMillis = now))
        assertTrue(isDeadlineSelectionValid(future, isEditing = true, deadlineChangedByUser = true, nowMillis = now))
    }

    @Test
    fun taskIsOverdue_andDeadlineFormatting_workCorrectly() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today1900 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 19, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today1400 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val overdueTodo = taskForBin.copy(deadlineTimestamp = today1400, status = TaskStatus.TODO)
        val overduePending = taskForBin.copy(deadlineTimestamp = today1400, status = TaskStatus.PENDING_APPROVAL)
        val overdueCompleted = taskForBin.copy(deadlineTimestamp = today1400, status = TaskStatus.COMPLETED)
        val futureTodo = taskForBin.copy(deadlineTimestamp = today1900, status = TaskStatus.TODO)
        val noDeadlineTodo = taskForBin.copy(deadlineTimestamp = 0L, status = TaskStatus.TODO)

        assertTrue(overdueTodo.isOverdue(now))
        assertFalse(overduePending.isOverdue(now))
        assertFalse(overdueCompleted.isOverdue(now))
        assertFalse(futureTodo.isOverdue(now))
        assertFalse(noDeadlineTodo.isOverdue(now))

        assertEquals("19:00 12/11/2026", formatTaskDeadlineForm(today1900, Locale.US))
        assertEquals("19:00 · 12/11/2026", formatTaskDeadlineDetail(today1900, Locale.US))
        assertEquals(
            "19:00 hôm nay",
            formatTaskShortDeadline(
                deadlineMillis = today1900,
                nowMillis = now,
                locale = Locale.US,
                todayFormatter = { "$it hôm nay" },
                tomorrowFormatter = { "$it ngày mai" }
            )
        )
    }
}
