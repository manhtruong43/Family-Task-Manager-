package com.family.taskmanager.ui.calendar

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.ui.theme.GlassCard

private val SheetBackground = Color(0xFF1E1B4B)
private val RecAccent = Color(0xFF22D3EE)
private val RecIndigo = Color(0xFF6366F1)
private val RecRed = Color(0xFFEF4444)
private val RecOrange = Color(0xFFF97316)
private val RecGreen = Color(0xFF22C55E)

// ─────────────────────────── Thẻ tóm tắt trên màn Lịch ───────────────────────────

/** Thẻ "🔁 Lịch trình cố định" trên màn Lịch, bấm để mở bảng quản lý. */
@Composable
fun RecurringSummaryCard(
    activeCount: Int,
    totalCount: Int,
    isAdmin: Boolean,
    onOpen: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = RecAccent.copy(alpha = 0.5f),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(RecAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = RecAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.rec_section_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = when {
                        totalCount == 0 && isAdmin -> stringResource(R.string.rec_section_empty_admin)
                        totalCount == 0 -> stringResource(R.string.rec_section_empty_member)
                        else -> stringResource(R.string.rec_section_summary, activeCount, totalCount)
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(if (isAdmin) R.string.rec_manage else R.string.rec_view),
                color = RecAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = RecAccent)
        }
    }
}

// ─────────────────────────── Việc dự kiến (ngày tương lai) ───────────────────────────

/** Thẻ mờ cho lần lặp sắp tới: chưa phải nhiệm vụ thật, app sẽ tự tạo vào đúng ngày. */
@Composable
fun ProjectedTaskCard(schedule: RecurringSchedule) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White.copy(alpha = 0.05f),
        borderColor = RecAccent.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor(schedule.priority).copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    schedule.title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    stringResource(
                        R.string.rec_meta,
                        assigneeLabel(schedule),
                        schedule.points
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    stringResource(
                        R.string.rec_projected_note,
                        RecurringSchedule.formatMinuteOfDay(schedule.dueMinuteOfDay)
                    ),
                    color = RecAccent,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Filled.Repeat, contentDescription = null, tint = RecAccent.copy(alpha = 0.8f))
        }
    }
}

// ─────────────────────────── Bảng quản lý lịch trình ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScheduleSheet(
    schedules: List<RecurringSchedule>,
    isAdmin: Boolean,
    members: List<User>,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSave: (editingId: String?, input: RecurringScheduleInput, onSaved: () -> Unit) -> Unit,
    onToggleActive: (RecurringSchedule, Boolean) -> Unit,
    onDelete: (RecurringSchedule) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editorTarget by remember { mutableStateOf<RecurringSchedule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecurringSchedule?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = RecAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.rec_sheet_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.rec_sheet_hint),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isAdmin) {
                Button(
                    onClick = {
                        editorTarget = null
                        showEditor = true
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.rec_add), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (schedules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔁", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(if (isAdmin) R.string.rec_empty_admin else R.string.rec_empty_member),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            isAdmin = isAdmin,
                            enabled = !isProcessing,
                            onToggleActive = { active -> onToggleActive(schedule, active) },
                            onEdit = {
                                editorTarget = schedule
                                showEditor = true
                            },
                            onDelete = { pendingDelete = schedule }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditor && isAdmin) {
        RecurringEditorDialog(
            initial = editorTarget,
            members = members,
            isProcessing = isProcessing,
            onDismiss = { showEditor = false },
            onConfirm = { input ->
                onSave(editorTarget?.id, input) { showEditor = false }
            }
        )
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SheetBackground,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text(stringResource(R.string.rec_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.rec_delete_message, schedule.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(schedule)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.rec_delete_confirm), color = RecRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.rec_cancel), color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: RecurringSchedule,
    isAdmin: Boolean,
    enabled: Boolean,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha = if (schedule.isActive) 1f else 0.55f
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = priorityColor(schedule.priority).copy(alpha = 0.5f * alpha)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        schedule.title,
                        color = Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = RecAccent.copy(alpha = alpha),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            repeatDescription(schedule),
                            color = RecAccent.copy(alpha = alpha),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.rec_meta, assigneeLabel(schedule), schedule.points),
                        color = Color.White.copy(alpha = 0.7f * alpha),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (schedule.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            schedule.description,
                            color = Color.White.copy(alpha = 0.55f * alpha),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isAdmin) {
                    Switch(
                        checked = schedule.isActive,
                        onCheckedChange = onToggleActive,
                        enabled = enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RecAccent
                        )
                    )
                } else if (!schedule.isActive) {
                    Text(stringResource(R.string.rec_paused), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            if (isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!schedule.isActive) {
                        Text(
                            stringResource(R.string.rec_paused),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    IconButton(onClick = onEdit, enabled = enabled) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rec_edit), tint = Color.White.copy(alpha = 0.85f))
                    }
                    IconButton(onClick = onDelete, enabled = enabled) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rec_delete), tint = RecRed)
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Hộp thoại tạo / sửa ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    initial: RecurringSchedule?,
    members: List<User>,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RecurringScheduleInput) -> Unit
) {
    val context = LocalContext.current
    val dayLabels = stringArrayResource(R.array.rec_days_short)
    val assignableMembers = remember(members) { members.filter { it.role == UserRole.MEMBER } }

    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var pointsText by rememberSaveable { mutableStateOf((initial?.points ?: 10).toString()) }
    var assigneeId by rememberSaveable { mutableStateOf(initial?.assignedToUserId ?: "") }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: TaskPriority.MEDIUM) }
    var repeatType by rememberSaveable { mutableStateOf(initial?.repeatType ?: RepeatType.DAILY) }
    var daysMask by rememberSaveable { mutableIntStateOf(initial?.daysOfWeekMask ?: 0) }
    var dueMinute by rememberSaveable {
        mutableIntStateOf(initial?.dueMinuteOfDay ?: RecurringSchedule.DEFAULT_DUE_MINUTE)
    }

    val points = pointsText.toIntOrNull()
    val pointsValid = points != null && points in 1..RecurringSchedule.MAX_POINTS
    val daysValid = repeatType == RepeatType.DAILY || daysMask != 0
    val canSave = title.isNotBlank() && pointsValid && daysValid && !isProcessing

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                stringResource(if (initial == null) R.string.rec_dialog_create else R.string.rec_dialog_edit),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(RecurringSchedule.MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.rec_field_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = recTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(RecurringSchedule.MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.rec_field_description)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = recTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { input -> pointsText = input.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.rec_field_points)) },
                    singleLine = true,
                    isError = pointsText.isNotEmpty() && !pointsValid,
                    supportingText = {
                        if (pointsText.isNotEmpty() && !pointsValid) {
                            Text(stringResource(R.string.rec_err_points, RecurringSchedule.MAX_POINTS), color = RecRed)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    colors = recTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Giao cho
                SectionLabel(stringResource(R.string.rec_field_assignee))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = assigneeId.isBlank(),
                            onClick = { assigneeId = "" },
                            label = { Text(stringResource(R.string.rec_everyone)) },
                            colors = recChipColors()
                        )
                    }
                    items(assignableMembers, key = { it.id }) { member ->
                        FilterChip(
                            selected = assigneeId == member.id,
                            onClick = { assigneeId = member.id },
                            label = { Text("${member.avatarUrl} ${member.name}", maxLines = 1) },
                            colors = recChipColors()
                        )
                    }
                }

                // Mức ưu tiên
                SectionLabel(stringResource(R.string.rec_field_priority))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { value ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(stringResource(priorityLabel(value))) },
                            colors = recChipColors(priorityColor(value))
                        )
                    }
                }

                // Lặp lại
                SectionLabel(stringResource(R.string.rec_field_repeat))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeatType == RepeatType.DAILY,
                        onClick = { repeatType = RepeatType.DAILY },
                        label = { Text(stringResource(R.string.rec_repeat_type_daily)) },
                        colors = recChipColors()
                    )
                    FilterChip(
                        selected = repeatType == RepeatType.WEEKLY,
                        onClick = { repeatType = RepeatType.WEEKLY },
                        label = { Text(stringResource(R.string.rec_repeat_type_weekly)) },
                        colors = recChipColors()
                    )
                }

                if (repeatType == RepeatType.WEEKLY) {
                    SectionLabel(stringResource(R.string.rec_field_days))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEachIndexed { index, label ->
                            DayToggle(
                                label = label,
                                selected = RecurringSchedule.isDaySelected(daysMask, index),
                                onClick = { daysMask = RecurringSchedule.toggleDay(daysMask, index) }
                            )
                        }
                    }
                    if (!daysValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.rec_err_days), color = RecRed, fontSize = 12.sp)
                    }
                }

                // Giờ hạn chót trong ngày
                SectionLabel(
                    stringResource(R.string.rec_field_due, RecurringSchedule.formatMinuteOfDay(dueMinute))
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> dueMinute = hour * 60 + minute },
                            dueMinute / 60,
                            dueMinute % 60,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = RecAccent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.rec_change_time), color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        RecurringScheduleInput(
                            title = title,
                            description = description,
                            assignee = assignableMembers.firstOrNull { it.id == assigneeId },
                            priority = priority,
                            points = points ?: 0,
                            repeatType = repeatType,
                            daysOfWeekMask = daysMask,
                            dueMinuteOfDay = dueMinute
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(
                    stringResource(R.string.rec_save),
                    color = if (canSave) RecAccent else Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rec_cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun DayToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) RecAccent else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) RecAccent else Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF0F172A) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ─────────────────────────── Tiện ích ───────────────────────────

/** "Hằng ngày · 20:00" hoặc "T2, T4, T6 · 07:30". */
@Composable
fun repeatDescription(schedule: RecurringSchedule): String {
    val time = RecurringSchedule.formatMinuteOfDay(schedule.dueMinuteOfDay)
    if (schedule.repeatsEveryDay()) return stringResource(R.string.rec_repeat_daily, time)
    val dayLabels = stringArrayResource(R.array.rec_days_short)
    val days = dayLabels.indices
        .filter { RecurringSchedule.isDaySelected(schedule.daysOfWeekMask, it) }
        .joinToString(", ") { dayLabels[it] }
    return stringResource(R.string.rec_repeat_weekly, days, time)
}

@Composable
private fun assigneeLabel(schedule: RecurringSchedule): String =
    if (schedule.assignedToUserId.isBlank()) stringResource(R.string.rec_everyone)
    else schedule.assignedToUserName

private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> RecRed
    TaskPriority.MEDIUM -> RecOrange
    TaskPriority.LOW -> RecGreen
}

private fun priorityLabel(priority: TaskPriority): Int = when (priority) {
    TaskPriority.HIGH -> R.string.rec_priority_high
    TaskPriority.MEDIUM -> R.string.rec_priority_medium
    TaskPriority.LOW -> R.string.rec_priority_low
}

@Composable
private fun recTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = RecAccent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = RecAccent,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    cursorColor = RecAccent,
    errorTextColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun recChipColors(selectedColor: Color = RecIndigo) = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.08f),
    labelColor = Color.White.copy(alpha = 0.85f),
    selectedContainerColor = selectedColor,
    selectedLabelColor = Color.White
)
