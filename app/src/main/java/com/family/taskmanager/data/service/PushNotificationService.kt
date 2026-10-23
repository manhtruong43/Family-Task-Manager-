package com.family.taskmanager.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.family.taskmanager.MainActivity
import com.family.taskmanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_TASKS = "channel_tasks"
        const val CHANNEL_ID_REWARDS = "channel_rewards"
        const val CHANNEL_NAME_TASKS = "Nhiệm vụ gia đình"
        const val CHANNEL_NAME_REWARDS = "Phần thưởng"
        private var notifId = 1000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val taskChannel = NotificationChannel(
                CHANNEL_ID_TASKS,
                CHANNEL_NAME_TASKS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nhiệm vụ được giao, duyệt và từ chối"
                enableVibration(true)
            }

            val rewardChannel = NotificationChannel(
                CHANNEL_ID_REWARDS,
                CHANNEL_NAME_REWARDS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo điểm thưởng và đổi thưởng"
            }

            manager.createNotificationChannel(taskChannel)
            manager.createNotificationChannel(rewardChannel)
        }
    }

    /**
     * Gửi push notification về nhiệm vụ (giao việc, duyệt, từ chối)
     */
    fun sendTaskNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TASKS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId++, notification)
    }

    /**
     * Gửi push notification về phần thưởng (điểm, đổi thưởng)
     */
    fun sendRewardNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REWARDS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId++, notification)
    }
}
