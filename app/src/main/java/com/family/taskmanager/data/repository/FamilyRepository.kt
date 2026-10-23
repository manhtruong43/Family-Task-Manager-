package com.family.taskmanager.data.repository

import com.family.taskmanager.data.local.FamilyDao
import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.FamilyGroup
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val familyDao: FamilyDao,
    private val userDao: UserDao,
    private val rewardRepository: RewardRepository
) {
    fun getFamily(familyId: String): Flow<FamilyGroup?> = familyDao.getFamilyById(familyId)

    suspend fun createFamily(familyName: String, adminUser: User): FamilyGroup {
        val inviteCode = (100000..999999).random().toString()
        val family = FamilyGroup(
            id = UUID.randomUUID().toString(),
            name = familyName,
            inviteCode = inviteCode,
            adminId = adminUser.id
        )
        familyDao.insertFamily(family)
        userDao.insertUser(adminUser.copy(familyId = family.id))
        // Tự động khởi tạo 5 món quà mẫu cho gia đình mới
        rewardRepository.initDefaultRewards(family.id)
        return family
    }

    suspend fun joinFamilyByCode(inviteCode: String, user: User): FamilyGroup? {
        val cleanCode = inviteCode.replace(" ", "").replace("-", "").trim()
        val family = familyDao.getFamilyByInviteCode(cleanCode) ?: return null
        userDao.insertUser(user.copy(familyId = family.id))
        return family
    }
}
