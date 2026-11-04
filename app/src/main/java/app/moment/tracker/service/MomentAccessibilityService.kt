package app.moment.tracker.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import app.moment.tracker.MomentApp
import app.moment.tracker.reminders.Notifications
import kotlinx.coroutines.*

class MomentAccessibilityService : AccessibilityService() {
    private val app get() = application as MomentApp
    private var serviceJob = SupervisorJob()
    private val serviceScope get() = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var detection: Job? = null
    private var monitoringEnabled = true
    private var screenReceiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> serviceScope.launch {
                    app.repository.onForeground(null)
                    app.overlays.hideAll()
                }
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> detect()
            }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        serviceJob = SupervisorJob()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
        serviceScope.launch {
            app.preferences.settings.collect {
                monitoringEnabled = it.monitoringEnabled
                if (!monitoringEnabled) app.repository.onForeground(null) else detect()
            }
        }
        serviceScope.launch {
            while (isActive) {
                if (screenUsable()) {
                    app.overlays.renderPill(app.repository.running.value)
                    if (app.repository.consumeCountdownAlert()) {
                        app.overlays.vibrate()
                        Notifications.countdown(this@MomentAccessibilityService)
                    }
                }
                delay(1000)
            }
        }
        detect()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) detect()
    }
    private fun screenUsable(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun detect() {
        detection?.cancel()
        detection = serviceScope.launch {
            delay(220)
            if (!monitoringEnabled || !screenUsable()) {
                app.repository.onForeground(null)
                app.overlays.hidePill()
                return@launch
            }
            // Never inspect text or traverse the view tree; only read the package of application windows.
            val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.layer }
            val target = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive } ?: apps.firstOrNull()
            val root = target?.root
            val pkg = root?.packageName?.toString()
            @Suppress("DEPRECATION")
            root?.recycle()
            // Temporary system windows do not count as switching to another application.
            if (pkg != null) app.repository.onForeground(pkg)
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        app.overlays.onConfigurationChanged()
    }
    override fun onInterrupt() {
        app.scope.launch { app.repository.onForeground(null) }
        app.overlays.hideAll()
    }
    override fun onDestroy() {
        connected = false
        detection?.cancel(); serviceJob.cancel()
        if (screenReceiverRegistered) runCatching { unregisterReceiver(receiver) }
        app.scope.launch { app.repository.onForeground(null) }
        app.overlays.hideAll()
        super.onDestroy()
    }
    companion object { @Volatile var connected = false; private set }
}
