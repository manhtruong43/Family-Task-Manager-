package com.family.taskmanager.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.notifications.NotificationBell
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val AccentIndigo = Color(0xFF6366F1)
private val AccentGold = Color(0xFFFBBF24)
private val AccentRed = Color(0xFFEF4444)
private val AccentGreen = Color(0xFF22C55E)

/**
 * Lịch kế hoạch tuần của gia đình.
 *
 * @param onTaskClick bấm vào 1 nhiệm vụ -> mở chi tiết. Mặc định không làm gì
 *                    (MainActivity có thể truyền navController.navigate(TaskDetail)).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onTaskClick: (String) -> Unit = {}
) {
    val tasks by viewModel.familyTasks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]

    // Tuần đang xem (offset so với tuần hiện tại) và ngày đang chọn - giữ nguyên khi xoay màn hình
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDateMillis by rememberSaveable { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }

    val now = System.currentTimeMillis()

    // Lọc "Của tôi": nhiệm vụ giao cho mình hoặc giao cho cả nhà
    val visibleTasks = remember(tasks, onlyMine, currentUser?.id) {
        if (!onlyMine) tasks
        else tasks.filter { it.assignedToUserId == currentUser?.id || it.assignedToUserId.isBlank() }
    }

    // Ngày đầu tuần (Thứ Hai)
    val weekStartMillis = remember(weekOffset) {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else -(dayOfWeek - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, daysToMonday + weekOffset * 7)
        startOfDay(cal.timeInMillis)
    }

    val weekDays: List<Long> = remember(weekStartMillis) {
        (0..6).map { index ->
            val cal = Calendar.getInstance().apply { timeInMillis = weekStartMillis }
            cal.add(Calendar.DAY_OF_YEAR, index)
            cal.timeInMillis
        }
    }

    val monthYearText = remember(weekStartMillis, locale) {
        SimpleDateFormat("LLLL yyyy", locale).format(Date(weekStartMillis))
            .replaceFirstChar { it.uppercase(locale) }
    }

    // Gom nhiệm vụ theo ngày hạn chót
    val tasksByDay: Map<Long, List<Task>> = remember(visibleTasks) {
        visibleTasks
            .filter { it.deadlineTimestamp > 0L }
            .groupBy { startOfDay(it.deadlineTimestamp) }
    }

    val tasksOnSelectedDate = (tasksByDay[selectedDateMillis] ?: emptyList())
        .sortedBy { it.deadlineTimestamp }

    val weekTasks = weekDays.flatMap { tasksByDay[it] ?: emptyList() }
    val weekDone = weekTasks.count { it.status == TaskStatus.COMPLETED }
    val weekOverdue = weekTasks.count { isOverdue(it, now) }
    val noDeadlineCount = visibleTasks.count {
        it.deadlineTimestamp == 0L && it.status != TaskStatus.COMPLETED
    }

    val todayMillis = startOfDay(now)
    val isViewingToday = weekOffset == 0 && selectedDateMillis == todayMillis

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.cal_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cal_back),
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (!isViewingToday) {
                            IconButton(onClick = {
                                weekOffset = 0
                                selectedDateMillis = todayMillis
                            }) {
                                Icon(
                                    Icons.Filled.Today,
                                    contentDescription = stringResource(R.string.cal_go_today),
                                    tint = Color.White
                                )
                            }
                        }
                        NotificationBell(userId = currentUser?.id)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Header tháng/năm + điều hướng tuần + 7 ngày ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    weekOffset--
                                    selectedDateMillis = shiftDays(selectedDateMillis, -7)
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = stringResource(R.string.cal_prev_week),
                                        tint = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = monthYearText,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            weekOffset == 0 -> stringResource(R.string.cal_this_week)
                                            weekOffset < 0 -> stringResource(R.string.cal_weeks_ago, -weekOffset)
                                            else -> stringResource(R.string.cal_weeks_later, weekOffset)
                                        },
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = {
                                    weekOffset++
                                    selectedDateMillis = shiftDays(selectedDateMillis, 7)
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cal_next_week),
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                weekDays.forEach { dayMillis ->
                                    val dayTasks = tasksByDay[dayMillis] ?: emptyList()
                                    DayCell(
                                        dayMillis = dayMillis,
                                        locale = locale,
                                        isToday = dayMillis == todayMillis,
                                        isSelected = dayMillis == selectedDateMillis,
                                        taskCount = dayTasks.size,
                                        hasOverdue = dayTasks.any { isOverdue(it, now) },
                                        onClick = { selectedDateMillis = dayMillis }
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Bộ lọc + thống kê nhanh của tuần ───
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !onlyMine,
                            onClick = { onlyMine = false },
                            label = { Text(stringResource(R.string.cal_filter_all)) },
                            colors = calendarChipColors()
                        )
                        FilterChip(
                            selected = onlyMine,
                            onClick = { onlyMine = true },
                            label = { Text(stringResource(R.string.cal_filter_mine)) },
                            colors = calendarChipColors()
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WeekStatCard("📋", stringResource(R.string.cal_week_total), weekTasks.size, AccentIndigo, Modifier.weight(1f))
                        WeekStatCard("✅", stringResource(R.string.cal_week_done), weekDone, AccentGreen, Modifier.weight(1f))
                        WeekStatCard("⚠️", stringResource(R.string.cal_week_overdue), weekOverdue, AccentRed, Modifier.weight(1f))
                    }
                }

                // ─── Header ngày được chọn ───
                item {
                    val selectedDateText = remember(selectedDateMillis, locale) {
                        SimpleDateFormat("EEEE, dd/MM/yyyy", locale)
                            .format(Date(selectedDateMillis))
                            .replaceFirstChar { it.uppercase(locale) }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedDateText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentIndigo)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cal_task_count, tasksOnSelectedDate.size),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ─── Danh sách nhiệm vụ trong ngày ───
                if (tasksOnSelectedDate.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📅", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.cal_no_tasks_day),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(tasksOnSelectedDate, key = { it.id }) { task ->
                        CalendarTaskCard(
                            task = task,
                            locale = locale,
                            now = now,
                            onClick = { onTaskClick(task.id) }
                        )
                    }
                }

                // ─── Nhắc nhở việc chưa đặt hạn ───
                if (noDeadlineCount > 0) {
                    item {
                        Text(
                            text = stringResource(R.string.cal_no_deadline_hint, noDeadlineCount),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.cal_reminder_hint),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────────────────────── Ô ngày trong tuần ───────────────────────────

@Composable
private fun DayCell(
    dayMillis: Long,
    locale: Locale,
    isToday: Boolean,
    isSelected: Boolean,
    taskCount: Int,
    hasOverdue: Boolean,
    onClick: () -> Unit
) {
    val dayLabel = remember(dayMillis, locale) {
        SimpleDateFormat("EEE", locale).format(Date(dayMillis))
    }
    val dayNumber = remember(dayMillis) {
        Calendar.getInstance().apply { timeInMillis = dayMillis }.get(Calendar.DAY_OF_MONTH).toString()
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> AccentIndigo
            isToday -> Color.White.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "dayBg"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = dayLabel,
            color = Color.White.copy(alpha = if (isSelected) 1f else 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = dayNumber,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (taskCount > 0) {
            Box(
                modifier = Modifier
                    .size(if (taskCount > 1) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> Color.White
                            hasOverdue -> AccentRed
                            else -> AccentGold
                        }
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WeekStatCard(
    emoji: String,
    label: String,
    value: Int,
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
            Text(emoji, fontSize = 18.sp)
            Text(value.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun calendarChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.08f),
    labelColor = Color.White.copy(alpha = 0.8f),
    selectedContainerColor = AccentIndigo,
    selectedLabelColor = Color.White
)

// ─────────────────────────── Thẻ nhiệm vụ ───────────────────────────

@Composable
fun CalendarTaskCard(
    task: Task,
    locale: Locale = Locale.getDefault(),
    now: Long = System.currentTimeMillis(),
    onClick: () -> Unit = {}
) {
    val priorityColor = when (task.priority) {
        TaskPriority.HIGH -> AccentRed
        TaskPriority.MEDIUM -> Color(0xFFF97316)
        TaskPriority.LOW -> AccentGreen
    }
    val overdue = isOverdue(task, now)
    val (statusIcon, statusColor, statusTextRes) = statusVisual(task.status)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (overdue) AccentRed.copy(alpha = 0.8f) else priorityColor.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thanh màu thể hiện độ ưu tiên
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                val assignee = if (task.assignedToUserId.isBlank()) {
                    stringResource(R.string.cal_everyone)
                } else {
                    task.assignedToUserName
                }
                Text(
                    text = stringResource(R.string.cal_task_meta, assignee, task.points),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.deadlineTimestamp > 0L) {
                    val timeText = remember(task.deadlineTimestamp, locale) {
                        SimpleDateFormat("HH:mm", locale).format(Date(task.deadlineTimestamp))
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (overdue) Icons.Filled.Warning else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (overdue) AccentRed else AccentGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (overdue) {
                                stringResource(R.string.cal_overdue_at, timeText)
                            } else {
                                stringResource(R.string.cal_deadline_at, timeText)
                            },
                            color = if (overdue) AccentRed else AccentGold,
                            fontSize = 12.sp,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(statusTextRes),
                    color = statusColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────── Hàm tiện ích ───────────────────────────

private data class StatusVisual(val icon: ImageVector, val color: Color, val textRes: Int)

private fun statusVisual(status: TaskStatus): StatusVisual = when (status) {
    TaskStatus.TODO -> StatusVisual(Icons.Filled.RadioButtonUnchecked, Color.White.copy(alpha = 0.6f), R.string.cal_status_todo)
    TaskStatus.IN_PROGRESS -> StatusVisual(Icons.Filled.HourglassEmpty, Color(0xFF3B82F6), R.string.cal_status_in_progress)
    TaskStatus.PENDING_APPROVAL -> StatusVisual(Icons.Filled.HourglassEmpty, Color(0xFFF59E0B), R.string.cal_status_pending)
    TaskStatus.COMPLETED -> StatusVisual(Icons.Filled.CheckCircle, AccentGreen, R.string.cal_status_completed)
    TaskStatus.REJECTED -> StatusVisual(Icons.Filled.Replay, AccentRed, R.string.cal_status_rejected)
}

/** Quá hạn = đã qua hạn chót mà vẫn chưa được duyệt hoàn thành / chưa nộp báo cáo. */
private fun isOverdue(task: Task, now: Long): Boolean =
    task.deadlineTimestamp in 1 until now &&
            task.status != TaskStatus.COMPLETED &&
            task.status != TaskStatus.PENDING_APPROVAL

private fun startOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun shiftDays(dayMillis: Long, days: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dayMillis
        add(Calendar.DAY_OF_YEAR, days)
    }
    return startOfDay(cal.timeInMillis)
}
