package com.family.taskmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.family.taskmanager.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserById(userId: String): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserByIdSync(userId: String): User?

    @Query("SELECT * FROM users WHERE familyId = :familyId")
    fun getUsersByFamily(familyId: String): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET points = points + :addedPoints WHERE id = :userId")
    suspend fun addPoints(userId: String, addedPoints: Int)

    @Query("UPDATE users SET points = points - :deductedPoints WHERE id = :userId")
    suspend fun deductPoints(userId: String, deductedPoints: Int)
}
