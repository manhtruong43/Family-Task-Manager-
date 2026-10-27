package com.family.taskmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.User

@Database(
    entities = [
        User::class,
        FamilyGroup::class,
        Task::class,
        Reward::class,
        RewardRedemption::class,
        NotificationItem::class,
        TaskReaction::class,
        RecurringSchedule::class // Lịch trình cố định (Chức năng 6 - Dũng)
    ],
    version = 4, // 3 -> 4: thêm bảng recurring_schedules
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun familyDao(): FamilyDao
    abstract fun taskDao(): TaskDao
    abstract fun rewardDao(): RewardDao
    abstract fun notificationDao(): NotificationDao
    abstract fun taskReactionDao(): TaskReactionDao
    abstract fun recurringScheduleDao(): RecurringScheduleDao
}
