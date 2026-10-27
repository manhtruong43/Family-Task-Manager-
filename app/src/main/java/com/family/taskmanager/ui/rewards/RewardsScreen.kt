package com.family.taskmanager.ui.rewards

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.RewardRepository
import com.family.taskmanager.ui.notifications.NotificationBell
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground
import java.text.SimpleDateFormat
import java.util.Date

private val AccentBlue = Color(0xFF38BDF8)
private val AccentGold = Color(0xFFFBBF24)
private val AccentGreen = Color(0xFF22C55E)
private val AccentRed = Color(0xFFF87171)
private val AccentOrange = Color(0xFFF59E0B)
private val DialogBackground = Color(0xFF1E1B4B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val rewards by viewModel.rewards.collectAsState()
    val redemptions by viewModel.redemptions.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val isAdmin = currentUser?.role == UserRole.ADMIN
    val userPoints = currentUser?.points ?: 0
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Trạng thái các hộp thoại
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingReward by remember { mutableStateOf<Reward?>(null) }
    var deletingReward by remember { mutableStateOf<Reward?>(null) }
    var redeemingReward by remember { mutableStateOf<Reward?>(null) }
    var rejectingRedemption by remember { mutableStateOf<RewardRedemption?>(null) }

    // Hiển thị Toast từ ViewModel
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            val text = context.getString(msg.resId, *msg.args.toTypedArray())
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.rw_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.rw_back),
                                tint = Color.White
                            )
                        }
                    },
                    actions = { NotificationBell(userId = currentUser?.id) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ─── Thẻ tổng quan điểm / yêu cầu chờ duyệt ───
                SummaryCard(
                    isAdmin = isAdmin,
                    userPoints = userPoints,
                    pendingCount = pendingCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // ─── Tabs ───
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentBlue
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                stringResource(R.string.rw_tab_rewards),
                                color = if (selectedTab == 0) AccentBlue else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            BadgedBox(
                                badge = {
                                    if (isAdmin && pendingCount > 0) {
                                        Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                            Text(pendingCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    stringResource(
                                        if (isAdmin) R.string.rw_tab_history_admin else R.string.rw_tab_history_member
                                    ),
                                    color = if (selectedTab == 1) AccentBlue else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    RewardListTab(
                        rewards = rewards,
                        isAdmin = isAdmin,
                        userPoints = userPoints,
                        isProcessing = isProcessing,
                        onAddClick = { showCreateDialog = true },
                        onEdit = { editingReward = it },
                        onDelete = { deletingReward = it },
                        onRedeem = { redeemingReward = it }
                    )
                } else {
                    RedemptionHistoryTab(
                        redemptions = redemptions,
                        isAdmin = isAdmin,
                        isProcessing = isProcessing,
                        onApprove = { viewModel.approveRedemption(it) },
                        onReject = { rejectingRedemption = it }
                    )
                }
            }
        }
    }

    // ─────────────────────────── Hộp thoại ───────────────────────────

    if (showCreateDialog) {
        RewardEditorDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, description, points ->
                viewModel.createReward(title, description, points)
                showCreateDialog = false
            }
        )
    }

    editingReward?.let { reward ->
        RewardEditorDialog(
            initial = reward,
            onDismiss = { editingReward = null },
            onConfirm = { title, description, points ->
                viewModel.updateReward(reward, title, description, points)
                editingReward = null
            }
        )
    }

    deletingReward?.let { reward ->
        ConfirmDialog(
            title = stringResource(R.string.rw_delete_title),
            message = stringResource(R.string.rw_delete_message, reward.title),
            confirmText = stringResource(R.string.rw_delete_confirm),
            confirmColor = AccentRed,
            onDismiss = { deletingReward = null },
            onConfirm = {
                viewModel.deleteReward(reward)
                deletingReward = null
            }
        )
    }

    redeemingReward?.let { reward ->
        ConfirmDialog(
            title = stringResource(R.string.rw_redeem_title),
            message = stringResource(
                R.string.rw_redeem_message,
                reward.title,
                reward.pointsRequired,
                userPoints - reward.pointsRequired
            ),
            confirmText = stringResource(R.string.rw_redeem_confirm),
            confirmColor = AccentGreen,
            onDismiss = { redeemingReward = null },
            onConfirm = {
                viewModel.redeemReward(reward)
                redeemingReward = null
            }
        )
    }

    rejectingRedemption?.let { redemption ->
        ConfirmDialog(
            title = stringResource(R.string.rw_reject_title),
            message = stringResource(
                R.string.rw_reject_message,
                redemption.userName,
                redemption.rewardTitle,
                redemption.pointsUsed
            ),
            confirmText = stringResource(R.string.rw_reject_confirm),
            confirmColor = AccentRed,
            onDismiss = { rejectingRedemption = null },
            onConfirm = {
                viewModel.rejectRedemption(redemption)
                rejectingRedemption = null
            }
        )
    }
}

// ─────────────────────────── Thẻ tổng quan ───────────────────────────

@Composable
private fun SummaryCard(
    isAdmin: Boolean,
    userPoints: Int,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), borderColor = AccentGold.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isAdmin) "🎁" else "⭐", fontSize = 34.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isAdmin) {
                    Text(
                        stringResource(R.string.rw_admin_summary_title),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.rw_admin_pending_count, pendingCount),
                        color = if (pendingCount > 0) AccentOrange else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        stringResource(R.string.rw_your_points),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.rw_points_value, userPoints),
                        color = AccentGold,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Tab danh sách quà ───────────────────────────

@Composable
private fun RewardListTab(
    rewards: List<Reward>,
    isAdmin: Boolean,
    userPoints: Int,
    isProcessing: Boolean,
    onAddClick: () -> Unit,
    onEdit: (Reward) -> Unit,
    onDelete: (Reward) -> Unit,
    onRedeem: (Reward) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isAdmin) {
            item {
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rw_add_reward), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (rewards.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🎁",
                    text = stringResource(if (isAdmin) R.string.rw_empty_rewards_admin else R.string.rw_empty_rewards_member)
                )
            }
        } else {
            items(rewards, key = { it.id }) { reward ->
                RewardItemCard(
                    reward = reward,
                    isAdmin = isAdmin,
                    userPoints = userPoints,
                    isProcessing = isProcessing,
                    onEdit = { onEdit(reward) },
                    onDelete = { onDelete(reward) },
                    onRedeem = { onRedeem(reward) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RewardItemCard(
    reward: Reward,
    isAdmin: Boolean,
    userPoints: Int,
    isProcessing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRedeem: () -> Unit
) {
    val canAfford = userPoints >= reward.pointsRequired
    val progress = (userPoints.toFloat() / reward.pointsRequired.coerceAtLeast(1)).coerceIn(0f, 1f)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (!isAdmin && canAfford) AccentGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reward.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (reward.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            reward.description,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.rw_price, reward.pointsRequired),
                        color = AccentGold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                if (isAdmin) {
                    IconButton(onClick = onEdit, enabled = !isProcessing) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rw_edit), tint = AccentBlue)
                    }
                    IconButton(onClick = onDelete, enabled = !isProcessing) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rw_delete), tint = AccentRed)
                    }
                } else {
                    Button(
                        onClick = onRedeem,
                        enabled = canAfford && !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(stringResource(R.string.rw_redeem), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Thanh tiến độ tích điểm để đổi quà (chỉ thành viên)
            if (!isAdmin) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (canAfford) AccentGreen else AccentGold,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (canAfford) {
                        stringResource(R.string.rw_can_redeem)
                    } else {
                        stringResource(R.string.rw_need_more, reward.pointsRequired - userPoints)
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────── Tab lịch sử đổi quà ───────────────────────────

@Composable
private fun RedemptionHistoryTab(
    redemptions: List<RewardRedemption>,
    isAdmin: Boolean,
    isProcessing: Boolean,
    onApprove: (RewardRedemption) -> Unit,
    onReject: (RewardRedemption) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (redemptions.isEmpty()) {
            item {
                EmptyState(emoji = "📜", text = stringResource(R.string.rw_empty_history))
            }
        } else {
            items(redemptions, key = { it.id }) { redemption ->
                RedemptionCard(
                    redemption = redemption,
                    isAdmin = isAdmin,
                    isProcessing = isProcessing,
                    onApprove = { onApprove(redemption) },
                    onReject = { onReject(redemption) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RedemptionCard(
    redemption: RewardRedemption,
    isAdmin: Boolean,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeText = remember(redemption.timestamp, locale) {
        SimpleDateFormat("HH:mm dd/MM/yyyy", locale).format(Date(redemption.timestamp))
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        redemption.rewardTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.rw_redeemed_by, redemption.userName, redemption.pointsUsed),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    Text(timeText, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                StatusChip(redemption.status)
            }

            if (isAdmin && redemption.status == RedemptionStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onApprove,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.rw_approve), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.rw_reject))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: RedemptionStatus) {
    val (textRes, color) = when (status) {
        RedemptionStatus.PENDING -> R.string.rw_status_pending to AccentOrange
        RedemptionStatus.APPROVED -> R.string.rw_status_approved to AccentGreen
        RedemptionStatus.REJECTED -> R.string.rw_status_rejected to AccentRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(stringResource(textRes), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(emoji: String, text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}

// ─────────────────────────── Hộp thoại dùng chung ───────────────────────────

@Composable
private fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    cursorColor = AccentBlue,
    errorTextColor = Color.White
)

@Composable
private fun RewardEditorDialog(
    initial: Reward?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, points: Int) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var pointsText by remember { mutableStateOf(initial?.pointsRequired?.toString() ?: "50") }

    val points = pointsText.toIntOrNull()
    val pointsValid = points != null && points in 1..RewardRepository.MAX_REWARD_POINTS
    val canSave = title.isNotBlank() && pointsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                stringResource(if (initial == null) R.string.rw_dialog_create_title else R.string.rw_dialog_edit_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text(stringResource(R.string.rw_field_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(150) },
                    label = { Text(stringResource(R.string.rw_field_description)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { input -> pointsText = input.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.rw_field_points)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    isError = pointsText.isNotEmpty() && !pointsValid,
                    supportingText = {
                        if (pointsText.isNotEmpty() && !pointsValid) {
                            Text(
                                stringResource(R.string.rw_field_points_error, RewardRepository.MAX_REWARD_POINTS),
                                color = AccentRed
                            )
                        }
                    },
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, description, points ?: 0) },
                enabled = canSave
            ) {
                Text(stringResource(R.string.rw_save), color = if (canSave) AccentBlue else Color.White.copy(alpha = 0.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rw_cancel), color = Color.White.copy(alpha = 0.8f))
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
                Text(stringResource(R.string.rw_cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
