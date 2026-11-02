package com.family.taskmanager.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.data.repository.ProofImageStorage
import com.family.taskmanager.data.repository.TaskActionResult
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val familyRepository: FamilyRepository,
    private val proofImageStorage: ProofImageStorage
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)

    val currentUser: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) userRepository.getUser(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCurrentUser(user: User) {
        _currentUserId.value = user.id
    }

    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId
    }

    val familyGroup: StateFlow<FamilyGroup?> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                familyRepository.getFamily(user.familyId)
            } else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val familyMembers: StateFlow<List<User>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                userRepository.getUsersByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val familyTasks: StateFlow<List<Task>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                taskRepository.getTasksByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Đang gửi báo cáo minh chứng (chép ảnh + lưu DB) -> khóa nút Gửi để không bấm 2 lần. */
    private val _isSubmittingProof = MutableStateFlow(false)
    val isSubmittingProof: StateFlow<Boolean> = _isSubmittingProof.asStateFlow()

    fun getTaskReactions(taskId: String): StateFlow<List<TaskReaction>> =
        taskRepository.getReactionsByTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleReaction(taskId: String, emoji: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            taskRepository.toggleReaction(taskId, user, emoji)
        }
    }

    fun createTask(
        title: String,
        description: String,
        assignedToUser: User?,
        priority: TaskPriority,
        points: Int,
        deadlineTimestamp: Long,
        onResult: (TaskActionResult) -> Unit = {}
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(TaskActionResult.NOT_ALLOWED)
            return
        }

        val newTask = Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            familyId = user.familyId,
            assignedToUserId = assignedToUser?.id ?: "",
            // Để trống khi giao cho tất cả – UI tự hiển thị nhãn đa ngôn ngữ (R.string.task_assign_all)
            assignedToUserName = assignedToUser?.name ?: "",
            createdByUserId = user.id,
            priority = priority,
            status = TaskStatus.TODO,
            points = points,
            deadlineTimestamp = deadlineTimestamp
        )
        viewModelScope.launch {
            onResult(taskRepository.createTask(newTask, user))
        }
    }

    /**
     * Nộp minh chứng: chép ảnh đã chọn vào bộ nhớ của app trước, rồi mới lưu vào DB.
     * Bắt buộc có ít nhất 1 ảnh chép thành công.
     */
    fun submitTaskProof(
        taskId: String,
        images: List<Uri>,
        note: String,
        onResult: (TaskActionResult) -> Unit = {}
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(TaskActionResult.NOT_ALLOWED)
            return
        }
        if (images.isEmpty()) {
            onResult(TaskActionResult.NO_PROOF)
            return
        }
        if (_isSubmittingProof.value) return

        viewModelScope.launch {
            _isSubmittingProof.value = true
            try {
                val savedPaths = proofImageStorage.persist(taskId, images)
                if (savedPaths.isEmpty()) {
                    onResult(TaskActionResult.NO_PROOF)
                    return@launch
                }
                val result = taskRepository.submitTaskProof(taskId, savedPaths.joinToString(","), note, user)
                // Nộp thất bại (không có quyền / sai trạng thái) -> dọn ảnh vừa chép để không tốn bộ nhớ
                if (result != TaskActionResult.SUCCESS) proofImageStorage.delete(savedPaths)
                onResult(result)
            } finally {
                _isSubmittingProof.value = false
            }
        }
    }

    fun approveTask(task: Task, onResult: (TaskActionResult) -> Unit = {}) =
        runAction(onResult) { user -> taskRepository.approveTask(task, user) }

    fun rejectTask(task: Task, onResult: (TaskActionResult) -> Unit = {}) =
        runAction(onResult) { user -> taskRepository.rejectTask(task, user) }

    /** Chuyển sang "Đang làm" (chỉ người được giao, khi task đang TODO / REJECTED). */
    fun startTask(taskId: String, onResult: (TaskActionResult) -> Unit = {}) =
        runAction(onResult) { user -> taskRepository.startTask(taskId, user) }

    /** Đặt lại về "Chưa làm" (chỉ người được giao, khi task đang IN_PROGRESS). */
    fun resetTaskToTodo(taskId: String, onResult: (TaskActionResult) -> Unit = {}) =
        runAction(onResult) { user -> taskRepository.resetTaskToTodo(taskId, user) }

    private fun runAction(
        onResult: (TaskActionResult) -> Unit,
        action: suspend (User) -> TaskActionResult
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(TaskActionResult.NOT_ALLOWED)
            return
        }
        viewModelScope.launch { onResult(action(user)) }
    }
}
