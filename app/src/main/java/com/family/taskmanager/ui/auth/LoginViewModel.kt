package com.family.taskmanager.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class OtpSent(
        val pendingUser: User,
        val otpCode: String,
        val isRegister: Boolean,
        val message: String = ""
    ) : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
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
        // Biểu thức chính quy kiểm tra định dạng email chuẩn RFC và hỗ trợ Gmail / các nhà cung cấp phổ biến
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

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

    /**
     * Luồng ĐĂNG NHẬP (Đã khắc phục lỗi bảo mật & validate nghiêm ngặt):
     * - Kiểm tra định dạng Email hợp lệ chuẩn xác.
     * - Kiểm tra Email có tồn tại trong hệ thống hay không.
     * - Bắt buộc Tên đăng nhập (Họ và tên) và Email phải khớp chính xác với tài khoản đã đăng ký (tránh trường hợp sai tên vẫn đăng nhập được).
     * - Nếu chưa xác thực OTP, yêu cầu xác thực OTP email mô phỏng.
     */
    fun login(name: String, email: String) {
        val cleanName = name.trim()
        val cleanEmail = email.lowercase().trim()

        if (cleanName.isBlank()) {
            _authState.value = AuthState.Error("Vui lòng nhập họ và tên đăng nhập!")
            return
        }
        if (cleanEmail.isBlank()) {
            _authState.value = AuthState.Error("Vui lòng nhập địa chỉ Email!")
            return
        }
        if (!isValidEmail(cleanEmail)) {
            _authState.value = AuthState.Error("Địa chỉ Email không đúng định dạng! (Ví dụ: tenban@gmail.com)")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            // 1. Kiểm tra xem Email đã tồn tại chưa
            val existingUserByEmail = userRepository.getUserByEmailSync(cleanEmail)
            if (existingUserByEmail == null) {
                _authState.value = AuthState.Error(
                    "Email '$cleanEmail' chưa được đăng ký trong hệ thống!\nVui lòng chuyển sang tab 'Đăng ký' để tạo tài khoản mới."
                )
                return@launch
            }

            // 2. Kiểm tra nghiêm ngặt: Tên đăng nhập (Họ và tên) phải khớp chính xác với tài khoản của email này
            if (!existingUserByEmail.name.trim().equals(cleanName, ignoreCase = true)) {
                _authState.value = AuthState.Error(
                    "Tên đăng nhập (Họ và tên) không chính xác với email '$cleanEmail'!\n(Tài khoản đăng ký với email này là: '${existingUserByEmail.name}')"
                )
                return@launch
            }

            // 3. Nếu tài khoản chưa xác thực email, yêu cầu nhập OTP mô phỏng
            if (!existingUserByEmail.isEmailVerified) {
                val otpCode = emailVerificationService.sendVerificationOtp(cleanEmail)
                pushNotificationService.sendOtpNotification(cleanEmail, otpCode)
                _authState.value = AuthState.OtpSent(
                    pendingUser = existingUserByEmail,
                    otpCode = otpCode,
                    isRegister = false,
                    message = "Tài khoản chưa được kích hoạt. Mã OTP đã được gửi tới email của bạn!"
                )
                return@launch
            }

            // 4. Đăng nhập thành công
            _authState.value = AuthState.Success(existingUserByEmail)
        }
    }

    /**
     * Luồng ĐĂNG KÝ:
     * - Validate Họ tên (ít nhất 2 ký tự), Email (đúng định dạng chuẩn).
     * - Kiểm tra xem Email đã tồn tại trong hệ thống chưa.
     * - Sinh mã OTP mô phỏng và gửi notification.
     */
    fun register(name: String, email: String, role: UserRole, avatarUrl: String) {
        val cleanName = name.trim()
        val cleanEmail = email.lowercase().trim()

        if (cleanName.isBlank() || cleanName.length < 2) {
            _authState.value = AuthState.Error("Họ và tên phải có ít nhất 2 ký tự!")
            return
        }
        if (cleanEmail.isBlank()) {
            _authState.value = AuthState.Error("Vui lòng nhập địa chỉ Email!")
            return
        }
        if (!isValidEmail(cleanEmail)) {
            _authState.value = AuthState.Error("Địa chỉ Email không đúng định dạng! (Ví dụ: tenban@gmail.com)")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val existingUser = userRepository.getUserByEmailSync(cleanEmail)
            if (existingUser != null) {
                _authState.value = AuthState.Error(
                    "Email '$cleanEmail' đã được đăng ký tài khoản bởi '${existingUser.name}'.\nVui lòng chuyển sang tab 'Đăng nhập'!"
                )
                return@launch
            }

            val userId = UUID.nameUUIDFromBytes(cleanEmail.toByteArray()).toString()
            val pendingUser = User(
                id = userId,
                name = cleanName,
                email = cleanEmail,
                role = role,
                avatarUrl = avatarUrl,
                isEmailVerified = false
            )

            val otpCode = emailVerificationService.sendVerificationOtp(cleanEmail)
            pushNotificationService.sendOtpNotification(cleanEmail, otpCode)

            _authState.value = AuthState.OtpSent(
                pendingUser = pendingUser,
                otpCode = otpCode,
                isRegister = true,
                message = "Mã OTP xác thực tài khoản đã được gửi tới email mô phỏng!"
            )
        }
    }

    /**
     * Xác nhận mã OTP do người dùng nhập.
     */
    fun verifyOtp(inputCode: String) {
        val currentState = _authState.value
        if (currentState !is AuthState.OtpSent) {
            _authState.value = AuthState.Error("Phiên xác thực không hợp lệ. Vui lòng thử lại!")
            return
        }

        val cleanCode = inputCode.trim()
        if (cleanCode.length != 6) {
            _authState.value = currentState.copy(message = "Mã OTP phải gồm 6 chữ số!")
            return
        }

        val email = currentState.pendingUser.email
        val isVerified = emailVerificationService.verifyOtp(email, cleanCode)

        if (!isVerified) {
            _authState.value = currentState.copy(
                message = "Mã OTP không chính xác hoặc đã hết hạn (5 phút). Vui lòng thử lại!"
            )
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

    /**
     * Gửi lại mã OTP mới khi hết hạn hoặc chưa nhận được.
     */
    fun resendOtp() {
        val currentState = _authState.value
        if (currentState !is AuthState.OtpSent) return

        viewModelScope.launch {
            val email = currentState.pendingUser.email
            val newOtp = emailVerificationService.sendVerificationOtp(email)
            pushNotificationService.sendOtpNotification(email, newOtp)

            _authState.value = currentState.copy(
                otpCode = newOtp,
                message = "Đã gửi lại mã OTP mới tới email của bạn!"
            )
        }
    }

    /**
     * Hủy xác thực OTP và quay lại form nhập liệu.
     */
    fun cancelOtp() {
        _authState.value = AuthState.Idle
    }
}
