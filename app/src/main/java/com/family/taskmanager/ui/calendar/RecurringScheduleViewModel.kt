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

    fun save(actor: User?, editingId: String?, input: RecurringScheduleInput, onSaved: () -> Unit) {
        actor ?: return
        runAction {
            val result = repository.saveSchedule(actor, editingId, input)
            if (result == RecurringResult.SAVED) onSaved()
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
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                _results.emit(block())
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
