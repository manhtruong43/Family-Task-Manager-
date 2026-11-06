package com.family.taskmanager

import android.database.sqlite.SQLiteException
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    private val dad = User(id = "dad", name = "Bố", email = "bo@x.com", role = UserRole.ADMIN, familyId = family)

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
        listOf(parent, bin, dad).forEach { db.userDao().insertUser(it) }
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
        assigneeId = bin.id,
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
        assertTrue(familyTasks().single().openToAll)              // việc chung: bị từ chối thì trả lại cả nhà
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
        assertEquals(1, db.notificationDao().getNotificationsByUser(bin.id).first().size)
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

    // ─── Cùng quy tắc phân quyền với giao việc thường ───

    @Test
    fun save_cannotAssignToYourself_orOutsider_butCanAssignOtherParent() = runBlocking<Unit> {
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, null, input().copy(assigneeId = parent.id)))
        val outsider = User(id = "out", name = "Khách", email = "o@x.com", role = UserRole.MEMBER, familyId = "family-2")
        db.userDao().insertUser(outsider)
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, null, input().copy(assigneeId = outsider.id)))

        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input(title = "Nấu cơm").copy(assigneeId = dad.id)))
        val saved = db.recurringScheduleDao().observeByFamily(family).first().single()
        // Lịch đang giao cho Bố: Bố không tự sửa được, Mẹ thì sửa được
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(dad, saved.id, input(title = "Nấu cơm").copy(assigneeId = bin.id)))
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, saved.id, input(title = "Nấu cơm").copy(assigneeId = bin.id)))
    }

    @Test
    fun everyoneSchedule_notifiesOtherParentAndChildren_butNotTheCreator() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input().copy(assigneeId = "")))

        val task = familyTasks().single()
        assertTrue(task.openToAll)
        assertEquals(1, db.notificationDao().getNotificationsByUser(dad.id).first().size)
        assertEquals(1, db.notificationDao().getNotificationsByUser(bin.id).first().size)
        assertEquals(0, db.notificationDao().getNotificationsByUser(parent.id).first().size)
    }

    @Test
    fun cachedParent_cannotManageAfterLeavingFamily() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()
        db.userDao().updateUser(parent.copy(familyId = ""))

        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, null, input(title = "Việc mới")))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, saved.id, input(title = "Đã sửa")))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.setActive(parent, saved.id, false))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.deleteSchedule(parent, saved.id))
        assertEquals(saved, onlySchedule())
        assertEquals(1, familyTasks().size)
    }

    @Test
    fun cachedParent_cannotManageAfterRoleOrFamilyChanges() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()

        listOf(
            parent.copy(role = UserRole.MEMBER),
            parent.copy(familyId = "family-2")
        ).forEach { changed ->
            db.userDao().updateUser(changed)
            assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, null, input()))
            assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, saved.id, input()))
            assertEquals(RecurringResult.NOT_ALLOWED, repository.setActive(parent, saved.id, false))
            assertEquals(RecurringResult.NOT_ALLOWED, repository.deleteSchedule(parent, saved.id))
            assertEquals(saved, onlySchedule())
        }
    }

    @Test
    fun assignedParent_cannotEditPauseResumeOrDelete_butOtherParentCan() = runBlocking<Unit> {
        val forDad = input().copy(assigneeId = dad.id)
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, forDad))
        val saved = onlySchedule()

        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(dad, saved.id, input()))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.setActive(dad, saved.id, false))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.setActive(dad, saved.id, true))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.deleteSchedule(dad, saved.id))
        assertEquals(saved, onlySchedule())

        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, saved.id, forDad.copy(title = "Lịch mới")))
        assertEquals(RecurringResult.PAUSED, repository.setActive(parent, saved.id, false))
        assertEquals(RecurringResult.RESUMED, repository.setActive(parent, saved.id, true))
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(parent, saved.id))
        assertTrue(db.recurringScheduleDao().observeByFamily(family).first().isEmpty())
        assertEquals(1, familyTasks().size)
    }

    @Test
    fun save_usesCurrentAssigneeName_andRejectsDepartedAssignee() = runBlocking<Unit> {
        db.userDao().updateUser(bin.copy(name = "Bin mới"))
        // Form vẫn giữ đối tượng bin có tên cũ.
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()
        assertEquals("Bin mới", saved.assignedToUserName)

        db.userDao().updateUser(bin.copy(familyId = ""))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, null, input(title = "Việc mới")))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, saved.id, input(title = "Đã sửa")))
        assertEquals(saved, onlySchedule())
        assertEquals(1, familyTasks().size)
    }

    @Test
    fun dissolvedFamily_cannotBeModifiedUsingCachedParent() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()
        db.familyDao().deleteFamily(family)

        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, null, input()))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.saveSchedule(parent, saved.id, input()))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.setActive(parent, saved.id, false))
        assertEquals(RecurringResult.NOT_ALLOWED, repository.deleteSchedule(parent, saved.id))
        assertEquals(saved, onlySchedule())
    }

    @Test
    fun missingAssigneeId_isRejectedWithoutChangingToEveryone() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()
        val missing = input().copy(assigneeId = "missing-member")

        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, saved.id, missing))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(parent, null, missing))
        assertEquals(saved, onlySchedule())
        assertEquals(bin.id, onlySchedule().assignedToUserId)
    }

    @Test
    fun explicitEmptyAssigneeId_changesScheduleToEveryone() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        val saved = onlySchedule()

        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, saved.id, input().copy(assigneeId = "")))
        assertEquals("", onlySchedule().assignedToUserId)
        assertEquals("", onlySchedule().assignedToUserName)

        repository.clock = clockAt(today.plusDays(1).atTime(0, 1))
        assertEquals(1, repository.generateDueTasks())
        assertTrue(db.taskDao().getTaskById(saved.taskIdFor(today.plusDays(1)))!!.openToAll)
        assertEquals(bin.id, db.taskDao().getTaskById(saved.taskIdFor(today))!!.assignedToUserId)
    }

    @Test
    fun save_reportsCommittedSuccess_whenGenerationFails_thenRetryCreatesOneInboxEntry() = runBlocking<Unit> {
        rejectNotificationInserts()

        assertEquals(RecurringResult.SAVED, repository.saveSchedule(parent, null, input()))
        assertEquals(-1L, onlySchedule().lastGeneratedEpochDay)
        assertTrue(familyTasks().isEmpty())
        assertTrue(db.notificationDao().getNotificationsByUser(bin.id).first().isEmpty())

        allowNotificationInserts()
        assertEquals(1, repository.generateDueTasks())
        assertEquals(0, repository.generateDueTasks())
        assertEquals(1, familyTasks().size)
        assertEquals(1, db.notificationDao().getNotificationsByUser(bin.id).first().size)
    }

    @Test
    fun resume_reportsCommittedSuccess_whenGenerationFails() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(id = "resume-failure", familyId = family, title = "Tưới cây",
                assignedToUserId = bin.id, assignedToUserName = bin.name,
                createdByUserId = parent.id, isActive = false)
        )
        rejectNotificationInserts()

        assertEquals(RecurringResult.RESUMED, repository.setActive(parent, "resume-failure", true))
        assertTrue(onlySchedule().isActive)
        assertEquals(-1L, onlySchedule().lastGeneratedEpochDay)
        assertTrue(familyTasks().isEmpty())

        allowNotificationInserts()
        assertEquals(1, repository.generateDueTasks())
        assertEquals(0, repository.generateDueTasks())
        assertEquals(1, db.notificationDao().getNotificationsByUser(bin.id).first().size)
    }

    @Test
    fun generation_rollsBackTaskAndMarker_whenInboxWriteFails() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(
            RecurringSchedule(id = "inbox-failure", familyId = family, title = "Gấp quần áo",
                assignedToUserId = bin.id, assignedToUserName = bin.name, createdByUserId = parent.id)
        )
        rejectNotificationInserts()

        try {
            repository.generateDueTasks()
            fail("Inbox insert failure must roll back generation")
        } catch (_: SQLiteException) {
            assertTrue(familyTasks().isEmpty())
            assertEquals(-1L, onlySchedule().lastGeneratedEpochDay)
            assertTrue(db.notificationDao().getNotificationsByUser(bin.id).first().isEmpty())
        }

        allowNotificationInserts()
        assertEquals(1, repository.generateDueTasks())
        assertEquals(0, repository.generateDueTasks())
        assertEquals(1, familyTasks().size)
        assertEquals(1, db.notificationDao().getNotificationsByUser(bin.id).first().size)
    }

    private fun rejectNotificationInserts() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_schedule_inbox BEFORE INSERT ON notifications " +
                "BEGIN SELECT RAISE(ABORT, 'injected inbox failure'); END"
        )
    }

    private fun allowNotificationInserts() {
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_schedule_inbox")
    }
}
