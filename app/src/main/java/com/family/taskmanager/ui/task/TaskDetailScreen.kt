package com.family.taskmanager.ui.task

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.family.taskmanager.R
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.theme.GlassBox
import com.family.taskmanager.ui.theme.GlassCard
import com.family.taskmanager.ui.theme.GlassGradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.familyTasks.collectAsState()
    val task = tasks.find { it.id == taskId }
    val currentUser by viewModel.currentUser.collectAsState()
    val reactions by viewModel.getTaskReactions(taskId).collectAsState()

    var proofNote by rememberSaveable { mutableStateOf("") }
    val selectedImageUris = remember { mutableStateListOf<Uri>() }

    // Fullscreen photo viewer state
    var photoViewerImages by remember { mutableStateOf<List<String>?>(null) }
    var photoViewerInitialIndex by remember { mutableIntStateOf(0) }

    // Launcher cho phép chọn NHIỀU ảnh từ thư viện
    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
        }
    }

    if (photoViewerImages != null && photoViewerImages!!.isNotEmpty()) {
        GlassPhotoViewerDialog(
            imageUrls = photoViewerImages!!,
            initialIndex = photoViewerInitialIndex,
            onDismiss = { photoViewerImages = null }
        )
    }

    GlassGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.task_detail_title), color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            if (task == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.task_not_found), color = Color.White)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusBadge(status = task.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (task.description.isNotEmpty()) {
                                Text(text = task.description, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Text(stringResource(R.string.task_detail_assignee, assigneeDisplayName(task)), color = Color.White)
                            Text(stringResource(R.string.task_detail_priority, priorityLabel(task.priority)), color = Color.White)
                            Text(stringResource(R.string.task_detail_points, task.points), color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(stringResource(R.string.task_reactions_label), color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("👍", "❤️", "🎉", "😄", "👏").forEach { emoji ->
                                    val count = reactions.count { it.emoji == emoji }
                                    GlassBox(
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.clickable { viewModel.toggleReaction(task.id, emoji) }
                                    ) {
                                        Text(
                                            text = "$emoji $count",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            if (reactions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.task_reacted_by,
                                        reactions.joinToString(", ") { "${it.userName} (${it.emoji})" }
                                    ),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Hiển thị Danh sách Ảnh Minh Chứng đã Upload (Cho phép CLICK PHÓNG TO)
                    val uploadedImages = task.getProofImageList()
                    if (uploadedImages.isNotEmpty()) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.task_proof_attached, uploadedImages.size),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(uploadedImages) { index, imageUrl ->
                                        Box(
                                            modifier = Modifier
                                                .size(130.dp, 130.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    photoViewerImages = uploadedImages
                                                    photoViewerInitialIndex = index
                                                }
                                        ) {
                                            Image(
                                                painter = rememberAsyncImagePainter(imageUrl),
                                                contentDescription = stringResource(R.string.cd_proof_image, index + 1),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                if (task.proofNote.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(stringResource(R.string.task_proof_note, task.proofNote), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Admin duyệt việc khi có báo cáo
                    if (currentUser?.role == UserRole.ADMIN && task.status == TaskStatus.PENDING_APPROVAL) {
                        Text(stringResource(R.string.task_review_section), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    viewModel.approveTask(task)
                                    onBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(stringResource(R.string.btn_approve_award), fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.rejectTask(task)
                                    onBack()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(stringResource(R.string.btn_reject))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Nút chuyển trạng thái sang "Đang làm" (IN_PROGRESS)
                    if (task.status == TaskStatus.TODO || task.status == TaskStatus.REJECTED) {
                        Button(
                            onClick = {
                                viewModel.startTask(task.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.cd_start_task),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (task.status == TaskStatus.REJECTED) R.string.btn_redo_task else R.string.btn_start_task
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (task.status == TaskStatus.IN_PROGRESS) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.task_in_progress_banner),
                                        color = Color(0xFFFBBF24),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.resetTaskToTodo(task.id) }
                                ) {
                                    Text(stringResource(R.string.btn_reset_todo), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // KHU VỰC BÁO CÁO & UPLOAD NHIỀU ẢNH MINH CHỨNG
                    if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.REJECTED) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.proof_section_title),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { multiImagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.cd_pick_images), tint = Color.White)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = if (selectedImageUris.isNotEmpty()) {
                                            stringResource(R.string.btn_pick_images_selected, selectedImageUris.size)
                                        } else {
                                            stringResource(R.string.btn_pick_images)
                                        },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Danh sách các ảnh đã chọn kèm nút Xóa ❌
                                if (selectedImageUris.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(stringResource(R.string.selected_images_hint), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(selectedImageUris) { index, uri ->
                                            Box(
                                                modifier = Modifier.size(110.dp, 110.dp)
                                            ) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(uri),
                                                    contentDescription = stringResource(R.string.cd_selected_image, index + 1),
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            photoViewerImages = selectedImageUris.map { it.toString() }
                                                            photoViewerInitialIndex = index
                                                        }
                                                )

                                                // Nút Xóa ❌ góc trên bên phải
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Red.copy(alpha = 0.85f))
                                                        .clickable { selectedImageUris.removeAt(index) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.cd_remove_image),
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = proofNote,
                                    onValueChange = { proofNote = it },
                                    label = { Text(stringResource(R.string.proof_note_hint), color = Color.White.copy(alpha = 0.8f)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val combinedProofUrls = if (selectedImageUris.isNotEmpty()) {
                                            selectedImageUris.joinToString(",") { it.toString() }
                                        } else {
                                            "https://placeholder.com/proof.jpg"
                                        }
                                        viewModel.submitTaskProof(task.id, combinedProofUrls, proofNote)
                                        onBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(stringResource(R.string.btn_submit_proof, selectedImageUris.size), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Hộp thoại Phóng To Ảnh Đầy Màn Hình (Full-screen Photo Viewer)
@Composable
fun GlassPhotoViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            // Nút Đóng ❌ góc trên bên phải
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
            }

            // Tiêu đề đếm số trang ảnh (vd: 2 / 5)
            Text(
                text = "${currentIndex + 1} / ${imageUrls.size}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )

            // Hình ảnh chính hiển thị Fit sắc nét
            if (currentIndex in imageUrls.indices) {
                Image(
                    painter = rememberAsyncImagePainter(imageUrls[currentIndex]),
                    contentDescription = stringResource(R.string.cd_zoomed_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 80.dp)
                )
            }

            // Nút chuyển ảnh Trước ◀️
            if (currentIndex > 0) {
                IconButton(
                    onClick = { currentIndex-- },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_prev_image), tint = Color.White)
                }
            }

            // Nút chuyển ảnh Tiếp theo ▶️
            if (currentIndex < imageUrls.size - 1) {
                IconButton(
                    onClick = { currentIndex++ },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_image), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TaskStatus) {
    val (textRes, textColor) = when (status) {
        TaskStatus.TODO -> Pair(R.string.task_status_todo, Color.White)
        TaskStatus.IN_PROGRESS -> Pair(R.string.task_status_in_progress, Color(0xFFFBBF24))
        TaskStatus.PENDING_APPROVAL -> Pair(R.string.task_status_pending, Color(0xFF38BDF8))
        TaskStatus.COMPLETED -> Pair(R.string.task_status_completed, Color(0xFF4ADE80))
        TaskStatus.REJECTED -> Pair(R.string.task_status_rejected, Color(0xFFF87171))
    }

    GlassBox(cornerRadius = 8.dp) {
        Text(text = stringResource(textRes), color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/**
 * Tên người thực hiện để hiển thị. Task giao cho "Tất cả" được nhận diện bằng assignedToUserId rỗng
 * (kể cả dữ liệu cũ có assignedToUserName = "Tất cả"), nhãn hiển thị lấy từ string resource.
 */
@Composable
fun assigneeDisplayName(task: Task): String =
    if (task.assignedToUserId.isEmpty() || task.assignedToUserName.isEmpty()) {
        stringResource(R.string.task_assign_all)
    } else {
        task.assignedToUserName
    }

@Composable
fun priorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.LOW -> R.string.priority_low
        TaskPriority.MEDIUM -> R.string.priority_medium
        TaskPriority.HIGH -> R.string.priority_high
    }
)
