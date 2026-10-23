package com.family.taskmanager.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FamilySetupState {
    object Idle : FamilySetupState()
    object Loading : FamilySetupState()
    data class Success(val familyGroup: FamilyGroup) : FamilySetupState()
    data class Error(val message: String) : FamilySetupState()
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FamilySetupState>(FamilySetupState.Idle)
    val state: StateFlow<FamilySetupState> = _state.asStateFlow()

    fun createFamily(familyName: String, currentUser: User) {
        if (familyName.isBlank()) {
            _state.value = FamilySetupState.Error("Vui lòng nhập tên gia đình")
            return
        }
        viewModelScope.launch {
            _state.value = FamilySetupState.Loading
            try {
                val family = familyRepository.createFamily(familyName, currentUser)
                _state.value = FamilySetupState.Success(family)
            } catch (e: Exception) {
                _state.value = FamilySetupState.Error(e.message ?: "Lỗi khi tạo nhóm gia đình")
            }
        }
    }

    fun joinFamily(inviteCode: String, currentUser: User) {
        if (inviteCode.isBlank()) {
            _state.value = FamilySetupState.Error("Vui lòng nhập mã mời")
            return
        }
        viewModelScope.launch {
            _state.value = FamilySetupState.Loading
            try {
                val family = familyRepository.joinFamilyByCode(inviteCode, currentUser)
                if (family != null) {
                    _state.value = FamilySetupState.Success(family)
                } else {
                    _state.value = FamilySetupState.Error("Mã mời không tồn tại. Vui lòng kiểm tra lại.")
                }
            } catch (e: Exception) {
                _state.value = FamilySetupState.Error(e.message ?: "Lỗi khi tham gia gia đình")
            }
        }
    }
}
