package app.moment.tracker.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "activities")
data class ActivityCategory(@PrimaryKey val id: String, val name: String, val color: Long)

@Entity(tableName = "time_entries", indices = [Index("startedAt")])
data class ManualEntry(
    @PrimaryKey val id: String = newId(), val note: String,
    val startedAt: Long, val endedAt: Long? = null,
    val source: String = "manual", val confidence: String = "confirmed"
)

@Entity(tableName = "checkins", indices = [Index(value = ["reminderEventId"], unique = true), Index("answeredAt")])
data class CheckIn(
    @PrimaryKey val id: String = newId(), val rawText: String, val answeredAt: Long,
    val reminderEventId: String? = null, val timeEntryId: String? = null
)

@Entity(tableName = "reminder_events", indices = [Index("scheduledAt")])
data class ReminderEvent(@PrimaryKey val id: String, val scheduledAt: Long, val status: String = "scheduled")

@Entity(tableName = "tracked_apps")
data class TrackedApp(
    @PrimaryKey val packageName: String, val label: String, val enabled: Boolean = true,
    val defaultMode: String = "COUNT_UP", val targetMs: Long = 25 * 60_000L
)

@Entity(tableName = "timer_sessions", indices = [Index("packageName"), Index("createdAt")])
data class TimerSession(
    @PrimaryKey val id: String = newId(), val packageName: String,
    val mode: String = "COUNT_UP", val targetMs: Long = 25 * 60_000L,
    val elapsedMs: Long = 0, val state: String = "paused",
    val alerted: Boolean = false, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_usage_segments", indices = [Index("sessionId"), Index("startedAt"), Index("packageName")])
data class UsageSegment(
    @PrimaryKey val id: String = newId(), val packageName: String, val sessionId: String,
    val startedAt: Long, val endedAt: Long? = null, val durationMs: Long = 0,
    val lastConfirmedAt: Long = startedAt, val endReason: String? = null,
    val confidence: String = "observed"
)

@Dao
interface MomentDao {
    @Query("SELECT * FROM tracked_apps ORDER BY label") fun trackedApps(): Flow<List<TrackedApp>>
    @Query("SELECT * FROM tracked_apps ORDER BY label") suspend fun allTrackedApps(): List<TrackedApp>
    @Query("SELECT * FROM tracked_apps WHERE packageName = :pkg") suspend fun trackedApp(pkg: String): TrackedApp?
    @Upsert suspend fun saveApp(app: TrackedApp)
    @Query("DELETE FROM tracked_apps WHERE packageName = :pkg") suspend fun removeApp(pkg: String)
    @Query("SELECT * FROM timer_sessions WHERE packageName = :pkg AND state != 'ended' ORDER BY createdAt DESC LIMIT 1")
    suspend fun openSession(pkg: String): TimerSession?
    @Upsert suspend fun saveSession(session: TimerSession)
    @Query("SELECT * FROM timer_sessions ORDER BY createdAt DESC") suspend fun allSessions(): List<TimerSession>
    @Upsert suspend fun saveSegment(segment: UsageSegment)
    @Query("SELECT * FROM app_usage_segments WHERE endedAt IS NULL") suspend fun unfinishedSegments(): List<UsageSegment>
    @Query("SELECT * FROM app_usage_segments ORDER BY startedAt DESC") fun segments(): Flow<List<UsageSegment>>
    @Query("SELECT * FROM app_usage_segments ORDER BY startedAt DESC") suspend fun allSegments(): List<UsageSegment>
    @Query("SELECT * FROM time_entries WHERE endedAt IS NULL LIMIT 1") suspend fun runningManual(): ManualEntry?
    @Query("SELECT * FROM time_entries ORDER BY startedAt DESC") fun manualEntries(): Flow<List<ManualEntry>>
    @Query("SELECT * FROM time_entries ORDER BY startedAt DESC") suspend fun allManualEntries(): List<ManualEntry>
    @Upsert suspend fun saveManual(entry: ManualEntry)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCheckin(checkIn: CheckIn): Long
    @Query("SELECT * FROM checkins ORDER BY answeredAt DESC LIMIT 200") fun checkins(): Flow<List<CheckIn>>
    @Query("SELECT * FROM checkins ORDER BY answeredAt DESC") suspend fun allCheckins(): List<CheckIn>
    @Query("SELECT * FROM reminder_events WHERE id = :id") suspend fun reminder(id: String): ReminderEvent?
    @Upsert suspend fun saveReminder(event: ReminderEvent)
    @Query("SELECT * FROM reminder_events ORDER BY scheduledAt DESC") suspend fun allReminders(): List<ReminderEvent>
    @Query("DELETE FROM checkins WHERE id = :id") suspend fun deleteCheckin(id: String)
}

@Database(entities = [ActivityCategory::class, ManualEntry::class, CheckIn::class, ReminderEvent::class,
    TrackedApp::class, TimerSession::class, UsageSegment::class], version = 1, exportSchema = true)
abstract class MomentDatabase : RoomDatabase() {
    abstract fun dao(): MomentDao
    companion object {
        fun open(context: Context): MomentDatabase = Room.databaseBuilder(context, MomentDatabase::class.java, "moment.db").build()
    }
}
