package com.family.taskmanager.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.family.taskmanager.R
import com.family.taskmanager.data.model.NotificationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nút chuông thông báo có badge số chưa đọc. Bấm vào mở danh sách thông báo dạng bottom sheet.
 * Đặt vào actions của TopAppBar:   actions = { NotificationBell(userId = currentUser?.id) }
 *
 * Đồng thời:
 *  - Tự hỏi quyền POST_NOTIFICATIONS lần đầu (Android 13+)
 *  - Khởi động bộ nhắc hạn chót (thông qua NotificationViewModel)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBell(
    userId: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    LaunchedEffect(userId) {
        if (!userId.isNullOrBlank()) viewModel.setUserId(userId)
    }

    // Tự động xin quyền thông báo 1 lần duy nhất
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequester()
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission(context) && viewModel.shouldAutoAskPermission()) {
            viewModel.onPermissionAsked()
            requestPermission()
        }
    }

    val unreadCount by viewModel.unreadCount.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }, modifier = modifier) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.noti_center_title),
                tint = tint
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Color(0xFF1E1B4B),
            contentColor = Color.White
        ) {
            NotificationSheetContent(
                notifications = notifications,
                unreadCount = unreadCount,
                onItemClick = { item -> if (!item.isRead) viewModel.markAsRead(item.id) },
                onDelete = { item -> viewModel.delete(item.id) },
                onMarkAllRead = { viewModel.markAllAsRead() },
                onDeleteAll = { viewModel.deleteAll() }
            )
        }
    }
}

@Composable
private fun NotificationSheetContent(
    notifications: List<NotificationItem>,
    unreadCount: Int,
    onItemClick: (NotificationItem) -> Unit,
    onDelete: (NotificationItem) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.noti_center_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.noti_unread_count, unreadCount),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Text(stringResource(R.string.noti_mark_all_read), color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
            if (notifications.isNotEmpty()) {
                TextButton(onClick = onDeleteAll) {
                    Text(stringResource(R.string.noti_delete_all), color = Color(0xFFF87171), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔕", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.noti_empty),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val background = if (item.isRead) Color.White.copy(alpha = 0.06f) else Color(0xFF6366F1).copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (item.isRead) Color.Transparent else Color(0xFF38BDF8))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.message,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatRelativeTime(item.timestamp),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.noti_delete),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    return when {
        minutes < 1 -> stringResource(R.string.noti_time_just_now)
        minutes < 60 -> stringResource(R.string.noti_time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.noti_time_hours_ago, hours.toInt())
        else -> SimpleDateFormat("HH:mm dd/MM/yyyy", locale).format(Date(timestamp))
    }
}
