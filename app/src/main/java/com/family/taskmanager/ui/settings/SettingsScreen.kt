package com.family.taskmanager.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val familyGroup by viewModel.familyGroup.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()

    var language by remember { mutableStateOf("vi") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var selectedAvatar by remember { mutableStateOf(currentUser?.avatarUrl ?: "🧔") }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Cài Đặt Ứng Dụng", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về", tint = Color.White)
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
                    .padding(20.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thông tin tài khoản", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            GlassBox(cornerRadius = 8.dp) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = "Xác thực", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Email đã xác thực ✅", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Họ tên: ${currentUser?.name ?: ""}", color = Color.White)
                        Text("Email: ${currentUser?.email ?: ""}", color = Color.White)
                        Text("Vai trò: ${if (currentUser?.role?.name == "ADMIN") "Bố / Mẹ (Admin)" else "Con (Member)"}", color = Color.White)

                        if (familyGroup != null && familyGroup?.inviteCode?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassBox(
                                cornerRadius = 12.dp,
                                backgroundColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Nhóm: ${familyGroup?.name}", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Mã mời: ${familyGroup?.inviteCode}", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }

                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(familyGroup?.inviteCode ?: ""))
                                            Toast.makeText(context, "Đã sao chép mã mời!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sao Chép")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Đổi Avatar:", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(User.AVATAR_PRESETS) { (emoji, label) ->
                                val isSelected = selectedAvatar == emoji
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f))
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f), CircleShape)
                                        .clickable { selectedAvatar = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (familyMembers.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("👨‍👩‍👧‍👦 Danh sách thành viên trong nhóm (${familyMembers.size}):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            familyMembers.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = member.avatarUrl, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(if (member.role == UserRole.ADMIN) "Bố / Mẹ (Admin)" else "Con (Member)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    }
                                    GlassBox(cornerRadius = 6.dp) {
                                        Text("⭐ ${member.points}pt", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tùy chọn ngôn ngữ:", color = Color.White, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = language == "vi",
                                onClick = { language = "vi" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8), unselectedColor = Color.White)
                            )
                            Text("Tiếng Việt", color = Color.White, modifier = Modifier.padding(end = 16.dp))

                            RadioButton(
                                selected = language == "en",
                                onClick = { language = "en" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8), unselectedColor = Color.White)
                            )
                            Text("English", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bật thông báo công việc", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Đăng xuất tài khoản", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
