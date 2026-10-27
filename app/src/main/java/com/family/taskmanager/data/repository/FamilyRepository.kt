package com.family.taskmanager.data.repository

import androidx.room.withTransaction
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.FamilyDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import com.family.taskmanager.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Các lỗi nghiệp vụ của module Nhóm gia đình. */
enum class FamilyError {
    EMPTY_NAME,
    INVALID_CODE,
    CODE_NOT_FOUND,
    ONLY_ADMIN_CAN_CREATE,
    ONLY_MEMBER_CAN_JOIN,
    ALREADY_IN_FAMILY,
    USER_NOT_FOUND,
    NOT_ALLOWED,
    CANNOT_REMOVE_ADMIN,
    ADMIN_MUST_STAY,
    CODE_GENERATION_FAILED
}

sealed class FamilyResult<out T> {
    data class Success<T>(val data: T) : FamilyResult<T>()
    data class Failure(val error: FamilyError) : FamilyResult<Nothing>()
}

@Singleton
class FamilyRepository @Inject constructor(
    private val database: AppDatabase,
    private val familyDao: FamilyDao,
    private val userDao: UserDao,
    private val rewardRepository: RewardRepository
) {
    companion object {
        const val INVITE_CODE_LENGTH = 6
        const val MAX_FAMILY_NAME_LENGTH = 40
        private const val MAX_CODE_ATTEMPTS = 30
    }

    private val random = SecureRandom()

    fun getFamily(familyId: String): Flow<FamilyGroup?> = familyDao.getFamilyById(familyId)

    /**
     * Tạo nhóm gia đình mới. Người tạo tự động là ADMIN (Bố/Mẹ) của nhóm.
     * Chỉ tài khoản đăng nhập với vai trò Quản lý mới được tạo nhóm.
     */
    suspend fun createFamily(familyName: String, sessionUser: User): FamilyResult<FamilyGroup> {
        val name = familyName.trim().take(MAX_FAMILY_NAME_LENGTH)
        if (name.isEmpty()) return FamilyResult.Failure(FamilyError.EMPTY_NAME)

        val user = familyDao.getUserById(sessionUser.id) ?: sessionUser
        if (user.familyId.isNotBlank()) return FamilyResult.Failure(FamilyError.ALREADY_IN_FAMILY)
        if (user.role != UserRole.ADMIN) return FamilyResult.Failure(FamilyError.ONLY_ADMIN_CAN_CREATE)

        val inviteCode = generateUniqueInviteCode()
            ?: return FamilyResult.Failure(FamilyError.CODE_GENERATION_FAILED)

        val family = FamilyGroup(
            id = UUID.randomUUID().toString(),
            name = name,
            inviteCode = inviteCode,
            adminId = user.id
        )

        database.withTransaction {
            familyDao.insertFamily(family)
            userDao.insertUser(user.copy(familyId = family.id, role = UserRole.ADMIN))
            // Tự động khởi tạo 5 món quà mẫu cho gia đình mới
            rewardRepository.initDefaultRewards(family.id)
        }
        return FamilyResult.Success(family)
    }

    /**
     * Tham gia nhóm bằng mã mời 6 chữ số. Người tham gia tự động là MEMBER.
     */
    suspend fun joinFamilyByCode(inviteCode: String, sessionUser: User): FamilyResult<FamilyGroup> {
        val cleanCode = inviteCode.filter { it.isDigit() }
        if (cleanCode.length != INVITE_CODE_LENGTH) return FamilyResult.Failure(FamilyError.INVALID_CODE)

        val user = familyDao.getUserById(sessionUser.id) ?: sessionUser
        if (user.familyId.isNotBlank()) return FamilyResult.Failure(FamilyError.ALREADY_IN_FAMILY)
        if (user.role != UserRole.MEMBER) return FamilyResult.Failure(FamilyError.ONLY_MEMBER_CAN_JOIN)

        val family = familyDao.getFamilyByInviteCode(cleanCode)
            ?: return FamilyResult.Failure(FamilyError.CODE_NOT_FOUND)

        userDao.insertUser(user.copy(familyId = family.id, role = UserRole.MEMBER))
        return FamilyResult.Success(family)
    }

    /** Admin tạo mã mời mới (mã cũ hết hiệu lực ngay lập tức). */
    suspend fun regenerateInviteCode(actorId: String): FamilyResult<String> {
        val actor = familyDao.getUserById(actorId) ?: return FamilyResult.Failure(FamilyError.USER_NOT_FOUND)
        if (actor.role != UserRole.ADMIN || actor.familyId.isBlank()) {
            return FamilyResult.Failure(FamilyError.NOT_ALLOWED)
        }
        val newCode = generateUniqueInviteCode()
            ?: return FamilyResult.Failure(FamilyError.CODE_GENERATION_FAILED)
        familyDao.updateInviteCode(actor.familyId, newCode)
        return FamilyResult.Success(newCode)
    }

    /** Admin xóa 1 thành viên khỏi nhóm. Không thể xóa Admin hoặc chính mình. */
    suspend fun removeMember(actorId: String, memberId: String): FamilyResult<Unit> {
        val actor = familyDao.getUserById(actorId) ?: return FamilyResult.Failure(FamilyError.USER_NOT_FOUND)
        val member = familyDao.getUserById(memberId) ?: return FamilyResult.Failure(FamilyError.USER_NOT_FOUND)

        if (actor.role != UserRole.ADMIN || actor.familyId.isBlank() || actor.familyId != member.familyId) {
            return FamilyResult.Failure(FamilyError.NOT_ALLOWED)
        }
        if (member.id == actor.id || member.role == UserRole.ADMIN) {
            return FamilyResult.Failure(FamilyError.CANNOT_REMOVE_ADMIN)
        }
        familyDao.clearUserFamily(member.id)
        return FamilyResult.Success(Unit)
    }

    /**
     * Rời nhóm gia đình.
     * - Thành viên: rời ngay.
     * - Admin: chỉ được rời khi là người duy nhất còn trong nhóm (khi đó nhóm bị giải tán).
     */
    suspend fun leaveFamily(userId: String): FamilyResult<Unit> {
        val user = familyDao.getUserById(userId) ?: return FamilyResult.Failure(FamilyError.USER_NOT_FOUND)
        val familyId = user.familyId
        if (familyId.isBlank()) return FamilyResult.Success(Unit)

        if (user.role == UserRole.ADMIN) {
            if (familyDao.countMembers(familyId) > 1) {
                return FamilyResult.Failure(FamilyError.ADMIN_MUST_STAY)
            }
            database.withTransaction {
                familyDao.clearUserFamily(user.id)
                familyDao.deleteFamily(familyId)
            }
        } else {
            familyDao.clearUserFamily(user.id)
        }
        return FamilyResult.Success(Unit)
    }

    /** Sinh mã 6 chữ số ngẫu nhiên (SecureRandom) và đảm bảo không trùng với nhóm khác. */
    private suspend fun generateUniqueInviteCode(): String? {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = (100000 + random.nextInt(900000)).toString()
            if (familyDao.countInviteCode(code) == 0) return code
        }
        return null
    }
}
