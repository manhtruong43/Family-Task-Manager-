package com.family.taskmanager.di

import android.content.Context
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.FamilyDao
import com.family.taskmanager.data.local.NotificationDao
import com.family.taskmanager.data.local.RewardDao
import com.family.taskmanager.data.local.TaskDao
import com.family.taskmanager.data.local.TaskReactionDao
import com.family.taskmanager.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "family_task_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideFamilyDao(db: AppDatabase): FamilyDao = db.familyDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideRewardDao(db: AppDatabase): RewardDao = db.rewardDao()

    @Provides
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideTaskReactionDao(db: AppDatabase): TaskReactionDao = db.taskReactionDao()
}
