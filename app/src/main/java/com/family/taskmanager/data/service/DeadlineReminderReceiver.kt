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
    fun recurringTaskScheduler(): RecurringTaskScheduler
}

/**
 * Nhận các báo thức nội bộ của app từ AlarmManager:
 *  - ACTION_DEADLINE_REMINDER: nhắc hạn chót 1 nhiệm vụ.
 *  - ACTION_DAILY_RECURRING: 00:01 mỗi đêm, sinh nhiệm vụ từ lịch trình cố định.
 */
class DeadlineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors
            .fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

        when (intent.action) {
            DeadlineReminderScheduler.ACTION_DEADLINE_REMINDER -> {
                val taskId = intent.getStringExtra(DeadlineReminderScheduler.EXTRA_TASK_ID) ?: return
                val scheduler = entryPoint.deadlineReminderScheduler()
                runAsync { scheduler.onReminderFired(taskId) }
            }

            RecurringTaskScheduler.ACTION_DAILY_RECURRING -> {
                val scheduler = entryPoint.recurringTaskScheduler()
                runAsync { scheduler.onDailyAlarm() }
            }
        }
    }

    /** goAsync(): cho phép đọc/ghi database trên luồng nền sau khi onReceive() trả về. */
    private fun runAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
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

        val entryPoint = EntryPointAccessors
            .fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val deadlineScheduler = entryPoint.deadlineReminderScheduler()
        val recurringScheduler = entryPoint.recurringTaskScheduler()

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deadlineScheduler.rescheduleAll()
                deadlineScheduler.start()
                recurringScheduler.start()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
