package com.family.taskmanager.ui.calendar

import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.model.User

internal data class RecurringEditorState(
    val initial: RecurringSchedule?,
    val assignableMembers: List<User>
)

/** Khôi phục bằng toàn bộ thành viên trước khi loại người đang sửa khỏi lựa chọn giao việc. */
internal fun prepareRecurringEditor(
    stored: RecurringSchedule?,
    members: List<User>,
    currentUserId: String?
): RecurringEditorState = RecurringEditorState(
    initial = stored?.withCurrentManager(members),
    assignableMembers = members.filter { it.id != currentUserId }
)

/** Dùng cùng canBeManagedBy và khôi phục người quản lý như transaction lưu lịch. */
internal fun canManageRecurringDraft(
    initial: RecurringSchedule?,
    actorId: String?,
    members: List<User>,
    assigneeId: String,
    assignmentMode: RecurringAssignmentMode
): Boolean {
    val actor = members.firstOrNull { it.id == actorId } ?: return false
    if (actor.role != UserRole.ADMIN || actor.familyId.isBlank()) return false
    val existing = initial?.withCurrentManager(members)
    if (existing != null && !existing.canBeManagedBy(actor, members)) return false
    if (assigneeId.isNotEmpty() && (
        assigneeId == actor.id || members.none { it.id == assigneeId && it.familyId == actor.familyId }
    )) return false

    val draft = (existing ?: RecurringSchedule(
        id = "", familyId = actor.familyId, title = "", createdByUserId = actor.id
    )).copy(
        assignedToUserId = assigneeId,
        assignmentMode = if (assigneeId.isEmpty()) assignmentMode else RecurringAssignmentMode.SHARED
    )
    return draft.withCurrentManager(members).canBeManagedBy(actor, members)
}
