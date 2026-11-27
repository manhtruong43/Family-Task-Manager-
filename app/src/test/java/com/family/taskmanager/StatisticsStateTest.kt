package com.family.taskmanager

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import com.family.taskmanager.data.model.Task
import com.family.taskmanager.data.model.TaskStatus
import java.util.Locale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.family.taskmanager.data.model.TaskApproval
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.statistics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StatisticsStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var now = 1_800_000_000_000L
    private var taskCount = -1
    private var earned = -1

    private fun show(days: Int, created: Long) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val cutoff = rememberRollingStatisticsCutoff(days, listOf(created)) { now }
                val visible = created >= requireNotNull(cutoff)
                SideEffect { taskCount = if (visible) 1 else 0; earned = if (visible) 10 else 0 }
            }
        }
        compose.waitForIdle()
        assertEquals(1, taskCount)
        assertEquals(10, earned)
    }

    private fun assertBoundary(days: Int) {
        val created = now - days * STATISTICS_DAY_MS + 10L
        show(days, created)
        now += 11L
        compose.mainClock.advanceTimeBy(32)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        compose.waitForIdle()
        assertEquals(0, taskCount)
        assertEquals(0, earned)
    }

    @Test fun weekExpiresWithoutPeriodChangeOrDatabaseUpdate() = assertBoundary(7)
    @Test fun monthExpiresWithoutPeriodChangeOrDatabaseUpdate() = assertBoundary(30)

    @Test fun resumeRefreshesBothSourcesWithoutChangingPeriod() {
        val created = now - 7 * STATISTICS_DAY_MS + 10L
        show(7, created)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        now += STATISTICS_DAY_MS
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(0, taskCount)
        assertEquals(0, earned)
    }

    @Test fun historyDisplaysPerWorkerPointsAndLabelsCombinedAmountAsTaskTotal() {
        val task = Task("t", "Read", familyId = "f", createdByUserId = "mom",
            assignedToUserId = "bin", assignedToUserName = "Bin", status = TaskStatus.COMPLETED)
        val rows = listOf(
            TaskApproval("t", 1, "f", "na", 10, 1, 2, workerName = "Na"),
            TaskApproval("t", 2, "f", "bin", 30, 1, 3, workerName = "Bin")
        )
        compose.setContent {
            MaterialTheme { HistoryRow(task, Locale.US, earningsByTask(rows).getValue("t")) }
        }
        compose.onNodeWithText("Na: +10⭐").assertExists()
        compose.onNodeWithText("Bin: +30⭐").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.stat_task_total_points, 40)).assertExists()
        compose.onNodeWithText("+40⭐", substring = false).assertDoesNotExist()
    }

    @Test fun legacyUnattributedPointsAreDisplayedSeparatelyFromFinalWorker() {
        val task = Task("t", "Read", familyId = "f", createdByUserId = "mom",
            assignedToUserId = "bin", assignedToUserName = "Bin", status = TaskStatus.COMPLETED)
        val rows = listOf(
            TaskApproval("t", -1, "f", "", 17, 1, 2, legacyEstimate = true),
            TaskApproval("t", 2, "f", "bin", 30, 1, 3, workerName = "Bin")
        )
        compose.setContent {
            MaterialTheme { HistoryRow(task, Locale.US, earningsByTask(rows).getValue("t")) }
        }
        val label = compose.activity.getString(R.string.stat_unknown_worker)
        compose.onNodeWithText("$label: ~+17⭐").assertExists()
        compose.onNodeWithText("Bin: +30⭐").assertExists()
    }

    @Test fun earnedChartIncludesDepartedWorkersAndUnattributedAwardsWithoutCompletedTasks() {
        val members = listOf(User("mom", "Mom", "m@test"), User("bin", "Bin", "b@test", points = 80))
        val awards = listOf(
            TaskApproval("cancelled", 1, "f", "na", 10, 1, 2, workerName = "Na"),
            TaskApproval("deleted-legacy", -1, "f", "", 17, 1, 1, legacyEstimate = true)
        )
        val entries = earnedPointEntries(members, awards)
        assertEquals(setOf("mom", "bin", "na", ""), entries.map { it.userId }.toSet())
        assertEquals(10, entries.single { it.userId == "na" }.pointsEarned)
        assertFalse(entries.single { it.userId == "na" }.isCurrentMember)
        assertEquals(0, entries.single { it.userId == "bin" }.pointsEarned) // balance is not earned points
        assertEquals(2, entries.count { it.isCurrentMember })
        compose.setContent { MaterialTheme { EarnedPointsChart(entries) } }
        compose.onNodeWithText("Na").assertExists()
        compose.onNodeWithText("+10⭐").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.stat_departed_member)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.stat_unknown_worker)).assertExists()
        compose.onNodeWithText("~+17⭐").assertExists()
        compose.onNodeWithText("+80⭐").assertDoesNotExist()
    }

    @Test fun equalScoreWorkerMovingRowsAfterRollingExpiry_keepsPositiveBar() {
        val awards = listOf(
            TaskApproval("na-task", 1, "f", "na", 10, now - 7 * STATISTICS_DAY_MS + 10, now, workerName = "Na"),
            TaskApproval("bin-task", 1, "f", "bin", 10, now - STATISTICS_DAY_MS, now, workerName = "Bin")
        )
        compose.setContent {
            val cutoff = rememberRollingStatisticsCutoff(7, awards.map { it.taskCreatedAt }) { now }
            val visible = awards.filter { it.taskCreatedAt >= requireNotNull(cutoff) }
            MaterialTheme { EarnedPointsChart(earnedPointEntries(emptyList(), visible)) }
        }
        val fullBar = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(1f, 0f..1f))
        compose.onAllNodes(fullBar, useUnmergedTree = true).assertCountEquals(2)
        now += 11
        compose.mainClock.advanceTimeBy(32)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        compose.waitForIdle()
        compose.onNodeWithText("Na").assertDoesNotExist()
        compose.onNodeWithText("Bin").assertExists()
        compose.onNodeWithText("+10⭐").assertExists()
        compose.onAllNodes(fullBar, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun earnedEntriesUseCurrentNamesAndLatestRetainedNamePerWorker() {
        val current = User("bin", "Bin now", "b@test", avatarUrl = "🐱")
        val awards = listOf(
            TaskApproval("a", 1, "f", "bin", 20, 1, 1, workerName = "Bin old"),
            TaskApproval("b", 1, "f", "na", 10, 1, 1, workerName = "Na old"),
            TaskApproval("b", 2, "f", "na", 10, 1, 2, workerName = "Na new")
        )
        val entries = earnedPointEntries(listOf(current), awards)
        assertEquals(40, entries.sumOf { it.pointsEarned })
        assertEquals("Bin now", entries.single { it.userId == "bin" }.name)
        assertEquals("🐱", entries.single { it.userId == "bin" }.avatarUrl)
        assertEquals("Na new", entries.single { it.userId == "na" }.name)
    }

    @Test fun workerTotalsKeepDepartedAndUnattributedAwardsSeparate() {
        val rows = listOf(
            TaskApproval("t", 1, "f", "na", 10, 1, 2, workerName = "Na"),
            TaskApproval("t", 2, "f", "bin", 10, 1, 3, workerName = "Bin"),
            TaskApproval("t", 3, "f", "bin", 20, 1, 4, workerName = "Bin"),
            TaskApproval("t", -1, "f", "", 17, 1, 1, legacyEstimate = true)
        )
        val workers = earningsByTask(rows).getValue("t")
        assertEquals(10, workers.single { it.userId == "na" }.points)
        assertEquals("Na", workers.single { it.userId == "na" }.name)
        assertEquals(30, workers.single { it.userId == "bin" }.points)
        assertEquals(17, workers.single { it.userId.isBlank() }.points)
        assertTrue(workers.single { it.userId.isBlank() }.estimated)
    }
}
