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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ColorDone = Color(0xFF22C55E)
private val ColorDoing = Color(0xFF3B82F6)
private val ColorPending = Color(0xFFF59E0B)
private val ColorRejected = Color(0xFFEF4444)
private val ColorTodo = Color.White.copy(alpha = 0.4f)
private val ColorGold = Color(0xFFFBBF24)
private val ColorIndigo = Color(0xFF6366F1)

/** Khoảng thời gian thống kê (lọc theo ngày tạo nhiệm vụ). */
private enum class StatPeriod(val days: Int?, val labelRes: Int) {
    WEEK(7, R.string.stat_period_week),
    MONTH(30, R.string.stat_period_month),
    ALL(null, R.string.stat_period_all)
}

/** Số liệu thống kê của 1 thành viên trong kỳ. */
private data class MemberStat(
    val user: User,
    val total: Int,
    val done: Int,
    val rejected: Int,
    val pointsEarned: Int
) {
    val rate: Float get() = if (total > 0) done.toFloat() / total else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val allTasks by viewModel.familyTasks.collectAsState()
    val members by viewModel.familyMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]

    var period by rememberSaveable { mutableStateOf(StatPeriod.WEEK) }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }

    // ─── Lọc nhiệm vụ theo khoảng thời gian ───
    val tasks = remember(allTasks, period) {
        val days = period.days
        if (days == null) allTasks
        else {
            val from = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            allTasks.filter { it.createdAt >= from }
        }
    }

    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.status == TaskStatus.COMPLETED }
    val inProgressTasks = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val pendingTasks = tasks.count { it.status == TaskStatus.PENDING_APPROVAL }
    val rejectedTasks = tasks.count { it.status == TaskStatus.REJECTED }
    val todoTasks = tasks.count { it.status == TaskStatus.TODO }
    val completionRate = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f

    // ─── Thống kê theo từng thành viên trong kỳ ───
    val memberStats = remember(tasks, members) {
        members.map { user ->
            val mine = tasks.filter { it.assignedToUserId == user.id }
            MemberStat(
                user = user,
                total = mine.size,
                done = mine.count { it.status == TaskStatus.COMPLETED },
                rejected = mine.count { it.status == TaskStatus.REJECTED },
                pointsEarned = mine.filter { it.status == TaskStatus.COMPLETED }.sumOf { it.points }
            )
        }
    }
    val earnedRanking = memberStats.sortedByDescending { it.pointsEarned }
    val balanceRanking = members.sortedByDescending { it.points }

    // ─── Lịch sử nhiệm vụ đã hoàn thành ───
    val history = remember(tasks) {
        tasks.filter { it.status == TaskStatus.COMPLETED }.sortedByDescending { it.createdAt }
    }
    val visibleHistory = if (showAllHistory) history else history.take(5)

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
                        Text(stringResource(R.string.stats_title), color = Color.White, fontWeight = FontWeight.Bold)
                    },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ─── Bộ lọc thời gian ───
                item {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPeriod.values().forEach { option ->
                                FilterChip(
                                    selected = period == option,
                                    onClick = { period = option },
                                    label = { Text(stringResource(option.labelRes)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White.copy(alpha = 0.8f),
                                        selectedContainerColor = ColorIndigo,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.stat_period_note),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                // ─── Tóm tắt 4 chỉ số ───
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatSummaryCard("📋", stringResource(R.string.stat_total_short), "$totalTasks", ColorIndigo, Modifier.weight(1f))
                        StatSummaryCard("✅", stringResource(R.string.stat_done_short), "$completedTasks", ColorDone, Modifier.weight(1f))
                        StatSummaryCard("⏳", stringResource(R.string.stat_doing_short), "$inProgressTasks", ColorDoing, Modifier.weight(1f))
                        StatSummaryCard("⏸", stringResource(R.string.stat_pending_short), "$pendingTasks", ColorPending, Modifier.weight(1f))
                    }
                }

                // ─── Donut Chart: tỷ lệ hoàn thành ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                                Canvas(modifier = Modifier.size(110.dp)) {
                                    drawDonutChart(
                                        progress = animatedProgress.value,
                                        completedColor = ColorDone,
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
                                        text = stringResource(R.string.stat_completed_label),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegendRow(ColorDone, stringResource(R.string.task_status_completed), completedTasks)
                                LegendRow(ColorDoing, stringResource(R.string.task_status_in_progress), inProgressTasks)
                                LegendRow(ColorPending, stringResource(R.string.task_status_pending), pendingTasks)
                                LegendRow(ColorRejected, stringResource(R.string.task_status_rejected), rejectedTasks)
                                LegendRow(ColorTodo, stringResource(R.string.task_status_todo), todoTasks)
                            }
                        }
                    }
                }

                // ─── Bar Chart: điểm kiếm được trong kỳ ───
                if (earnedRanking.isNotEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle(Icons.Filled.Stars, stringResource(R.string.stat_points_earned_title))
                                Spacer(modifier = Modifier.height(16.dp))

                                val maxPoints = (earnedRanking.maxOfOrNull { it.pointsEarned } ?: 1).coerceAtLeast(1)
                                val barColors = listOf(ColorIndigo, ColorDone, ColorPending, Color(0xFFEC4899), Color(0xFF14B8A6))

                                earnedRanking.forEachIndexed { index, stat ->
                                    PointsBarRow(
                                        stat = stat,
                                        index = index,
                                        maxPoints = maxPoints,
                                        barColor = barColors[index % barColors.size]
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Bảng xếp hạng điểm tích lũy hiện tại ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle(Icons.Filled.EmojiEvents, stringResource(R.string.stats_leaderboard))
                            Text(
                                stringResource(R.string.stat_leaderboard_note),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (balanceRanking.isEmpty()) {
                                Text(stringResource(R.string.stat_no_members), color = Color.White.copy(alpha = 0.7f))
                            }
                            balanceRanking.take(5).forEachIndexed { rank, user ->
                                val doneCount = memberStats.firstOrNull { it.user.id == user.id }?.done ?: 0
                                LeaderboardRow(
                                    rank = rank + 1,
                                    user = user,
                                    doneInPeriod = doneCount,
                                    isCurrentUser = user.id == currentUser?.id
                                )
                                if (rank < minOf(balanceRanking.size - 1, 4)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // ─── Năng suất từng thành viên ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.stat_member_productivity),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            memberStats.forEach { stat -> MemberProductivityRow(stat) }
                        }
                    }
                }

                // ─── Lịch sử công việc đã hoàn thành ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle(Icons.Filled.History, stringResource(R.string.stat_history_title))
                            Spacer(modifier = Modifier.height(10.dp))
                            if (history.isEmpty()) {
                                Text(
                                    stringResource(R.string.stat_history_empty),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                visibleHistory.forEach { task ->
                                    HistoryRow(task = task, locale = locale)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (history.size > 5) {
                                    TextButton(
                                        onClick = { showAllHistory = !showAllHistory },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            if (showAllHistory) stringResource(R.string.stat_show_less)
                                            else stringResource(R.string.stat_show_all, history.size),
                                            color = Color(0xFF38BDF8)
                                        )
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

// ─────────────────────────── Biểu đồ Canvas ───────────────────────────

/** Vẽ Donut Chart: 1 vòng nền + 1 cung thể hiện % hoàn thành, bắt đầu từ góc -90° (đỉnh). */
private fun DrawScope.drawDonutChart(
    progress: Float,
    completedColor: Color,
    trackColor: Color,
    strokeWidth: Float
) {
    val arcSize = size.minDimension - strokeWidth
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
    val arcBounds = Size(arcSize, arcSize)

    drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcBounds,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    if (progress > 0f) {
        drawArc(
            color = completedColor,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcBounds,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

// ─────────────────────────── Thành phần con ───────────────────────────

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ColorGold, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun PointsBarRow(stat: MemberStat, index: Int, maxPoints: Int, barColor: Color) {
    val barAnim = remember(stat.user.id) { Animatable(0f) }
    LaunchedEffect(stat.pointsEarned, maxPoints) {
        barAnim.animateTo(
            targetValue = stat.pointsEarned.toFloat() / maxPoints,
            animationSpec = tween(900, delayMillis = index * 100)
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(barColor.copy(alpha = 0.3f))
        ) {
            Text(stat.user.avatarUrl, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stat.user.name,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.width(70.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
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
            text = "+${stat.pointsEarned}⭐",
            color = ColorGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun MemberProductivityRow(stat: MemberStat) {
    val memberAnim = remember(stat.user.id) { Animatable(0f) }
    LaunchedEffect(stat.rate) {
        memberAnim.animateTo(stat.rate, animationSpec = tween(800))
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(stat.user.avatarUrl, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stat.user.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.stat_member_done_of, stat.done, stat.total, (stat.rate * 100).toInt()),
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
                        .background(ColorDone)
                )
            }
            if (stat.rejected > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    stringResource(R.string.stat_member_rejected, stat.rejected),
                    color = ColorRejected,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(task: Task, locale: Locale) {
    val dateText = remember(task.createdAt, locale) {
        SimpleDateFormat("dd/MM/yyyy", locale).format(Date(task.createdAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✅", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val assignee = if (task.assignedToUserId.isBlank()) {
                stringResource(R.string.stat_everyone)
            } else {
                task.assignedToUserName
            }
            Text(
                stringResource(R.string.stat_history_meta, assignee, dateText),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
        Text("+${task.points}⭐", color = ColorGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
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
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.width(90.dp), maxLines = 1)
        Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LeaderboardRow(rank: Int, user: User, doneInPeriod: Int, isCurrentUser: Boolean) {
    val rankEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "  $rank."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentUser) ColorIndigo.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(rankEmoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(user.avatarUrl, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ColorIndigo)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.common_you), color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Text(
                    stringResource(R.string.stat_done_in_period, doneInPeriod),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("⭐ ${user.points}", color = ColorGold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(stringResource(R.string.stat_points_unit), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}
