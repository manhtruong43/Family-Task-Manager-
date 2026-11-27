package com.family.taskmanager.data.service

import android.content.Context
import android.content.SharedPreferences
import com.family.taskmanager.data.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

internal val LEGACY_REMINDER_STAGES = setOf(
    "fixed_30", "fixed_60", "fixed_120", "flex_mid_week",
    "flex_early_pace", "flex_final_day", "flex_last_call", "overdue"
)

/** Một lần đọc và lập chỉ mục dữ liệu cũ cho cả lượt đối soát báo thức. */
private val LEGACY_OVERDUE_STAGE = Regex("overdue_c[0-9]+")

internal class LegacyReminderSnapshot(keys: Set<String>) {
    private val stagesByTask = buildMap<Pair<String, Long>, MutableSet<String>> {
        keys.forEach { key ->
            val base = key.substringBefore('#')
            val separator = base.lastIndexOf('@')
            if (separator < 0) return@forEach
            val deadline = base.substring(separator + 1).toLongOrNull() ?: return@forEach
            val rawStage = key.substringAfter('#', "")
            val stage = LEGACY_REMINDER_STAGES.firstOrNull {
                rawStage == it || (it.startsWith("flex_") && rawStage.startsWith(it + "_c")) ||
                    (it == "overdue" && rawStage.matches(LEGACY_OVERDUE_STAGE))
            }
            if (rawStage.isNotEmpty() && stage == null) return@forEach
            getOrPut(base.substring(0, separator) to deadline) { mutableSetOf() }
                .addAll(if (rawStage.isEmpty()) LEGACY_REMINDER_STAGES else setOf(requireNotNull(stage)))
        }
    }

    fun consumedStages(taskId: String, deadline: Long): Set<String> =
        stagesByTask[taskId to deadline].orEmpty()
}

/**
 * Lưu các tùy chọn liên quan đến thông báo:
 *  - Bật/tắt thông báo đẩy (công tắc trong màn Cài đặt)
 *  - Đã hỏi quyền POST_NOTIFICATIONS hay chưa (tránh hỏi lặp lại)
 *  - Các nhiệm vụ đã được nhắc hạn chót (tránh nhắc trùng)
 */
internal data class ReminderSettings(val leadMinutes: Int, val revision: Long = 0) {
    fun receiptKey(stage: String): String =
        if (stage.startsWith("fixed_")) "$stage@r$revision" else stage
}

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _reminderLeadMinutes = MutableStateFlow(
        DeadlineReminderPlanner.normalizedLeadMinutes(prefs.getInt(KEY_REMINDER_LEAD, Task.DEFAULT_REMINDER_LEAD_MINUTES))
    )
    /** Một lựa chọn chung cho mọi nhiệm vụ và lịch, kể cả dữ liệu đã tạo trước đó. */
    val reminderLeadMinutes: StateFlow<Int> = _reminderLeadMinutes.asStateFlow()

    private val _reminderSettings = MutableStateFlow(ReminderSettings(
        _reminderLeadMinutes.value, prefs.getLong(KEY_REMINDER_REVISION, 0)
    ))
    internal val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings.asStateFlow()

    private var reminderSettingsDirty = false

    private fun writeReminderSettings(settings: ReminderSettings): Boolean =
        prefs.edit().putInt(KEY_REMINDER_LEAD, settings.leadMinutes)
            .putLong(KEY_REMINDER_REVISION, settings.revision)
            .putString(KEY_REMINDER_WRITE_TOKEN, java.util.UUID.randomUUID().toString()).commit()

    private fun acknowledgedEditor(): SharedPreferences.Editor = prefs.edit().also { editor ->
        if (reminderSettingsDirty) {
            val acknowledged = _reminderSettings.value
            editor.putInt(KEY_REMINDER_LEAD, acknowledged.leadMinutes)
                .putLong(KEY_REMINDER_REVISION, acknowledged.revision)
        }
    }

    @Synchronized
    fun setReminderLeadMinutes(minutes: Int): Boolean {
        val normalized = DeadlineReminderPlanner.normalizedLeadMinutes(minutes)
        val acknowledged = _reminderSettings.value
        if (normalized == acknowledged.leadMinutes && !reminderSettingsDirty) return true
        val next = if (normalized == acknowledged.leadMinutes) acknowledged
            else ReminderSettings(normalized, acknowledged.revision + 1)
        if (!writeReminderSettings(next)) {
            reminderSettingsDirty = true
            // commit(false) đã có thể sửa cache RAM. Khôi phục cả hai trường ngay,
            // kể cả khi lần sửa đĩa này cũng thất bại; lần chọn tiếp theo không được skip.
            writeReminderSettings(acknowledged)
            android.util.Log.e("DeadlineReminder", "Unable to save reminder setting")
            return false
        }
        reminderSettingsDirty = false
        _reminderSettings.value = next
        _reminderLeadMinutes.value = normalized
        return true
    }

    val isEnabled: Boolean
        get() = _enabled.value

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        acknowledgedEditor().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        @Synchronized
        set(value) {
            acknowledgedEditor().putBoolean(KEY_PERMISSION_ASKED, value).apply()
        }

    @Synchronized
    internal fun legacyReminderSnapshot(): LegacyReminderSnapshot = LegacyReminderSnapshot(remindedKeys())

    @Synchronized
    fun wasReminded(taskId: String, deadline: Long, stageKey: String = ""): Boolean {
        val keys = remindedKeys()
        val base = reminderKey(taskId, deadline)
        if (base in keys || reminderKey(taskId, deadline, stageKey) in keys) return true
        // Bản cũ gắn bộ đếm tiến độ vào key. Giữ các mốc đã gửi khi nâng cấp, không phát lại.
        val legacyPrefix = "$base#$stageKey" + "_c"
        return if (stageKey == "overdue") {
            keys.any { it.startsWith("$base#") && it.substringAfter('#').matches(LEGACY_OVERDUE_STAGE) }
        } else stageKey.startsWith("flex_") && keys.any { it.startsWith(legacyPrefix) }
    }

    @Synchronized
    fun markReminded(taskId: String, deadline: Long, stageKey: String = "") =
        markRemindedStages(taskId, deadline, setOf(stageKey))

    @Synchronized
    fun markRemindedStages(taskId: String, deadline: Long, stageKeys: Set<String>) {
        val updated = remindedKeys()
            .filterNot { existing ->
                // Giữ lại các stage khác của cùng task+deadline (cho việc linh hoạt nhắc đa mốc),
                // chỉ xóa dấu cũ của các deadline khác của cùng taskId.
                existing.startsWith("$taskId@") && existing.substringAfter("@").substringBefore("#") != deadline.toString()
            }
            .toMutableSet()
        updated.addAll(stageKeys.map { reminderKey(taskId, deadline, it) })
        acknowledgedEditor().putStringSet(KEY_REMINDED, updated).apply()
    }

    /** Xóa dấu "đã nhắc" của các nhiệm vụ không còn tồn tại để file prefs không phình to. */
    @Synchronized
    fun pruneReminded(existingTaskIds: Set<String>) {
        val current = remindedKeys()
        val kept = current.filter { it.substringBefore("@") in existingTaskIds }.toSet()
        if (kept.size != current.size) {
            acknowledgedEditor().putStringSet(KEY_REMINDED, kept).apply()
        }
    }

    private fun remindedKeys(): Set<String> {
        // getStringSet trả về Set gốc của SharedPreferences -> phải copy trước khi sửa
        return prefs.getStringSet(KEY_REMINDED, emptySet())?.toSet() ?: emptySet()
    }

    private fun reminderKey(taskId: String, deadline: Long, stageKey: String = ""): String =
        if (stageKey.isBlank()) "$taskId@$deadline" else "$taskId@$deadline#$stageKey"

    companion object {
        const val PREFS_NAME = "family_task_notifications"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        private const val KEY_REMINDER_REVISION = "reminder_setting_revision"
        private const val KEY_REMINDER_WRITE_TOKEN = "reminder_setting_write_token"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
        private const val KEY_REMINDED = "reminded_tasks"
    }
}
