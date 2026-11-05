package com.family.taskmanager

import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * Kiểm thử logic lịch trình cố định (unit test JVM - chạy nhanh, không cần máy ảo).
 * Chạy: chuột phải thư mục app/src/test -> Run 'Tests in ...'.
 */
class RecurringScheduleTest {

    private val monday: LocalDate = LocalDate.of(2026, 11, 2)   // Thứ Hai
    private val saturday: LocalDate = monday.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    private fun schedule(
        repeatType: RepeatType = RepeatType.DAILY,
        mask: Int = 0,
        dueMinute: Int = 20 * 60
    ) = RecurringSchedule(
        id = "s1",
        familyId = "f1",
        title = "Rửa bát",
        repeatType = repeatType,
        daysOfWeekMask = mask,
        dueMinuteOfDay = dueMinute,
        createdByUserId = "admin"
    )

    @Test
    fun daily_occursEveryDay() {
        val daily = schedule()
        (0L until 7L).forEach { offset -> assertTrue(daily.occursOn(monday.plusDays(offset))) }
    }

    @Test
    fun weekly_occursOnlyOnSelectedDays() {
        // Bit 0 = Thứ Hai, bit 5 = Thứ Bảy
        val mask = RecurringSchedule.toggleDay(RecurringSchedule.toggleDay(0, 0), 5)
        val weekly = schedule(RepeatType.WEEKLY, mask)

        assertTrue(weekly.occursOn(monday))
        assertTrue(weekly.occursOn(saturday))
        assertFalse(weekly.occursOn(monday.plusDays(1)))       // Thứ Ba
        assertFalse(weekly.occursOn(saturday.plusDays(1)))     // Chủ Nhật
    }

    @Test
    fun weekly_withNoDays_neverOccurs() {
        val weekly = schedule(RepeatType.WEEKLY, 0)
        (0L until 7L).forEach { offset -> assertFalse(weekly.occursOn(monday.plusDays(offset))) }
    }

    @Test
    fun toggleDay_isReversible() {
        val once = RecurringSchedule.toggleDay(0, 3)
        assertTrue(RecurringSchedule.isDaySelected(once, 3))
        assertFalse(RecurringSchedule.isDaySelected(RecurringSchedule.toggleDay(once, 3), 3))
    }

    @Test
    fun weeklyWithAllDays_countsAsEveryDay() {
        assertTrue(schedule(RepeatType.WEEKLY, RecurringSchedule.ALL_DAYS_MASK).repeatsEveryDay())
        assertFalse(schedule(RepeatType.WEEKLY, 0b0000001).repeatsEveryDay())
        assertTrue(schedule().repeatsEveryDay())
    }

    @Test
    fun dueAtMillis_isDateAtDueTime() {
        val due = schedule(dueMinute = 20 * 60 + 30).dueAtMillis(monday, ZoneOffset.UTC)
        val expected = monday.atTime(20, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, due)
    }

    @Test
    fun taskId_isUniquePerDay_soTasksAreNeverDuplicated() {
        val s = schedule()
        assertEquals(s.taskIdFor(monday), s.taskIdFor(monday))
        assertFalse(s.taskIdFor(monday) == s.taskIdFor(monday.plusDays(1)))
    }

    @Test
    fun formatMinuteOfDay_padsWithZeros() {
        assertEquals("20:05", RecurringSchedule.formatMinuteOfDay(20 * 60 + 5))
        assertEquals("07:00", RecurringSchedule.formatMinuteOfDay(7 * 60))
    }
}
