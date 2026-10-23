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

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val rewardRepository: RewardRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun setCurrentUser(user: User) {
        _currentUser.value = user
    }

    val rewards: StateFlow<List<Reward>> = _currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                rewardRepository.getRewardsByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val redemptions: StateFlow<List<RewardRedemption>> = _currentUser
        .flatMapLatest { user ->
            if (user != null && user.familyId.isNotEmpty()) {
                rewardRepository.getRedemptionsByFamily(user.familyId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createReward(title: String, description: String, pointsRequired: Int) {
        val user = _currentUser.value ?: return
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
        val user = _currentUser.value ?: return
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
