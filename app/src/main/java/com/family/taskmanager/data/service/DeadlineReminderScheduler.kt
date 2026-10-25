package com.family.taskmanager.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.family.taskmanager.R
import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bộ nhắc hạn chót nhiệm vụ dùng AlarmManager (không cần thêm thư viện).
 *
 * Cách hoạt động:
 *  1. start(): lắng nghe bảng tasks (Flow của Room). Mỗi khi dữ liệu đổi
 *     (tạo / đổi trạng thái / xóa task) sẽ đặt lại toàn bộ báo thức.
 *  2. Mỗi task chưa xong có hạn chót -> đặt 1 báo thức lúc (hạn - LEAD_TIME).
 *     Nếu đã sát hạn hơn LEAD_TIME thì báo sau MIN_DELAY.
 *  3. Đến giờ, DeadlineReminderReceiver gọi onReminderFired(): đọc lại task từ DB,
 *     nếu vẫn chưa xong thì lưu vào hộp thông báo + bắn thông báo đẩy.
 *  4. Khởi động lại máy -> BootReceiver gọi rescheduleAll().
 */
@Singleton
class DeadlineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val preferences: NotificationPreferences,
    private val pushNotificationService: PushNotificationService
) {
    companion object {
        const val ACTION_DEADLINE_REMINDER = "com.family.taskmanager.action.DEADLINE_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"

        /** Nhắc trước hạn chót 30 phút. */
        const val LEAD_TIME_MS = 30 * 60 * 1000L

        /** Nếu đã sát hạn thì nhắc sau 5 giây (tiện cho việc demo). */
        const val MIN_DELAY_MS = 5_000L

        /** Các trạng thái còn cần nhắc. PENDING_APPROVAL/COMPLETED thì không nhắc nữa. */
        val REMINDABLE_STATUSES = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduledTaskIds = mutableSetOf<String>()

    /** Gọi nhiều lần cũng chỉ chạy 1 lần. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            notificationDao.observeTasksWithDeadline()
                .distinctUntilChanged()
                .collect { tasks -> syncAlarms(tasks) }
        }
    }

    suspend fun rescheduleAll() {
        syncAlarms(notificationDao.getTasksWithDeadline())
    }

    @Synchronized
    private fun syncAlarms(tasks: List<Task>) {
        val now = System.currentTimeMillis()
        preferences.pruneReminded(tasks.map { it.id }.toSet())

        val activeTasks = tasks.filter {
            it.status in REMINDABLE_STATUSES && it.deadlineTimestamp > now
        }
        val activeIds = activeTasks.map { it.id }.toSet()

        // Hủy báo thức của các task đã xong / bị xóa / đã quá hạn
        (scheduledTaskIds - activeIds).forEach { cancelAlarm(it) }
        scheduledTaskIds.clear()

        activeTasks.forEach { task ->
            if (preferences.wasReminded(task.id, task.deadlineTimestamp)) return@forEach
            val triggerAt = maxOf(task.deadlineTimestamp - LEAD_TIME_MS, now + MIN_DELAY_MS)
            scheduleAlarm(task.id, triggerAt)
            scheduledTaskIds.add(task.id)
        }
    }

    private fun reminderIntent(taskId: String): Intent =
        Intent(context, DeadlineReminderReceiver::class.java).apply {
            action = ACTION_DEADLINE_REMINDER
            // data khác nhau cho từng task -> mỗi task có 1 PendingIntent riêng
            data = Uri.parse("familytask://reminder/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }

    private fun scheduleAlarm(taskId: String, triggerAtMillis: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            reminderIntent(taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // Android 12+ chưa cấp quyền báo thức chính xác -> dùng báo thức gần đúng
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(taskId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            reminderIntent(taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Được DeadlineReminderReceiver gọi khi báo thức kêu. */
    suspend fun onReminderFired(taskId: String) {
        val task = notificationDao.getTaskById(taskId) ?: return
        if (task.status !in REMINDABLE_STATUSES || task.deadlineTimestamp <= 0L) return
        if (preferences.wasReminded(task.id, task.deadlineTimestamp)) return
        preferences.markReminded(task.id, task.deadlineTimestamp)

        val res = ServiceLocale.localizedContext(context)
        val locale = ServiceLocale.currentLocale(context)
        val deadlineText = SimpleDateFormat("HH:mm dd/MM", locale).format(Date(task.deadlineTimestamp))
        val assignee = if (task.assignedToUserId.isBlank()) {
            res.getString(R.string.noti_everyone)
        } else {
            task.assignedToUserName
        }
        val isOverdue = task.deadlineTimestamp <= System.currentTimeMillis()

        val title = res.getString(
            if (isOverdue) R.string.noti_overdue_title else R.string.noti_reminder_title
        )
        val message = res.getString(
            if (isOverdue) R.string.noti_overdue_message else R.string.noti_reminder_message,
            task.title,
            deadlineText,
            assignee
        )

        // Người nhận: người được giao, hoặc cả nhà nếu task giao cho "Tất cả"
        val receivers = if (task.assignedToUserId.isNotBlank()) {
            listOf(task.assignedToUserId)
        } else {
            notificationDao.getFamilyMemberIds(task.familyId)
        }
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
        pushNotificationService.sendDeadlineReminder(task.id, title, message)
    }
}
