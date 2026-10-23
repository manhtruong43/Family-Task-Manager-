package com.family.taskmanager.ui.family

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

@Composable
fun FamilySetupScreen(
    currentUser: User,
    viewModel: FamilyViewModel,
    onSuccess: (String) -> Unit
) {
    var familyName by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (currentUser.role == UserRole.ADMIN) "CREATE" else "JOIN") }

    val state by viewModel.state.collectAsState()
    var createdFamily by remember { mutableStateOf<FamilyGroup?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is FamilySetupState.Success) {
            val family = (state as FamilySetupState.Success).familyGroup
            if (mode == "CREATE" && createdFamily == null) {
                createdFamily = family
            } else {
                onSuccess(family.id)
            }
        }
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cài đặt Gia đình",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
                Text(
                    text = "Xin chào ${currentUser.name}! Vui lòng tạo hoặc tham gia nhóm gia đình.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                )

                if (createdFamily != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎉 Tạo Gia Đình Thành Công!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = createdFamily?.name ?: "",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Mã Mời Gia Đình Của Bạn:",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (createdFamily?.inviteCode ?: "000000").forEach { char ->
                                    GlassBox(
                                        cornerRadius = 10.dp
                                    ) {
                                        Text(
                                            text = char.toString(),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(createdFamily?.inviteCode ?: ""))
                                    Toast.makeText(context, "Đã sao chép mã mời gia đình!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép mã", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📋 Sao chép mã mời", color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { onSuccess(createdFamily?.id ?: "") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Vào Trang Chủ Ngay", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (mode == "CREATE") {
                                Text("Tạo nhóm gia đình mới", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = familyName,
                                    onValueChange = { familyName = it },
                                    label = { Text("Tên Gia đình (vd: Gia đình Yêu Thương)", color = Color.White.copy(alpha = 0.8f)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.createFamily(familyName, currentUser) },
                                    enabled = state !is FamilySetupState.Loading && familyName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    if (state is FamilySetupState.Loading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else {
                                        Text("Tạo Gia đình & Sinh mã mời", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { mode = "JOIN" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Đã có mã mời? Tham gia ngay", color = Color.White)
                                }
                            } else {
                                Text("Tham gia gia đình sẵn có", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { inviteCodeInput = it },
                                    label = { Text("Nhập Mã Mời (6 chữ số)", color = Color.White.copy(alpha = 0.8f)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.joinFamily(inviteCodeInput, currentUser) },
                                    enabled = state !is FamilySetupState.Loading && inviteCodeInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    if (state is FamilySetupState.Loading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else {
                                        Text("Tham gia Gia đình", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { mode = "CREATE" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Chưa có nhóm? Tạo nhóm mới", color = Color.White)
                                }
                            }
                        }
                    }

                    if (state is FamilySetupState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (state as FamilySetupState.Error).message,
                            color = Color(0xFFF87171)
                        )
                    }
                }
            }
        }
    }
}
