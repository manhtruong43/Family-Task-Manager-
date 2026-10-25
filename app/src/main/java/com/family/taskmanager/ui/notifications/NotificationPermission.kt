package com.family.taskmanager.ui.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Đã có quyền hiển thị thông báo chưa (Android 12 trở xuống luôn true). */
fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Trả về một hàm, gọi hàm đó để hiện hộp thoại xin quyền POST_NOTIFICATIONS (Android 13+).
 * [onResult] nhận true nếu đã có quyền / được cấp quyền.
 *
 * Ví dụ dùng (màn Cài đặt):
 *   val requestPermission = rememberNotificationPermissionRequester { granted -> ... }
 *   Button(onClick = { requestPermission() }) { ... }
 */
@Composable
fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        currentOnResult(granted)
    }

    return remember(launcher, context) {
        {
            if (hasNotificationPermission(context)) {
                currentOnResult(true)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentOnResult(true)
            }
        }
    }
}
