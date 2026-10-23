package com.family.taskmanager.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.task.StatusBadge
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateTaskClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onRewardsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val familyGroup by viewModel.familyGroup.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()
    val tasks by viewModel.familyTasks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var selectedMemberFilter by remember { mutableStateOf<User?>(null) }
    var selectedMemberDetail by remember { mutableStateOf<User?>(null) }

    val tabTitles = listOf("Tất cả", "Của tôi", "Cần duyệt", "Đã xong")

    val filteredTasks = tasks.filter { task ->
        val matchesTab = when (selectedTab) {
            1 -> task.assignedToUserId == currentUser?.id
            2 -> task.status == TaskStatus.PENDING_APPROVAL
            3 -> task.status == TaskStatus.COMPLETED
            else -> true
        }
        val matchesMember = if (selectedMemberFilter != null) {
            task.assignedToUserId == selectedMemberFilter?.id
        } else true

        matchesTab && matchesMember
    }

    if (showInviteDialog && familyGroup != null) {
        FamilyInviteDialog(
            familyName = familyGroup?.name ?: "Gia đình",
            inviteCode = familyGroup?.inviteCode ?: "------",
            onDismiss = { showInviteDialog = false }
        )
    }

    if (selectedMemberDetail != null) {
        MemberDetailDialog(
            member = selectedMemberDetail!!,
            currentUser = currentUser,
            tasks = tasks.filter { it.assignedToUserId == selectedMemberDetail?.id },
            onDismiss = { selectedMemberDetail = null },
            onCreateTaskForMember = {
                selectedMemberDetail = null
                onCreateTaskClick()
            }
        )
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (currentUser?.role == UserRole.ADMIN) {
                    FloatingActionButton(
                        onClick = onCreateTaskClick,
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tạo việc mới")
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Trang chủ") },
                        label = { Text("Nhiệm vụ") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF38BDF8), selectedTextColor = Color(0xFF38BDF8), unselectedIconColor = Color.White.copy(alpha = 0.6f), unselectedTextColor = Color.White.copy(alpha = 0.6f))
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onRewardsClick,
                        icon = { Icon(Icons.Default.CardGiftcard, contentDescription = "Phần thưởng") },
                        label = { Text("Đổi thưởng") },
                        colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White.copy(alpha = 0.6f), unselectedTextColor = Color.White.copy(alpha = 0.6f))
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onCalendarClick,
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Lịch") },
                        label = { Text("Lịch") },
                        colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White.copy(alpha = 0.6f), unselectedTextColor = Color.White.copy(alpha = 0.6f))
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onStatisticsClick,
                        icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Thống kê") },
                        label = { Text("Thống kê") },
                        colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White.copy(alpha = 0.6f), unselectedTextColor = Color.White.copy(alpha = 0.6f))
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onSettingsClick,
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Cài đặt") },
                        label = { Text("Cài đặt") },
                        colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White.copy(alpha = 0.6f), unselectedTextColor = Color.White.copy(alpha = 0.6f))
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header Glass Card
                GlassHeaderSection(
                    userName = currentUser?.name ?: "",
                    userAvatar = currentUser?.avatarUrl ?: "🧔",
                    role = currentUser?.role ?: UserRole.MEMBER,
                    familyName = familyGroup?.name ?: "Gia đình",
                    inviteCode = familyGroup?.inviteCode ?: "",
                    points = currentUser?.points ?: 0,
                    onInviteClick = { showInviteDialog = true }
                )

                // Khối DANH SÁCH THÀNH VIÊN GIA ĐÌNH
                GlassFamilyMembersSection(
                    members = familyMembers,
                    selectedFilter = selectedMemberFilter,
                    onMemberClick = { member ->
                        selectedMemberFilter = if (selectedMemberFilter?.id == member.id) null else member
                    },
                    onMemberLongClick = { member ->
                        selectedMemberDetail = member
                    },
                    onInviteClick = { showInviteDialog = true }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Khối QUY TẮC CƠ BẢN CỦA GIA ĐÌNH
                GlassFamilyRulesSection()

                Spacer(modifier = Modifier.height(6.dp))

                // Thanh Lọc Nhiệm Vụ & Badge Lọc Thành Viên
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedMemberFilter != null) "Công việc của ${selectedMemberFilter?.name}:" else "Danh sách nhiệm vụ:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    if (selectedMemberFilter != null) {
                        GlassBox(
                            cornerRadius = 8.dp,
                            backgroundColor = Color(0xFF38BDF8).copy(alpha = 0.3f),
                            modifier = Modifier.clickable { selectedMemberFilter = null }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Lọc: ${selectedMemberFilter?.name}", fontSize = 11.sp, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Close, contentDescription = "Xóa lọc", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        )
                    }
                }

                // Task List
                if (filteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedMemberFilter != null) "Thành viên ${selectedMemberFilter?.name} chưa có việc" else "Chưa có nhiệm vụ nào",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            GlassTaskItemCard(
                                task = task,
                                viewModel = viewModel,
                                onClick = { onTaskClick(task.id) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassFamilyRulesSection() {
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Gavel, contentDescription = "Quy tắc", tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📜 Quy tắc chung của Gia đình",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Text(
                    text = if (isExpanded) "Thu gọn ▲" else "Xem quy tắc ▼",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. ⏰ Hoàn thành nhiệm vụ việc nhà đúng hạn chót.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                Text("2. 📸 Chụp ảnh minh chứng rõ ràng khi báo cáo hoàn thành.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                Text("3. 🎁 Tích lũy đủ điểm thưởng để đổi quà cuối tuần.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                Text("4. ❤️ Luôn tôn trọng, lễ phép và sẵn sàng hỗ trợ lẫn nhau.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
fun GlassFamilyMembersSection(
    members: List<User>,
    selectedFilter: User?,
    onMemberClick: (User) -> Unit,
    onMemberLongClick: (User) -> Unit,
    onInviteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👨‍👩‍👧‍👦 Thành viên gia đình (${members.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )

            Text(
                text = "Bấm để lọc",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(members, key = { it.id }) { member ->
                val isSelected = selectedFilter?.id == member.id

                GlassCard(
                    onClick = { onMemberClick(member) },
                    cornerRadius = 14.dp,
                    backgroundColor = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                    borderColor = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.width(105.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = member.avatarUrl, fontSize = 22.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = member.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1
                        )

                        Text(
                            text = if (member.role == UserRole.ADMIN) "Bố/Mẹ" else "Con",
                            fontSize = 10.sp,
                            color = if (member.role == UserRole.ADMIN) Color(0xFF38BDF8) else Color(0xFF4ADE80)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        GlassBox(cornerRadius = 6.dp) {
                            Text(
                                text = "⭐ ${member.points}pt",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(
                    onClick = onInviteClick,
                    cornerRadius = 14.dp,
                    backgroundColor = Color.White.copy(alpha = 0.08f),
                    borderColor = Color(0xFFFBBF24).copy(alpha = 0.5f),
                    modifier = Modifier.width(90.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Thêm thành viên",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "+ Mời thêm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemberDetailDialog(
    member: User,
    currentUser: User?,
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onCreateTaskForMember: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = member.avatarUrl, fontSize = 34.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(member.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = if (member.role == UserRole.ADMIN) "Bố / Mẹ (Quản lý)" else "Con (Thành viên gia đình)",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassBox(cornerRadius = 10.dp) {
                        Text(
                            text = "⭐ ${member.points} điểm thưởng",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    GlassBox(cornerRadius = 10.dp) {
                        Text(
                            text = "📋 ${tasks.size} công việc",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (currentUser?.role == UserRole.ADMIN) {
                    Button(
                        onClick = onCreateTaskForMember,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Giao Nhiệm Vụ Mới", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đóng", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GlassHeaderSection(
    userName: String,
    userAvatar: String,
    role: UserRole,
    familyName: String,
    inviteCode: String,
    points: Int,
    onInviteClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = userAvatar, fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = familyName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Xin chào, $userName (${if (role == UserRole.ADMIN) "Bố/Mẹ" else "Con"})!",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                GlassBox(
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "⭐ $points pt",
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassBox(
                cornerRadius = 14.dp,
                backgroundColor = Color.White.copy(alpha = 0.18f),
                borderColor = Color(0xFFFBBF24).copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInviteClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔑 Mã Mời Tham Gia Gia Đình:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = if (inviteCode.isNotEmpty()) inviteCode else "Đang tải...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (inviteCode.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(inviteCode))
                                    Toast.makeText(context, "Đã sao chép mã mời: $inviteCode", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Sao chép mã mời",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onInviteClick) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Xem mã chi tiết",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FamilyInviteDialog(
    familyName: String,
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔑 Mã Mời Gia Đình",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = familyName,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    inviteCode.forEach { char ->
                        GlassBox(cornerRadius = 10.dp) {
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

                Text(
                    text = "Hướng dẫn cho thành viên mới:\n1. Mở app -> Chọn vai trò 'Con' hoặc 'Thành viên'\n2. Chọn 'Tham gia gia đình sẵn có'\n3. Nhập 6 chữ số trên để vào nhóm!",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteCode))
                        Toast.makeText(context, "Đã sao chép mã mời gia đình: $inviteCode", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép mã")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📋 Sao Chép Mã Mời", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đóng", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GlassTaskItemCard(
    task: Task,
    viewModel: HomeViewModel,
    onClick: () -> Unit
) {
    val reactions by viewModel.getTaskReactions(task.id).collectAsState()

    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    if (task.description.isNotEmpty()) {
                        Text(
                            text = task.description,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = task.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤 ${if (task.assignedToUserName.isEmpty()) "Tất cả" else task.assignedToUserName} • 🎁 +${task.points}pt",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍", "❤️", "🎉", "👏").forEach { emoji ->
                        val count = reactions.count { it.emoji == emoji }
                        GlassBox(
                            cornerRadius = 8.dp,
                            modifier = Modifier.clickable { viewModel.toggleReaction(task.id, emoji) }
                        ) {
                            Text(
                                text = if (count > 0) "$emoji $count" else emoji,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
