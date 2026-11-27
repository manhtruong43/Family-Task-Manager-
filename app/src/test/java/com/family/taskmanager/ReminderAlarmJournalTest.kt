package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.family.taskmanager.data.service.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReminderAlarmJournalTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val occurrence = ReminderOccurrence("task", 2_000_000, 4, 1_000_000, "fixed", 2)

    @Before fun reset() {
        context.getSharedPreferences(ReminderAlarmJournal.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun journalRoundTripPreservesInventoryAndExhaustionWithExactScope() {
        val alarm = ReminderAlarmRecord("alarm", ReminderAlarmKind.RETRY, 1_500_000, 3, occurrence, "fixed_30")
        val global = ReminderAlarmRecord(ReminderAlarmJournal.GLOBAL_ID, ReminderAlarmKind.RECONCILE, 1_600_000, 2)
        val state = ReminderAlarmState(mapOf(alarm.id to alarm, global.id to global),
            mapOf(occurrence.id to ReminderAttemptState(occurrence, 3, exhausted = true)), 2)
        ReminderAlarmJournal(context).write(state)
        assertEquals(state, ReminderAlarmJournal(context).read())
    }

    @Test fun occurrenceIdentitySeparatesDeadlineAssignmentLogicalTimeAndExplicitSettingChange() {
        val changed = listOf(
            occurrence.copy(deadline = occurrence.deadline + 1),
            occurrence.copy(generation = occurrence.generation + 1),
            occurrence.copy(dueAt = occurrence.dueAt + 1),
            occurrence.copy(slot = "flex"),
            occurrence.copy(settingRevision = occurrence.settingRevision + 1)
        )
        assertEquals(6, (changed + occurrence).map { it.id }.toSet().size)
        assertEquals(occurrence.id, occurrence.copy().id)
    }

    @Test fun failedCommitIsNotAcknowledgedEvenIfPreferenceMemoryAlreadyChanged() {
        val commits = AtomicInteger()
        val counted = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val raw = super.getSharedPreferences(name, mode)
                if (name != ReminderAlarmJournal.PREFS_NAME) return raw
                return object : SharedPreferences by raw {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = raw.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean {
                                if (commits.incrementAndGet() == 1) {
                                    // Emulate Android's memory-first update on a failed disk commit.
                                    delegate.apply()
                                    return false
                                }
                                return delegate.commit()
                            }
                        }
                    }
                }
            }
        }
        val state = ReminderAlarmState(attempts = mapOf(occurrence.id to
            ReminderAttemptState(occurrence, 3, exhausted = true)))
        try { ReminderAlarmJournal(counted).write(state); fail("Expected failed commit") }
        catch (_: ReminderJournalException) { }
        val restored = ReminderAlarmJournal(counted)
        restored.write(state)
        assertEquals(2, commits.get()) // must not trust the prior failed write's in-memory cache
        assertEquals(state, restored.read())
        restored.write(state)
        assertEquals(2, commits.get()) // an acknowledged unchanged snapshot may skip disk I/O
        ReminderAlarmJournal(counted).write(state)
        assertEquals(3, commits.get()) // a new instance must establish its own durable acknowledgement
        restored.write(state)
        assertEquals(4, commits.get()) // another writer invalidates the cached acknowledgement
    }

    @Test fun unreadableJournalIsNotSilentlyReplacedWithEmptyInventory() {
        val prefs = context.getSharedPreferences(ReminderAlarmJournal.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("state", "{broken-json").commit()
        try { ReminderAlarmJournal(context).read(); fail("Expected invalid journal") }
        catch (_: ReminderJournalException) { }
        assertEquals("{broken-json", prefs.getString("state", null))
    }
}
