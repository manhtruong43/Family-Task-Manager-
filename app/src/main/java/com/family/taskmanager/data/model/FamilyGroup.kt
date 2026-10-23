package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_groups")
data class FamilyGroup(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    val adminId: String,
    val createdAt: Long = System.currentTimeMillis()
)
