package com.family.taskmanager.ui.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.familyTasks.collectAsState()
    val members by viewModel.familyMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Tính toán dữ liệu thống kê
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.status == TaskStatus.COMPLETED }
    val inProgressTasks = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val pendingTasks = tasks.count { it.status == TaskStatus.PENDING_APPROVAL }
    val rejectedTasks = tasks.count { it.status == TaskStatus.REJECTED }
    val todoTasks = tasks.count { it.status == TaskStatus.TODO }

    val completionRate = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f

    // Sorted members by points
    val sortedMembers = members.sortedByDescending { it.points }

    // Animation cho vòng tròn tỷ lệ hoàn thành
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(completionRate) {
        animatedProgress.animateTo(
            targetValue = completionRate,
            animationSpec = tween(durationMillis = 1200)
        )
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Báo Cáo & Thống Kê Năng Suất",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Trở về",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ─── Tóm tắt 4 chỉ số ───
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatSummaryCard("📋", "Tổng", "$totalTasks", Color(0xFF6366F1), Modifier.weight(1f))
                        StatSummaryCard("✅", "Xong", "$completedTasks", Color(0xFF22C55E), Modifier.weight(1f))
                        StatSummaryCard("⏳", "Đang làm", "$inProgressTasks", Color(0xFF3B82F6), Modifier.weight(1f))
                        StatSummaryCard("⏸", "Chờ duyệt", "$pendingTasks", Color(0xFFF59E0B), Modifier.weight(1f))
                    }
                }

                // ─── Vòng tròn tiến độ tổng thể ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vẽ vòng tròn (Donut Chart) bằng Canvas
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(110.dp)
                            ) {
                                Canvas(modifier = Modifier.size(110.dp)) {
                                    drawDonutChart(
                                        progress = animatedProgress.value,
                                        completedColor = Color(0xFF22C55E),
                                        trackColor = Color.White.copy(alpha = 0.1f),
                                        strokeWidth = 18.dp.toPx()
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(animatedProgress.value * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Hoàn thành",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Chú thích
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegendRow(Color(0xFF22C55E), "Hoàn thành", completedTasks)
                                LegendRow(Color(0xFF3B82F6), "Đang làm", inProgressTasks)
                                LegendRow(Color(0xFFF59E0B), "Chờ duyệt", pendingTasks)
                                LegendRow(Color(0xFFEF4444), "Từ chối", rejectedTasks)
                                LegendRow(Color.White.copy(alpha = 0.4f), "Chưa làm", todoTasks)
                            }
                        }
                    }
                }

                // ─── Bar Chart: điểm từng thành viên ───
                if (sortedMembers.isNotEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Stars,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Điểm Tích Lũy Thành Viên",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val maxPoints = (sortedMembers.maxOfOrNull { it.points } ?: 1).coerceAtLeast(1)
                                val barColors = listOf(
                                    Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B),
                                    Color(0xFFEC4899), Color(0xFF14B8A6)
                                )

                                sortedMembers.forEachIndexed { index, user ->
                                    val barAnim = remember(user.id) { Animatable(0f) }
                                    LaunchedEffect(user.points) {
                                        barAnim.animateTo(
                                            targetValue = user.points.toFloat() / maxPoints,
                                            animationSpec = tween(900, delayMillis = index * 100)
                                        )
                                    }
                                    val barColor = barColors[index % barColors.size]

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 5.dp)
                                    ) {
                                        // Avatar
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(barColor.copy(alpha = 0.3f))
                                        ) {
                                            Text(user.avatarUrl, fontSize = 16.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = user.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.width(70.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        // Thanh bar
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(barAnim.value)
                                                    .height(14.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(barColor)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${user.points}⭐",
                                            color = Color(0xFFFBBF24),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Bảng xếp hạng Top 3 ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "🏆 Bảng Xếp Hạng",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            sortedMembers.take(5).forEachIndexed { rank, user ->
                                LeaderboardRow(
                                    rank = rank + 1,
                                    user = user,
                                    tasks = tasks,
                                    isCurrentUser = user.id == currentUser?.id
                                )
                                if (rank < minOf(sortedMembers.size - 1, 4)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // ─── Thống kê nhiệm vụ theo thành viên ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "📊 Phân Công Công Việc",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            members.forEach { user ->
                                val userTotal = tasks.count { it.assignedToUserId == user.id }
                                val userDone = tasks.count {
                                    it.assignedToUserId == user.id && it.status == TaskStatus.COMPLETED
                                }
                                val userRate = if (userTotal > 0) userDone.toFloat() / userTotal else 0f

                                val memberAnim = remember(user.id + "progress") { Animatable(0f) }
                                LaunchedEffect(userRate) {
                                    memberAnim.animateTo(userRate, animationSpec = tween(800))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Text(user.avatarUrl, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(user.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                "$userDone/$userTotal hoàn thành",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(memberAnim.value)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF22C55E))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ─── Vẽ Donut Chart bằng Canvas ───
private fun DrawScope.drawDonutChart(
    progress: Float,
    completedColor: Color,
    trackColor: Color,
    strokeWidth: Float
) {
    val arcSize = size.minDimension - strokeWidth
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
    val size = Size(arcSize, arcSize)

    // Track (nền)
    drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    // Progress
    if (progress > 0f) {
        drawArc(
            color = completedColor,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun StatSummaryCard(
    emoji: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, borderColor = color.copy(alpha = 0.5f)) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.width(70.dp))
        Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LeaderboardRow(rank: Int, user: User, tasks: List<Task>, isCurrentUser: Boolean) {
    val rankEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "  $rank."
    }
    val userDone = tasks.count { it.assignedToUserId == user.id && it.status == TaskStatus.COMPLETED }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrentUser) Color(0xFF6366F1).copy(alpha = 0.3f)
                else Color.White.copy(alpha = 0.05f)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(rankEmoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(user.avatarUrl, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF6366F1))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Bạn", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Text(
                    "$userDone nhiệm vụ hoàn thành",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "⭐ ${user.points}",
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    "điểm",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
