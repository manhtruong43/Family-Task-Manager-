package com.family.taskmanager.ui.calendar

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringCreationLookup
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.RecurringScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Kết quả gắn với một phiên editor, giữ lại qua khoảng trống giữa hai Activity. */
data class RecurringSaveState(
    val requestId: String,
    val result: RecurringResult? = null,
    val cancelled: Boolean = false,
    val operationId: String? = null // Mỗi lần thử khác nhau; không thay mã tạo lịch trong DB.
)

data class RecurringEditorLookupState(
    val requestId: String,
    val lookup: RecurringCreationLookup? = null,
    val failed: Boolean = false
)

data class RecurringSchedulesState(
    val familyId: String? = null,
    val schedules: List<RecurringSchedule> = emptyList(),
    val loaded: Boolean = false,
    val loadFailed: Boolean = false
)

/** Trạng thái và thao tác của phần "Lịch trình cố định" trên màn Lịch. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecurringScheduleViewModel @Inject constructor(
    private val repository: RecurringScheduleRepository
) : ViewModel() {

    private val familyId = MutableStateFlow<String?>(null)

    private val reloadVersion = MutableStateFlow(0)

    fun setFamily(id: String?) {
        if (familyId.value != id) {
            familyId.value = id
            lookupJob?.cancel()
            _editorLookup.value = null
            _saveState.value = null
        }
    }

    fun reloadSchedules() { reloadVersion.value++ }

    // Danh sách và trạng thái tải cùng một giá trị, không suy diễn "đang tải" từ danh sách rỗng.
    val scheduleState: StateFlow<RecurringSchedulesState> = combine(familyId, reloadVersion) { id, _ -> id }
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(RecurringSchedulesState()) else
                repository.observeSchedules(id)
                    .map { RecurringSchedulesState(id, it, loaded = true) }
                    .onStart { emit(RecurringSchedulesState(id)) }
                    .catch { error ->
                        if (error !is SQLiteException) throw error
                        Log.e("RecurringScheduleVM", "Unable to load schedules", error)
                        emit(RecurringSchedulesState(id, loadFailed = true))
                    }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringSchedulesState())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /** Kết quả thao tác (bắn 1 lần) để màn hình hiện Snackbar. */
    private val _results = MutableSharedFlow<RecurringResult>(extraBufferCapacity = 4)
    val results: SharedFlow<RecurringResult> = _results.asSharedFlow()

    private val _saveState = MutableStateFlow<RecurringSaveState?>(null)
    val saveState: StateFlow<RecurringSaveState?> = _saveState.asStateFlow()

    private val _editorLookup = MutableStateFlow<RecurringEditorLookupState?>(null)
    val editorLookup: StateFlow<RecurringEditorLookupState?> = _editorLookup.asStateFlow()
    private var lookupJob: Job? = null

    fun resolveEditor(actor: User?, requestId: String, retry: Boolean = false) {
        actor ?: return
        if (!retry && _editorLookup.value?.requestId == requestId) return
        lookupJob?.cancel()
        _editorLookup.value = RecurringEditorLookupState(requestId)
        lookupJob = viewModelScope.launch {
            try {
                _editorLookup.value = RecurringEditorLookupState(
                    requestId, repository.resolveCreationRequest(actor, requestId)
                )
            } catch (error: SQLiteException) {
                Log.e("RecurringScheduleVM", "Unable to resolve creation request", error)
                _editorLookup.value = RecurringEditorLookupState(requestId, failed = true)
            }
        }
    }

    /** Trả mã lần thử đã nhận để UI khóa ngay, không chờ StateFlow được vẽ lên màn hình. */
    fun save(actor: User?, editingId: String?, input: RecurringScheduleInput, requestId: String): String? {
        actor ?: return null
        val previous = _saveState.value
        if (previous?.requestId == requestId && previous.result == RecurringResult.SAVED) return previous.operationId
        val operationId = UUID.randomUUID().toString()
        return if (runAction(requestId, operationId) {
            repository.saveSchedule(actor, editingId, input, creationRequestId = requestId)
        }) operationId else null
    }

    fun setActive(actor: User?, schedule: RecurringSchedule, active: Boolean) {
        actor ?: return
        runAction { repository.setActive(actor, schedule.id, active) }
    }

    fun delete(actor: User?, schedule: RecurringSchedule) {
        actor ?: return
        runAction { repository.deleteSchedule(actor, schedule.id) }
    }

    private fun runAction(requestId: String? = null, operationId: String? = null, block: suspend () -> RecurringResult): Boolean {
        if (_isProcessing.value) return false
        _isProcessing.value = true
        if (requestId != null) _saveState.value = RecurringSaveState(requestId, operationId = operationId)
        viewModelScope.launch {
            try {
                val result = try {
                    block()
                } catch (error: SQLiteException) {
                    // Lỗi ghi/đọc DB dự kiến phải kết thúc yêu cầu, giữ draft để thử lại.
                    Log.e("RecurringScheduleVM", "Persistence operation failed", error)
                    RecurringResult.PERSISTENCE_ERROR
                }
                if (requestId != null) {
                    _saveState.value = RecurringSaveState(requestId, result, operationId = operationId)
                    if (result == RecurringResult.ALREADY_CREATED) _editorLookup.value = null
                }
                _results.emit(result)
            } catch (error: CancellationException) {
                if (requestId != null) {
                    _saveState.value = RecurringSaveState(requestId, cancelled = true, operationId = operationId)
                    _editorLookup.value = null // Commit có thể đã xảy ra; phải đối chiếu trước khi thử lại.
                }
                throw error
            } finally {
                _isProcessing.value = false
            }
        }
        return true
    }
}
