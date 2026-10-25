package com.family.taskmanager.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.family.taskmanager.MainActivity
import com.family.taskmanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dịch vụ phát thông báo hệ thống (local push notification).
 *
 * - 3 kênh thông báo (Notification Channel, bắt buộc từ Android 8.0 / API 26):
 *   nhiệm vụ, phần thưởng, nhắc hạn chót.
 * - Chỉ phát khi: người dùng bật thông báo trong Cài đặt VÀ đã cấp quyền
 *   POST_NOTIFICATIONS (Android 13+ / API 33+).
 */
@Singleton
class PushNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NotificationPreferences
) {
    companion object {
        const val CHANNEL_ID_TASKS = "channel_tasks"
        const val CHANNEL_ID_REWARDS = "channel_rewards"
        const val CHANNEL_ID_REMINDERS = "channel_reminders"

        private val nextNotificationId = AtomicInteger(1000)
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /** Tạo (hoặc cập nhật tên theo ngôn ngữ hiện tại) các kênh thông báo. */
    fun createNotificationChannels() {
        val res = ServiceLocale.localizedContext(context)

        val taskChannel = NotificationChannel(
            CHANNEL_ID_TASKS,
            res.getString(R.string.noti_channel_tasks),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = res.getString(R.string.noti_channel_tasks_desc)
            enableVibration(true)
        }

        val rewardChannel = NotificationChannel(
            CHANNEL_ID_REWARDS,
            res.getString(R.string.noti_channel_rewards),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = res.getString(R.string.noti_channel_rewards_desc)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDERS,
            res.getString(R.string.noti_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = res.getString(R.string.noti_channel_reminders_desc)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(taskChannel, rewardChannel, reminderChannel))
    }

    /** true nếu app được phép hiển thị thông báo (quyền runtime + người dùng không tắt trong hệ thống). */
    fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Thông báo về nhiệm vụ (giao việc, duyệt, từ chối). Giữ nguyên chữ ký để TaskRepository dùng. */
    fun sendTaskNotification(title: String, message: String) {
        post(
            channelId = CHANNEL_ID_TASKS,
            notificationId = nextNotificationId.getAndIncrement(),
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    /** Thông báo về phần thưởng (đổi quà, trao quà, từ chối). */
    fun sendRewardNotification(title: String, message: String) {
        post(
            channelId = CHANNEL_ID_REWARDS,
            notificationId = nextNotificationId.getAndIncrement(),
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /** Nhắc hạn chót: mỗi nhiệm vụ dùng 1 id cố định để không bị hiện trùng. */
    fun sendDeadlineReminder(taskId: String, title: String, message: String) {
        post(
            channelId = CHANNEL_ID_REMINDERS,
            notificationId = "reminder_$taskId".hashCode(),
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun post(
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        priority: Int
    ) {
        if (!preferences.isEnabled || !canPostNotifications()) return

        // Mở lại app đang chạy thay vì khởi động lại từ đầu (tránh mất phiên đăng nhập)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(
                if (channelId == CHANNEL_ID_REMINDERS) android.R.drawable.ic_popup_reminder
                else android.R.drawable.ic_dialog_info
            )
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Người dùng thu hồi quyền giữa chừng -> bỏ qua, dữ liệu vẫn nằm trong trung tâm thông báo của app
        }
    }
}
