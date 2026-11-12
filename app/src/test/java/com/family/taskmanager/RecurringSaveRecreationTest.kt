package com.family.taskmanager

import android.app.Application
import android.app.Dialog
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
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
import org.robolectric.shadows.ShadowDialog
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
    private var lookupRequest: String? = null
    private val sheetVisible = mutableStateOf(true)
    private var sheetDismissCount = 0
    private var staleSheetDismiss: (() -> Boolean)? = null
    private val mom = User("mom", "Mom", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val dad = User("dad", "Dad", "d@example.test", role = UserRole.ADMIN, familyId = "f")
    private var actor = mom
    private val currentMembers = mutableStateOf(listOf(mom, na))
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

    private fun setSheetContent(expectRetained: Boolean = true) {
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecurringScheduleViewModel(repository) as T
            }
            val retained = ViewModelProvider(activity, factory)[RecurringScheduleViewModel::class.java]
            if (::viewModel.isInitialized) {
                if (expectRetained) assertSame(viewModel, retained) else assertNotSame(viewModel, retained)
            }
            viewModel = retained
            retained.setFamily("f")
            activity.setContent {
                val list by retained.scheduleState.collectAsState()
                val lookup by retained.editorLookup.collectAsState()
                val busy by retained.isProcessing.collectAsState()
                val saveState by retained.saveState.collectAsState()
                MaterialTheme {
                    RecurringScheduleSheet(
                        visible = sheetVisible.value,
                        schedules = list.schedules, isAdmin = true, currentUserId = actor.id,
                        members = currentMembers.value, isProcessing = busy, saveState = saveState,
                        schedulesLoaded = list.loaded, schedulesLoadFailed = list.loadFailed,
                        editorLookup = lookup,
                        onResolveEditor = { id, retry ->
                            lookupRequest = id
                            retained.resolveEditor(actor, id, retry)
                        },
                        onReloadSchedules = retained::reloadSchedules,
                        onDismiss = { sheetDismissCount++; sheetVisible.value = false },
                        onSave = { requestId, id, input ->
                            saveClicks++
                            request = Triple(requestId, id, input)
                            retained.save(actor, id, input, requestId)
                        },
                        onToggleActive = { _, _ -> }, onDelete = {}
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun holdDatabase(beforeRelease: suspend () -> Unit = {}) {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        release = gate
        lockJob = worker.launch {
            db.withTransaction {
                entered.countDown()
                gate.await()
                beforeRelease()
            }
        }
        assertTrue("Database transaction did not start", entered.await(5, TimeUnit.SECONDS))
    }

    private fun waitForEditor() {
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForResult(result: RecurringResult) {
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.saveState.value?.result == result && !viewModel.isProcessing.value
        }
    }

    private fun field(id: Int) = compose.onNode(hasSetTextAction() and hasText(label(id)))

    private fun startSave(editing: Boolean = false, captureDismissal: Boolean = false) {
        setSheetContent()
        if (editing) {
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription(label(R.string.rec_edit))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        } else {
            compose.onNodeWithText(label(R.string.rec_add)).performClick()
            waitForEditor()
            compose.onNodeWithText(label(R.string.rec_mode_each)).performScrollTo().performClick()
        }
        waitForEditor()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.rec_field_title)))
            .performScrollTo().performTextReplacement("Delayed save")
        if (captureDismissal) {
            val node = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss), useUnmergedTree = true
            ).onFirst().fetchSemanticsNode()
            staleSheetDismiss = requireNotNull(node.config[SemanticsActions.Dismiss].action)
        }
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
        waitForEditor()
        compose.onNode(hasSetTextAction() and hasText("Delayed save")).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_mode_shared)).assertIsSelected()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
    }

    @Test fun creating_rotateBeforeCompletion_closesRestoredEditorAndCreatesExactlyOnce() {
        startSave()
        val originalRequest = requireNotNull(request)
        repository.systemPushSender = { _, _ -> throw IllegalStateException("System service failed after commit") }
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

    @Test fun freshViewModel_resolvesCommittedCreationBeforeAllowingEdits() {
        startSave()
        val originalRequest = requireNotNull(request)
        compose.activityRule.scenario.recreate()
        completeSave()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        holdDatabase() // Giữ truy vấn đối chiếu lại để kiểm tra không cho nhập trước khi biết kết quả.
        setSheetContent(expectRetained = false)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodesWithText(label(R.string.home_loading)).onFirst().assertExists()
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(label(R.string.rec_recovered_title)).fetchSemanticsNodes().isNotEmpty()
        }
        // Bản nháp khôi phục không được cho nhập hoặc Lưu trước khi đối chiếu.
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_continue_edit)).performClick()
        waitForEditor()
        field(R.string.rec_field_title).performScrollTo().performTextReplacement("Recovered edit")
        field(R.string.rec_field_points).performScrollTo().performTextReplacement("27")
        compose.onNodeWithText(label(R.string.rec_mode_shared)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        waitForResult(RecurringResult.SAVED)
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        runBlocking {
            val receipt = db.recurringScheduleDao().getCreationRequest(originalRequest.first)!!
            val edited = db.recurringScheduleDao().getById(receipt.scheduleId)!!
            assertEquals(receipt.scheduleId, request!!.second)
            assertEquals("Recovered edit", edited.title)
            assertEquals(27, edited.points)
            assertEquals(RecurringAssignmentMode.SHARED, edited.assignmentMode)
            assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
            assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
        }
        assertEquals(2, saveClicks)
    }

    private fun recoverInterruptedEdit(committed: Boolean) {
        startSave(editing = true)
        val original = requireNotNull(request)
        compose.activityRule.scenario.recreate()
        if (committed) completeSave()
        // Khác xoay màn hình: mất cả ViewModel và coroutine của lần lưu trước.
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        if (!committed) {
            release!!.complete(Unit)
            runBlocking { lockJob!!.join() }
        }
        setSheetContent(expectRetained = false)
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(label(R.string.rec_interrupted_edit_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(viewModel.saveState.value)
        compose.onNodeWithText(label(R.string.rec_close)).assertIsEnabled()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        runBlocking {
            assertEquals(
                if (committed) stored.copy(title = "Delayed save") else stored,
                db.recurringScheduleDao().getById(stored.id)
            )
            assertEquals(1, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertTrue(db.taskDao().getTasksByFamily("f").first().isEmpty())
        }
        assertEquals(1, saveClicks) // Chưa tự phát lại và chưa thông báo thành công.
        compose.onNodeWithText(label(R.string.rec_continue_edit)).performClick()
        waitForEditor()
        field(R.string.rec_field_title).assertTextContains("Delayed save")
        compose.onNodeWithText(label(R.string.rec_save)).assertIsEnabled()
        field(R.string.rec_field_title).performTextReplacement("Reviewed after interruption")
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        waitForResult(RecurringResult.SAVED)
        assertEquals(original.first, request!!.first)
        assertEquals(stored.id, request!!.second)
        runBlocking {
            assertEquals(
                stored.copy(title = "Reviewed after interruption"),
                db.recurringScheduleDao().getById(stored.id)
            )
            assertEquals(1, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertTrue(db.taskDao().getTasksByFamily("f").first().isEmpty())
        }
    }

    @Test fun interruptedEdit_beforeCommit_freshViewModelUnlocksForExplicitReview() {
        recoverInterruptedEdit(committed = false)
    }

    @Test fun interruptedEdit_afterCommit_freshViewModelUnlocksWithoutReplaying() {
        recoverInterruptedEdit(committed = true)
    }

    @Test fun interruptedEdit_reloadsChangedPermissionsInsteadOfReplaying() {
        startSave(editing = true)
        compose.activityRule.scenario.recreate()
        completeSave()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        runBlocking { db.userDao().updateUser(mom.copy(role = UserRole.MEMBER)) }
        currentMembers.value = listOf(mom.copy(role = UserRole.MEMBER), na)
        setSheetContent(expectRetained = false)
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(label(R.string.rec_interrupted_edit_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.rec_err_permission_changed)).assertExists()
        compose.onNodeWithText(label(R.string.rec_continue_edit)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_close)).assertIsEnabled().performClick()
        compose.onNodeWithText(label(R.string.rec_add)).assertExists()
        assertEquals(1, saveClicks)
    }

    private fun dismissRecovery(method: Int) {
        if (method == 0) {
            compose.onNodeWithText(label(R.string.rec_close)).assertIsEnabled().performClick()
        } else compose.runOnIdle {
            val dialog = ShadowDialog.getShownDialogs().last {
                it.isShowing && !it.javaClass.simpleName.contains("ModalBottomSheet")
            }
            if (method == 1) (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            else outsideTap(dialog)
        }
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.rec_close)).assertDoesNotExist()
    }

    @Test fun failedReceiptRecovery_canDismissAndReopenWithoutLosingCreationIdentity() {
        startSave()
        val original = requireNotNull(request).first
        compose.activityRule.scenario.recreate()
        completeSave()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        var tableRestored = false
        db.openHelper.writableDatabase.execSQL(
            "ALTER TABLE recurring_creation_requests RENAME TO unavailable_creation_requests"
        )
        try {
            setSheetContent(expectRetained = false)
            fun waitForLookupFailure() {
                compose.waitUntil(5000) {
                    shadowOf(Looper.getMainLooper()).idle()
                    viewModel.editorLookup.value?.failed == true
                }
                compose.onNodeWithText(label(R.string.rec_err_storage)).assertExists()
                compose.onNodeWithText(label(R.string.rec_close)).assertIsEnabled()
                compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
                assertEquals(original, lookupRequest)
                assertEquals(1, saveClicks)
            }
            waitForLookupFailure()
            // Close, Back và chạm ngoài đều thoát được, nhưng Add tiếp tục đối chiếu đúng mã cũ.
            for (method in 0..2) {
                dismissRecovery(method)
                compose.onNodeWithText(label(R.string.rec_add)).performClick()
                waitForLookupFailure()
            }
            dismissRecovery(0)
            compose.runOnIdle {
                val sheet = ShadowDialog.getShownDialogs().single {
                    it.isShowing && it.javaClass.simpleName.contains("ModalBottomSheet")
                }
                (sheet as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                !sheetVisible.value
            }
            // Cả bảng đã đóng cũng phải giữ yêu cầu qua lần mất ViewModel tiếp theo.
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
            setSheetContent(expectRetained = false)
            compose.runOnIdle { sheetVisible.value = true }
            compose.onNodeWithText(label(R.string.rec_add)).performClick()
            waitForLookupFailure()

            db.openHelper.writableDatabase.execSQL(
                "ALTER TABLE unavailable_creation_requests RENAME TO recurring_creation_requests"
            )
            tableRestored = true
            holdDatabase()
            compose.onNodeWithText(label(R.string.rec_retry)).performClick()
            // Đóng ngay khi đang tra cứu; kết quả trả về sau đó không được tự bỏ mã yêu cầu.
            compose.onNodeWithText(label(R.string.rec_cancel)).assertIsEnabled().performClick()
            compose.runOnIdle {
                val sheet = ShadowDialog.getShownDialogs().single {
                    it.isShowing && it.javaClass.simpleName.contains("ModalBottomSheet")
                }
                (sheet as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                !sheetVisible.value
            }
            release!!.complete(Unit)
            runBlocking { lockJob!!.join() }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.editorLookup.value?.lookup?.status == CreationRequestStatus.COMMITTED
            }
            compose.runOnIdle { sheetVisible.value = true }
            compose.onNodeWithText(label(R.string.rec_add)).performClick()
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithText(label(R.string.rec_continue_edit)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, lookupRequest)
            assertEquals(1, saveClicks)
            compose.onNodeWithText(label(R.string.rec_continue_edit)).performClick()
            waitForEditor()
            field(R.string.rec_field_title).assertTextContains("Delayed save")
            compose.onNodeWithText(label(R.string.rec_mode_each)).assertIsSelected()
            field(R.string.rec_field_title).performTextReplacement("Recovered without duplicate")
            compose.onNodeWithText(label(R.string.rec_save)).performClick()
            waitForResult(RecurringResult.SAVED)
            assertEquals(original, request!!.first)
            runBlocking {
                val receipt = db.recurringScheduleDao().getCreationRequest(original)!!
                assertEquals(receipt.scheduleId, request!!.second)
                assertEquals("Recovered without duplicate", db.recurringScheduleDao().getById(receipt.scheduleId)!!.title)
                assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
                assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
                assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
            }
        } finally {
            if (!tableRestored) db.openHelper.writableDatabase.execSQL(
                "ALTER TABLE unavailable_creation_requests RENAME TO recurring_creation_requests"
            )
        }
    }

    @Test fun receiptFailure_keepsDraftAndRequestAcrossRotation_thenRetryCreatesOnce() {
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        waitForEditor()
        field(R.string.rec_field_title).performTextReplacement("Keep draft after failure")
        compose.onNodeWithText(label(R.string.rec_mode_each)).performScrollTo().performClick()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_receipt BEFORE INSERT ON recurring_creation_requests " +
                "BEGIN SELECT RAISE(ABORT, 'receipt failed'); END"
        )
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        waitForResult(RecurringResult.PERSISTENCE_ERROR)
        val failedRequest = requireNotNull(request).first
        compose.onNodeWithText(label(R.string.rec_err_storage)).assertExists()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsEnabled()
        runBlocking {
            assertEquals(listOf(stored), db.recurringScheduleDao().getByFamilyOnce("f"))
            assertNull(db.recurringScheduleDao().getCreationRequest(failedRequest))
        }

        compose.activityRule.scenario.recreate()
        setSheetContent()
        waitForEditor()
        compose.onNodeWithText(label(R.string.rec_err_storage)).assertExists()
        field(R.string.rec_field_title).assertTextContains("Keep draft after failure")
        compose.onNodeWithText(label(R.string.rec_mode_each)).assertIsSelected()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receipt")
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        waitForResult(RecurringResult.SAVED)
        assertEquals(failedRequest, request!!.first)
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        runBlocking {
            assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
            assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
        }
    }

    private fun recoverConcurrentDraft(detectDuplicateBeforeDeath: Boolean) {
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        waitForEditor()
        val token = requireNotNull(lookupRequest)
        field(R.string.rec_field_title).performTextReplacement("My newer draft")
        field(R.string.rec_field_points).performScrollTo().performTextReplacement("37")
        val original = RecurringScheduleInput(
            "Committed elsewhere", "", "", TaskPriority.MEDIUM, 10, RepeatType.DAILY, 0, 1200,
            RecurringAssignmentMode.EACH_MEMBER
        )
        val before = runBlocking {
            assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, original, token))
            db.taskDao().getTasksByFamily("f").first().sortedBy { it.id } to
                db.notificationDao().getNotificationsByUser(na.id).first().sortedBy { it.id }
        }
        if (detectDuplicateBeforeDeath) {
            compose.onNodeWithText(label(R.string.rec_save)).performClick()
            waitForResult(RecurringResult.ALREADY_CREATED)
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithText(label(R.string.rec_continue_edit)).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Lưu/khôi phục UI rồi thay ViewModel: ALREADY_CREATED không còn trong bộ nhớ.
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        setSheetContent(expectRetained = false)
        assertNull(viewModel.saveState.value)
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(label(R.string.rec_continue_edit)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.rec_continue_edit)).performClick()
        waitForEditor()
        field(R.string.rec_field_title).assertTextContains("My newer draft")
        field(R.string.rec_field_points).assertTextContains("37")
        compose.onNodeWithText(label(R.string.rec_mode_shared)).assertIsSelected()
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        waitForResult(RecurringResult.SAVED)
        runBlocking {
            val receipt = db.recurringScheduleDao().getCreationRequest(token)!!
            val edited = db.recurringScheduleDao().getById(receipt.scheduleId)!!
            assertEquals(token, request!!.first)
            assertEquals(receipt.scheduleId, request!!.second)
            assertEquals("My newer draft", edited.title)
            assertEquals(37, edited.points)
            assertEquals(RecurringAssignmentMode.SHARED, edited.assignmentMode)
            assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertEquals(before.first, db.taskDao().getTasksByFamily("f").first().sortedBy { it.id })
            assertEquals(before.second, db.notificationDao().getNotificationsByUser(na.id).first().sortedBy { it.id })
        }
        assertEquals(if (detectDuplicateBeforeDeath) 2 else 1, saveClicks)
    }

    @Test fun concurrentCommit_preservesDraftAfterFreshActivityAndViewModel() {
        recoverConcurrentDraft(detectDuplicateBeforeDeath = true)
    }

    @Test fun externalCommit_preservesDraftWithoutDuplicateAcknowledgement() {
        recoverConcurrentDraft(detectDuplicateBeforeDeath = false)
    }

    @Test fun initialListFailure_canRetryWithoutOpeningEditor() {
        var restored = false
        db.openHelper.writableDatabase.execSQL("ALTER TABLE recurring_schedules RENAME TO unavailable_schedules")
        try {
            setSheetContent()
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.scheduleState.value.loadFailed
            }
            compose.onNodeWithText(label(R.string.rec_err_storage)).assertExists()
            compose.onNodeWithText(label(R.string.rec_empty_admin)).assertDoesNotExist()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithContentDescription(label(R.string.rec_edit)).assertDoesNotExist()

            db.openHelper.writableDatabase.execSQL("ALTER TABLE unavailable_schedules RENAME TO recurring_schedules")
            restored = true
            compose.onNodeWithText(label(R.string.rec_retry)).performClick()
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.scheduleState.value.loaded && viewModel.scheduleState.value.schedules == listOf(stored)
            }
            compose.onNodeWithText(stored.title).assertExists()
            compose.onNodeWithText(label(R.string.rec_err_storage)).assertDoesNotExist()
            // Không chỉ tải một lần: sau Retry, luồng vẫn nhận thay đổi DB tiếp theo.
            runBlocking { db.recurringScheduleDao().upsert(stored.copy(title = "Updated after retry")) }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithText("Updated after retry").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            if (!restored) {
                db.openHelper.writableDatabase.execSQL("ALTER TABLE unavailable_schedules RENAME TO recurring_schedules")
            }
        }
    }

    @Test fun cancellationAfterCommit_isTerminalAndResolvesSavedSchedule() {
        startSave()
        repository.systemPushSender = { _, _ -> throw CancellationException("Cancelled after commit") }
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.saveState.value?.cancelled == true && !viewModel.isProcessing.value &&
                compose.onAllNodesWithText(label(R.string.rec_recovered_title)).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(viewModel.saveState.value!!.result)
        compose.onNodeWithText(label(R.string.rec_err_storage)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        runBlocking { assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size) }
    }

    @Test fun roomDeletionWhileEditing_hasTerminalNotFoundAfterRecreation() {
        setSheetContent()
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription(label(R.string.rec_edit)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        waitForEditor()
        field(R.string.rec_field_title).performTextReplacement("Unsaved old edit")
        runBlocking { assertEquals(RecurringResult.DELETED, repository.deleteSchedule(mom, stored.id)) }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(label(R.string.rec_err_general)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_err_general)).assertExists()
        compose.onNodeWithText(label(R.string.home_loading)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_close)).performClick()
        compose.onNodeWithText(label(R.string.rec_add)).assertExists()
    }

    private fun outsideTap(dialog: Dialog) {
        dialog.setCanceledOnTouchOutside(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, -100f, -100f, 0)
        try { dialog.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun attemptPendingDismissals() {
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsNotEnabled().performClick()
        compose.runOnIdle {
            val shown = ShadowDialog.getShownDialogs().filter { it.isShowing }
            val sheet = shown.single { it.javaClass.simpleName.contains("ModalBottomSheet") }
            val editor = shown.last { !it.javaClass.simpleName.contains("ModalBottomSheet") }
            (editor as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            outsideTap(editor)
            (sheet as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            outsideTap(sheet)
            // Sự kiện dismiss đã lấy từ trước lúc bấm Lưu cũng phải bị từ chối.
            requireNotNull(staleSheetDismiss).invoke()
        }
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.rec_save)).assertExists()
        compose.onNodeWithText("Delayed save").assertExists()
        assertTrue(sheetVisible.value)
        assertEquals(0, sheetDismissCount)
    }

    @Test fun pendingDismissals_thenRecreation_successCreatesOnceAndUnlocksSheet() {
        startSave(captureDismissal = true)
        val original = requireNotNull(request).first
        attemptPendingDismissals()
        compose.activityRule.scenario.recreate()
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsNotEnabled()
        completeSave()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        runBlocking {
            assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
            assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
            assertNotNull(db.recurringScheduleDao().getCreationRequest(original))
        }
        compose.onNodeWithText(label(R.string.rec_add)).assertIsDisplayed()
        // Sau kết quả thành công, Back lại đóng bảng bình thường.
        compose.runOnIdle {
            val sheet = ShadowDialog.getShownDialogs().single {
                it.isShowing && it.javaClass.simpleName.contains("ModalBottomSheet")
            }
            (sheet as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !sheetVisible.value
        }
        assertEquals(1, sheetDismissCount)
    }

    @Test fun pendingDismissals_thenRecreation_failureKeepsIdentityForRetry() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_receipt BEFORE INSERT ON recurring_creation_requests " +
                "BEGIN SELECT RAISE(ABORT, 'receipt failed'); END"
        )
        startSave(captureDismissal = true)
        val original = requireNotNull(request).first
        attemptPendingDismissals()
        compose.activityRule.scenario.recreate()
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsNotEnabled()
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        waitForResult(RecurringResult.PERSISTENCE_ERROR)
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsEnabled()
        field(R.string.rec_field_title).assertTextContains("Delayed save")
        compose.onNodeWithText(label(R.string.rec_mode_each)).assertIsSelected()
        assertEquals(0, sheetDismissCount)
        runBlocking {
            assertEquals(listOf(stored), db.recurringScheduleDao().getByFamilyOnce("f"))
            assertNull(db.recurringScheduleDao().getCreationRequest(original))
        }
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receipt")
        compose.onNodeWithText(label(R.string.rec_save)).assertIsEnabled().performClick()
        waitForResult(RecurringResult.SAVED)
        assertEquals(original, request!!.first)
        runBlocking {
            assertEquals(2, db.recurringScheduleDao().getByFamilyOnce("f").size)
            assertEquals(1, db.taskDao().getTasksByFamily("f").first().size)
            assertEquals(1, db.notificationDao().getNotificationsByUser(na.id).first().size)
        }
        assertTrue(sheetVisible.value)
    }

    private fun sharedSchedule() = stored.copy(
        assignedToUserId = "", assignedToUserName = "", assignmentMode = RecurringAssignmentMode.SHARED
    )

    private fun openSharedEditor(user: User) {
        runBlocking {
            db.userDao().insertUser(dad)
            db.recurringScheduleDao().upsert(sharedSchedule())
        }
        actor = user
        currentMembers.value = listOf(mom, dad, na)
        setSheetContent()
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription(label(R.string.rec_edit)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        waitForEditor()
    }

    @Test fun otherParent_cannotSelectPerMemberAndSaveTheirOwnFutureAssignment() {
        openSharedEditor(dad)
        field(R.string.rec_field_title).performTextReplacement("Dad draft")
        compose.onNodeWithText(label(R.string.rec_mode_each)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.rec_err_management_transition)).performScrollTo().assertIsDisplayed()
        assertNull(request)
        compose.activityRule.scenario.recreate()
        setSheetContent()
        waitForEditor()
        field(R.string.rec_field_title).assertTextContains("Dad draft")
        compose.onNodeWithText(label(R.string.rec_mode_each)).assertIsSelected()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        runBlocking { assertEquals(sharedSchedule(), db.recurringScheduleDao().getById(stored.id)) }
    }

    @Test fun manager_canChangeSharedScheduleToPerMember() {
        openSharedEditor(mom)
        field(R.string.rec_field_title).performTextReplacement("Owner switched mode")
        field(R.string.rec_field_points).performScrollTo().performTextReplacement("27")
        compose.onNodeWithText(label(R.string.rec_mode_each)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.rec_err_management_transition)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsEnabled().performClick()
        waitForResult(RecurringResult.SAVED)
        runBlocking {
            val updated = db.recurringScheduleDao().getById(stored.id)!!
            assertEquals("Owner switched mode", updated.title)
            assertEquals(27, updated.points)
            assertEquals(mom.id, updated.createdByUserId)
            assertEquals(RecurringAssignmentMode.EACH_MEMBER, updated.assignmentMode)
            assertFalse(updated.isActive)
        }
    }

    @Test fun otherParent_canSaveContentOnlyChangesToSharedSchedule() {
        openSharedEditor(dad)
        field(R.string.rec_field_title).performTextReplacement("Updated shared title")
        field(R.string.rec_field_description).performScrollTo().performTextReplacement("Dad note")
        compose.onNodeWithText(label(R.string.rec_save)).assertIsEnabled().performClick()
        waitForResult(RecurringResult.SAVED)
        runBlocking {
            assertEquals(
                sharedSchedule().copy(title = "Updated shared title", description = "Dad note"),
                db.recurringScheduleDao().getById(stored.id)
            )
        }
    }

    @Test fun membershipChangesDuringSave_showInlineFailureAndKeepDraft() {
        openSharedEditor(dad)
        field(R.string.rec_field_title).performTextReplacement("Keep after leaving")
        holdDatabase { db.userDao().updateUser(dad.copy(familyId = "")) }
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        waitForResult(RecurringResult.NOT_ALLOWED)
        compose.onNodeWithText(label(R.string.rec_err_permission_changed)).performScrollTo().assertIsDisplayed()
        field(R.string.rec_field_title).assertTextContains("Keep after leaving")
        // Ngay cả khi danh sách mới bỏ actor, không được thay lỗi quyền bằng loading vô hạn.
        compose.runOnIdle { currentMembers.value = listOf(mom, na) }
        compose.onNodeWithText(label(R.string.rec_err_permission_changed)).assertExists()
        compose.onNodeWithText(label(R.string.home_loading)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        field(R.string.rec_field_title).assertTextContains("Keep after leaving")
        runBlocking { assertEquals(sharedSchedule(), db.recurringScheduleDao().getById(stored.id)) }
    }

    @Test fun assignmentChangesDuringSave_showRepositoryPermissionFailureInline() {
        openSharedEditor(dad)
        field(R.string.rec_field_title).performTextReplacement("Keep after reassignment")
        val reassigned = sharedSchedule().copy(assignedToUserId = dad.id, assignedToUserName = dad.name)
        holdDatabase { db.recurringScheduleDao().upsert(reassigned) }
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        release!!.complete(Unit)
        runBlocking { lockJob!!.join() }
        waitForResult(RecurringResult.INVALID_ASSIGNEE)
        compose.onNodeWithText(label(R.string.rec_err_permission_changed)).performScrollTo().assertIsDisplayed()
        field(R.string.rec_field_title).assertTextContains("Keep after reassignment")
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        runBlocking { assertEquals(reassigned, db.recurringScheduleDao().getById(stored.id)) }
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
