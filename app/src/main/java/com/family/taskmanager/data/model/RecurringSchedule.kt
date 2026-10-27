package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Kiểu lặp lại của lịch trình cố định. */
enum class RepeatType {
    DAILY,   // Hằng ngày
    WEEKLY   // Hằng tuần vào các thứ được chọn
}

/**
 * Lịch trình cố định (Chức năng 6 - Lập kế hoạch: tạo lịch trình hằng ngày/tuần cố định).
 *
 * Mỗi lịch trình là một "mẫu" nhiệm vụ. Vào mỗi ngày đến lịch, ứng dụng tự sinh
 * một Task bình thường từ mẫu này (xem RecurringScheduleRepository.generateDueTasks),
 * nên nhiệm vụ sinh ra đi qua đúng luồng sẵn có: làm -> nộp minh chứng -> duyệt -> cộng điểm.
 */
@Entity(tableName = "recurring_schedules")
data class RecurringSchedule(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val description: String = "",
    /** Rỗng = giao cho cả nhà (giống Task). */
    val assignedToUserId: String = "",
    val assignedToUserName: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val points: Int = 10,
    val repeatType: RepeatType = RepeatType.DAILY,
    /** Bit 0 = Thứ Hai ... bit 6 = Chủ Nhật. Chỉ dùng khi repeatType = WEEKLY. */
    val daysOfWeekMask: Int = 0,
    /** Giờ hết hạn trong ngày, tính bằng phút kể từ 00:00 (vd 20:00 = 1200). */
    val dueMinuteOfDay: Int = DEFAULT_DUE_MINUTE,
    val isActive: Boolean = true,
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Ngày gần nhất đã sinh nhiệm vụ (LocalDate.toEpochDay theo giờ máy). -1 = chưa sinh lần nào. */
    val lastGeneratedEpochDay: Long = -1L
) {
    /** Lịch trình có rơi vào ngày [date] không (không xét bật/tắt). */
    fun occursOn(date: LocalDate): Boolean = when (repeatType) {
        RepeatType.DAILY -> true
        RepeatType.WEEKLY -> isDaySelected(daysOfWeekMask, date.dayOfWeek.value - 1)
    }

    /** Thời điểm hết hạn (epoch millis) của lần lặp vào ngày [date]. */
    fun dueAtMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone)
            .plusMinutes(dueMinuteOfDay.toLong())
            .toInstant()
            .toEpochMilli()

    /** Id cố định cho nhiệm vụ sinh ra vào ngày [date] -> không bao giờ sinh trùng 2 lần. */
    fun taskIdFor(date: LocalDate): String = "rec_${id}_${date.toEpochDay()}"

    /**
     * Lịch tuần đã chọn đủ 7 ngày thì coi như hằng ngày (dùng khi hiển thị).
     * Viết dạng hàm (không phải thuộc tính) để Room không hiểu nhầm là một cột.
     */
    fun repeatsEveryDay(): Boolean =
        repeatType == RepeatType.DAILY || (daysOfWeekMask and ALL_DAYS_MASK) == ALL_DAYS_MASK

    companion object {
        const val DEFAULT_DUE_MINUTE = 20 * 60
        const val ALL_DAYS_MASK = 0b111_1111
        const val MAX_POINTS = 1_000
        const val MAX_TITLE_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 150

        /** [dayIndex]: 0 = Thứ Hai ... 6 = Chủ Nhật. */
        fun isDaySelected(mask: Int, dayIndex: Int): Boolean = (mask and (1 shl dayIndex)) != 0

        fun toggleDay(mask: Int, dayIndex: Int): Int = mask xor (1 shl dayIndex)

        fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        fun formatMinuteOfDay(minuteOfDay: Int): String =
            "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }
}
