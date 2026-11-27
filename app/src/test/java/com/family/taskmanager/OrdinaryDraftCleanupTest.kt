package com.family.taskmanager

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.*
import com.family.taskmanager.ui.calendar.RecurringScheduleViewModel
import com.family.taskmanager.ui.home.HomeViewModel
import com.family.taskmanager.ui.task.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OrdinaryDraftCleanupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var home: HomeViewModel
    private lateinit var recurring: RecurringScheduleViewModel
    private lateinit var schedules: RecurringScheduleRepository
    private lateinit var session: UnifiedRecurringSaveSession
    private val mode = mutableStateOf(TaskCreationMode.RECURRING)
    private val entry = mutableIntStateOf(0)
    private var ordinaryResult: TaskActionResult? = null
    private var restoreCount = 0
    private val mom = User("mom", "Mom", "m@test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@test", familyId = "f")
    private val input = RecurringScheduleInput("Read", "", na.id, TaskPriority.MEDIUM, 20, RepeatType.DAILY, 0, 1200)
    private val draft = UnifiedRecurringDraft("unused", null, "Read", "", "20", TaskPriority.MEDIUM,
        listOf(na.id), RecurringAssignmentMode.SHARED, RepeatType.DAILY, 0, 1200)

    @Before fun setup() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup("f", "Family", "123456", mom.id))
        listOf(mom, na).forEach { db.userDao().insertUser(it) }
        val prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        val push = PushNotificationService(context, prefs)
        val notifications = NotificationRepository(db.notificationDao(), push)
        schedules = RecurringScheduleRepository(context, db, notifications)
        val families = FamilyRepository(db, db.familyDao(), db.userDao(), RewardRepository(context, db, db.rewardDao(), notifications))
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    HomeViewModel::class.java -> HomeViewModel(
                        TaskRepository(context, db, db.taskDao(), db.userDao(), db.notificationDao(), db.taskReactionDao(), push),
                        UserRepository(db.userDao()), families, ProofImageStorage(context), schedules, prefs, SavedStateHandle()
                    ) as T
                    else -> RecurringScheduleViewModel(schedules) as T
                }
            }
            val provider = ViewModelProvider(activity, factory)
            home = provider[HomeViewModel::class.java]
            recurring = provider[RecurringScheduleViewModel::class.java]
            home.setCurrentUser(mom)
            activity.setContent {
                val user by home.currentUser.collectAsState()
                key(entry.intValue) {
                    session = rememberUnifiedRecurringSaveSession(
                        active = mode.value == TaskCreationMode.RECURRING, actor = user,
                        viewModel = recurring, store = home.recurringDraftStore,
                        onRestore = { restoreCount++; mode.value = TaskCreationMode.RECURRING },
                        onSaved = {}, onClose = {}
                    )
                }
            }
        }
        waitFor { session.formReady }
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        db.close()
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        compose.waitForIdle()
    }

    private fun saveOther(type: TaskCreationMode, requestId: String) {
        compose.runOnIdle {
            mode.value = type
            home.createTask(
                title = "Different task", description = "", assignedToUser = na,
                priority = TaskPriority.MEDIUM, points = 10,
                deadlineTimestamp = System.currentTimeMillis() + 86_400_000L,
                taskMode = if (type == TaskCreationMode.FLEXIBLE_WEEKLY) TaskMode.FLEXIBLE_WEEKLY else TaskMode.FIXED,
                targetCount = if (type == TaskCreationMode.FLEXIBLE_WEEKLY) 3 else 1,
                abandonedRecurringRequestId = requestId,
                onResult = { ordinaryResult = it }
            )
        }
        waitFor { ordinaryResult != null }
        assertEquals(TaskActionResult.SUCCESS, ordinaryResult)
    }

    private fun failedRecurringThenOther(type: TaskCreationMode) {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_schedule BEFORE INSERT ON recurring_creation_requests BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        val requestId = session.requestId
        compose.runOnIdle { session.save(draft, input) }
        waitFor { session.result == RecurringResult.PERSISTENCE_ERROR && session.formReady }
        assertNotNull(home.recurringDraftStore.get(mom.id, "f"))
        saveOther(type, requestId)
        assertNull(home.recurringDraftStore.get(mom.id, "f"))
        val previousRestores = restoreCount
        compose.runOnIdle { entry.intValue++; mode.value = TaskCreationMode.ONE_TIME }
        waitFor { session.initialized }
        assertEquals(TaskCreationMode.ONE_TIME, mode.value)
        assertEquals(previousRestores, restoreCount)
        runBlocking {
            assertTrue(db.recurringScheduleDao().getByFamilyOnce("f").isEmpty())
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
        }
    }

    @Test fun failureThenOneTimeSave_doesNotRestoreAbandonedRecurringDraft() = failedRecurringThenOther(TaskCreationMode.ONE_TIME)
    @Test fun failureThenFlexibleSave_doesNotRestoreAbandonedRecurringDraft() = failedRecurringThenOther(TaskCreationMode.FLEXIBLE_WEEKLY)

    @Test fun uncertainCommit_keepsRecoveryIdentityDespiteOtherTaskSuccess() {
        val requestId = session.requestId
        home.recurringDraftStore.put(mom.id, "f", draft.copy(requestId = requestId))
        db.openHelper.writableDatabase.execSQL("DROP TABLE recurring_creation_requests")
        saveOther(TaskCreationMode.ONE_TIME, requestId)
        assertEquals(requestId, home.recurringDraftStore.get(mom.id, "f")!!.requestId)
    }

    @Test fun committedRequest_cannotBeDiscardedAsUncommitted() = runBlocking<Unit> {
        val requestId = session.requestId
        home.recurringDraftStore.put(mom.id, "f", draft.copy(requestId = requestId))
        assertEquals(RecurringResult.SAVED, schedules.saveSchedule(mom, null, input, requestId))
        saveOther(TaskCreationMode.ONE_TIME, requestId)
        assertEquals(requestId, home.recurringDraftStore.get(mom.id, "f")!!.requestId)
        assertEquals(1, db.recurringScheduleDao().getByFamilyOnce("f").size)
    }
    private fun assertLookupFailureAllowsActualTaskTypeChoice(type: TaskCreationMode) {
        val requestId = session.requestId
        home.recurringDraftStore.put(mom.id, "f", draft.copy(requestId = requestId))
        db.openHelper.writableDatabase.execSQL("DROP TABLE recurring_creation_requests")
        compose.runOnIdle { recurring.resolveEditor(mom, requestId, retry = true) }
        waitFor { recurring.editorLookup.value?.failed == true }
        var returned = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CreateEditTaskScreen(
                    viewModel = home, onBack = { returned = true },
                    initialCreationMode = TaskCreationMode.RECURRING.name, recurringViewModel = recurring
                )
            }
        }
        val storageError = compose.activity.getString(R.string.rec_err_storage)
        waitFor { compose.onAllNodesWithText(storageError).fetchSemanticsNodes().isNotEmpty() }
        val label = compose.activity.getString(
            if (type == TaskCreationMode.ONE_TIME) R.string.tk_type_one_time else R.string.tk_type_flexible_weekly
        )
        compose.onNodeWithText(label).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(label).assertIsSelected()
        val saveLabel = compose.activity.getString(R.string.btn_create_and_assign)
        compose.onNodeWithText(saveLabel).performScrollTo()
        waitFor { compose.onAllNodes(hasText(saveLabel) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(saveLabel).assertIsEnabled().performClick()
        waitFor { returned }
        assertEquals(requestId, home.recurringDraftStore.get(mom.id, "f")!!.requestId)
        runBlocking {
            val saved = db.taskDao().getTasksByFamily("f").first().single()
            assertEquals(if (type == TaskCreationMode.ONE_TIME) TaskMode.FIXED else TaskMode.FLEXIBLE_WEEKLY, saved.taskMode)
        }
    }

    @Test fun actualScreenLookupFailure_allowsOneTimeCreationAndRetainsUnknownRequest() =
        assertLookupFailureAllowsActualTaskTypeChoice(TaskCreationMode.ONE_TIME)

    @Test fun actualScreenLookupFailure_allowsFlexibleCreationAndRetainsUnknownRequest() =
        assertLookupFailureAllowsActualTaskTypeChoice(TaskCreationMode.FLEXIBLE_WEEKLY)

    @Test fun actualScreenRecoveredEdit_stillDisablesTypeSwitching() = runBlocking<Unit> {
        val requestId = session.requestId
        home.recurringDraftStore.put(mom.id, "f", draft.copy(requestId = requestId))
        schedules.saveSchedule(mom, null, input, requestId)
        compose.runOnIdle { recurring.resolveEditor(mom, requestId, retry = true) }
        waitFor { recurring.editorLookup.value?.lookup?.status == CreationRequestStatus.COMMITTED }
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CreateEditTaskScreen(home, onBack = {}, initialCreationMode = TaskCreationMode.RECURRING.name,
                    recurringViewModel = recurring)
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.rec_continue_edit)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.tk_type_one_time)).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.tk_type_flexible_weekly)).assertIsNotEnabled()
    }
}
