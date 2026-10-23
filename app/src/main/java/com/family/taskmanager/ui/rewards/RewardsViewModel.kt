package com.family.taskmanager.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.RewardRedemption
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.repository.RewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.family.taskmanager.data.repository.UserRepository

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)

    val currentUser: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) userRepository.getUser(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCurrentUser(user: User) {
        _currentUserId.value = user.id
    }

    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId
    }

    val rewards: StateFlow<List<Reward>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                rewardRepository.getRewardsByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val redemptions: StateFlow<List<RewardRedemption>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                rewardRepository.getRedemptionsByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createReward(title: String, description: String, pointsRequired: Int) {
        val user = currentUser.value ?: return
        if (title.isBlank() || pointsRequired <= 0) return

        val reward = Reward(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            familyId = user.familyId,
            pointsRequired = pointsRequired
        )
        viewModelScope.launch {
            rewardRepository.createReward(reward)
        }
    }

    fun redeemReward(reward: Reward, onResult: (Boolean) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val success = rewardRepository.redeemReward(reward, user)
            onResult(success)
        }
    }

    fun approveRedemption(redemption: RewardRedemption) {
        viewModelScope.launch {
            rewardRepository.approveRedemption(redemption)
        }
    }
}
