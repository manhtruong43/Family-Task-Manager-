package com.family.taskmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    ADMIN,   // Bố / Mẹ (Giao việc, duyệt việc, tạo phần thưởng)
    MEMBER   // Con / Thành viên (Thực hiện việc, tích điểm, đổi thưởng)
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String = "🧔",
    val role: UserRole = UserRole.MEMBER,
    val familyId: String = "",
    val points: Int = 0,
    val isEmailVerified: Boolean = false
) {
    companion object {
        val AVATAR_PRESETS = listOf(
            "🧔" to "Bố",
            "👩" to "Mẹ",
            "👦" to "Con Trai",
            "👧" to "Con Gái",
            "🦸" to "Siêu Anh Hùng",
            "👑" to "Quán Quân",
            "🐱" to "Thú Cưng",
            "🐻" to "Gấu Bông"
        )
    }
}
