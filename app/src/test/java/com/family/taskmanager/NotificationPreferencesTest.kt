package com.family.taskmanager

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicInteger
import com.family.taskmanager.data.service.NotificationPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun globalLeadPersistsAndRejectsUnsupportedValues() {
        val preferences = NotificationPreferences(context)
        assertEquals(30, preferences.reminderLeadMinutes.value)
        preferences.setReminderLeadMinutes(120)
        assertEquals(120, NotificationPreferences(context).reminderLeadMinutes.value)
        preferences.setReminderLeadMinutes(13)
        assertEquals(30, preferences.reminderLeadMinutes.value)
    }

    @Test fun explicitLeadRevisionPersistsAndReturningToSameLeadHasANewIdentity() {
        val preferences = NotificationPreferences(context)
        assertEquals(0L, preferences.reminderSettings.value.revision)
        preferences.setReminderLeadMinutes(30) // already selected; do not re-arm
        assertEquals(0L, preferences.reminderSettings.value.revision)
        preferences.setReminderLeadMinutes(60)
        preferences.setReminderLeadMinutes(30)
        val restored = NotificationPreferences(context)
        assertEquals(30, restored.reminderLeadMinutes.value)
        assertEquals(2L, restored.reminderSettings.value.revision)
        assertNotEquals(preferences.reminderSettings.value.copy(revision = 0).receiptKey("fixed_30"),
            restored.reminderSettings.value.receiptKey("fixed_30"))
    }

    private fun memoryFirstFailures(failures: Int): Pair<NotificationPreferences, AtomicInteger> {
        val commits = AtomicInteger()
        val wrapped = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val raw = super.getSharedPreferences(name, mode)
                if (name != NotificationPreferences.PREFS_NAME) return raw
                return object : SharedPreferences by raw {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = raw.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                                delegate.putInt(key, value); return this
                            }
                            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                                delegate.putLong(key, value); return this
                            }
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value); return this
                            }
                            override fun commit(): Boolean {
                                if (commits.incrementAndGet() <= failures) {
                                    delegate.apply() // model Android's memory-first failed commit
                                    return false
                                }
                                return delegate.commit()
                            }
                        }
                    }
                }
            }
        }
        return NotificationPreferences(wrapped) to commits
    }

    @Test fun failedChangeReselectOriginalThenUnrelatedWriteCannotPersistRejectedSetting() {
        val (preferences, commits) = memoryFirstFailures(2)
        assertFalse(preferences.setReminderLeadMinutes(60)) // proposed write and rollback both report failure
        assertEquals(30, preferences.reminderLeadMinutes.value)
        assertEquals(0L, preferences.reminderSettings.value.revision)
        assertTrue(preferences.setReminderLeadMinutes(30)) // dirty must bypass the equality shortcut
        assertEquals(3, commits.get())
        preferences.setEnabled(false)
        val recreated = NotificationPreferences(context)
        assertEquals(30, recreated.reminderLeadMinutes.value)
        assertEquals(0L, recreated.reminderSettings.value.revision)
        assertFalse(recreated.isEnabled)
    }

    @Test fun unrelatedWriteBeforeReselectionAlsoKeepsLastAcknowledgedSetting() {
        val (preferences, _) = memoryFirstFailures(2)
        assertFalse(preferences.setReminderLeadMinutes(120))
        preferences.setEnabled(false)
        preferences.permissionAsked = true
        val recreated = NotificationPreferences(context)
        assertEquals(30, recreated.reminderLeadMinutes.value)
        assertEquals(0L, recreated.reminderSettings.value.revision)
        assertFalse(recreated.isEnabled)
        assertTrue(recreated.permissionAsked)
    }

    @Test fun retryingRejectedChoicePublishesOnlyOneAcceptedRevision() {
        val (preferences, _) = memoryFirstFailures(1)
        assertFalse(preferences.setReminderLeadMinutes(60))
        assertEquals(30, preferences.reminderSettings.value.leadMinutes)
        assertTrue(preferences.setReminderLeadMinutes(60))
        assertEquals(60, preferences.reminderSettings.value.leadMinutes)
        assertEquals(1L, preferences.reminderSettings.value.revision)
        assertEquals(preferences.reminderSettings.value, NotificationPreferences(context).reminderSettings.value)
    }

    @Test fun consumedStagesSurviveRestartButDoNotLeakAcrossDeadlines() {
        val preferences = NotificationPreferences(context)
        preferences.markRemindedStages("t", 123, setOf("flex_mid_week", "flex_early_pace"))
        val restored = NotificationPreferences(context)
        assertTrue(restored.wasReminded("t", 123, "flex_mid_week"))
        assertTrue(restored.wasReminded("t", 123, "flex_early_pace"))
        assertFalse(restored.wasReminded("t", 1234, "flex_mid_week"))
        restored.markReminded("t", 1234, "flex_final_day")
        assertFalse(restored.wasReminded("t", 123, "flex_mid_week"))
        assertTrue(restored.wasReminded("t", 1234, "flex_final_day"))
    }

    @Test fun indexedLegacySnapshotPreservesLastCallAndOldProgressFormats() {
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("reminded_tasks", setOf(
                "t@123#flex_last_call_c0_l30", "t@123#flex_mid_week_c2",
                "t@123#fixed_60", "old@123", "bad@no_timestamp#overdue"
            )).commit()
        val snapshot = NotificationPreferences(context).legacyReminderSnapshot()
        assertEquals(setOf("flex_last_call", "flex_mid_week", "fixed_60"), snapshot.consumedStages("t", 123))
        assertEquals(8, snapshot.consumedStages("old", 123).size)
        assertTrue(snapshot.consumedStages("t", 124).isEmpty())
        assertTrue(snapshot.consumedStages("bad", 123).isEmpty())
    }

    @Test fun legacyOverdueCountersKeepTaskAndDeadlineScope_andRejectMalformedSuffixes() {
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("reminded_tasks", setOf(
                "t@123#overdue_c0", "t@124#overdue_c12", "other@123#overdue_c2",
                "bad@123#overdue_cx", "bad@124#overdue_c-1", "bad@125#overdue_c2junk"
            )).commit()
        val preferences = NotificationPreferences(context)
        val snapshot = preferences.legacyReminderSnapshot()
        assertEquals(setOf("overdue"), snapshot.consumedStages("t", 123))
        assertEquals(setOf("overdue"), snapshot.consumedStages("t", 124))
        assertEquals(setOf("overdue"), snapshot.consumedStages("other", 123))
        assertTrue(snapshot.consumedStages("t", 125).isEmpty())
        assertTrue((123L..125L).all { snapshot.consumedStages("bad", it).isEmpty() })
        assertTrue(preferences.wasReminded("t", 123, "overdue"))
        assertFalse(preferences.wasReminded("t", 123, "flex_last_call"))
    }

    @Test fun upgradeDoesNotResendLegacyProgressStagesOrPlainReminders() {
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("reminded_tasks", setOf("t@123#flex_mid_week_c0", "old@123"))
            .commit()
        val preferences = NotificationPreferences(context)
        assertTrue(preferences.wasReminded("t", 123, "flex_mid_week"))
        assertTrue(preferences.wasReminded("old", 123, "fixed_120"))
        assertFalse(preferences.wasReminded("t", 123, "flex_final_day"))
    }
}
