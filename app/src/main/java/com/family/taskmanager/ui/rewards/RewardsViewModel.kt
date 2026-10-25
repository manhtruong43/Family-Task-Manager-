package com.family.taskmanager.ui.rewards

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.R
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import com.family.taskmanager.data.repository.RedeemResult
import com.family.taskmanager.data.repository.ReviewResult
import com.family.taskmanager.data.repository.RewardRepository
import com.family.taskmanager.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thông điệp ngắn hiển thị bằng Toast. */
data class RewardMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // User do MainActivity truyền vào (có thể là bản cũ, điểm chưa cập nhật)
    private val sessionUser = MutableStateFlow<User?>(null)

    fun setCurrentUser(user: User) {
        if (sessionUser.value?.id != user.id) sessionUser.value = user
    }

    /** User luôn được đọc lại từ DB -> điểm số, vai trò luôn mới nhất. */
    val currentUser: StateFlow<User?> = sessionUser
        .flatMapLatest { session ->
            if (session == null) flowOf(null)
            else userRepository.getUser(session.id).map { it ?: session }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rewards: StateFlow<List<Reward>> = currentUser
        .map { it?.familyId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { familyId ->
            if (familyId.isEmpty()) flowOf(emptyList())
            else rewardRepository.getRewardsByFamily(familyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Admin thấy lịch sử của cả nhà; Thành viên chỉ thấy lịch sử của chính mình (quyền riêng tư). */
    val redemptions: StateFlow<List<RewardRedemption>> = currentUser
        .map { user -> user?.let { Triple(it.id, it.familyId, it.role) } }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            when {
                key == null || key.second.isEmpty() -> flowOf(emptyList())
                key.third == UserRole.ADMIN -> rewardRepository.getRedemptionsByFamily(key.second)
                else -> rewardRepository.getRedemptionsByUser(key.first)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = redemptions
        .map { list -> list.count { it.status == RedemptionStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _messages = MutableSharedFlow<RewardMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<RewardMessage> = _messages.asSharedFlow()

    private fun runAction(block: suspend () -> Unit) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.emit(RewardMessage(R.string.rw_msg_error))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ─────────────── Admin: quản lý phần thưởng ───────────────

    fun createReward(title: String, description: String, pointsRequired: Int) {
        val user = currentUser.value ?: return
        runAction {
            val ok = rewardRepository.createReward(user, title, description, pointsRequired)
            _messages.emit(RewardMessage(if (ok) R.string.rw_msg_created else R.string.rw_msg_invalid))
        }
    }

    fun updateReward(reward: Reward, title: String, description: String, pointsRequired: Int) {
        val user = currentUser.value ?: return
        runAction {
            val ok = rewardRepository.updateReward(user, reward, title, description, pointsRequired)
            _messages.emit(RewardMessage(if (ok) R.string.rw_msg_updated else R.string.rw_msg_invalid))
        }
    }

    fun deleteReward(reward: Reward) {
        val user = currentUser.value ?: return
        runAction {
            val ok = rewardRepository.deleteReward(user, reward)
            _messages.emit(RewardMessage(if (ok) R.string.rw_msg_deleted else R.string.rw_msg_not_allowed))
        }
    }

    // ─────────────── Thành viên: đổi quà ───────────────

    fun redeemReward(reward: Reward) {
        val user = currentUser.value ?: return
        runAction {
            val message = when (rewardRepository.redeemReward(reward.id, user.id)) {
                is RedeemResult.Success -> RewardMessage(R.string.rw_msg_redeem_success, listOf(reward.title))
                RedeemResult.NotEnoughPoints -> RewardMessage(R.string.rw_msg_not_enough_points)
                RedeemResult.NotAllowed -> RewardMessage(R.string.rw_msg_not_allowed)
                RedeemResult.RewardNotFound -> RewardMessage(R.string.rw_msg_reward_not_found)
            }
            _messages.emit(message)
        }
    }

    // ─────────────── Admin: duyệt yêu cầu ───────────────

    fun approveRedemption(redemption: RewardRedemption) {
        val user = currentUser.value ?: return
        runAction {
            val result = rewardRepository.approveRedemption(redemption.id, user.id)
            _messages.emit(reviewMessage(result, R.string.rw_msg_approved))
        }
    }

    fun rejectRedemption(redemption: RewardRedemption) {
        val user = currentUser.value ?: return
        runAction {
            val result = rewardRepository.rejectRedemption(redemption.id, user.id)
            _messages.emit(reviewMessage(result, R.string.rw_msg_rejected))
        }
    }

    private fun reviewMessage(result: ReviewResult, @StringRes successRes: Int): RewardMessage =
        when (result) {
            ReviewResult.SUCCESS -> RewardMessage(successRes)
            ReviewResult.ALREADY_HANDLED -> RewardMessage(R.string.rw_msg_already_handled)
            ReviewResult.NOT_ALLOWED -> RewardMessage(R.string.rw_msg_not_allowed)
            ReviewResult.NOT_FOUND -> RewardMessage(R.string.rw_msg_error)
        }
}
