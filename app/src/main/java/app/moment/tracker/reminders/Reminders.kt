package app.moment.tracker.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.moment.tracker.MainActivity
import app.moment.tracker.MomentApp
import app.moment.tracker.R
import app.moment.tracker.core.ReminderWindow
import app.moment.tracker.data.ReminderEvent
import app.moment.tracker.data.newId
import app.moment.tracker.service.MomentAccessibilityService
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object ReminderScheduler {
    private fun pending(context: Context, id: String = "", scheduledAt: Long = 0) = PendingIntent.getBroadcast(
        context, 100, Intent(context, ReminderReceiver::class.java).apply {
            action = "app.moment.REMIND"; putExtra("event_id", id); putExtra("scheduled_at", scheduledAt)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    suspend fun schedule(context: Context, snoozeMinutes: Int? = null) {
        val app = context.applicationContext as MomentApp
        val s = app.preferences.get()
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if (!s.remindersEnabled && snoozeMinutes == null) return
        val next = ReminderWindow.next(ZonedDateTime.now(), s.intervalMinutes, s.startHour, s.endHour, s.weekdaysOnly, snoozeMinutes)
        val at = next.toInstant().toEpochMilli()
        // Battery-friendly, best-effort reminders; never claim exact-hour delivery.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, newId(), at))
    }
    suspend fun snooze(context: Context, minutes: Int) = schedule(context, minutes)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MomentApp
        app.scope.launch {
            try {
                val id = intent.getStringExtra("event_id") ?: newId()
                val at = intent.getLongExtra("scheduled_at", System.currentTimeMillis())
                val now = ZonedDateTime.now()
                val s = app.preferences.get()
                val allowed = now.hour >= s.startHour && now.hour < s.endHour &&
                    (!s.weekdaysOnly || now.dayOfWeek.value <= 5)
                app.repository.dao.saveReminder(ReminderEvent(id, at, if (allowed) "scheduled" else "outside_window"))
                val unlocked = context.getSystemService(PowerManager::class.java).isInteractive &&
                    !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                if (allowed) {
                    val displayed = unlocked && MomentAccessibilityService.connected && Settings.canDrawOverlays(context) && app.overlays.showRecord(id)
                    if (displayed) app.overlays.vibrate() else Notifications.reminder(context, id)
                }
                ReminderScheduler.schedule(context)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MomentApp
        app.scope.launch { try { ReminderScheduler.schedule(context) } finally { pending.finish() } }
    }
}

object Notifications {
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel("reminders", "轻量记录提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun reminder(context: Context, eventId: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(context, 200,
            Intent(context, MainActivity::class.java).putExtra("record_event", eventId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_moment).setContentTitle("此刻，在做什么？")
            .setContentText("留下一句话，记录这一刻。")
            .setContentIntent(intent).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        runCatching { manager(context).notify(100, note) }
    }
    fun countdown(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(context, 201, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager(context).notify(101, NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_moment).setContentTitle("计时目标已到达")
            .setContentText("可以休息一下，或在药丸上增加时间。")
            .setContentIntent(intent).setAutoCancel(true).build()) }
    }
}
