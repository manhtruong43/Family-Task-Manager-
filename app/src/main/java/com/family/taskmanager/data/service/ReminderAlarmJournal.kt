package com.family.taskmanager.data.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

internal data class ReminderOccurrence(
    val taskId: String,
    val deadline: Long,
    val generation: Long,
    val dueAt: Long,
    val slot: String,
    val settingRevision: Long = 0
) {
    val id: String get() = UUID.nameUUIDFromBytes(
        JSONArray(listOf(taskId, deadline, generation, dueAt, slot, settingRevision))
            .toString().toByteArray(Charsets.UTF_8)
    ).toString()
}

internal data class ReminderOccurrencePlan(val occurrence: ReminderOccurrence, val plan: PlannedReminder)
internal data class ReminderScheduleSnapshot(
    val occurrences: Map<String, ReminderOccurrencePlan>,
    val alarms: List<ReminderOccurrencePlan>,
    val retainedAttempts: Set<String> = emptySet()
)

internal enum class ReminderAlarmKind { NORMAL, RETRY, RECONCILE, RECOVERY_WATCHDOG }

internal data class ReminderAlarmRecord(
    val id: String,
    val kind: ReminderAlarmKind,
    val at: Long,
    val attempt: Int,
    val occurrence: ReminderOccurrence? = null,
    val stageKey: String = ""
)

internal data class ReminderAttemptState(
    val occurrence: ReminderOccurrence,
    val attempt: Int,
    val retryAt: Long? = null,
    val exhausted: Boolean = false,
    val inFlight: Boolean = false,
    val stageKey: String = ""
)

internal data class ReminderAlarmState(
    val alarms: Map<String, ReminderAlarmRecord> = emptyMap(),
    val attempts: Map<String, ReminderAttemptState> = emptyMap(),
    val reconciliationAttempt: Int = 0,
    val reconciliationInFlight: Boolean = false
)

internal class ReminderJournalException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Nhật ký báo thức/giới hạn thử lại, tách khỏi biên nhận gửi thành công trong Room.
 * Ghi đồng bộ trước khi đặt AlarmManager; chỉ quên danh tính sau khi hủy thành công.
 * Mỗi snapshot là một giá trị SharedPreferences, không ghi rời từng trường.
 */
internal class ReminderAlarmJournal(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var confirmedState: String? = null
    private var confirmedPayload: String? = null

    fun read(): ReminderAlarmState {
        val saved = prefs.getString(KEY, null) ?: return ReminderAlarmState()
        try {
            val json = JSONObject(saved)
            val alarms = json.getJSONArray("alarms").objects().map { row ->
                ReminderAlarmRecord(
                    row.getString("id"), ReminderAlarmKind.valueOf(row.getString("kind")),
                    row.getLong("at"), row.getInt("attempt"),
                    row.optJSONObject("occurrence")?.occurrence(), row.optString("stage")
                )
            }.associateBy { it.id }
            val attempts = json.getJSONArray("attempts").objects().map { row ->
                ReminderAttemptState(row.getJSONObject("occurrence").occurrence(), row.getInt("attempt"),
                    if (row.isNull("retryAt")) null else row.getLong("retryAt"),
                    row.getBoolean("exhausted"), row.getBoolean("inFlight"), row.optString("stage"))
            }.associateBy { it.occurrence.id }
            return ReminderAlarmState(alarms, attempts, json.optInt("reconciliationAttempt", 0),
                json.optBoolean("reconciliationInFlight", false))
        } catch (error: org.json.JSONException) {
            throw ReminderJournalException("Cannot read alarm journal; refusing to lose installed identities", error)
        } catch (error: IllegalArgumentException) {
            throw ReminderJournalException("Invalid alarm journal identity", error)
        }
    }

    fun write(state: ReminderAlarmState) {
        val json = JSONObject()
            .put("reconciliationAttempt", state.reconciliationAttempt)
            .put("reconciliationInFlight", state.reconciliationInFlight)
            .put("alarms", JSONArray(state.alarms.values.map { record ->
                JSONObject().put("id", record.id).put("kind", record.kind.name).put("at", record.at)
                    .put("attempt", record.attempt).put("stage", record.stageKey)
                    .put("occurrence", record.occurrence?.json() ?: JSONObject.NULL)
            }))
            .put("attempts", JSONArray(state.attempts.values.map { attempt ->
                JSONObject().put("occurrence", attempt.occurrence.json()).put("attempt", attempt.attempt)
                    .put("retryAt", attempt.retryAt ?: JSONObject.NULL)
                    .put("exhausted", attempt.exhausted).put("inFlight", attempt.inFlight).put("stage", attempt.stageKey)
            })).toString()
        if (confirmedState == json && prefs.getString(KEY, null) == confirmedPayload) return
        // commit(false) vẫn có thể đổi cache trong RAM của SharedPreferences.
        // Chỉ bỏ qua ghi khi chính instance này đã xác nhận commit; token mới buộc
        // lần thử sau thực hiện một thay đổi thật, kể cả sau một commit thất bại.
        val payload = JSONObject(json).put("writeToken", UUID.randomUUID().toString()).toString()
        if (!prefs.edit().putString(KEY, payload).commit()) {
            throw ReminderJournalException("Cannot persist alarm journal")
        }
        confirmedState = json
        confirmedPayload = payload
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun ReminderOccurrence.json() = JSONObject().put("taskId", taskId).put("deadline", deadline)
        .put("generation", generation).put("dueAt", dueAt).put("slot", slot).put("revision", settingRevision)
    private fun JSONObject.occurrence() = ReminderOccurrence(
        getString("taskId"), getLong("deadline"), getLong("generation"), getLong("dueAt"),
        getString("slot"), optLong("revision", 0)
    )

    companion object {
        const val PREFS_NAME = "deadline_alarm_journal"
        private const val KEY = "state"
        const val GLOBAL_ID = "reconciliation"
        const val WATCHDOG_ID = "claimed_work_recovery"
    }
}
