package com.family.taskmanager.data.repository

import com.family.taskmanager.data.local.UserDao
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun getUser(userId: String): Flow<User?> = userDao.getUserById(userId)

    suspend fun getUserSync(userId: String): User? = userDao.getUserByIdSync(userId)

    fun getUsersByFamily(familyId: String): Flow<List<User>> =
        userDao.getUsersByFamily(familyId)

    suspend fun saveUser(user: User) = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)
}
