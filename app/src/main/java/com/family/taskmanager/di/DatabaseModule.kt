package com.family.taskmanager.di

import android.content.Context
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.AppMigrations
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
            // Giữ dữ liệu khi nâng cấp: mỗi lần tăng version phải có Migration trong AppMigrations.
            .addMigrations(*AppMigrations.ALL)
            // Chỉ các bản DB thời còn phát triển (1-3) mới được xóa đi tạo lại
            .fallbackToDestructiveMigrationFrom(*AppMigrations.LEGACY_DEV_VERSIONS)
            // Cài bản app cũ hơn đè lên (hay gặp khi đổi nhánh git lúc phát triển) -> tạo lại DB thay vì crash
            .fallbackToDestructiveMigrationOnDowngrade()
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
