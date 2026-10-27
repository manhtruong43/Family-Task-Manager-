package com.family.taskmanager.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.NotificationItem
import com.family.taskmanager.data.repository.NotificationRepository
import com.family.taskmanager.data.service.DeadlineReminderScheduler
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.RecurringTaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val preferences: NotificationPreferences,
    deadlineReminderScheduler: DeadlineReminderScheduler,
    recurringTaskScheduler: RecurringTaskScheduler
) : ViewModel() {

    init {
        // Bắt đầu theo dõi hạn chót ngay khi có màn hình hiển thị chuông thông báo
        deadlineReminderScheduler.start()
        // Sinh nhiệm vụ hôm nay từ lịch trình cố định + hẹn lần chạy lúc 00:01 đêm nay
        recurringTaskScheduler.start()
    }

    private val userId = MutableStateFlow<String?>(null)

    fun setUserId(id: String) {
        if (userId.value != id) userId.value = id
    }

    val notifications: StateFlow<List<NotificationItem>> = userId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else notificationRepository.observeNotifications(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = userId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(0)
            else notificationRepository.observeUnreadCount(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(notificationId: String) {
        viewModelScope.launch { notificationRepository.markAsRead(notificationId) }
    }

    fun markAllAsRead() {
        val id = userId.value ?: return
        viewModelScope.launch { notificationRepository.markAllAsRead(id) }
    }

    fun delete(notificationId: String) {
        viewModelScope.launch { notificationRepository.delete(notificationId) }
    }

    fun deleteAll() {
        val id = userId.value ?: return
        viewModelScope.launch { notificationRepository.deleteAll(id) }
    }

    /** Chỉ tự động hỏi quyền 1 lần; sau đó người dùng bật lại trong Cài đặt. */
    fun shouldAutoAskPermission(): Boolean = !preferences.permissionAsked

    fun onPermissionAsked() {
        preferences.permissionAsked = true
    }
}
