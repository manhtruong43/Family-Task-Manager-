package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    // ─── Trung tâm thông báo trong app ───

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun getNotificationsByUser(userId: String): Flow<List<NotificationItem>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationItem>)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllAsRead(userId: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String)

    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteNotification(notificationId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    // ─── Tìm người nhận thông báo ───

    @Query("SELECT id FROM users WHERE familyId = :familyId AND role = :role")
    suspend fun getUserIdsByRole(familyId: String, role: UserRole): List<String>

    @Query("SELECT id FROM users WHERE familyId = :familyId")
    suspend fun getFamilyMemberIds(familyId: String): List<String>

    // ─── Dữ liệu cho bộ nhắc hạn chót (chỉ đọc bảng tasks) ───

    @Query("SELECT * FROM tasks WHERE deadlineTimestamp > 0")
    fun observeTasksWithDeadline(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deadlineTimestamp > 0")
    suspend fun getTasksWithDeadline(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): Task?
}
