package com.family.taskmanager.ui.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.family.taskmanager.R
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskMode
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.TaskActionResult
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.ui.calendar.RecurringScheduleViewModel
import com.family.taskmanager.ui.calendar.canManageRecurringDraft
import com.family.taskmanager.ui.home.HomeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 3 chế độ tạo nhiệm vụ hợp nhất ngay tại màn Tạo nhiệm vụ (góp ý của Giảng viên). */
enum class TaskCreationMode {
    ONE_TIME,
    RECURRING,
    FLEXIBLE_WEEKLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** null = tạo việc mới; khác null = sửa việc có id này. */
    taskId: String? = null,
    /** Chế độ mặc định khi tạo mới: ONE_TIME / RECURRING / FLEXIBLE_WEEKLY. */
    initialCreationMode: String = TaskCreationMode.ONE_TIME.name,
    recurringViewModel: RecurringScheduleViewModel = hiltViewModel()
) {
    val isEditing = taskId != null
    val currentUser by viewModel.currentUser.collectAsState()
    val familyTasks by viewModel.familyTasks.collectAsState()
    val editingTask = taskId?.let { id -> familyTasks.find { it.id == id } }

    var creationMode by rememberSaveable {
        val parsed = runCatching { TaskCreationMode.valueOf(initialCreationMode) }
            .getOrDefault(TaskCreationMode.ONE_TIME)
        mutableStateOf(parsed)
    }
    val isRecurringMode = !isEditing && creationMode == TaskCreationMode.RECURRING
    val isFlexibleMode = (!isEditing && creationMode == TaskCreationMode.FLEXIBLE_WEEKLY) ||
        (isEditing && editingTask?.taskMode == TaskMode.FLEXIBLE_WEEKLY)

    // Dùng rememberSaveable để dữ liệu đang nhập không bị mất khi xoay màn hình
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var pointsText by rememberSaveable { mutableStateOf("10") }
    var selectedPriority by rememberSaveable { mutableStateOf(TaskPriority.MEDIUM) }

    // Cài đặt việc linh hoạt trong tuần (số lần cần hoàn thành: 2..7)
    var flexibleTargetCount by rememberSaveable { mutableIntStateOf(3) }
    val reminderLeadMinutes by viewModel.reminderLeadMinutes.collectAsState()

    // Cài đặt lịch trình lặp lại (khi chọn chế độ RECURRING)
    var repeatType by rememberSaveable { mutableStateOf(RepeatType.DAILY) }
    var daysOfWeekMask by rememberSaveable { mutableIntStateOf(0) }
    var recurringDueMinute by rememberSaveable { mutableIntStateOf(RecurringSchedule.DEFAULT_DUE_MINUTE) }

    // Đang lưu -> khóa nút để không tạo trùng
    val isSavingTask by viewModel.isSavingTask.collectAsState()
    var isSaving by remember { mutableStateOf(false) }

    val points = pointsText.toIntOrNull()
    val maxPoints = if (isRecurringMode) RecurringSchedule.MAX_POINTS else TaskRepository.MAX_TASK_POINTS
    val minPoints = if (isRecurringMode) 1 else 0
    val pointsValid = points != null && points in minPoints..maxPoints

    // Hạn chót mặc định: 20:00 ngày mai (hoặc 20:00 Chủ Nhật tuần này nếu mở chế độ Linh hoạt tuần)
    var selectedDeadlineTimestamp by rememberSaveable {
        val initialDeadline = if (creationMode == TaskCreationMode.FLEXIBLE_WEEKLY) {
            defaultFlexibleWeeklyDeadlineMillis()
        } else {
            defaultTaskDeadlineMillis()
        }
        mutableLongStateOf(initialDeadline)
    }
    var userTouchedDeadline by rememberSaveable { mutableStateOf(false) }
    var originalDeadlineTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val context = LocalContext.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    val dateOnlyFormat = remember(locale) { SimpleDateFormat("dd/MM/yyyy", locale) }
    val timeOnlyFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val deadlineValid = if (isRecurringMode) {
        true
    } else {
        isDeadlineSelectionValid(
            selectedDeadlineMillis = selectedDeadlineTimestamp,
            isEditing = isEditing,
            originalDeadlineMillis = originalDeadlineTimestamp,
            nowMillis = nowMillis
        )
    }

    val showTimePicker: () -> Unit = {
        val current = Calendar.getInstance().apply {
            if (selectedDeadlineTimestamp > 0L) {
                timeInMillis = selectedDeadlineTimestamp
            } else {
                set(Calendar.HOUR_OF_DAY, DEFAULT_DEADLINE_HOUR)
                set(Calendar.MINUTE, DEFAULT_DEADLINE_MINUTE)
            }
        }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val currentNow = System.currentTimeMillis()
                nowMillis = currentNow
                userTouchedDeadline = true
                selectedDeadlineTimestamp = applySelectedTimePreservingDate(
                    currentDeadlineMillis = selectedDeadlineTimestamp,
                    hourOfDay = hourOfDay,
                    minute = minute,
                    nowMillis = currentNow,
                    originalDeadlineMillis = originalDeadlineTimestamp
                )
            },
            current.get(Calendar.HOUR_OF_DAY),
            current.get(Calendar.MINUTE),
            true
        ).show()
    }

    val showDatePicker: (Boolean) -> Unit = { openTimeAfterDate ->
        val current = Calendar.getInstance().apply {
            timeInMillis = if (selectedDeadlineTimestamp > 0L) selectedDeadlineTimestamp else System.currentTimeMillis()
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                nowMillis = System.currentTimeMillis()
                userTouchedDeadline = true
                selectedDeadlineTimestamp = applySelectedDatePreservingTime(
                    currentDeadlineMillis = selectedDeadlineTimestamp,
                    year = year,
                    month = month,
                    dayOfMonth = dayOfMonth,
                    originalDeadlineMillis = originalDeadlineTimestamp
                )
                if (openTimeAfterDate) {
                    showTimePicker()
                }
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = startOfDayMillis(System.currentTimeMillis())
        }.show()
    }

    val members by viewModel.familyMembers.collectAsState()
    val assignableMembers = members.filter { it.id != currentUser?.id }
    var selectedAssigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    var assigneeChangedByUser by rememberSaveable { mutableStateOf(false) }
    val selectedAssignee: User? = assignableMembers.find { it.id == selectedAssigneeId }
    var expandedAssignee by remember { mutableStateOf(false) }

    var assignMode by rememberSaveable { mutableStateOf(AssignMode.ANYONE) }
    var eachSelectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val eachSelected = assignableMembers.filter { it.id in eachSelectedIds }
    val assigneeValid = if (isEditing) (!isFlexibleMode || !selectedAssigneeId.isNullOrBlank())
        else eachSelectedIds.isNotEmpty() && eachSelected.size == eachSelectedIds.size
    val recurringDaysValid = !isRecurringMode || repeatType == RepeatType.DAILY || (daysOfWeekMask and RecurringSchedule.ALL_DAYS_MASK) != 0

    // Chế độ sửa: điền sẵn dữ liệu cũ đúng 1 lần (xoay màn hình không bị ghi đè lại)
    var prefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editingTask) {
        val task = editingTask
        if (task != null && !prefilled) {
            title = task.title
            description = task.description
            pointsText = task.points.toString()
            selectedPriority = task.priority
            selectedDeadlineTimestamp = task.deadlineTimestamp
            originalDeadlineTimestamp = task.deadlineTimestamp
            selectedAssigneeId = task.assignedToUserId.ifEmpty { null }
            assigneeChangedByUser = false
            flexibleTargetCount = task.targetCount.coerceIn(Task.MIN_FLEXIBLE_TARGET_COUNT, Task.MAX_FLEXIBLE_TARGET_COUNT)
            prefilled = true
        }
    }

    val recurringSession = rememberUnifiedRecurringSaveSession(
        active = isRecurringMode,
        restorePendingDraft = !isEditing,
        actor = currentUser,
        viewModel = recurringViewModel,
        store = viewModel.recurringDraftStore,
        onRestore = { draft ->
            creationMode = TaskCreationMode.RECURRING
            title = draft.title
            description = draft.description
            pointsText = draft.points
            selectedPriority = draft.priority
            eachSelectedIds = draft.assigneeIds
            assignMode = if (draft.assignmentMode == RecurringAssignmentMode.EACH_MEMBER) AssignMode.EACH else AssignMode.ANYONE
            repeatType = draft.repeatType
            daysOfWeekMask = draft.daysMask
            recurringDueMinute = draft.dueMinute
        },
        onSaved = {
            Toast.makeText(context, context.getString(R.string.rec_msg_saved), Toast.LENGTH_SHORT).show()
            onBack()
        },
        onClose = onBack
    )
    val busy = isSaving || isSavingTask || recurringSession.processing
    val formBlocked = busy || !recurringSession.initialized || (isRecurringMode && !recurringSession.formReady)
    val taskTypeSwitchEnabled = !busy && recurringSession.initialized && recurringSession.editingId == null
    val recurringAssigneeId = if (eachSelectedIds.size == 1) eachSelectedIds.single() else ""
    val recurringAssignmentMode = if (eachSelectedIds.size >= 2 && assignMode == AssignMode.EACH)
        RecurringAssignmentMode.EACH_MEMBER else RecurringAssignmentMode.SHARED
    val recurringPermission = !isRecurringMode || canManageRecurringDraft(
        recurringSession.initial, currentUser?.id, members, recurringAssigneeId, recurringAssignmentMode
    )
    BackHandler { if (!busy) recurringSession.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(when {
                        isEditing -> R.string.tk_edit_title
                        isRecurringMode && recurringSession.editingId != null -> R.string.rec_dialog_edit
                        else -> R.string.create_task_title
                    }))
                },
                navigationIcon = {
                    IconButton(enabled = !busy, onClick = recurringSession.close) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isRecurringMode) {
                when {
                    recurringSession.lookupFailed -> {
                        Text(stringResource(R.string.rec_err_storage), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = recurringSession.retry) { Text(stringResource(R.string.rec_retry)) }
                    }
                    recurringSession.needsAcknowledgement -> {
                        Text(stringResource(R.string.rec_recovered_draft))
                        OutlinedButton(onClick = recurringSession.continueEditing) {
                            Text(stringResource(R.string.rec_continue_edit))
                        }
                    }
                    recurringSession.missing -> Text(
                        stringResource(R.string.rec_err_general), color = MaterialTheme.colorScheme.error
                    )
                    formBlocked && !busy -> Text(stringResource(R.string.home_loading))
                }
                if (!formBlocked && !recurringPermission) {
                    Text(stringResource(R.string.rec_err_management_transition), color = MaterialTheme.colorScheme.error)
                }
                recurringSession.result?.let { result ->
                    val message = when (result) {
                        RecurringResult.PERSISTENCE_ERROR -> R.string.rec_err_storage
                        RecurringResult.INVALID_ASSIGNEE, RecurringResult.NOT_ALLOWED -> R.string.rec_err_permission_changed
                        RecurringResult.NOT_FOUND -> R.string.rec_err_general
                        else -> null
                    }
                    message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            // Chọn loại nhiệm vụ khi TẠO MỚI: Một lần / Lặp lại / Linh hoạt tuần
            if (!isEditing) {
                Text(stringResource(R.string.tk_type_label), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        enabled = taskTypeSwitchEnabled,
                        selected = creationMode == TaskCreationMode.ONE_TIME,
                        onClick = {
                            creationMode = TaskCreationMode.ONE_TIME
                            if (!userTouchedDeadline) {
                                selectedDeadlineTimestamp = defaultTaskDeadlineMillis()
                            }
                        },
                        label = { Text(stringResource(R.string.tk_type_one_time), fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        enabled = taskTypeSwitchEnabled,
                        selected = creationMode == TaskCreationMode.RECURRING,
                        onClick = { creationMode = TaskCreationMode.RECURRING },
                        label = { Text(stringResource(R.string.tk_type_recurring), fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        enabled = taskTypeSwitchEnabled,
                        selected = creationMode == TaskCreationMode.FLEXIBLE_WEEKLY,
                        onClick = {
                            creationMode = TaskCreationMode.FLEXIBLE_WEEKLY
                            if (!userTouchedDeadline) {
                                selectedDeadlineTimestamp = defaultFlexibleWeeklyDeadlineMillis()
                            }
                        },
                        label = { Text(stringResource(R.string.tk_type_flexible_weekly), fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            OutlinedTextField(
                enabled = !formBlocked,
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                enabled = !formBlocked,
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.task_description_hint)) },
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chọn người làm
            Text(stringResource(R.string.task_assign_to), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                val fallbackAssigneeName = editingTask
                    ?.takeIf { it.assignedToUserId == selectedAssigneeId }
                    ?.assignedToUserName
                    ?.takeIf { it.isNotBlank() }
                val baseAssigneeName = selectedAssignee?.name ?: fallbackAssigneeName
                val assigneeDisplayText = when {
                    baseAssigneeName == null -> stringResource(R.string.task_assign_all)
                    editingTask?.openToAll == true && !assigneeChangedByUser ->
                        "$baseAssigneeName (${stringResource(R.string.tk_mode_anyone)})"
                    else -> baseAssigneeName
                }
                ExposedDropdownMenuBox(
                    expanded = expandedAssignee,
                    onExpandedChange = { if (!formBlocked) expandedAssignee = !expandedAssignee }
                ) {
                    OutlinedTextField(
                enabled = !formBlocked,
                        value = assigneeDisplayText,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAssignee) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !formBlocked)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAssignee,
                        onDismissRequest = { expandedAssignee = false }
                    ) {
                        if (!isFlexibleMode) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.task_assign_all)) },
                                onClick = {
                                    selectedAssigneeId = null
                                    assigneeChangedByUser = true
                                    expandedAssignee = false
                                }
                            )
                        }
                        assignableMembers.forEach { user ->
                            DropdownMenuItem(
                                text = { Text("${user.name} (${roleLabel(user)})") },
                                onClick = {
                                    selectedAssigneeId = user.id
                                    assigneeChangedByUser = true
                                    expandedAssignee = false
                                }
                            )
                        }
                    }
                }
            } else {
                val allSelected = assignableMembers.isNotEmpty() && eachSelected.size == assignableMembers.size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !formBlocked) {
                            eachSelectedIds = if (allSelected) emptyList() else assignableMembers.map { it.id }
                        }
                ) {
                    Checkbox(
                        enabled = !formBlocked,
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            eachSelectedIds = if (checked) assignableMembers.map { it.id } else emptyList()
                        }
                    )
                    Text(stringResource(R.string.tk_select_all), fontWeight = FontWeight.SemiBold)
                }
                assignableMembers.forEach { user ->
                    val checked = user.id in eachSelectedIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !formBlocked) {
                                eachSelectedIds = if (checked) eachSelectedIds - user.id else eachSelectedIds + user.id
                            }
                    ) {
                        Checkbox(
                        enabled = !formBlocked,
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                eachSelectedIds = if (isChecked) eachSelectedIds + user.id else eachSelectedIds - user.id
                            }
                        )
                        Text("${user.name} (${roleLabel(user)})")
                    }
                }

                if (eachSelected.size >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!isFlexibleMode) {
                        AssignModeOption(
                        enabled = !formBlocked,
                            selected = assignMode == AssignMode.ANYONE,
                            text = stringResource(R.string.tk_mode_anyone),
                            hint = stringResource(R.string.tk_mode_anyone_hint),
                            onClick = { assignMode = AssignMode.ANYONE }
                        )
                    }
                    AssignModeOption(
                        enabled = !formBlocked,
                        selected = isFlexibleMode || assignMode == AssignMode.EACH,
                        text = stringResource(R.string.tk_mode_each),
                        hint = stringResource(R.string.tk_mode_each_hint),
                        onClick = { assignMode = AssignMode.EACH }
                    )
                }
                if (isRecurringMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tk_recurring_assign_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.task_priority_label), fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RadioButton(
                    enabled = !formBlocked,
                    selected = selectedPriority == TaskPriority.LOW,
                    onClick = { selectedPriority = TaskPriority.LOW }
                )
                Text(stringResource(R.string.priority_low), modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    enabled = !formBlocked,
                    selected = selectedPriority == TaskPriority.MEDIUM,
                    onClick = { selectedPriority = TaskPriority.MEDIUM }
                )
                Text(stringResource(R.string.priority_medium), modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    enabled = !formBlocked,
                    selected = selectedPriority == TaskPriority.HIGH,
                    onClick = { selectedPriority = TaskPriority.HIGH }
                )
                Text(stringResource(R.string.priority_high))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                enabled = !formBlocked,
                value = pointsText,
                onValueChange = { pointsText = it.filter { char -> char.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.task_points_hint)) },
                singleLine = true,
                isError = !pointsValid,
                supportingText = {
                    if (!pointsValid) {
                        Text(
                            if (isRecurringMode) {
                                stringResource(R.string.rec_err_points, RecurringSchedule.MAX_POINTS)
                            } else {
                                stringResource(R.string.tk_form_points_error, TaskRepository.MAX_TASK_POINTS)
                            }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            // Cấu hình SỐ LẦN HOÀN THÀNH cho chế độ Linh hoạt trong tuần
            if (isFlexibleMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tk_flexible_target_label, flexibleTargetCount),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                val minSelectableTarget = if (isEditing) {
                    maxOf(Task.MIN_FLEXIBLE_TARGET_COUNT, editingTask?.completedCount ?: 0)
                } else {
                    Task.MIN_FLEXIBLE_TARGET_COUNT
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (Task.MIN_FLEXIBLE_TARGET_COUNT..Task.MAX_FLEXIBLE_TARGET_COUNT).forEach { count ->
                        FilterChip(
                            selected = flexibleTargetCount == count,
                            enabled = !formBlocked && count >= minSelectableTarget,
                            onClick = { flexibleTargetCount = count },
                            label = { Text("$count", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tk_flexible_target_hint, flexibleTargetCount, points ?: 0),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cấu hình THỜI HẠN: nếu Lặp lại -> chọn Hằng ngày / Hằng tuần + Thứ + Giờ; còn lại -> Date + Time Picker
            if (isRecurringMode) {
                Text(stringResource(R.string.rec_field_repeat), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        enabled = !formBlocked,
                        selected = repeatType == RepeatType.DAILY,
                        onClick = { repeatType = RepeatType.DAILY },
                        label = { Text(stringResource(R.string.rec_repeat_type_daily)) }
                    )
                    FilterChip(
                        enabled = !formBlocked,
                        selected = repeatType == RepeatType.WEEKLY,
                        onClick = { repeatType = RepeatType.WEEKLY },
                        label = { Text(stringResource(R.string.rec_repeat_type_weekly)) }
                    )
                }

                if (repeatType == RepeatType.WEEKLY) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.rec_field_days), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val dayLabels = stringArrayResource(R.array.rec_days_short)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEachIndexed { index, label ->
                            val selected = RecurringSchedule.isDaySelected(daysOfWeekMask, index)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = !formBlocked) {
                                        daysOfWeekMask = RecurringSchedule.toggleDay(daysOfWeekMask, index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!recurringDaysValid) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.rec_err_days),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.rec_field_due, RecurringSchedule.formatMinuteOfDay(recurringDueMinute)),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !formBlocked,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> recurringDueMinute = hour * 60 + minute },
                            recurringDueMinute / 60,
                            recurringDueMinute % 60,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rec_change_time))
                }
            } else {
                // Chọn hạn chót (Ngày + Giờ) cho việc Một lần và Linh hoạt trong tuần
                Text(
                    text = stringResource(
                        if (isFlexibleMode) R.string.tk_flexible_deadline_label else R.string.task_deadline_label
                    ),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    enabled = !formBlocked,
                    onClick = { showDatePicker(true) },
                    border = if (!deadlineValid) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                    } else {
                        CardDefaults.outlinedCardBorder()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = stringResource(R.string.select_deadline_date),
                                tint = if (!deadlineValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (selectedDeadlineTimestamp > 0L) {
                                        formatTaskDeadlineForm(selectedDeadlineTimestamp, locale)
                                    } else {
                                        stringResource(R.string.tk_no_deadline)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (!deadlineValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.tk_deadline_subtext),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.btn_change),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                    enabled = !formBlocked,
                        onClick = { showDatePicker(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedDeadlineTimestamp > 0L) {
                                stringResource(R.string.tk_change_date_fmt, dateOnlyFormat.format(Date(selectedDeadlineTimestamp)))
                            } else {
                                stringResource(R.string.tk_pick_date)
                            },
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                    enabled = !formBlocked,
                        onClick = showTimePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedDeadlineTimestamp > 0L) {
                                stringResource(R.string.tk_change_time_fmt, timeOnlyFormat.format(Date(selectedDeadlineTimestamp)))
                            } else {
                                stringResource(R.string.tk_pick_time)
                            },
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }
                }

                if (!deadlineValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tk_deadline_past_error),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val reminderLabel = when (reminderLeadMinutes) {
                60 -> R.string.tk_reminder_1h
                120 -> R.string.tk_reminder_2h
                else -> R.string.tk_reminder_30m
            }
            Text(
                stringResource(R.string.tk_reminder_global_summary, stringResource(reminderLabel)),
                fontSize = 13.sp
            )
            if (isFlexibleMode) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tk_reminder_flexible_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (isRecurringMode) {
                        val recurringAssignMode = recurringAssignmentMode
                        val input = RecurringScheduleInput(
                            title = title,
                            description = description,
                            assigneeId = recurringAssigneeId,
                            priority = selectedPriority,
                            points = points ?: 1,
                            repeatType = repeatType,
                            daysOfWeekMask = daysOfWeekMask,
                            dueMinuteOfDay = recurringDueMinute,
                            assignmentMode = recurringAssignMode,
                            reminderLeadMinutes = reminderLeadMinutes
                        )
                        recurringSession.save(
                            UnifiedRecurringDraft(
                                requestId = recurringSession.requestId,
                                editingId = recurringSession.editingId,
                                title = title, description = description, points = pointsText,
                                priority = selectedPriority, assigneeIds = eachSelectedIds,
                                assignmentMode = recurringAssignMode, repeatType = repeatType,
                                daysMask = daysOfWeekMask, dueMinute = recurringDueMinute
                            ),
                            input
                        )
                        return@Button
                    }

                    val clickNow = System.currentTimeMillis()
                    nowMillis = clickNow
                    if (!isDeadlineSelectionValid(selectedDeadlineTimestamp, isEditing, originalDeadlineTimestamp, clickNow)) {
                        Toast.makeText(context, context.getString(R.string.tk_deadline_past_error), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    val handleResult: (TaskActionResult) -> Unit = { result ->
                        isSaving = false
                        if (result == TaskActionResult.SUCCESS) {
                            onBack()
                        } else {
                            Toast.makeText(context, context.getString(result.messageRes()), Toast.LENGTH_SHORT).show()
                        }
                    }
                    val targetTaskMode = if (isFlexibleMode) TaskMode.FLEXIBLE_WEEKLY else TaskMode.FIXED
                    val effectiveTargetCount = if (isFlexibleMode) flexibleTargetCount else 1

                    if (taskId != null) {
                        val openToAllOverride = if (assigneeChangedByUser) (selectedAssigneeId == null) else null
                        viewModel.updateTask(
                            taskId = taskId,
                            title = title,
                            description = description,
                            assignedToUser = selectedAssignee,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            assigneeId = selectedAssigneeId,
                            openToAll = openToAllOverride,
                            targetCount = if (isFlexibleMode) flexibleTargetCount else null,
                            reminderLeadMinutes = reminderLeadMinutes,
                            onResult = handleResult
                        )
                    } else if (eachSelected.size == 1) {
                        viewModel.createTask(
                            title = title,
                            description = description,
                            assignedToUser = eachSelected.single(),
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            taskMode = targetTaskMode,
                            targetCount = effectiveTargetCount,
                            abandonedRecurringRequestId = recurringSession.requestId,
                            reminderLeadMinutes = reminderLeadMinutes,
                            onResult = handleResult
                        )
                    } else if (isFlexibleMode || assignMode == AssignMode.EACH) {
                        viewModel.createTaskForEach(
                            title = title,
                            description = description,
                            assignees = eachSelected,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            taskMode = targetTaskMode,
                            targetCount = effectiveTargetCount,
                            abandonedRecurringRequestId = recurringSession.requestId,
                            reminderLeadMinutes = reminderLeadMinutes,
                            onResult = handleResult
                        )
                    } else {
                        viewModel.createTask(
                            title = title,
                            description = description,
                            assignedToUser = null,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            taskMode = targetTaskMode,
                            targetCount = effectiveTargetCount,
                            abandonedRecurringRequestId = recurringSession.requestId,
                            reminderLeadMinutes = reminderLeadMinutes,
                            onResult = handleResult
                        )
                    }
                },
                enabled = title.isNotBlank() && pointsValid && assigneeValid && deadlineValid && recurringDaysValid && !formBlocked && recurringPermission &&
                        (!isEditing || (editingTask != null && prefilled)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    stringResource(
                        when {
                            busy -> R.string.tk_form_saving
                            isEditing -> R.string.tk_btn_save_changes
                            isRecurringMode && recurringSession.editingId != null -> R.string.rec_save
                            isRecurringMode -> R.string.tk_btn_create_recurring
                            else -> R.string.btn_create_and_assign
                        }
                    ),
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** Cách giao việc khi TẠO mới. */
private enum class AssignMode { ONE, ANYONE, EACH }

/** 1 dòng chọn cách giao việc: nút tròn + tên + giải thích ngắn. */
@Composable
private fun AssignModeOption(
    selected: Boolean,
    text: String,
    hint: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column {
            Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (hint != null) {
                Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun roleLabel(user: User): String = stringResource(user.displayRoleRes())
