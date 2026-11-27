package com.family.taskmanager.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Một cutoff chung cho nhiệm vụ và điểm; cập nhật khi quay lại màn hình và khi dữ liệu hết hạn. */
@Composable
internal fun rememberRollingStatisticsCutoff(
    days: Int?,
    createdAt: List<Long>,
    clock: () -> Long = System::currentTimeMillis
): Long? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentClock by rememberUpdatedState(clock)
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(lifecycle, days, createdAt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            do {
                now = currentClock()
                if (days == null) break
                delay(nextStatisticsRefresh(now, days, createdAt))
            } while (currentCoroutineContext().isActive)
        }
    }
    return statisticsCutoff(now, days)
}
