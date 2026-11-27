package com.family.taskmanager.ui.task

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.TaskPriority

/**
 * Bản nháp của yêu cầu đã gửi nằm ở HomeViewModel (scope Activity), ngoài navigation entry.
 * Đóng màn hình khi chưa xác minh được commit không làm mất mã chống tạo trùng.
 */
internal data class UnifiedRecurringDraft(
    val requestId: String,
    val editingId: String?,
    val title: String,
    val description: String,
    val points: String,
    val priority: TaskPriority,
    val assigneeIds: List<String>,
    val assignmentMode: RecurringAssignmentMode,
    val repeatType: RepeatType,
    val daysMask: Int,
    val dueMinute: Int
) {
    fun toBundle() = Bundle().apply {
        putString("request", requestId)
        putString("editing", editingId)
        putString("title", title)
        putString("description", description)
        putString("points", points)
        putString("priority", priority.name)
        putStringArrayList("assignees", ArrayList(assigneeIds))
        putString("mode", assignmentMode.name)
        putString("repeat", repeatType.name)
        putInt("days", daysMask)
        putInt("minute", dueMinute)
    }

    companion object {
        fun fromBundle(value: Bundle) = UnifiedRecurringDraft(
            requestId = requireNotNull(value.getString("request")),
            editingId = value.getString("editing"),
            title = value.getString("title").orEmpty(),
            description = value.getString("description").orEmpty(),
            points = value.getString("points").orEmpty(),
            priority = TaskPriority.valueOf(requireNotNull(value.getString("priority"))),
            assigneeIds = value.getStringArrayList("assignees").orEmpty(),
            assignmentMode = RecurringAssignmentMode.valueOf(requireNotNull(value.getString("mode"))),
            repeatType = RepeatType.valueOf(requireNotNull(value.getString("repeat"))),
            daysMask = value.getInt("days"),
            dueMinute = value.getInt("minute")
        )
    }
}

internal class UnifiedRecurringDraftStore(private val state: SavedStateHandle) {
    private fun key(actorId: String, familyId: String) = "recurringDraft:${actorId.length}:$actorId:$familyId"

    fun get(actorId: String, familyId: String): UnifiedRecurringDraft? =
        state.get<Bundle>(key(actorId, familyId))?.let(UnifiedRecurringDraft::fromBundle)

    fun put(actorId: String, familyId: String, draft: UnifiedRecurringDraft) {
        state[key(actorId, familyId)] = draft.toBundle()
    }

    fun clear(actorId: String, familyId: String, requestId: String) {
        if (get(actorId, familyId)?.requestId == requestId) {
            state.remove<Bundle>(key(actorId, familyId))
        }
    }
}
