package com.family.taskmanager

import com.family.taskmanager.data.model.TaskApproval
import com.family.taskmanager.data.model.User
import com.family.taskmanager.ui.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class FamilyApprovalStateTest {
    private val userA = User("account-a", "Mom A", "a@test", familyId = "family-a")
    private val userB = User("account-b", "Mom B", "b@test", familyId = "family-b")
    private val awardA = TaskApproval("task-a", 1, "family-a", "na", 10, 1, 2, workerName = "Na A")
    private val awardB = TaskApproval("task-b", 1, "family-b", "bin", 20, 1, 2, workerName = "Bin B")

    @Test fun stoppedCollection_accountSwitchAndDelayedQueryNeverReplayPreviousFamily() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val accountId = MutableStateFlow<String?>(userA.id)
        val currentUser = MutableStateFlow<User?>(userA)
        val releaseB = CompletableDeferred<Unit>()
        val bQueryStarted = CompletableDeferred<Unit>()
        val state = familyApprovalState(accountId, currentUser, scope) { family ->
            if (family == userA.familyId) flowOf(listOf(awardA))
            else flow { bQueryStarted.complete(Unit); releaseB.await(); emit(listOf(awardB)) }
        }
        try {
            val firstCollector = scope.launch { state.collect() }
            withTimeout(5_000) { state.first { it.approvals == listOf(awardA) } }
            firstCollector.cancelAndJoin()
            // Production WhileSubscribed timeout, followed by replay expiration.
            delay(5_100)
            withTimeout(5_000) { while (state.value.owner != null) delay(10) }
            accountId.value = userB.id
            currentUser.value = userB
            val ownerB = approvalOwner(userB.id, userB)
            val displayed = CopyOnWriteArrayList<List<TaskApproval>>()
            val secondCollector = scope.launch { state.collect { displayed.add(it.visibleTo(ownerB)) } }
            withTimeout(5_000) { bQueryStarted.await() }
            assertTrue(state.value.visibleTo(ownerB).isEmpty())
            assertTrue(displayed.all { rows -> rows.none { it.familyId == userA.familyId } })
            releaseB.complete(Unit)
            withTimeout(5_000) { state.first { it.visibleTo(ownerB) == listOf(awardB) } }
            secondCollector.cancelAndJoin()
            assertTrue(displayed.all { rows -> rows.none { it.familyId == userA.familyId } })
        } finally { scope.cancel() }
    }

    @Test fun retainedReplayIsHiddenImmediatelyOnAccountOrFamilyMismatch() {
        val ownerA = requireNotNull(approvalOwner(userA.id, userA))
        val retained = FamilyApprovalState(ownerA, listOf(awardA))
        assertEquals(listOf(awardA), retained.visibleTo(ownerA))
        assertNull(approvalOwner(userB.id, userA)) // user query still replays the old account
        assertTrue(retained.visibleTo(approvalOwner(userB.id, userA)).isEmpty())
        assertTrue(retained.visibleTo(approvalOwner(userB.id, userB)).isEmpty())
        assertTrue(retained.visibleTo(FamilyApprovalOwner("another-account", userA.familyId)).isEmpty())
        assertTrue(retained.visibleTo(FamilyApprovalOwner(userA.id, "new-family")).isEmpty())
        assertTrue(retained.visibleTo(null).isEmpty())
    }

    @Test fun receiptOwnershipIsCheckedEvenWhenTheQueryStateHasTheExpectedOwner() {
        val ownerB = requireNotNull(approvalOwner(userB.id, userB))
        assertEquals(listOf(awardB), FamilyApprovalState(ownerB, listOf(awardA, awardB)).visibleTo(ownerB))
    }
}
