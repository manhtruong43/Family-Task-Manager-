package com.family.taskmanager.ui.task

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.CreationRequestStatus
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.ui.calendar.RecurringScheduleViewModel
import java.util.UUID

/** Retained completion and persisted creation identity for the unified creation form. */
internal data class UnifiedRecurringSaveSession(
    val requestId: String,
    val editingId: String?,
    val initialized: Boolean,
    val processing: Boolean,
    val formReady: Boolean,
    val needsAcknowledgement: Boolean,
    val lookupFailed: Boolean,
    val missing: Boolean,
    val result: RecurringResult?,
    val initial: com.family.taskmanager.data.model.RecurringSchedule?,
    val continueEditing: () -> Unit,
    val retry: () -> Unit,
    val save: (UnifiedRecurringDraft, RecurringScheduleInput) -> Unit,
    val close: () -> Unit
)

@Composable
internal fun rememberUnifiedRecurringSaveSession(
    active: Boolean,
    restorePendingDraft: Boolean = true,
    actor: User?,
    viewModel: RecurringScheduleViewModel,
    store: UnifiedRecurringDraftStore,
    onRestore: (UnifiedRecurringDraft) -> Unit,
    onSaved: () -> Unit,
    onClose: () -> Unit
): UnifiedRecurringSaveSession {
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var actionResult by rememberSaveable { mutableStateOf<RecurringResult?>(null) }
    val saveState by viewModel.saveState.collectAsState()
    val processing by viewModel.isProcessing.collectAsState()
    val lookupState by viewModel.editorLookup.collectAsState()
    val restore by rememberUpdatedState(onRestore)
    val saved by rememberUpdatedState(onSaved)
    val closeScreen by rememberUpdatedState(onClose)

    LaunchedEffect(actor?.id, actor?.familyId, restorePendingDraft) {
        initialized = false
        if (actor != null) {
            val backup = if (restorePendingDraft) store.get(actor.id, actor.familyId) else null
            if (backup != null && requestId != backup.requestId) {
                requestId = backup.requestId
                editingId = backup.editingId
                restore(backup)
            }
            initialized = true
        }
    }

    LaunchedEffect(active, initialized, actor?.id, actor?.familyId, requestId) {
        if (active && initialized) viewModel.resolveEditor(actor, requestId)
    }

    LaunchedEffect(saveState, pendingOperation, processing, initialized) {
        val operation = pendingOperation ?: return@LaunchedEffect
        val matching = saveState?.takeIf { it.requestId == requestId && it.operationId == operation }
        when {
            matching?.result == RecurringResult.SAVED -> {
                actor?.let { store.clear(it.id, it.familyId, requestId) }
                pendingOperation = null
                saved()
            }
            matching?.result != null || matching?.cancelled == true -> {
                pendingOperation = null
                actionResult = matching.result
                if (matching.cancelled || matching.result == RecurringResult.ALREADY_CREATED) {
                    acknowledged = false
                    viewModel.resolveEditor(actor, requestId, retry = true)
                }
            }
            initialized && matching == null && !processing -> {
                // Fresh ViewModel: an interrupted edit must be reviewed, never replayed automatically.
                pendingOperation = null
                acknowledged = false
                viewModel.resolveEditor(actor, requestId, retry = true)
            }
        }
    }

    val lookup = lookupState?.takeIf { it.requestId == requestId }
    val status = lookup?.lookup?.status
    val needsAcknowledgement = status == CreationRequestStatus.COMMITTED && !acknowledged
    val missing = status == CreationRequestStatus.NOT_FOUND || status == CreationRequestStatus.NOT_ALLOWED
    val busy = processing || pendingOperation != null
    val ready = initialized && !busy && when (status) {
        CreationRequestStatus.NEW -> editingId == null
        CreationRequestStatus.COMMITTED -> acknowledged && editingId != null
        else -> false
    }

    return UnifiedRecurringSaveSession(
        requestId = requestId,
        editingId = editingId,
        initialized = initialized,
        processing = busy,
        formReady = ready,
        needsAcknowledgement = needsAcknowledgement,
        lookupFailed = lookup?.failed == true,
        missing = missing,
        result = actionResult,
        initial = lookup?.lookup?.schedule,
        continueEditing = {
            if (!busy && status == CreationRequestStatus.COMMITTED) {
                editingId = lookup?.lookup?.schedule?.id
                acknowledged = true
                actionResult = null
            }
        },
        retry = { if (!busy) viewModel.resolveEditor(actor, requestId, retry = true) },
        save = { draft, input ->
            // Guards read current retained state as well as the composition snapshot.
            if (ready && !viewModel.isProcessing.value && actor != null) {
                store.put(actor.id, actor.familyId, draft.copy(requestId = requestId, editingId = editingId))
                val accepted = viewModel.save(actor, editingId, input, requestId)
                if (accepted != null) {
                    pendingOperation = accepted
                    actionResult = null
                }
            }
        },
        close = {
            if (!viewModel.isProcessing.value && pendingOperation == null) {
                // An unresolved storage failure keeps its draft outside this dismissible route.
                if (status == CreationRequestStatus.NEW || status == CreationRequestStatus.COMMITTED || missing) {
                    actor?.let { store.clear(it.id, it.familyId, requestId) }
                }
                closeScreen()
            }
        }
    )
}
