package com.family.taskmanager.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

enum class AuthTab {
    LOGIN,
    REGISTER
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (User) -> Unit
) {
    var currentTab by remember { mutableStateOf(AuthTab.LOGIN) }

    // Dữ liệu form đăng nhập
    var loginName by remember { mutableStateOf("") }
    var loginEmail by remember { mutableStateOf("") }

    // Dữ liệu form đăng ký
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.ADMIN) }
    var selectedAvatar by remember { mutableStateOf(User.AVATAR_PRESETS[0].first) }

    // Dữ liệu nhập mã OTP
    var otpInput by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess((authState as AuthState.Success).user)
        }
    }

    GlassGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Tiêu đề ứng dụng
            Text(
                text = "👨‍👩‍👧‍👦 Family Task Manager",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp
            )
            Text(
                text = "Quản lý việc nhà • Tích điểm • Gắn kết gia đình",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Nếu đang trong bước xác thực OTP thì hiển thị màn hình OTP
            if (authState is AuthState.OtpSent) {
                val otpState = authState as AuthState.OtpSent
                OtpVerificationCard(
                    otpState = otpState,
                    otpInput = otpInput,
                    onOtpInputChange = { if (it.length <= 6) otpInput = it },
                    onAutoFill = { otpInput = otpState.otpCode },
                    onVerify = { viewModel.verifyOtp(otpInput) },
                    onResend = { viewModel.resendOtp() },
                    onCancel = {
                        viewModel.cancelOtp()
                        otpInput = ""
                    }
                )
            } else {
                // Thanh chuyển Tab Đăng nhập / Đăng ký
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    cornerRadius = 14.dp,
                    backgroundColor = Color.Black.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        TabButton(
                            title = "Đăng Nhập",
                            isSelected = currentTab == AuthTab.LOGIN,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentTab = AuthTab.LOGIN
                                viewModel.clearError()
                            }
                        )
                        TabButton(
                            title = "Đăng Ký Mới",
                            isSelected = currentTab == AuthTab.REGISTER,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentTab = AuthTab.REGISTER
                                viewModel.clearError()
                            }
                        )
                    }
                }

                // Card nội dung chính
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        if (currentTab == AuthTab.LOGIN) {
                            // FORM ĐĂNG NHẬP
                            Text(
                                text = "Đăng nhập tài khoản",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Vui lòng nhập đúng Họ tên và Email đã đăng ký",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = loginName,
                                onValueChange = {
                                    loginName = it
                                    viewModel.clearError()
                                },
                                label = { Text("Họ và tên đăng nhập", color = Color.White.copy(alpha = 0.8f)) },
                                placeholder = { Text("Ví dụ: Nguyễn Văn A", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = getTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = loginEmail,
                                onValueChange = {
                                    loginEmail = it
                                    viewModel.clearError()
                                },
                                label = { Text("Địa chỉ Email", color = Color.White.copy(alpha = 0.8f)) },
                                placeholder = { Text("Ví dụ: example@gmail.com", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                colors = getTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Hiển thị thông báo lỗi
                            ErrorMessage(authState = authState)

                            Button(
                                onClick = { viewModel.login(loginName, loginEmail) },
                                enabled = authState !is AuthState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Đăng Nhập", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chưa có tài khoản? ",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        currentTab = AuthTab.REGISTER
                                        viewModel.clearError()
                                    }
                                ) {
                                    Text(
                                        text = "Đăng ký ngay",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            // FORM ĐĂNG KÝ
                            Text(
                                text = "Tạo tài khoản thành viên mới",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Chọn vai trò và nhận mã OTP xác thực email",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                            )

                            Text(
                                text = "1. Chọn Avatar nhận diện:",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(User.AVATAR_PRESETS) { (emoji, label) ->
                                    val isSelected = selectedAvatar == emoji
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFF0284C7).copy(alpha = 0.6f)
                                                else Color.White.copy(alpha = 0.1f)
                                            )
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectedAvatar = emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 24.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "2. Thông tin cá nhân:",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = regName,
                                onValueChange = {
                                    regName = it
                                    viewModel.clearError()
                                },
                                label = { Text("Họ và tên (ít nhất 2 ký tự)", color = Color.White.copy(alpha = 0.8f)) },
                                placeholder = { Text("Ví dụ: Bùi Đình Kiên", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = getTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = {
                                    regEmail = it
                                    viewModel.clearError()
                                },
                                label = { Text("Địa chỉ Email (Nhận mã OTP)", color = Color.White.copy(alpha = 0.8f)) },
                                placeholder = { Text("Ví dụ: buidinhkien@gmail.com", color = Color.White.copy(alpha = 0.4f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                colors = getTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "3. Vai trò trong gia đình:",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRole == UserRole.ADMIN,
                                    onClick = { selectedRole = UserRole.ADMIN },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF38BDF8),
                                        unselectedColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                                Column(modifier = Modifier.clickable { selectedRole = UserRole.ADMIN }) {
                                    Text("Bố / Mẹ (Admin)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Giao việc & Duyệt thưởng", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                RadioButton(
                                    selected = selectedRole == UserRole.MEMBER,
                                    onClick = { selectedRole = UserRole.MEMBER },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF38BDF8),
                                        unselectedColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                                Column(modifier = Modifier.clickable { selectedRole = UserRole.MEMBER }) {
                                    Text("Con (Member)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Làm việc & Đổi quà", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            ErrorMessage(authState = authState)

                            Button(
                                onClick = { viewModel.register(regName, regEmail, selectedRole, selectedAvatar) },
                                enabled = authState !is AuthState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Đăng Ký & Nhận Mã OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Đã có tài khoản? ",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        currentTab = AuthTab.LOGIN
                                        viewModel.clearError()
                                    }
                                ) {
                                    Text(
                                        text = "Đăng nhập",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Giao diện Thẻ Xác Thực OTP Mô Phỏng
 */
@Composable
private fun OtpVerificationCard(
    otpState: AuthState.OtpSent,
    otpInput: String,
    onOtpInputChange: (String) -> Unit,
    onAutoFill: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🔐", fontSize = 42.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Xác Thực Email (OTP)",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )

            Text(
                text = "Hệ thống đã gửi mã OTP 6 chữ số tới email:",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = otpState.pendingUser.email,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // BANNER HỘP THƯ ĐẾN MÔ PHỎNG (Email Simulation Box)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📬 Hộp thư mô phỏng",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Hiệu lực 5 phút",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Mã xác thực OTP của bạn là: ${otpState.otpCode}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onAutoFill,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text("Điền nhanh mã này", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ô nhập OTP
            OutlinedTextField(
                value = otpInput,
                onValueChange = onOtpInputChange,
                label = { Text("Nhập 6 chữ số OTP", color = Color.White.copy(alpha = 0.8f)) },
                placeholder = { Text("______", color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                colors = getTextFieldColors(),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Hiển thị thông báo trạng thái hoặc lỗi
            if (otpState.message.isNotBlank()) {
                val isError = otpState.message.contains("không chính xác") || otpState.message.contains("hết hạn") || otpState.message.contains("phải gồm")
                Text(
                    text = otpState.message,
                    color = if (isError) Color(0xFFF87171) else Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Nút Xác nhận
            Button(
                onClick = onVerify,
                enabled = otpInput.trim().length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Xác Nhận & Hoàn Tất", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onResend) {
                    Text("Gửi lại mã OTP", color = Color(0xFF38BDF8), fontSize = 13.sp)
                }
                TextButton(onClick = onCancel) {
                    Text("Quay lại sửa", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorMessage(authState: AuthState) {
    if (authState is AuthState.Error) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = authState.message,
                color = Color(0xFFFCA5A5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun getTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF38BDF8),
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = Color(0xFF38BDF8)
)
