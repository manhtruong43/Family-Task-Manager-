package com.family.taskmanager.ui.home

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.family.taskmanager.R
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.TaskPermissions
import com.family.taskmanager.ui.notifications.NotificationBell
import com.family.taskmanager.ui.task.StatusBadge
import com.family.taskmanager.ui.task.assigneeDisplayName
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

private val AccentBlue = Color(0xFF38BDF8)
private val AccentGold = Color(0xFFFBBF24)
private val AccentGreen = Color(0xFF4ADE80)
private val PrimaryBlue = Color(0xFF0284C7)

@OptIn(ExperimentalFoundationApi::class)
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

    val isAdmin = currentUser?.role == UserRole.ADMIN

    // Tab 3: Bố/Mẹ thấy "Cần duyệt" (mọi việc chờ duyệt); Con thấy "Chờ duyệt" (việc của mình đang chờ)
    val tabTitles = listOf(
        stringResource(R.string.home_tab_all),
        stringResource(R.string.home_tab_mine),
        stringResource(if (isAdmin) R.string.home_tab_to_review else R.string.home_tab_waiting),
        stringResource(R.string.home_tab_done)
    )

    val filteredTasks = tasks.filter { task ->
        val matchesTab = when (selectedTab) {
            // "Của tôi": việc giao đích danh cho tôi + việc chung của cả nhà (nếu tôi là Con)
            1 -> TaskPermissions.isMine(currentUser, task)
            2 -> task.status == TaskStatus.PENDING_APPROVAL &&
                    (isAdmin || TaskPermissions.isMine(currentUser, task))
            3 -> task.status == TaskStatus.COMPLETED
            else -> true
        }
        val matchesMember = selectedMemberFilter?.let { task.assignedToUserId == it.id } ?: true
        matchesTab && matchesMember
    }

    val defaultFamilyName = stringResource(R.string.home_family_default)

    if (showInviteDialog && familyGroup != null) {
        FamilyInviteDialog(
            familyName = familyGroup?.name ?: defaultFamilyName,
            inviteCode = familyGroup?.inviteCode ?: "------",
            onDismiss = { showInviteDialog = false }
        )
    }

    selectedMemberDetail?.let { member ->
        MemberDetailDialog(
            member = member,
            currentUser = currentUser,
            tasks = tasks.filter { it.assignedToUserId == member.id },
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
                if (isAdmin) {
                    FloatingActionButton(
                        onClick = onCreateTaskClick,
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_cd_create_task))
                    }
                }
            },
            bottomBar = {
                HomeBottomBar(
                    onRewardsClick = onRewardsClick,
                    onCalendarClick = onCalendarClick,
                    onStatisticsClick = onStatisticsClick,
                    onSettingsClick = onSettingsClick
                )
            }
        ) { paddingValues ->
            // Toàn bộ màn hình là MỘT LazyColumn: header, thành viên, quy tắc, tab và danh sách
            // nhiệm vụ cuộn chung với nhau.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item(key = "header") {
                    GlassHeaderSection(
                        userId = currentUser?.id,
                        userName = currentUser?.name ?: "",
                        userAvatar = currentUser?.avatarUrl ?: "🧔",
                        role = currentUser?.role ?: UserRole.MEMBER,
                        familyName = familyGroup?.name ?: defaultFamilyName,
                        inviteCode = familyGroup?.inviteCode ?: "",
                        points = currentUser?.points ?: 0,
                        onInviteClick = { showInviteDialog = true }
                    )
                }

                item(key = "members") {
                    GlassFamilyMembersSection(
                        members = familyMembers,
                        selectedFilter = selectedMemberFilter,
                        onMemberClick = { member ->
                            selectedMemberFilter = if (selectedMemberFilter?.id == member.id) null else member
                        },
                        onMemberLongClick = { member -> selectedMemberDetail = member },
                        onInviteClick = { showInviteDialog = true }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item(key = "rules") {
                    GlassFamilyRulesSection()
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item(key = "filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedMemberFilter?.let { stringResource(R.string.home_tasks_of, it.name) }
                                ?: stringResource(R.string.home_task_list),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        selectedMemberFilter?.let { member ->
                            GlassBox(
                                cornerRadius = 8.dp,
                                backgroundColor = AccentBlue.copy(alpha = 0.3f),
                                modifier = Modifier.clickable { selectedMemberFilter = null }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.home_filter_by, member.name), fontSize = 11.sp, color = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.home_cd_clear_filter),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tabs dính trên đầu khi cuộn xuống danh sách
                stickyHeader(key = "tabs") {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF0F172A).copy(alpha = 0.92f),
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
                                        maxLines = 1,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) AccentBlue else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            )
                        }
                    }
                }

                if (filteredTasks.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedMemberFilter?.let { stringResource(R.string.home_member_no_tasks, it.name) }
                                    ?: stringResource(R.string.home_no_tasks),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    item(key = "list-top") { Spacer(modifier = Modifier.height(12.dp)) }
                    items(filteredTasks, key = { it.id }) { task ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
private fun HomeBottomBar(
    onRewardsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val unselected = NavigationBarItemDefaults.colors(
        unselectedIconColor = Color.White.copy(alpha = 0.6f),
        unselectedTextColor = Color.White.copy(alpha = 0.6f)
    )
    NavigationBar(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home_nav_tasks)) },
            label = { Text(stringResource(R.string.home_nav_tasks), maxLines = 1) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                selectedTextColor = AccentBlue,
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onRewardsClick,
            icon = { Icon(Icons.Default.CardGiftcard, contentDescription = stringResource(R.string.home_nav_rewards)) },
            label = { Text(stringResource(R.string.home_nav_rewards), maxLines = 1) },
            colors = unselected
        )
        NavigationBarItem(
            selected = false,
            onClick = onCalendarClick,
            icon = { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.home_nav_calendar)) },
            label = { Text(stringResource(R.string.home_nav_calendar), maxLines = 1) },
            colors = unselected
        )
        NavigationBarItem(
            selected = false,
            onClick = onStatisticsClick,
            icon = { Icon(Icons.Default.Leaderboard, contentDescription = stringResource(R.string.home_nav_statistics)) },
            label = { Text(stringResource(R.string.home_nav_statistics), maxLines = 1) },
            colors = unselected
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_nav_settings)) },
            label = { Text(stringResource(R.string.home_nav_settings), maxLines = 1) },
            colors = unselected
        )
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = stringResource(R.string.home_cd_rules),
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_rules_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                Text(
                    text = stringResource(if (isExpanded) R.string.home_rules_collapse else R.string.home_rules_expand),
                    fontSize = 11.sp,
                    color = AccentBlue
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    R.string.home_rule_1,
                    R.string.home_rule_2,
                    R.string.home_rule_3,
                    R.string.home_rule_4
                ).forEach { rule ->
                    Text(stringResource(rule), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
                text = stringResource(R.string.home_members_title, members.size),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.home_members_hint),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(members, key = { it.id }) { member ->
                val isSelected = selectedFilter?.id == member.id

                // Bấm = lọc việc theo thành viên; bấm giữ = xem chi tiết thành viên
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { onMemberClick(member) },
                            onLongClick = { onMemberLongClick(member) }
                        )
                ) {
                    GlassCard(
                        cornerRadius = 14.dp,
                        backgroundColor = if (isSelected) PrimaryBlue.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                        borderColor = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
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
                                        color = if (isSelected) AccentBlue else Color.Transparent,
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
                                text = stringResource(if (member.role == UserRole.ADMIN) R.string.home_role_parent else R.string.home_role_child),
                                fontSize = 10.sp,
                                color = if (member.role == UserRole.ADMIN) AccentBlue else AccentGreen
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            GlassBox(cornerRadius = 6.dp) {
                                Text(
                                    text = stringResource(R.string.home_points_short, member.points),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(
                    onClick = onInviteClick,
                    cornerRadius = 14.dp,
                    backgroundColor = Color.White.copy(alpha = 0.08f),
                    borderColor = AccentGold.copy(alpha = 0.5f),
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
                            contentDescription = stringResource(R.string.home_cd_add_member),
                            tint = AccentGold,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.home_invite_more),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
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
                    text = stringResource(
                        if (member.role == UserRole.ADMIN) R.string.home_role_parent_long else R.string.home_role_child_long
                    ),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlassBox(cornerRadius = 10.dp) {
                        Text(
                            text = stringResource(R.string.home_member_points, member.points),
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    GlassBox(cornerRadius = 10.dp) {
                        Text(
                            text = stringResource(R.string.home_member_task_count, tasks.size),
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
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.home_assign_new_task), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.home_close), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GlassHeaderSection(
    userId: String?,
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
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            text = stringResource(
                                R.string.home_greeting,
                                userName,
                                stringResource(if (role == UserRole.ADMIN) R.string.home_role_parent else R.string.home_role_child)
                            ),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    NotificationBell(userId = userId)
                    Spacer(modifier = Modifier.width(6.dp))
                    GlassBox {
                        Text(
                            text = stringResource(R.string.home_points_short, points),
                            color = AccentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassBox(
                cornerRadius = 14.dp,
                backgroundColor = Color.White.copy(alpha = 0.18f),
                borderColor = AccentGold.copy(alpha = 0.6f),
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
                            text = stringResource(R.string.home_invite_code_label),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = inviteCode.ifEmpty { stringResource(R.string.home_loading) },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (inviteCode.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(inviteCode))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.home_code_copied, inviteCode),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.home_cd_copy_code),
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onInviteClick) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = stringResource(R.string.home_cd_view_code),
                                tint = AccentBlue
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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_invite_dialog_title),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inviteCode.forEach { char ->
                        GlassBox(cornerRadius = 10.dp) {
                            Text(
                                text = char.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.home_invite_instructions),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteCode))
                        Toast.makeText(
                            context,
                            context.getString(R.string.home_code_copied, inviteCode),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.home_cd_copy_code))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.home_copy_code), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.home_close), color = Color.White)
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
                    text = stringResource(R.string.home_task_meta, assigneeDisplayName(task), task.points),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f, fill = false)
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
