package app.moment.tracker.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.moment.tracker.MomentApp
import app.moment.tracker.data.*
import app.moment.tracker.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

class MomentViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MomentApp
    private val repository = app.repository
    val trackedApps = repository.dao.trackedApps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val checkins = repository.dao.checkins().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val entries = repository.dao.manualEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val segments = repository.dao.segments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MomentSettings())
    val running = repository.running
    val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    init { loadApps() }
    private fun loadApps() = viewModelScope.launch {
        installed.value = withContext(Dispatchers.IO) {
            val pm = app.packageManager
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != app.packageName }.distinctBy { it.packageName }.sortedBy { it.label }
        }
    }
    fun saveApp(rule: TrackedApp) = viewModelScope.launch { repository.saveApp(rule) }
    fun removeApp(pkg: String) = viewModelScope.launch { repository.removeApp(pkg) }
    fun updateSettings(change: (MomentSettings) -> MomentSettings) = viewModelScope.launch {
        val before = app.preferences.get()
        app.preferences.update(change)
        val after = app.preferences.get()
        if (before.remindersEnabled != after.remindersEnabled || before.intervalMinutes != after.intervalMinutes ||
            before.startHour != after.startHour || before.endHour != after.endHour || before.weekdaysOnly != after.weekdaysOnly) {
            ReminderScheduler.schedule(app)
        }
    }
    fun record(text: String, startTask: Boolean, event: String?, done: (Boolean) -> Unit) = viewModelScope.launch {
        done(runCatching { repository.record(text, startTask, event) }.getOrDefault(false))
    }
    fun stopManual() = viewModelScope.launch { repository.stopManual() }
    fun saveEntry(entry: ManualEntry, done: (Boolean) -> Unit) = viewModelScope.launch {
        done(runCatching { repository.saveManual(entry) }.getOrDefault(false))
    }
}
