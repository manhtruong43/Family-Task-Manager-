package com.family.taskmanager.data.repository

import com.family.taskmanager.data.model.Task
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

    /** Việc này có phải "của tôi" không: giao đích danh cho tôi, hoặc giao cho cả nhà và tôi là Con. */
    fun isMine(user: User?, task: Task): Boolean {
        if (!sameFamily(user, task)) return false
        user!!
        return if (task.assignedToUserId.isBlank()) {
            user.role == UserRole.MEMBER
        } else {
            task.assignedToUserId == user.id
        }
    }

    /** Được bắt đầu làm / đặt lại / nộp minh chứng: chỉ người được giao (việc chung: chỉ các Con). */
    fun canWork(user: User?, task: Task): Boolean = isMine(user, task)

    /** Được duyệt / từ chối: Admin (Bố/Mẹ) cùng gia đình. */
    fun canReview(user: User?, task: Task): Boolean =
        sameFamily(user, task) && user!!.role == UserRole.ADMIN

    /** Được tạo / giao việc mới: Admin đã có gia đình. */
    fun canCreate(user: User?): Boolean =
        user != null && user.role == UserRole.ADMIN && user.familyId.isNotBlank()
}
