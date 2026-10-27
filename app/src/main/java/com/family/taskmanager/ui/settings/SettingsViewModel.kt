package com.family.taskmanager.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.R
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.data.repository.FamilyResult
import com.family.taskmanager.data.repository.UserRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import com.family.taskmanager.ui.family.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val familyRepository: FamilyRepository,
    private val notificationPreferences: NotificationPreferences,
    private val pushNotificationService: PushNotificationService
) : ViewModel() {

    companion object {
        const val MAX_NAME_LENGTH = 30
    }

    private val sessionUser = MutableStateFlow<User?>(null)

    fun setSessionUser(user: User) {
        if (sessionUser.value?.id != user.id) sessionUser.value = user
    }

    /** Thông tin user luôn đọc lại từ DB (avatar, tên, điểm mới nhất). */
    val user: StateFlow<User?> = sessionUser
        .flatMapLatest { session ->
            if (session == null) flowOf(null)
            else userRepository.getUser(session.id).map { it ?: session }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val familyIdFlow = user.map { it?.familyId.orEmpty() }.distinctUntilChanged()

    val family: StateFlow<FamilyGroup?> = familyIdFlow
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(null) else familyRepository.getFamily(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Danh sách thành viên: Admin đứng đầu, sau đó xếp theo tên. */
    val members: StateFlow<List<User>> = familyIdFlow
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else userRepository.getUsersByFamily(id).map { list ->
                list.sortedWith(compareBy<User> { it.role != UserRole.ADMIN }.thenBy { it.name.lowercase() })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationsEnabled: StateFlow<Boolean> = notificationPreferences.enabled

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    /** Bắn ra khi người dùng đã rời nhóm thành công -> màn hình sẽ đăng xuất. */
    private val _leftFamily = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val leftFamily: SharedFlow<Unit> = _leftFamily.asSharedFlow()

    private fun emit(@StringRes res: Int) {
        _messages.tryEmit(res)
    }

    // ─────────────── Hồ sơ cá nhân ───────────────

    fun updateAvatar(avatar: String) {
        val current = user.value ?: return
        if (current.avatarUrl == avatar) return
        viewModelScope.launch {
            userRepository.updateUser(current.copy(avatarUrl = avatar))
            emit(R.string.set_msg_avatar_saved)
        }
    }

    fun updateName(newName: String) {
        val current = user.value ?: return
        val name = newName.trim().take(MAX_NAME_LENGTH)
        if (name.isEmpty()) {
            emit(R.string.error_empty_name)
            return
        }
        if (name == current.name) return
        viewModelScope.launch {
            userRepository.updateUser(current.copy(name = name))
            emit(R.string.set_msg_name_saved)
        }
    }

    // ─────────────── Thông báo ───────────────

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.setEnabled(enabled)
    }

    fun canPostNotifications(): Boolean = pushNotificationService.canPostNotifications()

    /** Gọi sau khi đổi ngôn ngữ để tên kênh thông báo trong Cài đặt hệ thống cũng đổi theo. */
    fun onLanguageChanged() {
        pushNotificationService.createNotificationChannels()
    }

    // ─────────────── Quản lý nhóm gia đình ───────────────

    fun regenerateInviteCode() {
        val current = user.value ?: return
        viewModelScope.launch {
            when (val result = familyRepository.regenerateInviteCode(current.id)) {
                is FamilyResult.Success -> emit(R.string.set_msg_code_regenerated)
                is FamilyResult.Failure -> emit(result.error.messageRes())
            }
        }
    }

    fun removeMember(member: User) {
        val current = user.value ?: return
        viewModelScope.launch {
            when (val result = familyRepository.removeMember(current.id, member.id)) {
                is FamilyResult.Success -> emit(R.string.set_msg_member_removed)
                is FamilyResult.Failure -> emit(result.error.messageRes())
            }
        }
    }

    fun leaveFamily() {
        val current = user.value ?: return
        viewModelScope.launch {
            when (val result = familyRepository.leaveFamily(current.id)) {
                is FamilyResult.Success -> {
                    emit(R.string.set_msg_left_family)
                    _leftFamily.tryEmit(Unit)
                }
                is FamilyResult.Failure -> emit(result.error.messageRes())
            }
        }
    }
}
