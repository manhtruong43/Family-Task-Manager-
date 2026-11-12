package com.family.taskmanager

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringCreationLookup
import com.family.taskmanager.data.repository.CreationRequestStatus
import com.family.taskmanager.ui.calendar.RecurringEditorLookupState
import com.family.taskmanager.ui.calendar.RecurringSaveState
import com.family.taskmanager.ui.calendar.RecurringScheduleSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
// Giữ chiều rộng mặc định 320dp: Robolectric #8460 làm Dialog/TextField lặp layout ở 411dp.
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class RecurringEditorRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val mom = User("mom", "Mom", "m@example.test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@example.test", familyId = "f")
    private val stored = RecurringSchedule(
        id = "existing", familyId = "f", title = "Original title", createdByUserId = mom.id,
        assignedToUserId = na.id, assignedToUserName = na.name, isActive = false
    )
    private val schedules = mutableStateOf(listOf(stored))
    private var saved: Pair<String?, RecurringScheduleInput>? = null
    private var acceptSave = true
    private var completeSaveImmediately = true
    private var operationCount = 0
    private var lastRequestId: String? = null
    private val saveState = mutableStateOf<RecurringSaveState?>(null)
    private val loaded = mutableStateOf(true)
    private val lookup = mutableStateOf<RecurringEditorLookupState?>(null)

    // Cài lại cùng màn hình trên Activity mới; state bản nháp chỉ có thể đến từ saved instance state.
    private fun setSheetContent() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    RecurringScheduleSheet(
                        schedules = schedules.value, isAdmin = true, currentUserId = mom.id,
                        members = listOf(mom, na), isProcessing = false, saveState = saveState.value,
                        schedulesLoaded = loaded.value, schedulesLoadFailed = false,
                        editorLookup = lookup.value,
                        onResolveEditor = { id, _ ->
                            lookup.value = RecurringEditorLookupState(id, RecurringCreationLookup(CreationRequestStatus.NEW))
                        },
                        onReloadSchedules = { loaded.value = true },
                        onDismiss = {},
                        onSave = { requestId, id, input ->
                            if (!acceptSave) null else {
                                saved = id to input
                                lastRequestId = requestId
                                val operation = "fake-" + ++operationCount
                                if (completeSaveImmediately) {
                                    saveState.value = RecurringSaveState(
                                        requestId, RecurringResult.SAVED, operationId = operation
                                    )
                                }
                                operation
                            }
                        },
                        onToggleActive = { _, _ -> }, onDelete = {}
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun replaceField(id: Int, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label(id)))
            .performScrollTo().performTextReplacement(value)
    }

    private fun clickText(id: Int) {
        compose.onNodeWithText(label(id)).performScrollTo().performClick()
    }

    private fun changeDraft() {
        replaceField(R.string.rec_field_title, "Unsaved title")
        replaceField(R.string.rec_field_description, "Unsaved details")
        replaceField(R.string.rec_field_points, "27")
        clickText(R.string.rec_everyone)
        clickText(R.string.rec_mode_each)
        clickText(R.string.rec_priority_high)
        clickText(R.string.rec_repeat_type_weekly)
        val monday = compose.activity.resources.getStringArray(R.array.rec_days_short).first()
        compose.onNodeWithText(monday).performScrollTo().performClick()
    }

    private fun assertDraftAndSave(expectedId: String?) {
        compose.onNodeWithText(label(R.string.rec_mode_each)).assertIsSelected()
        compose.onNodeWithText("Unsaved title").assertExists()
        // Nút nằm ngoài vùng cuộn của nội dung dialog.
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        compose.runOnIdle {
            val (id, input) = requireNotNull(saved)
            assertEquals(expectedId, id)
            assertEquals("Unsaved title", input.title)
            assertEquals("Unsaved details", input.description)
            assertEquals(27, input.points)
            assertEquals("", input.assigneeId)
            assertEquals(RecurringAssignmentMode.EACH_MEMBER, input.assignmentMode)
            assertEquals(TaskPriority.HIGH, input.priority)
            assertEquals(RepeatType.WEEKLY, input.repeatType)
            assertEquals(1, input.daysOfWeekMask)
        }
    }

    @Test fun creating_activityRecreationPreservesAssignmentModeAndUnsavedFields() {
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        changeDraft()
        compose.activityRule.scenario.recreate()
        setSheetContent()
        assertDraftAndSave(null)
    }

    @Test fun editing_activityRecreationWaitsForSameScheduleAndRestoresDraft() {
        setSheetContent()
        compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        changeDraft()
        compose.runOnIdle { loaded.value = false; schedules.value = emptyList() }
        compose.activityRule.scenario.recreate()
        setSheetContent()

        // Khi ID cũ chưa tải lại thì không được mở form tạo mới hoặc cho Lưu.
        compose.onAllNodesWithText(label(R.string.home_loading)).onFirst().assertExists()
        compose.onNodeWithText(label(R.string.rec_dialog_create)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()

        compose.runOnIdle { schedules.value = listOf(stored); loaded.value = true }
        compose.waitForIdle()
        assertDraftAndSave(stored.id)
    }

    @Test fun deletedWhileEditing_showsNotFoundInsteadOfLoading() {
        setSheetContent()
        compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        replaceField(R.string.rec_field_title, "Unsaved edit")
        compose.runOnIdle { schedules.value = emptyList() }
        compose.onNodeWithText(label(R.string.rec_err_general)).assertExists()
        compose.onNodeWithText(label(R.string.home_loading)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_close)).performClick()
        compose.onNodeWithText(label(R.string.rec_err_general)).assertDoesNotExist()
    }

    @Test fun restoringDeletedTarget_showsNotFoundAndCanDismiss() {
        setSheetContent()
        compose.onNodeWithContentDescription(label(R.string.rec_edit)).performClick()
        replaceField(R.string.rec_field_title, "Unsaved edit")
        compose.runOnIdle { schedules.value = emptyList() }
        compose.activityRule.scenario.recreate()
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_err_general)).assertExists()
        compose.onNodeWithText(label(R.string.home_loading)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_close)).performClick()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        compose.onNodeWithText("Unsaved edit").assertDoesNotExist()
    }

    @Test fun mainList_distinguishesLoadingFromConfirmedEmpty() {
        loaded.value = false
        schedules.value = emptyList()
        setSheetContent()
        compose.onNodeWithText(label(R.string.home_loading)).assertExists()
        compose.onNodeWithText(label(R.string.rec_empty_admin)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_retry)).assertDoesNotExist()
        compose.runOnIdle { loaded.value = true }
        compose.onNodeWithText(label(R.string.rec_empty_admin)).assertExists()
        compose.onNodeWithText(label(R.string.home_loading)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_retry)).assertDoesNotExist()
    }

    @Test fun acceptedSave_locksBeforeBusyStateArrives() {
        completeSaveImmediately = false
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        replaceField(R.string.rec_field_title, "Immediate lock")
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        // Host vẫn truyền isProcessing=false: khóa phải có hiệu lực ngay khi nhận lần thử.
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
        compose.runOnIdle {
            saveState.value = RecurringSaveState(
                requireNotNull(lastRequestId), RecurringResult.SAVED, operationId = "fake-1"
            )
        }
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_add)).assertIsEnabled()
    }

    @Test fun rejectedSave_doesNotLeaveDismissalLocked() {
        acceptSave = false
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        replaceField(R.string.rec_field_title, "Rejected attempt")
        compose.onNodeWithText(label(R.string.rec_save)).performClick()
        compose.onNodeWithText(label(R.string.rec_cancel)).assertIsEnabled().performClick()
        compose.onNodeWithText(label(R.string.rec_save)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_add)).assertIsEnabled()
    }

    @Test fun cancellingDraft_thenReopeningDoesNotRestoreDiscardedValues() {
        setSheetContent()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        replaceField(R.string.rec_field_title, "Discard me")
        compose.onNodeWithText(label(R.string.rec_cancel)).performClick()
        compose.onNodeWithText(label(R.string.rec_add)).performClick()
        compose.onNodeWithText("Discard me").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.rec_save)).assertIsNotEnabled()
    }
}
