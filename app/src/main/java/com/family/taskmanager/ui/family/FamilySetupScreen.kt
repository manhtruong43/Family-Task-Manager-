package com.family.taskmanager.ui.family

import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.FamilyRepository
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

private val AccentBlue = Color(0xFF38BDF8)
private val AccentGold = Color(0xFFFBBF24)

/**
 * Màn hình thiết lập nhóm gia đình (sau khi đăng nhập lần đầu).
 *
 * Phân quyền tự động theo vai trò đã chọn khi đăng nhập:
 *  - Quản lý (Bố/Mẹ)  -> TẠO nhóm mới, trở thành Admin của nhóm.
 *  - Thành viên (Con) -> THAM GIA nhóm bằng mã mời, trở thành Member.
 */
@Composable
fun FamilySetupScreen(
    currentUser: User,
    viewModel: FamilyViewModel,
    onSuccess: (String) -> Unit
) {
    val isAdmin = currentUser.role == UserRole.ADMIN
    var familyName by rememberSaveable { mutableStateOf("") }
    var inviteCodeInput by rememberSaveable { mutableStateOf("") }

    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Tham gia thành công -> vào thẳng trang chủ
    LaunchedEffect(state) {
        val current = state
        if (current is FamilySetupState.Joined) {
            onSuccess(current.familyGroup.id)
        }
    }

    GlassGradientBackground {
        Scaffold(containerColor = Color.Transparent) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.fam_setup_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
                Text(
                    text = stringResource(
                        if (isAdmin) R.string.fam_setup_subtitle_admin else R.string.fam_setup_subtitle_member,
                        currentUser.name
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                )

                val created = (state as? FamilySetupState.Created)?.familyGroup
                if (created != null) {
                    // ─── Tạo nhóm thành công: hiển thị mã mời ───
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.fam_created_title),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = created.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.fam_your_invite_code),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                created.inviteCode.forEach { char ->
                                    GlassBox(cornerRadius = 10.dp) {
                                        Text(
                                            text = char.toString(),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.fam_invite_hint),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            val copiedText = stringResource(R.string.invite_code_copied)
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(created.inviteCode))
                                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.copy_invite_code), color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { onSuccess(created.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(stringResource(R.string.fam_go_home), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (isAdmin) {
                                // ─── Bố/Mẹ: tạo nhóm ───
                                Text(
                                    stringResource(R.string.create_family),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = familyName,
                                    onValueChange = {
                                        familyName = it.take(FamilyRepository.MAX_FAMILY_NAME_LENGTH)
                                        viewModel.clearError()
                                    },
                                    label = { Text(stringResource(R.string.fam_name_hint)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = familyTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.createFamily(familyName, currentUser) },
                                    enabled = state !is FamilySetupState.Loading && familyName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    if (state is FamilySetupState.Loading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(stringResource(R.string.fam_create_button), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // ─── Con: tham gia bằng mã mời ───
                                Text(
                                    stringResource(R.string.join_family),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { input ->
                                        inviteCodeInput = input.filter { it.isDigit() }
                                            .take(FamilyRepository.INVITE_CODE_LENGTH)
                                        viewModel.clearError()
                                    },
                                    label = { Text(stringResource(R.string.fam_code_hint)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    colors = familyTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.joinFamily(inviteCodeInput, currentUser) },
                                    enabled = state !is FamilySetupState.Loading &&
                                            inviteCodeInput.length == FamilyRepository.INVITE_CODE_LENGTH,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    if (state is FamilySetupState.Loading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(stringResource(R.string.fam_join_button), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Giải thích phân quyền tự động
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = Color.White.copy(alpha = 0.06f)) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isAdmin) R.string.fam_role_hint_admin else R.string.fam_role_hint_member
                                ),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    val errorState = state
                    if (errorState is FamilySetupState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(errorState.messageRes),
                            color = Color(0xFFF87171),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun familyTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    cursorColor = AccentBlue
)
