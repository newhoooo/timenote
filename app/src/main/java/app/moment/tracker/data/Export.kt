package app.moment.tracker.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

object Export {
    suspend fun json(db: MomentDatabase): String = db.withTransaction {
        val dao = db.dao()
        JSONObject().apply {
            put("format", "moment-backup"); put("version", 1); put("exportedAt", System.currentTimeMillis())
            put("trackedApps", JSONArray().apply { dao.allTrackedApps().forEach { a -> put(JSONObject().apply {
                put("packageName", a.packageName); put("label", a.label); put("enabled", a.enabled)
                put("defaultMode", a.defaultMode); put("targetMs", a.targetMs)
            }) } })
            put("checkins", JSONArray().apply { dao.allCheckins().forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("rawText", c.rawText); put("answeredAt", c.answeredAt)
                put("reminderEventId", c.reminderEventId ?: JSONObject.NULL); put("timeEntryId", c.timeEntryId ?: JSONObject.NULL)
            }) } })
            put("timeEntries", JSONArray().apply { dao.allManualEntries().forEach { e -> put(JSONObject().apply {
                put("id", e.id); put("note", e.note); put("startedAt", e.startedAt)
                put("endedAt", e.endedAt ?: JSONObject.NULL); put("source", e.source); put("confidence", e.confidence)
            }) } })
            put("timerSessions", JSONArray().apply { dao.allSessions().forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("packageName", s.packageName); put("mode", s.mode); put("targetMs", s.targetMs)
                put("elapsedMs", s.elapsedMs); put("state", s.state); put("alerted", s.alerted); put("createdAt", s.createdAt)
            }) } })
            put("usageSegments", JSONArray().apply { dao.allSegments().forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("packageName", s.packageName); put("sessionId", s.sessionId)
                put("startedAt", s.startedAt); put("endedAt", s.endedAt ?: JSONObject.NULL); put("durationMs", s.durationMs)
                put("lastConfirmedAt", s.lastConfirmedAt); put("endReason", s.endReason ?: JSONObject.NULL); put("confidence", s.confidence)
            }) } })
            put("reminderEvents", JSONArray().apply { dao.allReminders().forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("scheduledAt", r.scheduledAt); put("status", r.status)
            }) } })
        }.toString(2)
    }
    suspend fun csv(db: MomentDatabase): String = db.withTransaction {
        fun cell(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        buildString {
            append("\uFEFF类型,内容或包名,开始时间UTC毫秒,结束时间UTC毫秒,有效时长毫秒,状态\n")
            db.dao().allCheckins().forEach { append("打点,${cell(it.rawText)},${it.answeredAt},,,已记录\n") }
            db.dao().allManualEntries().forEach {
                append("人工任务,${cell(it.note)},${it.startedAt},${it.endedAt ?: ""},${it.endedAt?.minus(it.startedAt) ?: ""},${it.confidence}\n")
            }
            db.dao().allSegments().forEach {
                append("APP使用,${cell(it.packageName)},${it.startedAt},${it.endedAt ?: ""},${it.durationMs},${it.endReason ?: "进行中"}\n")
            }
        }
    }
}
