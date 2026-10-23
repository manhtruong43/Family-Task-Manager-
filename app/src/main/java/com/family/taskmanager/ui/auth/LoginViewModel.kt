package com.family.taskmanager.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun loginOrRegister(name: String, email: String, role: UserRole, avatarUrl: String) {
        if (name.isBlank() || email.isBlank()) {
            _authState.value = AuthState.Error("Vui lòng điền đầy đủ tên và email")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val cleanEmail = email.lowercase().trim()
            val cleanName = name.trim()
            val userId = UUID.nameUUIDFromBytes(cleanEmail.toByteArray()).toString()

            var user = userRepository.getUserSync(userId)
            if (user == null) {
                user = User(
                    id = userId,
                    name = cleanName,
                    email = cleanEmail,
                    role = role,
                    avatarUrl = avatarUrl,
                    isEmailVerified = true
                )
                userRepository.saveUser(user)
            } else {
                user = user.copy(
                    name = cleanName,
                    role = role,
                    avatarUrl = avatarUrl,
                    isEmailVerified = true
                )
                userRepository.updateUser(user)
            }
            _authState.value = AuthState.Success(user)
        }
    }
}
