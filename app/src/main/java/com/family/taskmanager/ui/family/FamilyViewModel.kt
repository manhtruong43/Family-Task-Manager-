package com.family.taskmanager.ui.family

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.R
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.FamilyError
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.data.repository.FamilyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FamilySetupState {
    object Idle : FamilySetupState()
    object Loading : FamilySetupState()
    data class Created(val familyGroup: FamilyGroup) : FamilySetupState()
    data class Joined(val familyGroup: FamilyGroup) : FamilySetupState()
    data class Error(@StringRes val messageRes: Int) : FamilySetupState()
}

/** Chuyển lỗi nghiệp vụ thành chuỗi hiển thị (dùng chung cho màn Family và Settings). */
@StringRes
fun FamilyError.messageRes(): Int = when (this) {
    FamilyError.EMPTY_NAME -> R.string.fam_error_empty_name
    FamilyError.INVALID_CODE -> R.string.fam_error_invalid_code
    FamilyError.CODE_NOT_FOUND -> R.string.fam_error_code_not_found
    FamilyError.ONLY_ADMIN_CAN_CREATE -> R.string.fam_error_only_admin_create
    FamilyError.ONLY_MEMBER_CAN_JOIN -> R.string.fam_error_only_member_join
    FamilyError.ALREADY_IN_FAMILY -> R.string.fam_error_already_in_family
    FamilyError.USER_NOT_FOUND -> R.string.fam_error_user_not_found
    FamilyError.NOT_ALLOWED -> R.string.fam_error_not_allowed
    FamilyError.CANNOT_REMOVE_ADMIN -> R.string.fam_error_cannot_remove_admin
    FamilyError.ADMIN_MUST_STAY -> R.string.fam_error_admin_must_stay
    FamilyError.CODE_GENERATION_FAILED -> R.string.fam_error_general
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FamilySetupState>(FamilySetupState.Idle)
    val state: StateFlow<FamilySetupState> = _state.asStateFlow()

    fun createFamily(familyName: String, currentUser: User) {
        if (_state.value is FamilySetupState.Loading) return
        if (familyName.isBlank()) {
            _state.value = FamilySetupState.Error(R.string.fam_error_empty_name)
            return
        }
        viewModelScope.launch {
            _state.value = FamilySetupState.Loading
            _state.value = try {
                when (val result = familyRepository.createFamily(familyName, currentUser)) {
                    is FamilyResult.Success -> FamilySetupState.Created(result.data)
                    is FamilyResult.Failure -> FamilySetupState.Error(result.error.messageRes())
                }
            } catch (e: Exception) {
                FamilySetupState.Error(R.string.fam_error_general)
            }
        }
    }

    fun joinFamily(inviteCode: String, currentUser: User) {
        if (_state.value is FamilySetupState.Loading) return
        if (inviteCode.isBlank()) {
            _state.value = FamilySetupState.Error(R.string.fam_error_invalid_code)
            return
        }
        viewModelScope.launch {
            _state.value = FamilySetupState.Loading
            _state.value = try {
                when (val result = familyRepository.joinFamilyByCode(inviteCode, currentUser)) {
                    is FamilyResult.Success -> FamilySetupState.Joined(result.data)
                    is FamilyResult.Failure -> FamilySetupState.Error(result.error.messageRes())
                }
            } catch (e: Exception) {
                FamilySetupState.Error(R.string.fam_error_general)
            }
        }
    }

    fun clearError() {
        if (_state.value is FamilySetupState.Error) _state.value = FamilySetupState.Idle
    }
}
