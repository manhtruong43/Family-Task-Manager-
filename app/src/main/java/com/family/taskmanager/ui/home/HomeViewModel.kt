package com.family.taskmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskReaction
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun setCurrentUser(user: User) {
        _currentUser.value = user
    }

    val familyGroup: StateFlow<FamilyGroup?> = _currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                familyRepository.getFamily(user.familyId)
            } else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val familyMembers: StateFlow<List<User>> = _currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                userRepository.getUsersByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val familyTasks: StateFlow<List<Task>> = _currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                taskRepository.getTasksByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTaskReactions(taskId: String): StateFlow<List<TaskReaction>> =
        taskRepository.getReactionsByTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleReaction(taskId: String, emoji: String) {
        val user = _currentUser.value ?: return
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
        deadlineTimestamp: Long
    ) {
        val user = _currentUser.value ?: return
        if (title.isBlank() || user.familyId.isEmpty()) return

        val newTask = Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            familyId = user.familyId,
            assignedToUserId = assignedToUser?.id ?: "",
            assignedToUserName = assignedToUser?.name ?: "Tất cả",
            createdByUserId = user.id,
            priority = priority,
            status = TaskStatus.TODO,
            points = points,
            deadlineTimestamp = deadlineTimestamp
        )
        viewModelScope.launch {
            taskRepository.createTask(newTask)
        }
    }

    fun submitTaskProof(taskId: String, proofUrl: String, note: String) {
        viewModelScope.launch {
            taskRepository.submitTaskProof(taskId, proofUrl, note)
        }
    }

    fun approveTask(task: Task) {
        viewModelScope.launch {
            taskRepository.approveTask(task)
        }
    }

    fun rejectTask(task: Task) {
        viewModelScope.launch {
            taskRepository.rejectTask(task)
        }
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch {
            taskRepository.updateTaskStatus(taskId, status)
        }
    }
}
