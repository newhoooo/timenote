package app.moment.tracker

import android.app.Application
import app.moment.tracker.data.MomentDatabase
import app.moment.tracker.data.Preferences
import app.moment.tracker.data.TrackerRepository
import app.moment.tracker.overlay.OverlayController
import kotlinx.coroutines.*

class MomentApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { MomentDatabase.open(this) }
    val preferences by lazy { Preferences(this) }
    val repository by lazy { TrackerRepository(database, scope) }
    val overlays by lazy { OverlayController(this, repository, preferences, scope) }
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repository.recoverInterrupted()
        }
    }
}
