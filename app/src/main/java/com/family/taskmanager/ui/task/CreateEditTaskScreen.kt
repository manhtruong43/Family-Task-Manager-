package com.family.taskmanager.ui.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("10") }
    var selectedPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    val members by viewModel.familyMembers.collectAsState()
    var selectedAssignee by remember { mutableStateOf<User?>(null) }
    var expandedAssignee by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tạo Nhiệm vụ Mới") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
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
                label = { Text("Tên nhiệm vụ (vd: Dọn phòng ngủ)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Mô tả công việc chi tiết") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chọn thành viên được giao
            Text("Phân công cho thành viên:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expandedAssignee,
                onExpandedChange = { expandedAssignee = !expandedAssignee }
            ) {
                OutlinedTextField(
                    value = selectedAssignee?.name ?: "Tất cả thành viên",
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
                        text = { Text("Tất cả thành viên") },
                        onClick = {
                            selectedAssignee = null
                            expandedAssignee = false
                        }
                    )
                    members.forEach { user ->
                        DropdownMenuItem(
                            text = { Text("${user.name} (${if (user.role.name == "ADMIN") "Bố/Mẹ" else "Con"})") },
                            onClick = {
                                selectedAssignee = user
                                expandedAssignee = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Mức độ ưu tiên:", fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedPriority == TaskPriority.LOW,
                    onClick = { selectedPriority = TaskPriority.LOW }
                )
                Text("Thấp", modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = selectedPriority == TaskPriority.MEDIUM,
                    onClick = { selectedPriority = TaskPriority.MEDIUM }
                )
                Text("Trung bình", modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = selectedPriority == TaskPriority.HIGH,
                    onClick = { selectedPriority = TaskPriority.HIGH }
                )
                Text("Cao")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pointsText,
                onValueChange = { pointsText = it.filter { char -> char.isDigit() } },
                label = { Text("Điểm thưởng khi hoàn thành (vd: 15)") },
                modifier = Modifier.fillMaxWidth()
            )

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
                        deadlineTimestamp = System.currentTimeMillis() + 86400000L // 1 ngày sau
                    )
                    onBack()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Tạo & Giao Nhiệm vụ", fontSize = 16.sp)
            }
        }
    }
}
