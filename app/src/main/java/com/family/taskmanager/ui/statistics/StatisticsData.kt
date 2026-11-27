package com.family.taskmanager.ui.statistics

import com.family.taskmanager.data.model.TaskApproval
import com.family.taskmanager.data.model.User

internal data class WorkerEarnings(
    val userId: String,
    val name: String,
    val points: Int,
    val estimated: Boolean
)

/** Không gán điểm của người rời nhóm hay điểm chưa xác định cho người nhận cuối. */
internal fun earningsByTask(approvals: List<TaskApproval>): Map<String, List<WorkerEarnings>> =
    approvals.groupBy { it.taskId }.mapValues { (_, taskRounds) ->
        taskRounds.groupBy { it.userId }.map { (userId, rounds) ->
            WorkerEarnings(
                userId = userId,
                name = rounds.filter { it.workerName.isNotBlank() }.maxByOrNull { it.approvedAt }?.workerName.orEmpty(),
                points = rounds.sumOf { it.points },
                estimated = rounds.any { it.legacyEstimate }
            )
        }
    }

internal data class EarnedPointEntry(
    val userId: String,
    val name: String,
    val avatarUrl: String,
    val pointsEarned: Int,
    val isCurrentMember: Boolean,
    val estimated: Boolean
)

/** Biểu đồ điểm đã nhận gồm cả người rời nhóm và điểm cũ chưa xác định người làm. */
internal fun earnedPointEntries(members: List<User>, approvals: List<TaskApproval>): List<EarnedPointEntry> {
    val current = members.associateBy { it.id }
    val awards = approvals.groupBy { it.userId }
    return (current.keys + awards.keys).map { userId ->
        val user = current[userId]
        val rounds = awards[userId].orEmpty()
        EarnedPointEntry(
            userId = userId,
            name = user?.name ?: rounds.filter { it.workerName.isNotBlank() }
                .maxByOrNull { it.approvedAt }?.workerName.orEmpty(),
            avatarUrl = user?.avatarUrl ?: if (userId.isBlank()) "❔" else "👤",
            pointsEarned = rounds.sumOf { it.points },
            isCurrentMember = user != null,
            estimated = rounds.any { it.legacyEstimate }
        )
    }.sortedByDescending { it.pointsEarned }
}

internal const val STATISTICS_DAY_MS = 86_400_000L

internal fun statisticsCutoff(now: Long, days: Int?): Long? = days?.let { now - it * STATISTICS_DAY_MS }

/** Thức đúng lúc bản ghi hết cửa sổ; tối đa một phút để phát hiện thay đổi đồng hồ hệ thống. */
internal fun nextStatisticsRefresh(now: Long, days: Int, createdAt: List<Long>): Long {
    val duration = days * STATISTICS_DAY_MS
    val nextExpiry = createdAt.asSequence().map { it + duration + 1L }.filter { it > now }.minOrNull()
    return minOf(60_000L, nextExpiry?.minus(now) ?: 60_000L).coerceAtLeast(1L)
}
