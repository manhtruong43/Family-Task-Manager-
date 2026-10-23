package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_reactions")
data class TaskReaction(
    @PrimaryKey val id: String,
    val taskId: String,
    val userId: String,
    val userName: String,
    val emoji: String, // "👍", "❤️", "🎉", "😄", "👏"
    val timestamp: Long = System.currentTimeMillis()
)
