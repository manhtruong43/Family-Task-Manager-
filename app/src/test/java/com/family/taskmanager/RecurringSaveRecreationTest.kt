package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import com.family.taskmanager.ui.calendar.RecurringScheduleSheet
import com.family.taskmanager.ui.calendar.RecurringScheduleViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
// Dialog/TextField dùng chiều rộng mặc định để tránh Robolectric #8460.
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecurringSaveRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: RecurringScheduleRepository
    private lateinit var viewModel: RecurringScheduleViewModel
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var release: CompletableDeferred<Unit>? = null
    private var lockJob: Job? = null
    private var request: Triple<String, String?, RecurringScheduleInput>? = null
    private var saveClicks = 0
    private val mom = User("mom", "Mom", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val day = LocalDate.of(2026, 11, 12)
    private val stored = RecurringSchedule(
        id = "existing", familyId = "f", title = "Original title", createdByUserId = mom.id,
        assignedToUserId = na.id, assignedToUserName = na.name, isActive = false,
        lastGeneratedEpochDay = day.minusDays(2).toEpochDay()
    )

    @Before fun setup() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup(id = "f", name = "Test family", inviteCode = "123456", adminId = mom.id))
        listOf(mom, na).forEach { db.userDao().insertUser(it) }
        db.recurringScheduleDao().upsert(stored)
        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        val notifications = NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        repository = RecurringScheduleRepository(context, db, notifications)
        repository.clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @After fun cleanup() {
        release?.complete(Unit)
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        runBlocking { lockJob?.join() }
        worker.cancel()
        db.close()
    }

    private fun setSheetContent() {
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecurringScheduleViewModel(repository) as T
            }
            val retained = ViewModelProvider(activity, factory)[RecurringScheduleViewModel::class.java]
            if (::viewModel.isInitialized) assertSame(viewModel, retained)
            viewModel = retained
            retained.setFamily("f")
            activity.setContent {
                val schedules by retained.schedules.collectAsState()
                val busy by retained.isProcessing.collectAsState()
                val saveState by retained.saveState.collectAsState()
                MaterialTheme {
                    RecurringScheduleSheet(
                        schedules = schedules, isAdmin = true, currentUserId = mom.id,
                        members = listOf(mom, na), isProcessing = busy, saveState = saveState,
                        onDismiss = {},
                        onSave = { requestId, id, input ->
                            saveClicks++
                            request = Triple(requestId, id, input)
                            retained.save(mom, id, input, requestId)
                        },
                        onToggleActive = { _, _ -> }, onDelete = {}
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun holdDatabase() {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        release = gate
        lockJob = worker.launch {
            db.withTransaction {
                entered.countDown()
                gate.await()
            }
        }
        assertTrue("Database transaction did not start", entered.await(5, TimeUnit.SECONDS))
    }

    private fun startSave(editing: Boolean = false) {
        setSheetContent()
        if (editing) {
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription(label(R.string.rec_edit))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        } else {
            compose.onNodeWithText(label(R.string.rec_add)).performClick()
            compose.onNodeWithText(label(R.string.rec_mode_each)).performScrollTo().performClick()
        }
        compose.onNode(hasSetTextAction() and hasText(label(R.string.rec_field_title)))
            .performScrollTo().performTextReplacement("Delayed save")
        holdDatabase()
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        compose.runOnIdle { assertTrue(viewModel.isProcessing.value) }
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
    }

    private fun completeSave() {
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        compose.waitUntil(5000) {
            // Room trả kết quả về Main qua Handler; Looper PAUSED cần được chạy cả khi không có Compose root.
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.saveState.value?.result == RecurringResult.SAVED && !viewModel.isProcessing.value
        }
    }

    private fun assertClosedAndDraftCleared() {
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        compose.onNode(hasSetTextAction() and hasText("Delayed save")).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_mode_shared)).assertIsSelected()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
    }

    @Test fun creating_rotateBeforeCompletion_closesRestoredEditorAndCreatesExactlyOnce() {
        startSave()
        val originalRequest = requireNotNull(request)
        compose.activityRule.scenario.recreate()
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        compose.onNodeWithText("Delayed save").assertExists()
        // Hai lần gọi sát nhau cũng không được xếp thêm một thao tác lưu.
        compose.runOnIdle {
            viewModel.save(mom, null, originalRequest.third, originalRequest.first)
        }
        completeSave()
        assertClosedAndDraftCleared()

        // Kết quả thành công còn được giữ để chặn phát lại yêu cầu cũ.
        compose.runOnIdle {
            viewModel.save(mom, null, originalRequest.third, originalRequest.first)
        }
        compose.runOnIdle { assertFalse(viewModel.isProcessing.value) }
        runBlocking {
            assertEquals(2, db.recurringScheduleDao().observeByFamily("f").first().size)
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
            assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
        }
        assertEquals(1, saveClicks)
    }

    @Test fun completionWithoutComposedScreen_isRetainedUntilNewScreenObservesIt() {
        startSave()
        compose.activityRule.scenario.recreate()
        // Activity mới chưa được cài nội dung: không có editor nào quan sát kết quả.
        completeSave()
        runBlocking {
            assertEquals(2, db.recurringScheduleDao().observeByFamily("f").first().size)
        }
        setSheetContent()
        assertClosedAndDraftCleared()
        assertEquals(1, saveClicks)
    }

    @Test fun editing_rotateBeforeCompletion_closesEditorAndUpdatesSameSchedule() {
        startSave(editing = true)
        compose.activityRule.scenario.recreate()
        setSheetContent()
        completeSave()
        assertClosedAndDraftCleared()
        runBlocking {
            val updated = db.recurringScheduleDao().observeByFamily("f").first().single()
            assertEquals(stored.copy(title = "Delayed save"), updated)
            assertTrue(db.taskDao().getTasksByFamily("f").first().isEmpty())
        }
        assertEquals(stored.id, request!!.second)
        assertEquals(1, saveClicks)
    }
}
