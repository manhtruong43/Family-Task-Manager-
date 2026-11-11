package com.family.taskmanager

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecurringManagementRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: RecurringScheduleRepository
    private val mom = User("mom", "Mẹ", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val dad = User("dad", "Bố", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val each = RecurringSchedule(
        id = "each", familyId = "f", title = "Dọn nhà", createdByUserId = mom.id,
        isActive = false, assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )
    private val input = RecurringScheduleInput(
        title = "Đã sửa", description = "", assigneeId = "", priority = TaskPriority.HIGH,
        points = 99, repeatType = RepeatType.DAILY, daysOfWeekMask = 0, dueMinuteOfDay = 1200,
        assignmentMode = RecurringAssignmentMode.EACH_MEMBER
    )

    @Before fun setup() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup(id = "f", name = "Nhà thử", inviteCode = "123456", adminId = mom.id))
        listOf(mom, dad, na).forEach { db.userDao().insertUser(it) }
        db.recurringScheduleDao().upsert(each)
        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        repository = RecurringScheduleRepository(
            context, db, NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        )
        repository.clock = Clock.fixed(LocalDate.of(2026, 11, 12).atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @After fun cleanup() { db.close() }

    @Test fun includedParent_cannotEditPauseResumeDelete_orEscapeByChangingModeOrAssignee() = runBlocking<Unit> {
        val edits = listOf(
            input,
            input.copy(assignmentMode = RecurringAssignmentMode.SHARED),
            input.copy(assigneeId = na.id, assignmentMode = RecurringAssignmentMode.SHARED),
            input.copy(assigneeId = dad.id)
        )
        for (edit in edits) {
            assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.saveSchedule(dad, each.id, edit))
            assertEquals(each, db.recurringScheduleDao().getById(each.id))
        }
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.setActive(dad, each.id, false))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.setActive(dad, each.id, true))
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.deleteSchedule(dad, each.id))
        assertEquals(each, db.recurringScheduleDao().getById(each.id))

        // Người tạo vẫn có quyền chỉnh sửa, bật/tắt và xóa lịch.
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, each.id, input))
        assertEquals(99, db.recurringScheduleDao().getById(each.id)!!.points)
        assertEquals(RecurringResult.PAUSED, repository.setActive(mom, each.id, false))
        assertEquals(RecurringResult.RESUMED, repository.setActive(mom, each.id, true))
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(mom, each.id))
    }

    @Test fun sharedClaim_otherParentStillCanManage() = runBlocking<Unit> {
        db.recurringScheduleDao().upsert(each.copy(assignmentMode = RecurringAssignmentMode.SHARED))
        assertEquals(
            RecurringResult.SAVED,
            repository.saveSchedule(dad, each.id, input.copy(assignmentMode = RecurringAssignmentMode.SHARED))
        )
        assertEquals(RecurringResult.PAUSED, repository.setActive(dad, each.id, false))
        assertEquals(RecurringResult.RESUMED, repository.setActive(dad, each.id, true))
        assertEquals(RecurringResult.DELETED, repository.deleteSchedule(dad, each.id))
    }

    @Test fun management_resolvesCurrentMembershipInsideTransaction() = runBlocking<Unit> {
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.setActive(dad, each.id, false))
        db.userDao().updateUser(mom.copy(familyId = "")) // Bố không còn người duyệt nên không thuộc tập người nhận.
        assertEquals(RecurringResult.PAUSED, repository.setActive(dad, each.id, false))
        db.userDao().insertUser(mom.copy(id = "other", email = "other@example.test"))
        // Snapshot dad không đổi, nhưng dữ liệu hiện tại lại khiến Bố trở thành người nhận.
        assertEquals(RecurringResult.INVALID_ASSIGNEE, repository.deleteSchedule(dad, each.id))
        assertNotNull(db.recurringScheduleDao().getById(each.id))
    }
}
