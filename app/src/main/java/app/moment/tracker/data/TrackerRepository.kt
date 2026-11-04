package app.moment.tracker.data

import android.os.SystemClock
import androidx.room.withTransaction
import app.moment.tracker.core.SessionClock
import app.moment.tracker.core.TimerMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RunningUsage(
    val session: TimerSession, val label: String,
    val startedElapsed: Long? = null, val segment: UsageSegment? = null
) {
    val isRunning get() = startedElapsed != null
    fun elapsed(now: Long = SystemClock.elapsedRealtime()) = SessionClock.elapsed(session.elapsedMs, startedElapsed, now)
}

class TrackerRepository(private val db: MomentDatabase, scope: CoroutineScope) {
    val dao = db.dao()
    private val mutex = Mutex()
    private val _running = MutableStateFlow<RunningUsage?>(null)
    val running = _running.asStateFlow()
    private var lastForeground: String? = null
    private var blockedPackage: String? = null
    private val initialized = scope.async {
        db.withTransaction {
            // A process death is not evidence the app stayed in the foreground. Close at the last checkpoint.
            dao.unfinishedSegments().forEach { segment ->
                dao.saveSegment(segment.copy(endedAt = segment.lastConfirmedAt,
                    endReason = "interrupted", confidence = "partial"))
            }
            dao.allSessions().filter { it.state == "running" }.forEach {
                dao.saveSession(it.copy(state = "interrupted"))
            }
        }
    }
    init {
        scope.launch {
            while (isActive) { delay(15_000); checkpoint() }
        }
    }
    suspend fun recoverInterrupted() { initialized.await() }

    suspend fun onForeground(packageName: String?, force: Boolean = false) {
        initialized.await()
        mutex.withLock {
            if (!force && lastForeground == packageName) return
            if (lastForeground != packageName) blockedPackage = null
            lastForeground = packageName
            db.withTransaction {
                finishSegment("background")
                _running.value = null
                if (packageName == null || packageName == blockedPackage) return@withTransaction
                val app = dao.trackedApp(packageName)?.takeIf { it.enabled } ?: return@withTransaction
                val session = dao.openSession(packageName) ?: TimerSession(packageName = packageName,
                    mode = app.defaultMode, targetMs = app.targetMs)
                if (session.state == "user_paused") {
                    _running.value = RunningUsage(session, app.label)
                    return@withTransaction
                }
                startSegment(session, app.label)
            }
        }
    }

    private suspend fun startSegment(session: TimerSession, label: String) {
        val wall = System.currentTimeMillis()
        val segment = UsageSegment(packageName = session.packageName, sessionId = session.id, startedAt = wall)
        val updated = session.copy(state = "running")
        dao.saveSession(updated)
        dao.saveSegment(segment)
        _running.value = RunningUsage(updated, label, SystemClock.elapsedRealtime(), segment)
    }

    private suspend fun finishSegment(reason: String, state: String = "paused"): RunningUsage? {
        val active = _running.value ?: return null
        if (!active.isRunning) return active
        val total = active.elapsed()
        val duration = (total - active.session.elapsedMs).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        active.segment?.let { dao.saveSegment(it.copy(endedAt = now, durationMs = duration,
            lastConfirmedAt = now, endReason = reason)) }
        val session = active.session.copy(elapsedMs = total, state = state)
        dao.saveSession(session)
        return RunningUsage(session, active.label).also { _running.value = it }
    }

    suspend fun togglePause() {
        initialized.await()
        mutex.withLock {
            val active = _running.value ?: return
            db.withTransaction {
                if (active.isRunning) finishSegment("user_pause", "user_paused")
                else if (lastForeground == active.session.packageName) startSegment(active.session, active.label)
            }
        }
    }

    suspend fun endSession() {
        initialized.await()
        mutex.withLock {
            val active = _running.value ?: return
            db.withTransaction {
                val ended = finishSegment("user_end", "ended") ?: active
                dao.saveSession(ended.session.copy(state = "ended"))
                blockedPackage = ended.session.packageName
                _running.value = null
            }
        }
    }

    suspend fun toggleMode() {
        initialized.await()
        mutex.withLock {
            val active = _running.value ?: return
            val mode = if (active.session.mode == "COUNT_UP") "COUNT_DOWN" else "COUNT_UP"
            val changed = active.session.copy(mode = mode)
            dao.saveSession(changed.copy(elapsedMs = active.elapsed()))
            _running.value = active.copy(session = changed)
        }
    }

    suspend fun extendTarget(minutes: Int = 5) {
        initialized.await()
        mutex.withLock {
            val active = _running.value ?: return
            val target = maxOf(active.session.targetMs, active.elapsed()) + minutes * 60_000L
            val changed = active.session.copy(targetMs = target, alerted = false)
            dao.saveSession(changed.copy(elapsedMs = active.elapsed()))
            _running.value = active.copy(session = changed)
        }
    }

    suspend fun consumeCountdownAlert(): Boolean {
        initialized.await()
        return mutex.withLock {
            val active = _running.value ?: return@withLock false
            val session = active.session
            if (!active.isRunning || !SessionClock.shouldAlert(TimerMode.valueOf(session.mode), session.targetMs,
                    active.elapsed(), session.alerted)) return@withLock false
            val changed = session.copy(alerted = true)
            dao.saveSession(changed.copy(elapsedMs = active.elapsed()))
            _running.value = active.copy(session = changed)
            true
        }
    }

    suspend fun checkpoint() {
        initialized.await()
        mutex.withLock {
            val active = _running.value?.takeIf { it.isRunning } ?: return
            val total = active.elapsed()
            db.withTransaction {
                dao.saveSession(active.session.copy(elapsedMs = total))
                active.segment?.let { dao.saveSegment(it.copy(durationMs = total - active.session.elapsedMs,
                    lastConfirmedAt = System.currentTimeMillis())) }
            }
        }
    }

    suspend fun record(text: String, startTask: Boolean = false, reminderId: String? = null): Boolean {
        initialized.await()
        val clean = text.trim().take(2000)
        if (clean.isBlank()) return false
        return mutex.withLock {
            db.withTransaction {
                val now = System.currentTimeMillis()
                val entryId = if (startTask) newId() else null
                val inserted = dao.insertCheckin(CheckIn(rawText = clean, answeredAt = now,
                    reminderEventId = reminderId, timeEntryId = entryId))
                if (inserted == -1L) return@withTransaction false
                if (startTask) {
                    dao.runningManual()?.let { dao.saveManual(it.copy(endedAt = maxOf(now, it.startedAt))) }
                    dao.saveManual(ManualEntry(id = entryId!!, note = clean, startedAt = now))
                }
                reminderId?.let { id -> dao.reminder(id)?.let { dao.saveReminder(it.copy(status = "answered")) } }
                true
            }
        }
    }

    suspend fun stopManual() {
        mutex.withLock {
            db.withTransaction {
                dao.runningManual()?.let { dao.saveManual(it.copy(endedAt = maxOf(System.currentTimeMillis(), it.startedAt))) }
            }
        }
    }

    suspend fun saveManual(entry: ManualEntry): Boolean {
        initialized.await()
        return mutex.withLock {
            db.withTransaction {
                val end = entry.endedAt ?: Long.MAX_VALUE
                val overlaps = dao.allManualEntries().any {
                    it.id != entry.id && it.startedAt < end && (it.endedAt ?: Long.MAX_VALUE) > entry.startedAt
                }
                val now = System.currentTimeMillis()
                if (entry.note.isBlank() || end < entry.startedAt || overlaps || entry.startedAt > now ||
                    (entry.endedAt != null && entry.endedAt > now)) false
                else { dao.saveManual(entry.copy(source = "manual", confidence = "confirmed")); true }
            }
        }
    }

    suspend fun saveApp(app: TrackedApp) { dao.saveApp(app) }
    suspend fun removeApp(pkg: String) { dao.removeApp(pkg) }
}
