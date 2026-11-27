package com.family.taskmanager

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.model.*
import com.family.taskmanager.data.repository.*
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import com.family.taskmanager.ui.calendar.RecurringScheduleViewModel
import com.family.taskmanager.ui.task.*
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UnifiedRecurringSaveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var repository: RecurringScheduleRepository
    private lateinit var vm: RecurringScheduleViewModel
    private lateinit var session: UnifiedRecurringSaveSession
    private var state = SavedStateHandle()
    private var store = UnifiedRecurringDraftStore(state)
    private var saved = 0
    private var closed = 0
    private var restored: UnifiedRecurringDraft? = null
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gate: CompletableDeferred<Unit>? = null
    private var lock: Job? = null
    private val mom = User("mom", "Mom", "m@test", role = UserRole.ADMIN, familyId = "f")
    private val na = User("na", "Na", "n@test", familyId = "f")
    private val draft = UnifiedRecurringDraft(
        "request", null, "Read", "Ten pages", "20", TaskPriority.HIGH,
        listOf(na.id), RecurringAssignmentMode.SHARED, RepeatType.DAILY, 0, 1200
    )
    private val input = RecurringScheduleInput(
        "Read", "Ten pages", na.id, TaskPriority.HIGH, 20, RepeatType.DAILY, 0, 1200
    )

    @Before fun setup() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.familyDao().insertFamily(FamilyGroup("f", "Family", "123456", mom.id))
        listOf(mom, na).forEach { db.userDao().insertUser(it) }
        val prefs = NotificationPreferences(context).also { it.setEnabled(false) }
        repository = RecurringScheduleRepository(
            context, db, NotificationRepository(db.notificationDao(), PushNotificationService(context, prefs))
        )
    }

    @After fun cleanup() {
        gate?.complete(Unit)
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        runBlocking { lock?.join() }
        worker.cancel()
        db.close()
    }

    private fun content(fresh: Boolean = false, recoverDraft: Boolean = true) {
        compose.activityRule.scenario.onActivity { activity ->
            if (fresh) activity.viewModelStore.clear()
            vm = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = RecurringScheduleViewModel(repository) as T
            })[RecurringScheduleViewModel::class.java]
            activity.setContent {
                var title by rememberSaveable { mutableStateOf("Read") }
                session = rememberUnifiedRecurringSaveSession(
                    active = recoverDraft, restorePendingDraft = recoverDraft,
                    actor = mom, viewModel = vm, store = store,
                    onRestore = { restored = it; title = it.title },
                    onSaved = { saved++ }, onClose = { closed++ }
                )
            }
        }
        waitUntil { session.initialized }
    }

    private fun waitUntil(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        compose.waitForIdle()
    }

    private fun holdDatabase() {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        gate = release
        lock = worker.launch {
            db.withTransaction { entered.countDown(); release.await() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
    }

    @Test fun delayedCreation_rotationAndDismissal_preserveIdentityAndCloseExactlyOnce() {
        content()
        waitUntil { session.formReady }
        holdDatabase()
        val requestId = session.requestId
        compose.runOnIdle {
            session.save(draft, input)
            session.close() // A queued dismissal must not discard the in-flight request.
        }
        assertEquals(0, closed)
        assertEquals(requestId, store.get(mom.id, "f")!!.requestId)
        compose.activityRule.scenario.recreate()
        content()
        compose.runOnIdle {
            assertTrue(session.processing)
            assertFalse(session.formReady)
        }
        gate!!.complete(Unit)
        runBlocking { lock!!.join() }
        waitUntil { saved == 1 }
        assertNull(store.get(mom.id, "f"))
        runBlocking { assertEquals(1, db.recurringScheduleDao().getByFamilyOnce("f").size) }
    }

    @Test fun freshViewModel_committedDraftRequiresExplicitEdit_andPreservesNewerInput() = runBlocking<Unit> {
        assertEquals(RecurringResult.SAVED, repository.saveSchedule(mom, null, input, draft.requestId))
        val schedulesBefore = db.recurringScheduleDao().getByFamilyOnce("f")
        val tasksBefore = db.taskDao().getTasksByFamily("f").first()
        val notificationsBefore = db.notificationDao().getNotificationsByUser(na.id).first()
        val newer = draft.copy(title = "New draft title", points = "35", daysMask = 7)
        // Round-trip only saved Bundle state, without carrying a ViewModel result.
        val bundle: Bundle = newer.toBundle()
        state = SavedStateHandle(mapOf("recurringDraft:3:mom:f" to bundle))
        store = UnifiedRecurringDraftStore(state)
        content(fresh = true)
        waitUntil { session.needsAcknowledgement }
        assertEquals(newer, restored)
        assertFalse(session.formReady)
        compose.runOnIdle {
            session.save(newer, input.copy(title = newer.title, points = 35))
        }
        assertEquals(schedulesBefore, db.recurringScheduleDao().getByFamilyOnce("f"))
        compose.runOnIdle { session.continueEditing() }
        waitUntil { session.formReady }
        compose.runOnIdle { session.save(newer, input.copy(title = newer.title, points = 35)) }
        waitUntil { saved == 1 }
        val result = db.recurringScheduleDao().getByFamilyOnce("f").single()
        assertEquals(schedulesBefore.single().id, result.id)
        assertEquals("New draft title", result.title)
        assertEquals(35, result.points)
        assertEquals(tasksBefore, db.taskDao().getTasksByFamily("f").first())
        assertEquals(notificationsBefore, db.notificationDao().getNotificationsByUser(na.id).first())
    }

    @Test fun persistenceFailure_finishesSaveAndKeepsDraftForRetry() {
        content()
        waitUntil { session.formReady }
        runBlocking {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_create BEFORE INSERT ON recurring_creation_requests BEGIN SELECT RAISE(ABORT, 'test failure'); END"
            )
        }
        compose.runOnIdle { session.save(draft, input) }
        waitUntil { session.result == RecurringResult.PERSISTENCE_ERROR && !session.processing }
        assertNotNull(store.get(mom.id, "f"))
        assertTrue(session.formReady)
        runBlocking {
            assertTrue(db.recurringScheduleDao().getByFamilyOnce("f").isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_create")
        }
        compose.runOnIdle { session.save(draft, input) }
        waitUntil { saved == 1 }
        runBlocking { assertEquals(1, db.recurringScheduleDao().getByFamilyOnce("f").size) }
    }

    @Test fun unknownCommit_lookupFailureAllowsClose_andRetainsRequestForReopening() {
        store.put(mom.id, "f", draft)
        runBlocking { db.openHelper.writableDatabase.execSQL("DROP TABLE recurring_creation_requests") }
        content(fresh = true)
        waitUntil { session.lookupFailed }
        assertFalse(session.formReady)
        compose.runOnIdle { session.close() }
        assertEquals(1, closed)
        assertEquals(draft, store.get(mom.id, "f"))
    }

    @Test fun ordinaryTaskEditDoesNotRestoreOrDiscardARecurringDraft() {
        store.put(mom.id, "f", draft)
        content(recoverDraft = false)
        compose.runOnIdle { session.close() }
        assertNull(restored)
        assertEquals(draft, store.get(mom.id, "f"))
    }

    @Test fun backupIsIsolatedByActorAndFamily_andCanOnlyClearItsOwnRequest() {
        store.put(mom.id, "f", draft)
        assertNull(store.get("dad", "f"))
        assertNull(store.get(mom.id, "another-family"))
        store.clear(mom.id, "f", "different-request")
        assertEquals(draft, store.get(mom.id, "f"))
        store.clear(mom.id, "f", draft.requestId)
        assertNull(store.get(mom.id, "f"))
    }
}
