package com.family.taskmanager.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.family.taskmanager.data.repository.RecurringScheduleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hẹn giờ sinh nhiệm vụ từ lịch trình cố định.
 *
 *  - start(): gọi khi mở app (chuông thông báo xuất hiện) -> sinh việc hôm nay + hẹn báo thức đêm nay.
 *  - 00:01 mỗi đêm: DeadlineReminderReceiver nhận ACTION_DAILY_RECURRING -> onDailyAlarm().
 *  - Khởi động lại máy: BootReceiver gọi start() để hẹn lại báo thức.
 *
 * Dùng báo thức gần đúng (setAndAllowWhileIdle): trễ vài phút sau nửa đêm không ảnh hưởng nghiệp vụ.
 */
@Singleton
class RecurringTaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecurringScheduleRepository
) {
    companion object {
        const val ACTION_DAILY_RECURRING = "com.family.taskmanager.action.DAILY_RECURRING"
        private const val REQUEST_CODE = 7301
        private val DAILY_RUN_TIME: LocalTime = LocalTime.of(0, 1)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** An toàn khi gọi nhiều lần: việc sinh nhiệm vụ không bao giờ tạo trùng. */
    fun start() {
        scheduleNextDailyAlarm()
        scope.launch { repository.generateDueTasks() }
    }

    /** Được gọi bởi receiver lúc 00:01. */
    suspend fun onDailyAlarm() {
        repository.generateDueTasks()
        scheduleNextDailyAlarm()
    }

    private fun scheduleNextDailyAlarm() {
        val zone = ZoneId.systemDefault()
        val triggerAt = LocalDate.now(zone)
            .plusDays(1)
            .atTime(DAILY_RUN_TIME)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, DeadlineReminderReceiver::class.java).apply {
            action = ACTION_DAILY_RECURRING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}
