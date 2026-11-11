package com.family.taskmanager.data.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.family.taskmanager.R

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
    val isEmailVerified: Boolean = false,
    /**
     * Nhãn quan hệ hiển thị (FATHER / MOTHER / CHILD / ''), độc lập với [avatarUrl] và [role].
     * Hồ sơ cũ chưa có dữ liệu này ('') sẽ hiển thị nhãn trung tính (Bố/Mẹ) thay vì đoán theo avatar.
     */
    val relationLabel: String = ""
) {
    /**
     * Nguồn thống nhất lấy nhãn vai trò/quan hệ hiển thị đa ngôn ngữ:
     * - Ưu tiên [relationLabel] đã lưu trong hồ sơ (Bố / Mẹ).
     * - Không suy đoán Bố/Mẹ từ [avatarUrl] (đổi avatar không làm đổi nhãn).
     * - Hồ sơ cũ thiếu [relationLabel] hiển thị nhãn trung tính.
     */
    @StringRes
    fun displayRoleRes(homeStyle: Boolean = false): Int = when {
        role != UserRole.ADMIN -> if (homeStyle) R.string.home_role_child else R.string.role_child
        relationLabel == RELATION_FATHER -> R.string.role_father_short
        relationLabel == RELATION_MOTHER -> R.string.role_mother_short
        else -> if (homeStyle) R.string.home_role_parent else R.string.role_parent
    }

    companion object {
        const val RELATION_FATHER = "FATHER"
        const val RELATION_MOTHER = "MOTHER"
        const val RELATION_CHILD = "CHILD"

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
