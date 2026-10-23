package com.family.taskmanager.ui.rewards

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.taskmanager.data.model.RedemptionStatus
import com.family.taskmanager.data.model.Reward
import com.family.taskmanager.data.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val rewards by viewModel.rewards.collectAsState()
    val redemptions by viewModel.redemptions.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    var newRewardTitle by remember { mutableStateOf("") }
    var newRewardPoints by remember { mutableStateOf("50") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cửa hàng Phần thưởng") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Danh sách phần thưởng") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Lịch sử đổi thưởng") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (currentUser?.role == UserRole.ADMIN) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Tạo phần thưởng mới (Dành cho Admin)", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newRewardTitle,
                                        onValueChange = { newRewardTitle = it },
                                        label = { Text("Tên quà (vd: Xem phim cuối tuần)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newRewardPoints,
                                        onValueChange = { newRewardPoints = it.filter { c -> c.isDigit() } },
                                        label = { Text("Số điểm cần đổi (vd: 50)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.createReward(
                                                newRewardTitle,
                                                "",
                                                newRewardPoints.toIntOrNull() ?: 50
                                            )
                                            newRewardTitle = ""
                                        },
                                        enabled = newRewardTitle.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Thêm Phần thưởng")
                                    }
                                }
                            }
                        }
                    }

                    items(rewards, key = { it.id }) { reward ->
                        RewardItemCard(
                            reward = reward,
                            userPoints = currentUser?.points ?: 0,
                            onRedeem = {
                                viewModel.redeemReward(reward) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Đã gửi yêu cầu đổi quà!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Không đủ điểm tích lũy!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(redemptions, key = { it.id }) { redemption ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(redemption.rewardTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Người đổi: ${redemption.userName} (-${redemption.pointsUsed} điểm)", fontSize = 13.sp)
                                }

                                if (currentUser?.role == UserRole.ADMIN && redemption.status == RedemptionStatus.PENDING) {
                                    Button(
                                        onClick = { viewModel.approveRedemption(redemption) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                                    ) {
                                        Text("Trao Quà")
                                    }
                                } else {
                                    Text(
                                        text = if (redemption.status == RedemptionStatus.APPROVED) "Đã trao" else "Chờ trao",
                                        fontWeight = FontWeight.Bold,
                                        color = if (redemption.status == RedemptionStatus.APPROVED) Color(0xFF16A34A) else Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RewardItemCard(
    reward: Reward,
    userPoints: Int,
    onRedeem: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reward.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Giá đổi: ${reward.pointsRequired} điểm", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onRedeem,
                enabled = userPoints >= reward.pointsRequired
            ) {
                Text("Đổi Quà")
            }
        }
    }
}
