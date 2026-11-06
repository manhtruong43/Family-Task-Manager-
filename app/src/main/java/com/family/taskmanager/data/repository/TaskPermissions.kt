package com.family.taskmanager.data.repository

import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole

/** Kết quả một thao tác trên nhiệm vụ (giao diện dùng để báo cho người dùng). */
enum class TaskActionResult {
    SUCCESS,
    NOT_ALLOWED,     // Người thao tác không có quyền với nhiệm vụ này
    INVALID_STATE,   // Trạng thái hiện tại không cho phép (vd: duyệt 2 lần, nộp khi đã chờ duyệt)
    NOT_FOUND,       // Nhiệm vụ không còn tồn tại
    NO_PROOF,        // Nộp báo cáo mà không có ảnh minh chứng hợp lệ
    INVALID_INPUT    // Dữ liệu tạo nhiệm vụ không hợp lệ (tên trống, điểm âm...)
}

/**
 * Quy tắc phân quyền nhiệm vụ - viết thành hàm thuần (không đụng DB, không đụng Android)
 * để dùng chung cho giao diện (ẩn/hiện nút) và TaskRepository (chặn ở tầng nghiệp vụ),
 * đồng thời kiểm thử được bằng unit test JVM (xem TaskPermissionsTest).
 *
 * Quy ước: task.assignedToUserId rỗng = việc giao cho cả nhà.
 */
object TaskPermissions {

    /** Hai đối tượng có thuộc cùng một gia đình (và gia đình đó tồn tại) không. */
    fun sameFamily(user: User?, task: Task): Boolean =
        user != null && user.familyId.isNotBlank() && user.familyId == task.familyId

    /**
     * Việc này có phải "của tôi" không: giao đích danh cho tôi, hoặc việc chung của cả nhà
     * (ai làm cũng được) mà tôi KHÔNG phải người giao - cả Con lẫn Bố/Mẹ kia đều nhận được.
     */
    fun isMine(user: User?, task: Task): Boolean {
        if (!sameFamily(user, task)) return false
        user!!
        return if (task.assignedToUserId.isBlank()) {
            user.id != task.createdByUserId
        } else {
            task.assignedToUserId == user.id
        }
    }

    /** Được bắt đầu làm / đặt lại / nộp minh chứng: chỉ người được giao (việc chung: mọi người trừ người giao). */
    fun canWork(user: User?, task: Task): Boolean = isMine(user, task)

    /**
     * Được duyệt / từ chối: Admin (Bố/Mẹ) cùng gia đình, và KHÔNG phải người đang làm việc đó
     * (Mẹ giao cho Bố thì chỉ Mẹ duyệt được - không ai tự duyệt việc của mình).
     */
    fun canReview(user: User?, task: Task): Boolean =
        sameFamily(user, task) && user!!.role == UserRole.ADMIN && task.assignedToUserId != user.id

    /** Trạng thái còn SỬA được nội dung: chưa ai nộp minh chứng. */
    val EDITABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)

    /** Trạng thái còn HỦY được: mọi trạng thái trừ đã hoàn thành (điểm đã cộng, thống kê đã tính). */
    val CANCELLABLE_STATUSES = setOf(
        TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PENDING_APPROVAL, TaskStatus.REJECTED
    )

    /**
     * Được sửa / hủy (chưa xét trạng thái): Bố/Mẹ cùng gia đình, trừ việc đang giao cho chính mình.
     * Giao diện và Repository kết hợp thêm EDITABLE_STATUSES / CANCELLABLE_STATUSES.
     */
    fun canManage(user: User?, task: Task): Boolean =
        sameFamily(user, task) && user!!.role == UserRole.ADMIN && task.assignedToUserId != user.id

    fun canEdit(user: User?, task: Task): Boolean =
        canManage(user, task) && task.status in EDITABLE_STATUSES

    fun canCancel(user: User?, task: Task): Boolean =
        canManage(user, task) && task.status in CANCELLABLE_STATUSES

    /** Được giao việc cho [assignee]: người nhận cùng gia đình và KHÔNG phải chính người giao. */
    fun canAssignTo(actor: User?, assignee: User?): Boolean =
        canCreate(actor) && assignee != null && assignee.familyId == actor!!.familyId && assignee.id != actor.id

    /** Được tạo / giao việc mới: Admin đã có gia đình. */
    fun canCreate(user: User?): Boolean =
        user != null && user.role == UserRole.ADMIN && user.familyId.isNotBlank()
}
