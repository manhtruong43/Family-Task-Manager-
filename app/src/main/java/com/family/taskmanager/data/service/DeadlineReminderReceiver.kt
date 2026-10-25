package com.family.taskmanager.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cầu nối để BroadcastReceiver lấy được các đối tượng Hilt (singleton).
 * Dùng EntryPoint thay vì @AndroidEntryPoint để tránh lỗi gọi super.onReceive() trừu tượng.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun deadlineReminderScheduler(): DeadlineReminderScheduler
}

/** Nhận báo thức hạn chót từ AlarmManager. */
class DeadlineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER) return
        val taskId = intent.getStringExtra(DeadlineReminderScheduler.EXTRA_TASK_ID) ?: return

        val scheduler = EntryPointAccessors
            .fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
            .deadlineReminderScheduler()

        // goAsync(): cho phép đọc database trên luồng nền sau khi onReceive() trả về
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.onReminderFired(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Đặt lại toàn bộ báo thức sau khi khởi động lại máy hoặc cập nhật app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val scheduler = EntryPointAccessors
            .fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
            .deadlineReminderScheduler()

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll()
                scheduler.start()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
