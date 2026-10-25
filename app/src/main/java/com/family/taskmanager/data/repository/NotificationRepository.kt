package com.family.taskmanager.data.repository

import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý trung tâm thông báo trong app (bảng notifications)
 * và phát thông báo đẩy tương ứng.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val pushNotificationService: PushNotificationService
) {
    enum class Kind { TASK, REWARD }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> =
        notificationDao.getNotificationsByUser(userId)

    fun observeUnreadCount(userId: String): Flow<Int> =
        notificationDao.observeUnreadCount(userId)

    suspend fun markAsRead(notificationId: String) = notificationDao.markAsRead(notificationId)

    suspend fun markAllAsRead(userId: String) = notificationDao.markAllAsRead(userId)

    suspend fun delete(notificationId: String) = notificationDao.deleteNotification(notificationId)

    suspend fun deleteAll(userId: String) = notificationDao.deleteAllForUser(userId)

    suspend fun getAdminIds(familyId: String): List<String> =
        notificationDao.getUserIdsByRole(familyId, UserRole.ADMIN)

    suspend fun getFamilyMemberIds(familyId: String): List<String> =
        notificationDao.getFamilyMemberIds(familyId)

    /** Lưu thông báo vào hộp thư của 1 người và bắn thông báo đẩy. */
    suspend fun notifyUser(userId: String, title: String, message: String, kind: Kind) {
        notifyUsers(listOf(userId), title, message, kind)
    }

    /** Lưu thông báo cho nhiều người (vd: tất cả Admin) và bắn 1 thông báo đẩy. */
    suspend fun notifyUsers(userIds: List<String>, title: String, message: String, kind: Kind) {
        val receivers = userIds.filter { it.isNotBlank() }.distinct()
        if (receivers.isEmpty()) return

        val now = System.currentTimeMillis()
        notificationDao.insertNotifications(
            receivers.map { userId ->
                NotificationItem(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    message = message,
                    timestamp = now
                )
            }
        )

        when (kind) {
            Kind.TASK -> pushNotificationService.sendTaskNotification(title, message)
            Kind.REWARD -> pushNotificationService.sendRewardNotification(title, message)
        }
    }
}
