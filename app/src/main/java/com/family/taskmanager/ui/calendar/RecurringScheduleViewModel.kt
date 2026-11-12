package com.family.taskmanager.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.RecurringSchedule
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.RecurringResult
import com.family.taskmanager.data.repository.RecurringScheduleInput
import com.family.taskmanager.data.repository.RecurringScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kết quả gắn với một phiên editor, giữ lại qua khoảng trống giữa hai Activity. */
data class RecurringSaveState(val requestId: String, val result: RecurringResult? = null)

/** Trạng thái và thao tác của phần "Lịch trình cố định" trên màn Lịch. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecurringScheduleViewModel @Inject constructor(
    private val repository: RecurringScheduleRepository
) : ViewModel() {

    private val familyId = MutableStateFlow<String?>(null)

    fun setFamily(id: String?) {
        if (familyId.value != id) familyId.value = id
    }

    val schedules: StateFlow<List<RecurringSchedule>> = familyId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList()) else repository.observeSchedules(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /** Kết quả thao tác (bắn 1 lần) để màn hình hiện Snackbar. */
    private val _results = MutableSharedFlow<RecurringResult>(extraBufferCapacity = 4)
    val results: SharedFlow<RecurringResult> = _results.asSharedFlow()

    private val _saveState = MutableStateFlow<RecurringSaveState?>(null)
    val saveState: StateFlow<RecurringSaveState?> = _saveState.asStateFlow()

    fun save(actor: User?, editingId: String?, input: RecurringScheduleInput, requestId: String) {
        actor ?: return
        // Giữ kết quả thành công để cả UI chưa kịp đóng cũng không tạo lại cùng yêu cầu.
        if (_saveState.value == RecurringSaveState(requestId, RecurringResult.SAVED)) return
        runAction {
            _saveState.value = RecurringSaveState(requestId)
            val result = repository.saveSchedule(actor, editingId, input)
            _saveState.value = RecurringSaveState(requestId, result)
            result
        }
    }

    fun setActive(actor: User?, schedule: RecurringSchedule, active: Boolean) {
        actor ?: return
        runAction { repository.setActive(actor, schedule.id, active) }
    }

    fun delete(actor: User?, schedule: RecurringSchedule) {
        actor ?: return
        runAction { repository.deleteSchedule(actor, schedule.id) }
    }

    private fun runAction(block: suspend () -> RecurringResult) {
        if (_isProcessing.value) return
        // Khóa ngay khi nhận thao tác, trước khi coroutine có cơ hội nhường luồng.
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                _results.emit(block())
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
