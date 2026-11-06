package com.family.taskmanager.ui.auth

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

enum class AuthTab {
    LOGIN,
    REGISTER
}

/** Phân biệt rõ Bố / Mẹ / Con khi đăng ký (Bố và Mẹ đều có quyền ADMIN). */
enum class RegisterIdentity(val role: UserRole, val defaultAvatar: String) {
    FATHER(UserRole.ADMIN, "🧔"),
    MOTHER(UserRole.ADMIN, "👩"),
    CHILD(UserRole.MEMBER, "👦")
}

private val AccentBlue = Color(0xFF38BDF8)
private val PrimaryBlue = Color(0xFF0284C7)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (User) -> Unit
) {
    // rememberSaveable: xoay màn hình không mất dữ liệu đang nhập
    var currentTab by rememberSaveable { mutableStateOf(AuthTab.LOGIN) }

    // Dữ liệu form đăng nhập
    var loginName by rememberSaveable { mutableStateOf("") }
    var loginEmail by rememberSaveable { mutableStateOf("") }

    // Dữ liệu form đăng ký
    var regName by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var selectedIdentity by rememberSaveable { mutableStateOf(RegisterIdentity.FATHER) }
    var selectedAvatar by rememberSaveable { mutableStateOf(RegisterIdentity.FATHER.defaultAvatar) }

    // Dữ liệu nhập mã OTP
    var otpInput by rememberSaveable { mutableStateOf("") }

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

            Text(
                text = stringResource(R.string.auth_app_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.auth_app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            val state = authState
            if (state is AuthState.OtpSent) {
                OtpVerificationCard(
                    otpState = state,
                    otpInput = otpInput,
                    onOtpInputChange = { input ->
                        otpInput = input.filter { it.isDigit() }.take(LoginViewModel.OTP_LENGTH)
                    },
                    onAutoFill = { otpInput = state.otpCode },
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
                            title = stringResource(R.string.auth_tab_login),
                            isSelected = currentTab == AuthTab.LOGIN,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentTab = AuthTab.LOGIN
                                viewModel.clearError()
                            }
                        )
                        TabButton(
                            title = stringResource(R.string.auth_tab_register),
                            isSelected = currentTab == AuthTab.REGISTER,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentTab = AuthTab.REGISTER
                                viewModel.clearError()
                            }
                        )
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        if (currentTab == AuthTab.LOGIN) {
                            LoginForm(
                                name = loginName,
                                email = loginEmail,
                                authState = authState,
                                onNameChange = {
                                    loginName = it
                                    viewModel.clearError()
                                },
                                onEmailChange = {
                                    loginEmail = it
                                    viewModel.clearError()
                                },
                                onSubmit = { viewModel.login(loginName, loginEmail) },
                                onSwitchToRegister = {
                                    currentTab = AuthTab.REGISTER
                                    viewModel.clearError()
                                }
                            )
                        } else {
                            RegisterForm(
                                name = regName,
                                email = regEmail,
                                selectedIdentity = selectedIdentity,
                                selectedAvatar = selectedAvatar,
                                authState = authState,
                                onNameChange = {
                                    regName = it
                                    viewModel.clearError()
                                },
                                onEmailChange = {
                                    regEmail = it
                                    viewModel.clearError()
                                },
                                onIdentityChange = { identity ->
                                    selectedIdentity = identity
                                    selectedAvatar = identity.defaultAvatar
                                },
                                onAvatarChange = { selectedAvatar = it },
                                onSubmit = {
                                    viewModel.register(
                                        name = regName,
                                        email = regEmail,
                                        role = selectedIdentity.role,
                                        avatarUrl = selectedAvatar
                                    )
                                },
                                onSwitchToLogin = {
                                    currentTab = AuthTab.LOGIN
                                    viewModel.clearError()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    name: String,
    email: String,
    authState: AuthState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchToRegister: () -> Unit
) {
    Text(
        text = stringResource(R.string.auth_login_title),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
    Text(
        text = stringResource(R.string.auth_login_subtitle),
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
    )

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.auth_field_login_name), color = Color.White.copy(alpha = 0.8f)) },
        placeholder = { Text(stringResource(R.string.auth_hint_name), color = Color.White.copy(alpha = 0.4f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = authTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.auth_field_email), color = Color.White.copy(alpha = 0.8f)) },
        placeholder = { Text(stringResource(R.string.auth_hint_email), color = Color.White.copy(alpha = 0.4f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        colors = authTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(20.dp))

    ErrorMessage(authState = authState)

    SubmitButton(
        text = stringResource(R.string.auth_btn_login),
        isLoading = authState is AuthState.Loading,
        onClick = onSubmit
    )

    Spacer(modifier = Modifier.height(12.dp))

    SwitchTabRow(
        question = stringResource(R.string.auth_no_account),
        action = stringResource(R.string.auth_register_now),
        onClick = onSwitchToRegister
    )
}

@Composable
private fun RegisterForm(
    name: String,
    email: String,
    selectedIdentity: RegisterIdentity,
    selectedAvatar: String,
    authState: AuthState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onIdentityChange: (RegisterIdentity) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchToLogin: () -> Unit
) {
    Text(
        text = stringResource(R.string.auth_register_title),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
    Text(
        text = stringResource(R.string.auth_register_subtitle),
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
    )

    SectionLabel(stringResource(R.string.auth_step_avatar))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(User.AVATAR_PRESETS) { (emoji, _) ->
            val isSelected = selectedAvatar == emoji
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) PrimaryBlue.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .clickable { onAvatarChange(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    SectionLabel(stringResource(R.string.auth_step_info))
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.auth_field_register_name), color = Color.White.copy(alpha = 0.8f)) },
        placeholder = { Text(stringResource(R.string.auth_hint_name), color = Color.White.copy(alpha = 0.4f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = authTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.auth_field_register_email), color = Color.White.copy(alpha = 0.8f)) },
        placeholder = { Text(stringResource(R.string.auth_hint_email), color = Color.White.copy(alpha = 0.4f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        colors = authTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(14.dp))

    SectionLabel(stringResource(R.string.auth_step_role))
    RoleOption(
        selected = selectedIdentity == RegisterIdentity.FATHER,
        title = stringResource(R.string.auth_role_father),
        description = stringResource(R.string.auth_role_father_desc),
        onClick = { onIdentityChange(RegisterIdentity.FATHER) }
    )
    RoleOption(
        selected = selectedIdentity == RegisterIdentity.MOTHER,
        title = stringResource(R.string.auth_role_mother),
        description = stringResource(R.string.auth_role_mother_desc),
        onClick = { onIdentityChange(RegisterIdentity.MOTHER) }
    )
    RoleOption(
        selected = selectedIdentity == RegisterIdentity.CHILD,
        title = stringResource(R.string.auth_role_child_option),
        description = stringResource(R.string.auth_role_child_option_desc),
        onClick = { onIdentityChange(RegisterIdentity.CHILD) }
    )

    Spacer(modifier = Modifier.height(18.dp))

    ErrorMessage(authState = authState)

    SubmitButton(
        text = stringResource(R.string.auth_btn_register),
        isLoading = authState is AuthState.Loading,
        onClick = onSubmit
    )

    Spacer(modifier = Modifier.height(12.dp))

    SwitchTabRow(
        question = stringResource(R.string.auth_have_account),
        action = stringResource(R.string.auth_login_now),
        onClick = onSwitchToLogin
    )
}

/** Thẻ xác thực OTP mô phỏng (mã hiện ngay trong "hộp thư mô phỏng"). */
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
                text = stringResource(R.string.auth_otp_title),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            Text(
                text = stringResource(R.string.auth_otp_sent_to),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = otpState.pendingUser.email,
                color = AccentBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Hộp thư mô phỏng
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auth_otp_inbox),
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.auth_otp_validity),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.auth_otp_code_is, otpState.otpCode),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onAutoFill,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text(stringResource(R.string.auth_otp_autofill), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = otpInput,
                onValueChange = onOtpInputChange,
                label = { Text(stringResource(R.string.auth_otp_field), color = Color.White.copy(alpha = 0.8f)) },
                placeholder = { Text("______", color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                colors = authTextFieldColors(),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            otpState.message?.let { message ->
                Text(
                    text = stringResource(message.res, *message.args.toTypedArray()),
                    color = if (message.isError) Color(0xFFF87171) else Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onVerify,
                enabled = otpInput.trim().length == LoginViewModel.OTP_LENGTH,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.auth_otp_confirm), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onResend) {
                    Text(stringResource(R.string.auth_otp_resend), color = AccentBlue, fontSize = 13.sp)
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.auth_otp_back), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun RoleOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AccentBlue,
                unselectedColor = Color.White.copy(alpha = 0.6f)
            )
        )
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SubmitButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SwitchTabRow(question: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = question, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        TextButton(onClick = onClick) {
            Text(text = action, color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            .background(if (isSelected) PrimaryBlue else Color.Transparent)
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
                text = stringResource(authState.message.res, *authState.message.args.toTypedArray()),
                color = Color(0xFFFCA5A5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = AccentBlue
)
