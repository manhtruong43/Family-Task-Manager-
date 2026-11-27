package com.family.taskmanager.ui.home

import com.family.taskmanager.data.model.TaskApproval
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal data class FamilyApprovalOwner(val accountId: String, val familyId: String)

internal fun approvalOwner(accountId: String?, user: User?): FamilyApprovalOwner? =
    if (!accountId.isNullOrBlank() && user?.id == accountId && user.familyId.isNotBlank())
        FamilyApprovalOwner(accountId, user.familyId) else null

internal data class FamilyApprovalState(
    val owner: FamilyApprovalOwner? = null,
    val approvals: List<TaskApproval> = emptyList()
) {
    /** Không dùng replay của tài khoản/gia đình trước, kể cả trong lúc truy vấn mới còn đang chờ. */
    fun visibleTo(currentOwner: FamilyApprovalOwner?): List<TaskApproval> =
        if (currentOwner == null || owner != currentOwner) emptyList()
        else approvals.filter { it.familyId == currentOwner.familyId }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun familyApprovalState(
    accountId: StateFlow<String?>,
    currentUser: StateFlow<User?>,
    scope: CoroutineScope,
    load: (String) -> Flow<List<TaskApproval>>
): StateFlow<FamilyApprovalState> =
    combine(accountId, currentUser, ::approvalOwner)
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) flowOf(FamilyApprovalState())
            else load(owner.familyId)
                .map { FamilyApprovalState(owner, it) }
                .onStart { emit(FamilyApprovalState(owner)) }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
            FamilyApprovalState()
        )
