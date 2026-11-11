package com.family.taskmanager.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.R
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.UserRepository
import com.family.taskmanager.data.service.EmailVerificationService
import com.family.taskmanager.data.service.PushNotificationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Thông báo cho màn đăng nhập dưới dạng MÃ chuỗi tài nguyên (không phải chữ cứng)
 * để đổi Tiếng Việt / English là đổi theo, và giao diện biết chắc đây là lỗi hay thông tin.
 */
data class AuthMessage(
    @StringRes val res: Int,
    val args: List<String> = emptyList(),
    val isError: Boolean = true
)

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class OtpSent(
        val pendingUser: User,
        val otpCode: String,
        val isRegister: Boolean,
        val message: AuthMessage? = null
    ) : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: AuthMessage) : AuthState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val emailVerificationService: EmailVerificationService,
    private val pushNotificationService: PushNotificationService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        // Biểu thức chính quy kiểm tra định dạng email chuẩn (Gmail và các nhà cung cấp phổ biến)
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        const val OTP_LENGTH = 6
        const val MIN_NAME_LENGTH = 2

        fun isValidEmail(email: String): Boolean {
            val trimmed = email.trim()
            return trimmed.isNotBlank() && EMAIL_REGEX.matches(trimmed)
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }

    private fun fail(@StringRes res: Int, vararg args: String) {
        _authState.value = AuthState.Error(AuthMessage(res, args.toList()))
    }

    /**
     * Luồng ĐĂNG NHẬP:
     * - Kiểm tra định dạng Email.
     * - Email phải tồn tại và Họ tên phải khớp với tài khoản đã đăng ký.
     * - Nếu tài khoản chưa xác thực email -> yêu cầu nhập OTP mô phỏng.
     */
    fun login(name: String, email: String) {
        val cleanName = name.trim()
        val cleanEmail = email.lowercase().trim()

        when {
            cleanName.isBlank() -> return fail(R.string.auth_err_name_required)
            cleanEmail.isBlank() -> return fail(R.string.auth_err_email_required)
            !isValidEmail(cleanEmail) -> return fail(R.string.auth_err_email_invalid)
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val existingUser = userRepository.getUserByEmailSync(cleanEmail)
            if (existingUser == null) {
                fail(R.string.auth_err_email_not_registered, cleanEmail)
                return@launch
            }

            // Tên đăng nhập phải khớp với tài khoản của email này
            if (!existingUser.name.trim().equals(cleanName, ignoreCase = true)) {
                fail(R.string.auth_err_name_mismatch, cleanEmail)
                return@launch
            }

            if (!existingUser.isEmailVerified) {
                val otpCode = emailVerificationService.sendVerificationOtp(cleanEmail)
                pushNotificationService.sendOtpNotification(cleanEmail, otpCode)
                _authState.value = AuthState.OtpSent(
                    pendingUser = existingUser,
                    otpCode = otpCode,
                    isRegister = false,
                    message = AuthMessage(R.string.auth_info_not_activated, isError = false)
                )
                return@launch
            }

            _authState.value = AuthState.Success(existingUser)
        }
    }

    /**
     * Luồng ĐĂNG KÝ:
     * - Họ tên ít nhất 2 ký tự, Email đúng định dạng và chưa được dùng.
     * - Sinh mã OTP mô phỏng và gửi thông báo.
     */
    fun register(
        name: String,
        email: String,
        role: UserRole,
        avatarUrl: String,
        relationLabel: String = ""
    ) {
        val cleanName = name.trim()
        val cleanEmail = email.lowercase().trim()

        when {
            cleanName.length < MIN_NAME_LENGTH -> return fail(R.string.auth_err_name_too_short)
            cleanEmail.isBlank() -> return fail(R.string.auth_err_email_required)
            !isValidEmail(cleanEmail) -> return fail(R.string.auth_err_email_invalid)
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val existingUser = userRepository.getUserByEmailSync(cleanEmail)
            if (existingUser != null) {
                // Không hiện tên chủ tài khoản: tránh lộ thông tin người khác
                fail(R.string.auth_err_email_taken, cleanEmail)
                return@launch
            }

            val pendingUser = User(
                id = UUID.nameUUIDFromBytes(cleanEmail.toByteArray()).toString(),
                name = cleanName,
                email = cleanEmail,
                role = role,
                avatarUrl = avatarUrl,
                isEmailVerified = false,
                relationLabel = relationLabel
            )

            val otpCode = emailVerificationService.sendVerificationOtp(cleanEmail)
            pushNotificationService.sendOtpNotification(cleanEmail, otpCode)

            _authState.value = AuthState.OtpSent(
                pendingUser = pendingUser,
                otpCode = otpCode,
                isRegister = true,
                message = AuthMessage(R.string.auth_info_otp_sent, isError = false)
            )
        }
    }

    /** Xác nhận mã OTP người dùng nhập. */
    fun verifyOtp(inputCode: String) {
        val currentState = _authState.value
        if (currentState !is AuthState.OtpSent) {
            fail(R.string.auth_err_session_invalid)
            return
        }

        val cleanCode = inputCode.trim()
        if (cleanCode.length != OTP_LENGTH) {
            _authState.value = currentState.copy(message = AuthMessage(R.string.auth_err_otp_length))
            return
        }

        val email = currentState.pendingUser.email
        if (!emailVerificationService.verifyOtp(email, cleanCode)) {
            _authState.value = currentState.copy(message = AuthMessage(R.string.auth_err_otp_wrong))
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val verifiedUser = currentState.pendingUser.copy(isEmailVerified = true)

            if (currentState.isRegister) {
                userRepository.saveUser(verifiedUser)
            } else {
                userRepository.updateUser(verifiedUser)
            }

            _authState.value = AuthState.Success(verifiedUser)
        }
    }

    /** Gửi lại mã OTP mới khi hết hạn hoặc chưa nhận được. */
    fun resendOtp() {
        val currentState = _authState.value
        if (currentState !is AuthState.OtpSent) return

        viewModelScope.launch {
            val email = currentState.pendingUser.email
            val newOtp = emailVerificationService.sendVerificationOtp(email)
            pushNotificationService.sendOtpNotification(email, newOtp)

            _authState.value = currentState.copy(
                otpCode = newOtp,
                message = AuthMessage(R.string.auth_info_otp_resent, isError = false)
            )
        }
    }

    /** Hủy xác thực OTP và quay lại form nhập liệu. */
    fun cancelOtp() {
        _authState.value = AuthState.Idle
    }
}
