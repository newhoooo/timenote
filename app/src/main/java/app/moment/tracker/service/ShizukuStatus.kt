package app.moment.tracker.service

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuStatus {
    fun online(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun authorized(): Boolean = online() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    fun request(): Boolean = runCatching {
        if (!online()) return false
        if (!authorized()) Shizuku.requestPermission(310)
        true
    }.getOrDefault(false)
}
