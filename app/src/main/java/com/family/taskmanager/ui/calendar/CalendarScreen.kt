package com.family.taskmanager.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.task.assigneeDisplayName
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.familyTasks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // State: tuần hiện đang xem (offset từ tuần hiện tại)
    var weekOffset by remember { mutableStateOf(0) }
    // Ngày đang được chọn
    val today = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableStateOf(today.time) }

    // Tính toán ngày đầu tuần (Thứ 2)
    val weekStart = remember(weekOffset) {
        val cal = Calendar.getInstance()
        // Về thứ Hai đầu tuần
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else -(dayOfWeek - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, daysToMonday + weekOffset * 7)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal
    }

    // Tạo danh sách 7 ngày của tuần
    val weekDays = remember(weekStart) {
        (0..6).map { dayIndex ->
            val cal = weekStart.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, dayIndex)
            cal.time
        }
    }

    // Format tháng/năm cho header
    val monthYearText = remember(weekStart) {
        SimpleDateFormat("MMMM yyyy", Locale("vi", "VN")).format(weekStart.time)
            .replaceFirstChar { it.uppercase() }
    }

    // Filter nhiệm vụ theo ngày được chọn
    val tasksOnSelectedDate = tasks.filter { task ->
        if (task.deadlineTimestamp == 0L) return@filter false
        val taskCal = Calendar.getInstance().apply { timeInMillis = task.deadlineTimestamp }
        val selCal = Calendar.getInstance().apply { time = selectedDate }
        taskCal.get(Calendar.YEAR) == selCal.get(Calendar.YEAR) &&
                taskCal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
    }

    // Đếm nhiệm vụ theo ngày để hiển thị dot indicator
    fun taskCountForDate(date: Date): Int {
        val cal = Calendar.getInstance().apply { time = date }
        return tasks.count { task ->
            if (task.deadlineTimestamp == 0L) return@count false
            val taskCal = Calendar.getInstance().apply { timeInMillis = task.deadlineTimestamp }
            taskCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    taskCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        }
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Lịch Kế Hoạch Gia Đình", color = Color.White, fontWeight = FontWeight.Bold) },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Header tháng/năm + điều hướng tuần ───
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Hàng điều hướng tuần
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { weekOffset-- }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Tuần trước",
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
                                    if (weekOffset == 0) {
                                        Text(
                                            text = "Tuần hiện tại",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = if (weekOffset < 0) "${-weekOffset} tuần trước" else "$weekOffset tuần tới",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { weekOffset++ }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Tuần sau",
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // ─── Grid 7 ngày trong tuần ───
                            val dayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                weekDays.forEachIndexed { index, date ->
                                    val cal = Calendar.getInstance().apply { time = date }
                                    val isToday = run {
                                        val todayCal = Calendar.getInstance()
                                        cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                                cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                                    }
                                    val selCal = Calendar.getInstance().apply { time = selectedDate }
                                    val isSelected = cal.get(Calendar.YEAR) == selCal.get(Calendar.YEAR) &&
                                            cal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
                                    val taskCount = taskCountForDate(date)

                                    val bgColor by animateColorAsState(
                                        targetValue = when {
                                            isSelected -> Color(0xFF6366F1)
                                            isToday -> Color.White.copy(alpha = 0.2f)
                                            else -> Color.Transparent
                                        },
                                        animationSpec = tween(200),
                                        label = "dayBg"
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bgColor)
                                            .clickable { selectedDate = date }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = dayLabels[index],
                                            color = Color.White.copy(alpha = if (isSelected) 1f else 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = cal.get(Calendar.DAY_OF_MONTH).toString(),
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Dot indicator cho số nhiệm vụ
                                        if (taskCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color.White
                                                        else Color(0xFFFBBF24)
                                                    )
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Header ngày được chọn ───
                item {
                    val selectedDateText = SimpleDateFormat(
                        "EEEE, dd/MM/yyyy", Locale("vi", "VN")
                    ).format(selectedDate).replaceFirstChar { it.uppercase() }

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
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Badge số lượng task
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF6366F1))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${tasksOnSelectedDate.size} nhiệm vụ",
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📅", fontSize = 40.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Không có nhiệm vụ nào\ntrong ngày này",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(tasksOnSelectedDate, key = { it.id }) { task ->
                        CalendarTaskCard(task = task)
                    }
                }

                // ─── Padding cuối ───
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun CalendarTaskCard(task: Task) {
    val priorityColor = when (task.priority) {
        TaskPriority.HIGH -> Color(0xFFEF4444)
        TaskPriority.MEDIUM -> Color(0xFFF97316)
        TaskPriority.LOW -> Color(0xFF22C55E)
    }
    val statusIcon = when (task.status) {
        TaskStatus.COMPLETED -> Icons.Filled.CheckCircle
        TaskStatus.IN_PROGRESS -> Icons.Filled.HourglassEmpty
        else -> Icons.Filled.RadioButtonUnchecked
    }
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> Color(0xFF22C55E)
        TaskStatus.IN_PROGRESS -> Color(0xFF3B82F6)
        TaskStatus.PENDING_APPROVAL -> Color(0xFFF59E0B)
        TaskStatus.REJECTED -> Color(0xFFEF4444)
        else -> Color.White.copy(alpha = 0.5f)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = priorityColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thanh ưu tiên bên trái
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Nội dung
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
                Text(
                    text = "👤 ${assigneeDisplayName(task)}  •  ⭐ ${task.points} điểm",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (task.deadlineTimestamp > 0L) {
                    val deadlineText = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(task.deadlineTimestamp))
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "⏰ Hạn: $deadlineText",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp
                    )
                }
            }

            // Icon trạng thái
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
