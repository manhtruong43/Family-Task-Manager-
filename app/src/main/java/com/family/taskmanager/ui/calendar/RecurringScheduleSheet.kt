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
import androidx.compose.material3.SheetValue
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.window.DialogProperties
import com.family.taskmanager.R
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.CreationRequestStatus
import com.family.taskmanager.ui.theme.GlassCard

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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
fun ProjectedTaskCard(task: Task) {
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
                    .background(priorityColor(task.priority).copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
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
                        if (task.assignedToUserId.isEmpty()) stringResource(R.string.rec_everyone_shared) else task.assignedToUserName,
                        task.points
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
                        RecurringSchedule.formatMinuteOfDay(Instant.ofEpochMilli(task.deadlineTimestamp)
                            .atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute })
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
    currentUserId: String?,
    members: List<User>,
    isProcessing: Boolean,
    saveState: RecurringSaveState?,
    schedulesLoaded: Boolean,
    schedulesLoadFailed: Boolean,
    editorLookup: RecurringEditorLookupState?,
    onResolveEditor: (requestId: String, retry: Boolean) -> Unit,
    onReloadSchedules: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (requestId: String, editingId: String?, input: RecurringScheduleInput) -> String?,
    onToggleActive: (RecurringSchedule, Boolean) -> Unit,
    onDelete: (RecurringSchedule) -> Unit,
    visible: Boolean = true
) {
    var editorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val editorDrafts = rememberSaveableStateHolder()
    var editorRequestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pendingDelete by remember { mutableStateOf<RecurringSchedule?>(null) }
    var pendingSaveId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOperationId by rememberSaveable { mutableStateOf<String?>(null) }
    // Không phải một lần lưu đang chạy: giữ mã tạo chưa xác minh kể cả khi người dùng đóng bảng.
    var unresolvedCreationId by rememberSaveable { mutableStateOf<String?>(null) }
    var interruptedEditReview by rememberSaveable { mutableStateOf(false) }
    val restoredPendingRequest = remember { pendingSaveId }
    val restoredPendingOperation = remember { pendingOperationId }
    val latestProcessing = rememberUpdatedState(isProcessing)
    // Đọc State trực tiếp trong callback để chặn cả sự kiện đã xếp hàng trước lần vẽ tiếp theo.
    val dismissBlocked = { latestProcessing.value || pendingSaveId != null }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !dismissBlocked() }
    )
    val saveCompleted = saveState?.requestId == editorRequestId && saveState.result == RecurringResult.SAVED

    LaunchedEffect(pendingSaveId, pendingOperationId, saveState, editorLookup, isProcessing) {
        val pending = pendingSaveId
        if (pending != null) {
            val matching = saveState?.takeIf {
                it.requestId == pending && it.operationId == pendingOperationId
            }
            val terminal = matching != null && (matching.result != null || matching.cancelled)
            val lostOperation = pending == restoredPendingRequest &&
                pendingOperationId == restoredPendingOperation && !isProcessing && matching == null
            if (editorTargetId == null && (lostOperation || matching?.cancelled == true ||
                    matching?.result == RecurringResult.ALREADY_CREATED)) {
                // Giữ yêu cầu đến khi người dùng xử lý kết quả tra cứu, kể cả khi bảng đã đóng.
                unresolvedCreationId = pending
            } else if (lostOperation) {
                // ViewModel mới không còn coroutine cũ. Không coi là thành công, cũng không phát lại.
                interruptedEditReview = true
            }
            if (terminal || lostOperation) {
                pendingSaveId = null
                pendingOperationId = null
            }
        }
    }

    LaunchedEffect(saveState, editorRequestId) {
        if (saveCompleted) {
            showEditor = false
            interruptedEditReview = false
            if (unresolvedCreationId == editorRequestId) unresolvedCreationId = null
            editorDrafts.removeState(editorRequestId)
        }
    }

    LaunchedEffect(visible, showEditor, editorTargetId, editorRequestId, editorLookup, isProcessing, currentUserId) {
        if (visible && showEditor && editorTargetId == null && !saveCompleted && !isProcessing) {
            onResolveEditor(editorRequestId, false)
        }
    }

    // Host giữ composable này khi ẩn để bản nháp và mã yêu cầu chưa xác minh không bị vứt bỏ.
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = { if (!dismissBlocked()) onDismiss() },
        sheetState = sheetState,
        // Back vẫn được dispatcher nhận; guard và confirmValueChange chặn đóng theo State mới nhất.
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
                        editorTargetId = null
                        interruptedEditReview = false
                        editorRequestId = unresolvedCreationId ?: UUID.randomUUID().toString()
                        showEditor = true
                        if (unresolvedCreationId != null) onResolveEditor(editorRequestId, true)
                    },
                    enabled = !dismissBlocked(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.rec_add), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (schedulesLoadFailed) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.rec_err_storage), color = RecRed, textAlign = TextAlign.Center)
                    TextButton(onClick = onReloadSchedules) {
                        Text(stringResource(R.string.rec_retry), color = RecAccent)
                    }
                }
            } else if (!schedulesLoaded) {
                Text(
                    stringResource(R.string.home_loading),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else if (schedules.isEmpty()) {
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
                            canManage = isAdmin && schedule.canBeManagedBy(
                                members.firstOrNull { it.id == currentUserId }, members
                            ),
                            enabled = !dismissBlocked(),
                            onToggleActive = { active -> onToggleActive(schedule, active) },
                            onEdit = {
                                editorTargetId = schedule.id
                                interruptedEditReview = false
                                editorRequestId = UUID.randomUUID().toString()
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

    if (showEditor && isAdmin && !saveCompleted) {
        val targetId = editorTargetId
        val editorKey = editorRequestId
        val target = schedules.firstOrNull { it.id == targetId }
        val lookupState = editorLookup?.takeIf { it.requestId == editorRequestId }
        val lookup = lookupState?.lookup
        val actionResult = saveState?.takeIf { it.requestId == editorRequestId }?.result
        val permissionRejected = actionResult == RecurringResult.NOT_ALLOWED ||
            actionResult == RecurringResult.INVALID_ASSIGNEE
        val closeEditor = {
            if (!dismissBlocked()) {
                showEditor = false
                interruptedEditReview = false
                val keepUnverifiedCreation = unresolvedCreationId == editorKey && lookup == null
                if (!keepUnverifiedCreation) {
                    if (unresolvedCreationId == editorKey) unresolvedCreationId = null
                    editorDrafts.removeState(editorKey)
                }
            }
        }
        val titleRes = if (targetId == null) R.string.rec_dialog_create else R.string.rec_dialog_edit

        when {
            targetId == null && lookupState?.failed == true -> RecurringEditorMessage(
                titleRes, R.string.rec_err_storage, closeEditor, dismissBlocked = dismissBlocked(),
                onRetry = { onResolveEditor(editorRequestId, true) }
            )
            targetId == null && lookup?.status == CreationRequestStatus.NOT_FOUND -> RecurringEditorMessage(
                titleRes, R.string.rec_err_general, closeEditor, dismissBlocked = dismissBlocked()
            )
            targetId == null && lookup?.status == CreationRequestStatus.NOT_ALLOWED -> RecurringEditorMessage(
                titleRes, R.string.rec_err_not_allowed, closeEditor, dismissBlocked = dismissBlocked()
            )
            targetId != null && !schedulesLoaded && schedulesLoadFailed -> RecurringEditorMessage(
                titleRes, R.string.rec_err_storage, closeEditor, dismissBlocked = dismissBlocked(), onRetry = onReloadSchedules
            )
            (targetId == null && lookup == null) ||
                (targetId != null && !schedulesLoaded) ||
                (members.none { it.id == currentUserId } && !permissionRejected && !interruptedEditReview) -> RecurringEditorMessage(
                    titleRes, R.string.home_loading, closeEditor, loading = true, dismissBlocked = dismissBlocked()
                )
            targetId != null && target == null -> RecurringEditorMessage(
                titleRes, R.string.rec_err_general, closeEditor, dismissBlocked = dismissBlocked()
            )
            targetId != null && interruptedEditReview -> {
                val canEdit = target?.canBeManagedBy(members.firstOrNull { it.id == currentUserId }, members) == true
                AlertDialog(
                    onDismissRequest = closeEditor,
                    title = { Text(stringResource(R.string.rec_interrupted_edit_title)) },
                    text = { Text(stringResource(
                        if (canEdit) R.string.rec_interrupted_edit_message else R.string.rec_err_permission_changed
                    )) },
                    confirmButton = {
                        if (canEdit) TextButton(onClick = { interruptedEditReview = false }) {
                            Text(stringResource(R.string.rec_continue_edit))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = closeEditor) { Text(stringResource(R.string.rec_close)) }
                    }
                )
            }
            targetId == null && lookup?.status == CreationRequestStatus.COMMITTED -> {
                val committed = requireNotNull(lookup.schedule)
                val canEdit = committed.canBeManagedBy(members.firstOrNull { it.id == currentUserId }, members)
                AlertDialog(
                    onDismissRequest = closeEditor,
                    properties = DialogProperties(dismissOnBackPress = !dismissBlocked(), dismissOnClickOutside = !dismissBlocked()),
                    title = { Text(stringResource(R.string.rec_recovered_title)) },
                    text = { Text(stringResource(
                        if (canEdit) R.string.rec_recovered_draft else R.string.rec_err_assignee
                    )) },
                    confirmButton = {
                        if (canEdit) TextButton(enabled = !dismissBlocked(), onClick = {
                            if (!dismissBlocked()) {
                                editorTargetId = committed.id
                                if (unresolvedCreationId == editorKey) unresolvedCreationId = null
                            }
                            // Luôn giữ key và draft đã lưu; ViewModel mới không biết các thay đổi chưa áp dụng.
                            // Chỉ chuyển sang sửa đúng lịch trong biên nhận, không tạo yêu cầu mới.
                        }) {
                            Text(stringResource(R.string.rec_continue_edit))
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !dismissBlocked(), onClick = closeEditor) { Text(stringResource(R.string.rec_close)) }
                    }
                )
            }
            else -> {
                val editor = prepareRecurringEditor(target, members, currentUserId)
                editorDrafts.SaveableStateProvider(editorKey) {
                    RecurringEditorDialog(
                        initial = editor.initial,
                        members = editor.assignableMembers,
                        familyMembers = members,
                        currentUserId = currentUserId,
                        isProcessing = dismissBlocked(),
                        actionResult = actionResult,
                        onDismiss = closeEditor,
                        onConfirm = { input ->
                            if (showEditor && editorRequestId == editorKey && !dismissBlocked()) {
                                interruptedEditReview = false
                                pendingSaveId = editorKey
                                pendingOperationId = null
                                val operation = onSave(editorKey, targetId, input)
                                if (operation == null) pendingSaveId = null
                                else pendingOperationId = operation
                            }
                        }
                    )
                }
            }
        }
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
private fun RecurringEditorMessage(
    titleRes: Int,
    messageRes: Int,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    dismissBlocked: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = !dismissBlocked, dismissOnClickOutside = !dismissBlocked),
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(enabled = onRetry != null || !dismissBlocked, onClick = onRetry ?: onDismiss) {
                Text(stringResource(
                    if (onRetry != null) R.string.rec_retry
                    else if (loading) R.string.rec_cancel else R.string.rec_close
                ))
            }
        },
        dismissButton = {
            if (onRetry != null) TextButton(enabled = !dismissBlocked, onClick = onDismiss) { Text(stringResource(R.string.rec_close)) }
        }
    )
}

@Composable
private fun ScheduleCard(
    schedule: RecurringSchedule,
    canManage: Boolean,
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
                if (canManage) {
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
            if (canManage) {
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
    familyMembers: List<User>,
    currentUserId: String?,
    isProcessing: Boolean,
    actionResult: RecurringResult?,
    onDismiss: () -> Unit,
    onConfirm: (RecurringScheduleInput) -> Unit
) {
    val context = LocalContext.current
    val dayLabels = stringArrayResource(R.array.rec_days_short)
    // Sheet đã loại người đang thao tác khỏi lựa chọn giao việc; vẫn giữ cả Con lẫn Bố/Mẹ kia.
    val assignableMembers = members

    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var pointsText by rememberSaveable { mutableStateOf((initial?.points ?: 10).toString()) }
    var assigneeId by rememberSaveable { mutableStateOf(initial?.assignedToUserId ?: "") }
    var assignmentMode by rememberSaveable {
        mutableStateOf(initial?.assignmentMode ?: RecurringAssignmentMode.SHARED)
    }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: TaskPriority.MEDIUM) }
    var repeatType by rememberSaveable { mutableStateOf(initial?.repeatType ?: RepeatType.DAILY) }
    var daysMask by rememberSaveable { mutableIntStateOf(initial?.daysOfWeekMask ?: 0) }
    var dueMinute by rememberSaveable {
        mutableIntStateOf(initial?.dueMinuteOfDay ?: RecurringSchedule.DEFAULT_DUE_MINUTE)
    }

    val points = pointsText.toIntOrNull()
    val pointsValid = points != null && points in 1..RecurringSchedule.MAX_POINTS
    val daysValid = repeatType == RepeatType.DAILY || daysMask != 0
    val canManageDraft = canManageRecurringDraft(initial, currentUserId, familyMembers, assigneeId, assignmentMode)
    val canSave = title.isNotBlank() && pointsValid && daysValid && canManageDraft && !isProcessing
    val resultMessage = when (actionResult) {
        RecurringResult.PERSISTENCE_ERROR -> R.string.rec_err_storage
        RecurringResult.NOT_ALLOWED, RecurringResult.INVALID_ASSIGNEE -> R.string.rec_err_permission_changed
        RecurringResult.NOT_FOUND -> R.string.rec_err_general
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = !isProcessing, dismissOnClickOutside = !isProcessing),
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
                if (resultMessage != null) {
                    Text(stringResource(resultMessage), color = RecRed)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = title,
                    enabled = !isProcessing,
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
                    enabled = !isProcessing,
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
                    enabled = !isProcessing,
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
                            enabled = !isProcessing,
                            selected = assigneeId.isEmpty(),
                            onClick = { assigneeId = "" },
                            label = { Text(stringResource(R.string.rec_everyone)) },
                            colors = recChipColors()
                        )
                    }
                    items(assignableMembers, key = { it.id }) { member ->
                        FilterChip(
                            enabled = !isProcessing,
                            selected = assigneeId == member.id,
                            onClick = { assigneeId = member.id },
                            label = { Text("${member.avatarUrl} ${member.name}", maxLines = 1) },
                            colors = recChipColors()
                        )
                    }
                }

                if (assigneeId.isEmpty()) {
                    SectionLabel(stringResource(R.string.rec_field_assignment_mode))
                    RecurringAssignmentMode.entries.forEach { mode ->
                        FilterChip(
                            enabled = !isProcessing,
                            selected = assignmentMode == mode,
                            onClick = { assignmentMode = mode },
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == RecurringAssignmentMode.SHARED) R.string.rec_mode_shared
                                        else R.string.rec_mode_each
                                    ),
                                    maxLines = 2
                                )
                            },
                            colors = recChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        stringResource(
                            if (assignmentMode == RecurringAssignmentMode.SHARED) R.string.rec_mode_shared_hint
                            else R.string.rec_mode_each_hint
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                if (!canManageDraft) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.rec_err_management_transition), color = RecRed, fontSize = 12.sp)
                }
                if (initial != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.rec_edit_future_hint),
                        color = RecAccent,
                        fontSize = 12.sp
                    )
                }

                // Mức ưu tiên
                SectionLabel(stringResource(R.string.rec_field_priority))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { value ->
                        FilterChip(
                            enabled = !isProcessing,
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
                            enabled = !isProcessing,
                        selected = repeatType == RepeatType.DAILY,
                        onClick = { repeatType = RepeatType.DAILY },
                        label = { Text(stringResource(R.string.rec_repeat_type_daily)) },
                        colors = recChipColors()
                    )
                    FilterChip(
                            enabled = !isProcessing,
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
                                enabled = !isProcessing,
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
                    enabled = !isProcessing,
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
                            assigneeId = assigneeId,
                            priority = priority,
                            points = points ?: 0,
                            repeatType = repeatType,
                            daysOfWeekMask = daysMask,
                            dueMinuteOfDay = dueMinute,
                            assignmentMode = assignmentMode
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
            TextButton(enabled = !isProcessing, onClick = onDismiss) {
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
private fun DayToggle(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) RecAccent else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) RecAccent else Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
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
    when {
        schedule.assignedToUserId.isNotBlank() -> schedule.assignedToUserName
        schedule.assignmentMode == RecurringAssignmentMode.EACH_MEMBER -> stringResource(R.string.rec_everyone_each)
        else -> stringResource(R.string.rec_everyone_shared)
    }

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
