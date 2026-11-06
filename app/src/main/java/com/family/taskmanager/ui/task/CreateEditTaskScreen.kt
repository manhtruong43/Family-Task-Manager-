package com.family.taskmanager.ui.task

import com.family.taskmanager.data.model.UserRole
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.R
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.TaskActionResult
import com.family.taskmanager.data.repository.TaskRepository
import com.family.taskmanager.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** null = tạo việc mới; khác null = sửa việc có id này. */
    taskId: String? = null
) {
    val isEditing = taskId != null
    val currentUser by viewModel.currentUser.collectAsState()
    val familyTasks by viewModel.familyTasks.collectAsState()
    val editingTask = taskId?.let { id -> familyTasks.find { it.id == id } }

    // Dùng rememberSaveable để dữ liệu đang nhập không bị mất khi xoay màn hình
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var pointsText by rememberSaveable { mutableStateOf("10") }
    // Đang lưu -> khóa nút để không tạo trùng; chỉ quay về khi tạo THÀNH CÔNG
    var isSaving by remember { mutableStateOf(false) }
    val points = pointsText.toIntOrNull()
    val pointsValid = points != null && points in 0..TaskRepository.MAX_TASK_POINTS
    var selectedPriority by rememberSaveable { mutableStateOf(TaskPriority.MEDIUM) }
    
    // Hạn chót mặc định: 23:59 ngày mai
    var selectedDeadlineTimestamp by rememberSaveable {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }
        mutableLongStateOf(cal.timeInMillis)
    }
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // Tạo DatePickerDialog mỗi lần bấm để luôn mở đúng ngày đang chọn (kể cả sau khi xoay màn hình)
    val showDatePicker: () -> Unit = {
        val current = Calendar.getInstance().apply {
            timeInMillis = if (selectedDeadlineTimestamp > 0L) selectedDeadlineTimestamp else System.currentTimeMillis()
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDeadlineTimestamp = cal.timeInMillis
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000L // Không chọn ngày quá khứ
        }.show()
    }

    val members by viewModel.familyMembers.collectAsState()
    // Không tự giao việc cho chính mình (không ai tự duyệt việc của mình)
    val assignableMembers = members.filter { it.id != currentUser?.id }
    // Lưu id (String) thay vì cả đối tượng User để có thể rememberSaveable
    var selectedAssigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAssignee: User? = assignableMembers.find { it.id == selectedAssigneeId }
    var expandedAssignee by remember { mutableStateOf(false) }

    // Cách giao (chỉ khi tạo mới). Khi sửa: chọn 1 người hoặc "Tất cả" trong ô chọn như cũ.
    var assignMode by rememberSaveable { mutableStateOf(AssignMode.ONE) }
    // "Mỗi người đều làm": lưu id các người được tích (String để rememberSaveable được)
    var eachSelectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val eachSelected = assignableMembers.filter { it.id in eachSelectedIds }
    val assigneeValid = when {
        isEditing -> true
        assignMode == AssignMode.ONE -> selectedAssignee != null
        assignMode == AssignMode.EACH -> eachSelected.isNotEmpty()
        else -> true
    }

    // Chế độ sửa: điền sẵn dữ liệu cũ đúng 1 lần (xoay màn hình không bị ghi đè lại)
    var prefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editingTask) {
        val task = editingTask
        if (task != null && !prefilled) {
            title = task.title
            description = task.description
            pointsText = task.points.toString()
            selectedPriority = task.priority
            // Giữ đúng cả giá trị 0 (việc không có hạn): chỉ đổi khi Bố/Mẹ tự chọn ngày mới
            selectedDeadlineTimestamp = task.deadlineTimestamp
            selectedAssigneeId = task.assignedToUserId.ifEmpty { null }
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isEditing) R.string.tk_edit_title else R.string.create_task_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title_hint)) },
                singleLine = true,
                // Enter = sang ô Mô tả
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.task_description_hint)) },
                minLines = 3,
                maxLines = 5,
                // Chữ vẫn tự xuống dòng khi dài, nhưng phím Enter chuyển sang ô kế tiếp thay vì chèn dòng trống
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chọn người làm
            Text(stringResource(R.string.task_assign_to), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (!isEditing) {
                // 3 cách giao: Một người / Cả nhà - ai làm cũng được / Mỗi người đều làm
                AssignModeOption(
                    selected = assignMode == AssignMode.ONE,
                    text = stringResource(R.string.tk_mode_one),
                    onClick = { assignMode = AssignMode.ONE }
                )
                AssignModeOption(
                    selected = assignMode == AssignMode.ANYONE,
                    text = stringResource(R.string.tk_mode_anyone),
                    hint = stringResource(R.string.tk_mode_anyone_hint),
                    onClick = { assignMode = AssignMode.ANYONE }
                )
                AssignModeOption(
                    selected = assignMode == AssignMode.EACH,
                    text = stringResource(R.string.tk_mode_each),
                    hint = stringResource(R.string.tk_mode_each_hint),
                    onClick = { assignMode = AssignMode.EACH }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isEditing || assignMode == AssignMode.ONE) {
                ExposedDropdownMenuBox(
                    expanded = expandedAssignee,
                    onExpandedChange = { expandedAssignee = !expandedAssignee }
                ) {
                    OutlinedTextField(
                        value = selectedAssignee?.name ?: stringResource(
                            if (isEditing) R.string.task_assign_all else R.string.tk_pick_assignee
                        ),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAssignee) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAssignee,
                        onDismissRequest = { expandedAssignee = false }
                    ) {
                        // Khi sửa vẫn cho chuyển về "Cả nhà - ai làm cũng được"
                        if (isEditing) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.task_assign_all)) },
                                onClick = {
                                    selectedAssigneeId = null
                                    expandedAssignee = false
                                }
                            )
                        }
                        assignableMembers.forEach { user ->
                            DropdownMenuItem(
                                text = { Text("${user.name} (${roleLabel(user)})") },
                                onClick = {
                                    selectedAssigneeId = user.id
                                    expandedAssignee = false
                                }
                            )
                        }
                    }
                }
            }

            if (!isEditing && assignMode == AssignMode.EACH) {
                // Danh sách tích chọn + "Chọn tất cả"
                val allSelected = assignableMembers.isNotEmpty() && eachSelected.size == assignableMembers.size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            eachSelectedIds = if (allSelected) emptyList() else assignableMembers.map { it.id }
                        }
                ) {
                    Checkbox(
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
                            .clickable {
                                eachSelectedIds = if (checked) eachSelectedIds - user.id else eachSelectedIds + user.id
                            }
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                eachSelectedIds = if (isChecked) eachSelectedIds + user.id else eachSelectedIds - user.id
                            }
                        )
                        Text("${user.name} (${roleLabel(user)})")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.task_priority_label), fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedPriority == TaskPriority.LOW,
                    onClick = { selectedPriority = TaskPriority.LOW }
                )
                Text(stringResource(R.string.priority_low), modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = selectedPriority == TaskPriority.MEDIUM,
                    onClick = { selectedPriority = TaskPriority.MEDIUM }
                )
                Text(stringResource(R.string.priority_medium), modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = selectedPriority == TaskPriority.HIGH,
                    onClick = { selectedPriority = TaskPriority.HIGH }
                )
                Text(stringResource(R.string.priority_high))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pointsText,
                // Giới hạn 6 chữ số để không tràn số; điểm hợp lệ: 0..MAX_TASK_POINTS (giống TaskRepository)
                onValueChange = { pointsText = it.filter { char -> char.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.task_points_hint)) },
                singleLine = true,
                isError = !pointsValid,
                supportingText = {
                    if (!pointsValid) {
                        Text(stringResource(R.string.tk_form_points_error, TaskRepository.MAX_TASK_POINTS))
                    }
                },
                // Bàn phím số, Enter = đóng bàn phím
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chọn hạn chót (Date Picker)
            Text(stringResource(R.string.task_deadline_label), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                onClick = showDatePicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.select_deadline_date),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (selectedDeadlineTimestamp > 0L) {
                                    dateFormat.format(Date(selectedDeadlineTimestamp))
                                } else {
                                    stringResource(R.string.tk_no_deadline)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = stringResource(R.string.task_deadline_subtext),
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

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    isSaving = true
                    val handleResult: (TaskActionResult) -> Unit = { result ->
                        isSaving = false
                        if (result == TaskActionResult.SUCCESS) {
                            onBack()
                        } else {
                            Toast.makeText(context, context.getString(result.messageRes()), Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (taskId != null) {
                        viewModel.updateTask(
                            taskId = taskId,
                            title = title,
                            description = description,
                            assignedToUser = selectedAssignee,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            onResult = handleResult
                        )
                    } else if (assignMode == AssignMode.EACH) {
                        viewModel.createTaskForEach(
                            title = title,
                            description = description,
                            assignees = eachSelected,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            onResult = handleResult
                        )
                    } else {
                        viewModel.createTask(
                            title = title,
                            description = description,
                            // ANYONE = việc chung (không giao đích danh)
                            assignedToUser = if (assignMode == AssignMode.ONE) selectedAssignee else null,
                            priority = selectedPriority,
                            points = points ?: 0,
                            deadlineTimestamp = selectedDeadlineTimestamp,
                            onResult = handleResult
                        )
                    }
                },
                // Đang sửa mà việc đã bị xóa / chưa tải xong -> khóa nút
                enabled = title.isNotBlank() && pointsValid && assigneeValid && !isSaving &&
                        (!isEditing || editingTask != null),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    stringResource(
                        when {
                            isSaving -> R.string.tk_form_saving
                            isEditing -> R.string.tk_btn_save_changes
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
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (hint != null) {
                Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun roleLabel(user: User): String = stringResource(
    when {
        user.role != UserRole.ADMIN -> R.string.role_child
        user.avatarUrl == "🧔" -> R.string.role_father_short
        user.avatarUrl == "👩" -> R.string.role_mother_short
        else -> R.string.role_parent
    }
)
