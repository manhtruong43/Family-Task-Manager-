package com.family.taskmanager.ui.task

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import android.app.DatePickerDialog
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.family.taskmanager.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    // Dùng rememberSaveable để dữ liệu đang nhập không bị mất khi xoay màn hình
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var pointsText by rememberSaveable { mutableStateOf("10") }
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
        val current = Calendar.getInstance().apply { timeInMillis = selectedDeadlineTimestamp }
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
    // Lưu id (String) thay vì cả đối tượng User để có thể rememberSaveable
    var selectedAssigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAssignee: User? = members.find { it.id == selectedAssigneeId }
    var expandedAssignee by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_task_title)) },
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

            // Chọn thành viên được giao
            Text(stringResource(R.string.task_assign_to), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expandedAssignee,
                onExpandedChange = { expandedAssignee = !expandedAssignee }
            ) {
                OutlinedTextField(
                    value = selectedAssignee?.name ?: stringResource(R.string.task_assign_all),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAssignee) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedAssignee,
                    onDismissRequest = { expandedAssignee = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_assign_all)) },
                        onClick = {
                            selectedAssigneeId = null
                            expandedAssignee = false
                        }
                    )
                    members.forEach { user ->
                        val roleLabel = if (user.role.name == "ADMIN") {
                            stringResource(R.string.role_parent)
                        } else {
                            stringResource(R.string.role_child)
                        }
                        DropdownMenuItem(
                            text = { Text("${user.name} ($roleLabel)") },
                            onClick = {
                                selectedAssigneeId = user.id
                                expandedAssignee = false
                            }
                        )
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
                onValueChange = { pointsText = it.filter { char -> char.isDigit() } },
                label = { Text(stringResource(R.string.task_points_hint)) },
                singleLine = true,
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
                                text = dateFormat.format(Date(selectedDeadlineTimestamp)),
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
                    val points = pointsText.toIntOrNull() ?: 10
                    viewModel.createTask(
                        title = title,
                        description = description,
                        assignedToUser = selectedAssignee,
                        priority = selectedPriority,
                        points = points,
                        deadlineTimestamp = selectedDeadlineTimestamp
                    )
                    onBack()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(stringResource(R.string.btn_create_and_assign), fontSize = 16.sp)
            }
        }
    }
}
