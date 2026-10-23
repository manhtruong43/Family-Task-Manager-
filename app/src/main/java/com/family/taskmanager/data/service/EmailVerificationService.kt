package com.family.taskmanager.data.service

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class OtpRecord(
    val code: String,
    val expiresAt: Long = System.currentTimeMillis() + 300_000L // Hạn 5 phút
)

@Singleton
class EmailVerificationService @Inject constructor() {

    private val activeOtps = ConcurrentHashMap<String, OtpRecord>()

    suspend fun sendVerificationOtp(email: String): String {
        // Giả lập độ trễ kết nối máy chủ gửi Email
        delay(800)

        // Tạo mã OTP 6 chữ số ngẫu nhiên
        val otpCode = (100000..999999).random().toString()
        activeOtps[email.lowercase().trim()] = OtpRecord(code = otpCode)

        return otpCode
    }

    fun verifyOtp(email: String, inputCode: String): Boolean {
        val record = activeOtps[email.lowercase().trim()] ?: return false
        if (System.currentTimeMillis() > record.expiresAt) {
            activeOtps.remove(email.lowercase().trim())
            return false
        }
        val isCorrect = record.code == inputCode.trim()
        if (isCorrect) {
            activeOtps.remove(email.lowercase().trim())
        }
        return isCorrect
    }

    fun getActiveOtp(email: String): String? {
        return activeOtps[email.lowercase().trim()]?.code
    }
}
