package com.family.taskmanager.ui.task

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DEFAULT_DEADLINE_HOUR = 20
const val DEFAULT_DEADLINE_MINUTE = 0

/**
 * Hạn chót mặc định khi tạo nhiệm vụ mới: 20:00 ngày mai (thay vì cố định 23:59).
 */
fun defaultTaskDeadlineMillis(nowMillis: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, DEFAULT_DEADLINE_HOUR)
        set(Calendar.MINUTE, DEFAULT_DEADLINE_MINUTE)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Kiểm tra hai mốc thời gian có trùng nhau đến từng phút (cùng năm, ngày trong năm, giờ, phút) hay không.
 */
fun isSameDeadlineMinute(firstMillis: Long, secondMillis: Long): Boolean {
    if (firstMillis <= 0L || secondMillis <= 0L) return firstMillis == secondMillis
    val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
    val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR) &&
        first.get(Calendar.HOUR_OF_DAY) == second.get(Calendar.HOUR_OF_DAY) &&
        first.get(Calendar.MINUTE) == second.get(Calendar.MINUTE)
}

/**
 * Khi người dùng đổi ngày trong DatePicker: giữ nguyên giờ & phút đã chọn trước đó
 * (hoặc dùng 20:00 nếu nhiệm vụ trước đó chưa đặt hạn `0L`), không ép về 23:59.
 * Nếu kết quả trùng hạn gốc [originalDeadlineMillis] đến từng phút, giữ nguyên giá trị gốc
 * (kể cả giây và mili giây, ví dụ 23:59:59) để tránh bị hiểu nhầm là đã đổi hạn.
 */
fun applySelectedDatePreservingTime(
    currentDeadlineMillis: Long,
    year: Int,
    month: Int,
    dayOfMonth: Int,
    originalDeadlineMillis: Long? = null
): Long {
    val (hourOfDay, minute) = if (currentDeadlineMillis > 0L) {
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentDeadlineMillis }
        currentCal.get(Calendar.HOUR_OF_DAY) to currentCal.get(Calendar.MINUTE)
    } else {
        DEFAULT_DEADLINE_HOUR to DEFAULT_DEADLINE_MINUTE
    }
    val candidate = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return if (originalDeadlineMillis != null && originalDeadlineMillis > 0L &&
        isSameDeadlineMinute(candidate, originalDeadlineMillis)
    ) {
        originalDeadlineMillis
    } else {
        candidate
    }
}

/**
 * Khi người dùng đổi giờ trong TimePicker: giữ nguyên ngày đang chọn.
 * Nếu nhiệm vụ trước đó chưa có hạn (`0L`), lấy ngày hôm nay (hoặc ngày mai nếu giờ đó hôm nay đã qua).
 * Nếu kết quả trùng hạn gốc [originalDeadlineMillis] đến từng phút, giữ nguyên giá trị gốc
 * (kể cả giây và mili giây, ví dụ 23:59:59) để tránh bị hiểu nhầm là đã đổi hạn.
 */
fun applySelectedTimePreservingDate(
    currentDeadlineMillis: Long,
    hourOfDay: Int,
    minute: Int,
    nowMillis: Long = System.currentTimeMillis(),
    originalDeadlineMillis: Long? = null
): Long {
    val cal = Calendar.getInstance()
    if (currentDeadlineMillis > 0L) {
        cal.timeInMillis = currentDeadlineMillis
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    } else {
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val candidate = cal.timeInMillis
    return if (originalDeadlineMillis != null && originalDeadlineMillis > 0L &&
        isSameDeadlineMinute(candidate, originalDeadlineMillis)
    ) {
        originalDeadlineMillis
    } else {
        candidate
    }
}

/**
 * Kiểm tra hạn chót có hợp lệ để lưu hay không:
 * - Không có hạn (`0L`): hợp lệ.
 * - Đang sửa và hạn đang chọn bằng hạn gốc của nhiệm vụ (`selectedDeadlineMillis == originalDeadlineMillis`):
 *   hợp lệ (giữ nguyên hạn cũ, kể cả khi việc đã quá hạn).
 * - Các trường hợp còn lại: thời điểm hạn phải lớn hơn hiện tại (`selectedDeadlineMillis > nowMillis`).
 */
fun isDeadlineSelectionValid(
    selectedDeadlineMillis: Long,
    isEditing: Boolean,
    originalDeadlineMillis: Long?,
    nowMillis: Long = System.currentTimeMillis()
): Boolean = when {
    selectedDeadlineMillis < 0L -> false
    selectedDeadlineMillis == 0L -> true
    isEditing && originalDeadlineMillis != null && selectedDeadlineMillis == originalDeadlineMillis -> true
    else -> selectedDeadlineMillis > nowMillis
}

fun formatTaskDeadlineForm(deadlineMillis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("HH:mm dd/MM/yyyy", locale).format(Date(deadlineMillis))

fun formatTaskDeadlineDetail(deadlineMillis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("HH:mm · dd/MM/yyyy", locale).format(Date(deadlineMillis))

fun formatTaskShortDeadline(
    deadlineMillis: Long,
    nowMillis: Long,
    locale: Locale = Locale.getDefault(),
    todayFormatter: (String) -> String,
    tomorrowFormatter: (String) -> String
): String {
    val timeText = SimpleDateFormat("HH:mm", locale).format(Date(deadlineMillis))
    val todayStart = startOfDayMillis(nowMillis)
    val tomorrowStart = shiftDaysMillis(todayStart, 1)
    val deadlineDayStart = startOfDayMillis(deadlineMillis)
    return when (deadlineDayStart) {
        todayStart -> todayFormatter(timeText)
        tomorrowStart -> tomorrowFormatter(timeText)
        else -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val dueCal = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
            val pattern = if (nowCal.get(Calendar.YEAR) == dueCal.get(Calendar.YEAR)) {
                "HH:mm dd/MM"
            } else {
                "HH:mm dd/MM/yyyy"
            }
            SimpleDateFormat(pattern, locale).format(Date(deadlineMillis))
        }
    }
}

fun startOfDayMillis(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun shiftDaysMillis(dayStartMillis: Long, days: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayStartMillis
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

