package app.moment.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.store by preferencesDataStore("moment_settings")

data class MomentSettings(
    val remindersEnabled: Boolean = false, val intervalMinutes: Int = 60,
    val startHour: Int = 9, val endHour: Int = 22, val weekdaysOnly: Boolean = false,
    val vibration: Boolean = true, val pillEnabled: Boolean = true,
    val recordWidthDp: Int = 320, val recordHeightDp: Int = 270,
    val pillScale: Float = 1f, val opacity: Float = .97f,
    val fontSize: Int = 15, val monitoringEnabled: Boolean = true
)

class Preferences(private val context: Context) {
    private val mutex = Mutex()
    val settings = context.store.data.map { p ->
        MomentSettings(
            p[booleanPreferencesKey("reminders")] ?: false, p[intPreferencesKey("interval")] ?: 60,
            p[intPreferencesKey("start")] ?: 9, p[intPreferencesKey("end")] ?: 22,
            p[booleanPreferencesKey("weekdays")] ?: false, p[booleanPreferencesKey("vibration")] ?: true,
            p[booleanPreferencesKey("pill")] ?: true, p[intPreferencesKey("width")] ?: 320,
            p[intPreferencesKey("height")] ?: 270, p[floatPreferencesKey("pill_scale")] ?: 1f,
            p[floatPreferencesKey("opacity")] ?: .97f, p[intPreferencesKey("font")] ?: 15,
            p[booleanPreferencesKey("monitoring")] ?: true
        )
    }
    suspend fun get(): MomentSettings = settings.first()
    suspend fun update(change: (MomentSettings) -> MomentSettings) = mutex.withLock {
        val s = change(get())
        context.store.edit { p ->
            p[booleanPreferencesKey("reminders")] = s.remindersEnabled
            p[intPreferencesKey("interval")] = s.intervalMinutes.coerceIn(5, 240)
            p[intPreferencesKey("start")] = s.startHour.coerceIn(0, 23)
            p[intPreferencesKey("end")] = s.endHour.coerceIn(s.startHour + 1, 24)
            p[booleanPreferencesKey("weekdays")] = s.weekdaysOnly
            p[booleanPreferencesKey("vibration")] = s.vibration
            p[booleanPreferencesKey("pill")] = s.pillEnabled
            p[intPreferencesKey("width")] = s.recordWidthDp.coerceIn(240, 600)
            p[intPreferencesKey("height")] = s.recordHeightDp.coerceIn(230, 600)
            p[floatPreferencesKey("pill_scale")] = s.pillScale.coerceIn(.7f, 1.6f)
            p[floatPreferencesKey("opacity")] = s.opacity.coerceIn(.35f, 1f)
            p[intPreferencesKey("font")] = s.fontSize.coerceIn(12, 22)
            p[booleanPreferencesKey("monitoring")] = s.monitoringEnabled
        }
    }
    suspend fun position(key: String): Pair<Float, Float> {
        val p = context.store.data.first()
        return (p[floatPreferencesKey("${key}_x")] ?: .95f) to (p[floatPreferencesKey("${key}_y")] ?: .22f)
    }
    suspend fun savePosition(key: String, x: Float, y: Float) {
        context.store.edit { p ->
            p[floatPreferencesKey("${key}_x")] = x.coerceIn(0f, 1f)
            p[floatPreferencesKey("${key}_y")] = y.coerceIn(0f, 1f)
        }
    }
    suspend fun draft(): String = context.store.data.first()[stringPreferencesKey("draft")] ?: ""
    suspend fun saveDraft(text: String) { context.store.edit { it[stringPreferencesKey("draft")] = text } }
}
