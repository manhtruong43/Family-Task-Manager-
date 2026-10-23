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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
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

    var proofNote by remember { mutableStateOf("") }
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
                    title = { Text("Chi tiết Nhiệm vụ", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về", tint = Color.White)
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
                    Text("Không tìm thấy nhiệm vụ", color = Color.White)
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

                            Text("👤 Người thực hiện: ${if (task.assignedToUserName.isEmpty()) "Tất cả" else task.assignedToUserName}", color = Color.White)
                            Text("⚡ Mức độ ưu tiên: ${task.priority.name}", color = Color.White)
                            Text("🎁 Điểm thưởng: +${task.points} điểm", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Cảm xúc & Tương tác:", color = Color.White, fontWeight = FontWeight.Bold)
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
                                    text = "Đã thả cảm xúc: " + reactions.joinToString(", ") { "${it.userName} (${it.emoji})" },
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
                                    text = "📸 Ảnh Minh Chứng Đã Đính Kèm (${uploadedImages.size} ảnh - Bấm để phóng to):",
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
                                                contentDescription = "Ảnh minh chứng $index",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                if (task.proofNote.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("💬 Ghi chú: ${task.proofNote}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Admin duyệt việc khi có báo cáo
                    if (currentUser?.role == UserRole.ADMIN && task.status == TaskStatus.PENDING_APPROVAL) {
                        Text("Duyệt việc cho thành viên:", color = Color.White, fontWeight = FontWeight.Bold)
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
                                Text("Duyệt & Cộng Point", fontWeight = FontWeight.Bold)
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
                                Text("Từ chối")
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // KHU VỰC BÁO CÁO & UPLOAD NHIỀU ẢNH MINH CHỨNG
                    if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.REJECTED) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📸 Báo cáo & Upload Nhiều Ảnh Minh Chứng:",
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
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Chọn nhiều ảnh", tint = Color.White)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = if (selectedImageUris.isNotEmpty()) "📸 Đã chọn ${selectedImageUris.size} ảnh (+ Thêm ảnh khác)" else "📸 Chọn nhiều ảnh minh chứng từ thư viện",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Danh sách các ảnh đã chọn kèm nút Xóa ❌
                                if (selectedImageUris.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Ảnh đã chọn (Bấm ❌ để xóa hoặc bấm vào ảnh để phóng to):", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
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
                                                    contentDescription = "Ảnh chọn $index",
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
                                                        contentDescription = "Xóa ảnh",
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
                                    label = { Text("Ghi chú minh chứng (vd: Đã lau dọn sạch sẽ)", color = Color.White.copy(alpha = 0.8f)) },
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
                                    Text("Gửi Báo Cáo + ${selectedImageUris.size} Ảnh Minh Chứng", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
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
                    contentDescription = "Ảnh phóng to",
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ảnh trước", tint = Color.White)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ảnh tiếp", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TaskStatus) {
    val (text, textColor) = when (status) {
        TaskStatus.TODO -> Pair("Chưa làm", Color.White)
        TaskStatus.IN_PROGRESS -> Pair("Đang làm", Color(0xFFFBBF24))
        TaskStatus.PENDING_APPROVAL -> Pair("Chờ duyệt", Color(0xFF38BDF8))
        TaskStatus.COMPLETED -> Pair("Hoàn thành", Color(0xFF4ADE80))
        TaskStatus.REJECTED -> Pair("Làm lại", Color(0xFFF87171))
    }

    GlassBox(cornerRadius = 8.dp) {
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
