package com.family.taskmanager.ui.calendar

import com.family.taskmanager.data.model.RecurringSchedule
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
