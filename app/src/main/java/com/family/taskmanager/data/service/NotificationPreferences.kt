package com.family.taskmanager.data.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lưu các tùy chọn liên quan đến thông báo:
 *  - Bật/tắt thông báo đẩy (công tắc trong màn Cài đặt)
 *  - Đã hỏi quyền POST_NOTIFICATIONS hay chưa (tránh hỏi lặp lại)
 *  - Các nhiệm vụ đã được nhắc hạn chót (tránh nhắc trùng)
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isEnabled: Boolean
        get() = _enabled.value

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()
        }

    @Synchronized
    fun wasReminded(taskId: String, deadline: Long): Boolean {
        return remindedKeys().contains(reminderKey(taskId, deadline))
    }

    @Synchronized
    fun markReminded(taskId: String, deadline: Long) {
        val updated = remindedKeys()
            .filterNot { it.startsWith("$taskId@") }
            .toMutableSet()
        updated.add(reminderKey(taskId, deadline))
        prefs.edit().putStringSet(KEY_REMINDED, updated).apply()
    }

    /** Xóa dấu "đã nhắc" của các nhiệm vụ không còn tồn tại để file prefs không phình to. */
    @Synchronized
    fun pruneReminded(existingTaskIds: Set<String>) {
        val current = remindedKeys()
        val kept = current.filter { it.substringBefore("@") in existingTaskIds }.toSet()
        if (kept.size != current.size) {
            prefs.edit().putStringSet(KEY_REMINDED, kept).apply()
        }
    }

    private fun remindedKeys(): Set<String> {
        // getStringSet trả về Set gốc của SharedPreferences -> phải copy trước khi sửa
        return prefs.getStringSet(KEY_REMINDED, emptySet())?.toSet() ?: emptySet()
    }

    private fun reminderKey(taskId: String, deadline: Long) = "$taskId@$deadline"

    companion object {
        const val PREFS_NAME = "family_task_notifications"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
        private const val KEY_REMINDED = "reminded_tasks"
    }
}
