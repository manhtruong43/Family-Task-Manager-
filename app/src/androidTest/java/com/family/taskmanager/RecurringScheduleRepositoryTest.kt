package com.family.taskmanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.RepeatType
import com.family.taskmanager.data.model.TaskPriority
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.NotificationRepository
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.RecurringScheduleRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Kiểm thử lịch trình cố định: không bao giờ sinh trùng, đúng ngày, đúng quyền.
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'RecurringScheduleRepositoryTest'.
 *
 * Dùng ĐỒNG HỒ CỐ ĐỊNH (Thứ Tư 04/11/2026 lúc 10:00) nên kết quả không phụ thuộc giờ chạy test.
 */
@RunWith(AndroidJUnit4::class)
class RecurringScheduleRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecurringScheduleRepository

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 11, 4)          // Thứ Tư
    private val todayIndex = today.dayOfWeek.value - 1                 // 0 = Thứ Hai

    private val family = "family-1"
    private val parent = User(id = "admin", name = "Mẹ", email = "me@x.com", role = UserRole.ADMIN, familyId = family)
    private val bin = User(id = "bin", name = "Bin", email = "bin@x.com", role = UserRole.MEMBER, familyId = family)

    private fun clockAt(dateTime: LocalDateTime): Clock = Clock.fixed(dateTime.atZone(zone).toInstant(), zone)

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val push = PushNotificationService(context, NotificationPreferences(context))
        repository = RecurringScheduleRepository(context, db, NotificationRepository(db.notificationDao(), push))
        repository.clock = clockAt(today.atTime(10, 0))

        db.familyDao().insertFamily(FamilyGroup(id = family, name = "Nhà Bin", inviteCode = "123456", adminId = parent.id))
        listOf(parent, bin).forEach { db.userDao().insertUser(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun input(
        repeatType: RepeatType = RepeatType.DAILY,
        mask: Int = 0,
        dueMinute: Int = 20 * 60,
        points: Int = 10,
        title: String = "Rửa bát"
    ) = RecurringScheduleInput(
        title = title,
        description = "",
        assignee = bin,
        priority = TaskPriority.MEDIUM,
        points = points,
        repeatType = repeatType,
        daysOfWeekMask = mask,
        dueMinuteOfDay = dueMinute
    )

    private suspend fun familyTasks() = db.taskDao().getTasksByFamily(family).first()
    private suspend fun onlySchedule() = db.recurringScheduleDao().observeByFamily(family).first().single()

    @Test
    fun dailySchedule_createsTodayTask_onlyOnce() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        assertEquals(1, familyTasks().size)

        // Gọi sinh việc thêm nhiều lần (mở app, báo thức 00:01...) -> không sinh trùng
        assertEquals(0, repository.generateDueTasks())
        assertEquals(0, repository.generateDueTasks())
        assertEquals(1, familyTasks().size)

        val task = db.taskDao().getTaskById(onlySchedule().taskIdFor(today))
        assertNotNull(task)
        assertEquals(bin.id, task!!.assignedToUserId)
        assertEquals(onlySchedule().dueAtMillis(today, zone), task.deadlineTimestamp)
    }

    @Test
    fun nextDay_afterMidnight_createsNewTask() = runBlocking<Unit> {
        repository.saveSchedule(parent, null, input())
        assertEquals(1, familyTasks().size)

        // Báo thức 00:01 ngày hôm sau
        repository.clock = clockAt(today.plusDays(1).atTime(0, 1))
        assertEquals(1, repository.generateDueTasks())
        assertEquals(0, repository.generateDueTasks())
        assertEquals(2, familyTasks().size)
    }

    @Test
    fun weeklySchedule_notToday_createsNothing() = runBlocking<Unit> {
        val allButToday = RecurringSchedule.toggleDay(RecurringSchedule.ALL_DAYS_MASK, todayIndex)
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input(RepeatType.WEEKLY, allButToday)))
        assertEquals(0, familyTasks().size)
    }

    @Test
    fun weeklySchedule_onToday_createsTask() = runBlocking<Unit> {
        val onlyToday = RecurringSchedule.toggleDay(0, todayIndex)
        repository.saveSchedule(parent, null, input(RepeatType.WEEKLY, onlyToday))
        assertEquals(1, familyTasks().size)
    }

    @Test
    fun scheduleCreatedAfterDueTime_startsNextTime() = runBlocking<Unit> {
        // Hạn 07:00, lúc tạo là 10:00 -> không sinh việc quá hạn ngay
        repository.saveSchedule(parent, null, input(dueMinute = 7 * 60))
        assertEquals(0, familyTasks().size)
        assertEquals(today.toEpochDay(), onlySchedule().lastGeneratedEpochDay)

        // Hôm sau mới sinh việc đầu tiên
        repository.clock = clockAt(today.plusDays(1).atTime(0, 1))
        assertEquals(1, repository.generateDueTasks())
    }

    @Test
    fun pausedSchedule_createsNothing() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(
                id = "paused", familyId = family, title = "Tưới cây",
                isActive = false, createdByUserId = parent.id
            )
        )
        assertEquals(0, repository.generateDueTasks())
        assertEquals(0, familyTasks().size)
    }

    @Test
    fun assigneeLeftFamily_taskGoesToEveryone() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(
                id = "left", familyId = family, title = "Đổ rác",
                assignedToUserId = "ghost", assignedToUserName = "Người đã rời nhóm",
                createdByUserId = parent.id
            )
        )
        assertEquals(1, repository.generateDueTasks())
        assertEquals("", familyTasks().single().assignedToUserId)
    }

    @Test
    fun dissolvedFamily_scheduleIsTurnedOff_andCreatesNothing() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(id = "orphan", familyId = "family-da-giai-tan", title = "Lau nhà", createdByUserId = parent.id)
        )
        assertEquals(0, repository.generateDueTasks())
        assertEquals(0, db.taskDao().getTasksByFamily("family-da-giai-tan").first().size)
        assertFalse(db.recurringScheduleDao().getById("orphan")!!.isActive)
    }

    @Test
    fun manyGenerationsAtTheSameTime_createExactlyOneTask() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(
                id = "parallel", familyId = family, title = "Gấp quần áo",
                assignedToUserId = bin.id, assignedToUserName = bin.name, createdByUserId = parent.id
            )
        )
        // Mô phỏng mở app + báo thức 00:01 + khởi động máy cùng lúc: 20 lần sinh việc chạy song song
        val created = (1..20).map { async(Dispatchers.IO) { repository.generateDueTasks() } }.awaitAll()

        assertEquals(1, created.sum())
        assertEquals(1, familyTasks().size)
    }

    @Test
    fun validation_andPermissions() = runBlocking<Unit> {
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(bin, null, input()))
        assertEquals(RecurringResult.INVALID_TITLE, repository.saveSchedule(parent, null, input(title = "  ")))
        assertEquals(RecurringResult.INVALID_POINTS, repository.saveSchedule(parent, null, input(points = 0)))
        assertEquals(RecurringResult.NO_DAYS, repository.saveSchedule(parent, null, input(RepeatType.WEEKLY, 0)))
        assertEquals(0, db.recurringScheduleDao().observeByFamily(family).first().size)
    }

    @Test
    fun onlyParent_canPauseOrDelete() = runBlocking<Unit> {
        repository.saveSchedule(parent, null, input())
        val id = onlySchedule().id

        assertEquals(RecurringResult.NOT_ALLOWED, repository.setActive(bin, id, false))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.deleteSchedule(bin, id))
        assertEquals(RecurringResult.PAUSED, repository.setActive(parent, id, false))
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(parent, id))

        // Xóa lịch trình không xóa nhiệm vụ đã sinh
        assertEquals(1, familyTasks().size)
    }
}
