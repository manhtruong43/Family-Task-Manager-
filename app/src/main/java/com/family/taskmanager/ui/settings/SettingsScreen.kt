package com.family.taskmanager.ui.settings

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.family.taskmanager.R
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.notifications.hasNotificationPermission
import com.family.taskmanager.ui.notifications.rememberNotificationPermissionRequester
import com.family.taskmanager.ui.theme.AppLanguage
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

private val AccentBlue = Color(0xFF38BDF8)
private val AccentGold = Color(0xFFFBBF24)
private val AccentRed = Color(0xFFF87171)
private val DialogBackground = Color(0xFF1E1B4B)

/**
 * Màn hình Cài đặt: hồ sơ cá nhân, nhóm gia đình, ngôn ngữ, thông báo, đăng xuất.
 * Chữ ký (viewModel, onBack, onLogout) giữ nguyên để MainActivity không phải sửa;
 * SettingsViewModel được Hilt tự tạo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val sessionUser by viewModel.currentUser.collectAsState()
    LaunchedEffect(sessionUser) {
        sessionUser?.let { settingsViewModel.setSessionUser(it) }
    }

    val user by settingsViewModel.user.collectAsState()
    val family by settingsViewModel.family.collectAsState()
    val members by settingsViewModel.members.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val language by remember { AppLanguage.languageFlow(context) }.collectAsState()

    val isAdmin = user?.role == UserRole.ADMIN
    val currentOnLogout by rememberUpdatedState(onLogout)

    // Trạng thái hộp thoại / hiển thị
    var showEditName by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<User?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    // Quyền thông báo: tính lại mỗi khi có kết quả xin quyền
    var permissionCheckTick by remember { mutableIntStateOf(0) }
    val permissionGranted = remember(permissionCheckTick) { hasNotificationPermission(context) }
    val permissionDeniedText = stringResource(R.string.set_msg_permission_denied)
    val requestPermission = rememberNotificationPermissionRequester { granted ->
        permissionCheckTick++
        settingsViewModel.setNotificationsEnabled(granted)
        if (!granted) Toast.makeText(context, permissionDeniedText, Toast.LENGTH_LONG).show()
    }

    // Toast + sự kiện rời nhóm
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.messages.collect { res ->
            Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.leftFamily.collect { currentOnLogout() }
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.set_title), color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─────────── Hồ sơ cá nhân ───────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(user?.avatarUrl ?: "🙂", fontSize = 30.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user?.name.orEmpty(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    IconButton(onClick = { showEditName = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.set_edit_name),
                                            tint = AccentBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(user?.email.orEmpty(), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                Text(
                                    stringResource(if (isAdmin) R.string.set_role_admin else R.string.set_role_member),
                                    color = AccentGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (user?.isEmailVerified == true) {
                                GlassBox(cornerRadius = 8.dp) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Verified, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.set_verified), color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.set_change_avatar), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(User.AVATAR_PRESETS) { (emoji, _) ->
                                val isSelected = user?.avatarUrl == emoji
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f))
                                        .border(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) AccentBlue else Color.White.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .clickable { settingsViewModel.updateAvatar(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }

                // ─────────── Nhóm gia đình ───────────
                family?.let { group ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.set_family_section),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.set_family_name, group.name),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Mã mời: mặc định che đi để bảo vệ quyền riêng tư
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.invite_code), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text(
                                        text = if (showCode) group.inviteCode else "• • • • • •",
                                        color = AccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                                IconButton(onClick = { showCode = !showCode }) {
                                    Icon(
                                        if (showCode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(if (showCode) R.string.set_hide_code else R.string.set_show_code),
                                        tint = Color.White
                                    )
                                }
                                val copiedText = stringResource(R.string.invite_code_copied)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(group.inviteCode))
                                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_invite_code), tint = Color.White)
                                }
                                if (isAdmin) {
                                    IconButton(onClick = { confirmRegenerate = true }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.set_regenerate_code), tint = AccentBlue)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.set_members_title, members.size),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                MemberRow(
                                    member = member,
                                    isMe = member.id == user?.id,
                                    canRemove = isAdmin && member.role != UserRole.ADMIN && member.id != user?.id,
                                    onRemove = { memberToRemove = member }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            val adminHasOthers = isAdmin && members.size > 1
                            OutlinedButton(
                                onClick = { confirmLeave = true },
                                enabled = !adminHasOthers,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.set_leave_family))
                            }
                            if (adminHasOthers) {
                                Text(
                                    stringResource(R.string.set_leave_admin_hint),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ─────────── Ngôn ngữ & Thông báo ───────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.set_language), color = Color.White, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LanguageOption(
                                label = stringResource(R.string.set_language_vi),
                                selected = language == AppLanguage.VIETNAMESE,
                                onSelect = {
                                    AppLanguage.setLanguage(context, AppLanguage.VIETNAMESE)
                                    settingsViewModel.onLanguageChanged()
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            LanguageOption(
                                label = stringResource(R.string.set_language_en),
                                selected = language == AppLanguage.ENGLISH,
                                onSelect = {
                                    AppLanguage.setLanguage(context, AppLanguage.ENGLISH)
                                    settingsViewModel.onLanguageChanged()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.set_notifications), color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.set_notifications_desc),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = notificationsEnabled && permissionGranted,
                                onCheckedChange = { turnOn ->
                                    if (turnOn) requestPermission()
                                    else settingsViewModel.setNotificationsEnabled(false)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue)
                            )
                        }
                        if (notificationsEnabled && !permissionGranted) {
                            Text(
                                stringResource(R.string.set_notifications_no_permission),
                                color = AccentGold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // ─────────── Đăng xuất ───────────
                Button(
                    onClick = { confirmLogout = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AccentRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.set_logout), color = AccentRed, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ─────────────────────────── Hộp thoại ───────────────────────────

    if (showEditName) {
        EditNameDialog(
            initialName = user?.name.orEmpty(),
            onDismiss = { showEditName = false },
            onSave = { newName ->
                settingsViewModel.updateName(newName)
                showEditName = false
            }
        )
    }

    if (confirmRegenerate) {
        ConfirmDialog(
            title = stringResource(R.string.set_regenerate_title),
            message = stringResource(R.string.set_regenerate_message),
            confirmText = stringResource(R.string.set_regenerate_confirm),
            confirmColor = AccentBlue,
            onDismiss = { confirmRegenerate = false },
            onConfirm = {
                settingsViewModel.regenerateInviteCode()
                showCode = true
                confirmRegenerate = false
            }
        )
    }

    memberToRemove?.let { member ->
        ConfirmDialog(
            title = stringResource(R.string.set_remove_member_title),
            message = stringResource(R.string.set_remove_member_message, member.name),
            confirmText = stringResource(R.string.set_remove_member_confirm),
            confirmColor = AccentRed,
            onDismiss = { memberToRemove = null },
            onConfirm = {
                settingsViewModel.removeMember(member)
                memberToRemove = null
            }
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.set_leave_title),
            message = stringResource(if (isAdmin) R.string.set_leave_message_admin else R.string.set_leave_message_member),
            confirmText = stringResource(R.string.set_leave_confirm),
            confirmColor = AccentRed,
            onDismiss = { confirmLeave = false },
            onConfirm = {
                settingsViewModel.leaveFamily()
                confirmLeave = false
            }
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.set_logout_title),
            message = stringResource(R.string.set_logout_message),
            confirmText = stringResource(R.string.set_logout),
            confirmColor = AccentRed,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                onLogout()
            }
        )
    }
}

// ─────────────────────────── Thành phần con ───────────────────────────

@Composable
private fun MemberRow(
    member: User,
    isMe: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) Color(0xFF6366F1).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = member.avatarUrl, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (isMe) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_you), color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                stringResource(if (member.role == UserRole.ADMIN) R.string.set_role_admin else R.string.set_role_member),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        Text(
            stringResource(R.string.common_points_short, member.points),
            color = AccentGold,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.PersonRemove,
                    contentDescription = stringResource(R.string.set_remove_member_confirm),
                    tint = AccentRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(end = 8.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = Color.White.copy(alpha = 0.6f))
        )
        Text(label, color = Color.White)
    }
}

@Composable
private fun EditNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.set_edit_name), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(SettingsViewModel.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.full_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                    cursorColor = AccentBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.common_save), color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
