package app.moment.tracker.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import app.moment.tracker.core.SessionClock
import app.moment.tracker.data.*
import app.moment.tracker.reminders.ReminderScheduler
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/** Compact windows never request focus. Only an explicit tap in the editor enables the keyboard. */
class OverlayController(
    private val context: Context, private val repository: TrackerRepository,
    private val preferences: Preferences, private val scope: CoroutineScope
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val ink = Color.rgb(47, 45, 40)
    private val accent = Color.rgb(183, 94, 63)
    private var settings = MomentSettings()
    private var record: LinearLayout? = null
    private var recordParams: WindowManager.LayoutParams? = null
    private var editor: EditText? = null
    private var eventId: String? = null
    private var saving = false
    private var pill: LinearLayout? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var pillText: TextView? = null
    private var pillActions: LinearLayout? = null
    private var expanded = false
    private var pillHiddenFor: String? = null
    private var draftJob: Job? = null

    init {
        scope.launch {
            preferences.settings.collect { new ->
                settings = new
                record?.alpha = new.opacity
                editor?.textSize = new.fontSize.toFloat()
                recordParams?.let { p ->
                    p.width = dp(new.recordWidthDp).coerceAtMost(screenWidth() - dp(16))
                    p.height = dp(new.recordHeightDp).coerceAtMost(screenHeight() - dp(72))
                    record?.let { clamp(p); update(it, p) }
                }
                pill?.alpha = new.opacity
                if (!new.pillEnabled) hidePill()
                else pillParams?.let { p -> sizePill(p); pill?.let { update(it, p) } }
            }
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun screenHeight() = context.resources.displayMetrics.heightPixels
    private fun orientation() = if (screenWidth() > screenHeight()) "landscape" else "portrait"
    private fun shape(color: Int, radius: Int = 22): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.rgb(228, 222, 211))
    }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false) = TextView(context).apply {
        this.text = value; textSize = size; setTextColor(ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = TextView(context).apply {
        text = value; textSize = 13f; gravity = Gravity.CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setTextColor(if (primary) Color.WHITE else accent)
        background = shape(if (primary) accent else Color.rgb(246, 239, 229), 12)
        setOnClickListener { action() }
        isFocusable = false
    }
    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    fun showRecord(reminderEventId: String? = null): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (record != null) return true // Keep the existing draft and reminder identity intact.
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.rgb(255, 252, 247))
            setPadding(dp(16), dp(12), dp(16), dp(6))
            elevation = dp(10).toFloat(); alpha = settings.opacity
        }
        val p = params(dp(settings.recordWidthDp), dp(settings.recordHeightDp))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("⠿  此刻，在做什么？", 16f, true), LinearLayout.LayoutParams(0, dp(36), 1f))
        header.addView(button("×") { hideRecord() }, LinearLayout.LayoutParams(dp(36), dp(36)))
        root.addView(header)
        root.addView(text("留下一句话，稍后再回看。", 12f).apply { setTextColor(Color.GRAY) })
        val input = EditText(context).apply {
            hint = "例如：修改方案、看书、休息一下…"
            textSize = settings.fontSize.toFloat(); setTextColor(ink)
            setHintTextColor(Color.rgb(158, 150, 137))
            gravity = Gravity.TOP; setPadding(dp(10), dp(10), dp(10), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = shape(Color.rgb(248, 244, 237), 12)
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
            isFocusableInTouchMode = true
            setOnTouchListener { _, motion ->
                if (motion.action == MotionEvent.ACTION_DOWN) {
                    p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    update(root, p)
                    requestFocus()
                    post { context.getSystemService(InputMethodManager::class.java).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
                }
                false
            }
        }
        root.addView(input, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) })
        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button("只记录", true) { save(false) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        actions.addView(Space(context), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(button("开始 / 切换计时") { save(true) }, LinearLayout.LayoutParams(0, dp(42), 1.3f))
        root.addView(actions)
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(button("10 分钟后") {
            scope.launch { ReminderScheduler.snooze(context, 10); hideRecord() }
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        val resize = text("◢", 20f).apply { gravity = Gravity.BOTTOM or Gravity.END; setTextColor(accent) }
        footer.addView(resize, LinearLayout.LayoutParams(dp(40), dp(34)))
        root.addView(footer)
        record = root; recordParams = p; editor = input; eventId = reminderEventId
        clamp(p)
        try { wm.addView(root, p) } catch (_: RuntimeException) { record = null; editor = null; recordParams = null; return false }
        attachDrag(header, root, p, "record")
        attachResize(resize, root, p)
        scope.launch {
            val draft = preferences.draft()
            if (record === root && input.text.isEmpty()) input.setText(draft)
            if (record === root) restorePosition(root, p, "record")
        }
        input.doAfterTextChanged { editable ->
            draftJob?.cancel()
            val draft = editable?.toString().orEmpty()
            draftJob = scope.launch { delay(300); preferences.saveDraft(draft) }
        }
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { _, insets ->
            val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
            if (ime > 0) {
                p.y = p.y.coerceAtMost((screenHeight() - ime - p.height - dp(8)).coerceAtLeast(dp(24)))
                update(root, p)
            }
            insets
        }
        return true
    }

    private fun save(startTask: Boolean) {
        if (saving) return
        val value = editor?.text?.toString().orEmpty()
        if (value.isBlank()) { toast("先写一句正在做的事"); return }
        saving = true
        scope.launch {
            try {
                val recorded = repository.record(value, startTask, eventId)
                if (recorded) {
                    draftJob?.cancel(); preferences.saveDraft("")
                    hideRecord(preserveDraft = false)
                    toast(if (startTask) "已记录，开始计时" else "已记录这一刻")
                } else toast("这次提醒已经记录过了")
            } catch (_: Exception) { toast("保存失败，草稿已保留，请重试") }
            finally { saving = false }
        }
    }

    fun hideRecord(preserveDraft: Boolean = true) {
        val root = record ?: return
        val draft = editor?.text?.toString().orEmpty()
        draftJob?.cancel()
        if (preserveDraft) scope.launch { preferences.saveDraft(draft) }
        editor?.let { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
        runCatching { wm.removeView(root) }
        record = null; editor = null; recordParams = null; eventId = null
    }

    fun renderPill(active: RunningUsage?) {
        if (active == null || !settings.pillEnabled || !Settings.canDrawOverlays(context)) { hidePill(); return }
        if (pillHiddenFor != null && pillHiddenFor != active.session.id) pillHiddenFor = null
        if (pillHiddenFor == active.session.id) return
        if (pill == null) createPill()
        val elapsed = active.elapsed()
        val remaining = active.session.targetMs - elapsed
        val countdown = active.session.mode == "COUNT_DOWN"
        val display = if (countdown) {
            (if (remaining < 0) "+" else "剩 ") + SessionClock.format(remaining)
        } else SessionClock.format(elapsed)
        pillText?.text = "${if (active.isRunning) "●" else "Ⅱ"} ${active.label.take(6)}  $display"
        pillText?.setTextColor(if (countdown && remaining <= 0) Color.rgb(255, 189, 150) else Color.rgb(255, 249, 238))
        (pillActions?.getChildAt(0) as? TextView)?.text = if (active.isRunning) "暂停" else "继续"
    }

    private fun createPill() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = shape(Color.rgb(48, 47, 42), 25)
            elevation = dp(8).toFloat(); alpha = settings.opacity
        }
        val label = text("时刻", 13f, true).apply {
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(label, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(context).apply { visibility = View.GONE; gravity = Gravity.CENTER }
        fun add(value: String, action: () -> Unit) {
            actions.addView(button(value, action = action), LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        add("暂停") { scope.launch { repository.togglePause() } }
        add("正/倒") { scope.launch { repository.toggleMode() } }
        add("+5分") { scope.launch { repository.extendTarget() } }
        add("结束") { scope.launch { repository.endSession() } }
        root.addView(actions)
        val p = params(dp(185), dp(42))
        pill = root; pillParams = p; pillText = label; pillActions = actions; expanded = false
        sizePill(p); clamp(p)
        try { wm.addView(root, p) } catch (_: RuntimeException) { pill = null; return }
        attachDrag(label, root, p, "pill") {
            expanded = !expanded
            actions.visibility = if (expanded) View.VISIBLE else View.GONE
            sizePill(p); clamp(p); update(root, p)
        }
        root.setOnLongClickListener { showRecord(); true }
        scope.launch { if (pill === root) restorePosition(root, p, "pill") }
    }

    fun restorePill() { pillHiddenFor = null; renderPill(repository.running.value) }
    fun hidePill() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null; pillText = null; pillActions = null; pillParams = null
    }
    fun hideAll() { hideRecord(); hidePill() }
    fun onConfigurationChanged() {
        recordParams?.let { p -> record?.let { root -> scope.launch { restorePosition(root, p, "record") } } }
        pillParams?.let { p -> pill?.let { root -> scope.launch { restorePosition(root, p, "pill") } } }
    }
    private fun sizePill(p: WindowManager.LayoutParams) {
        p.width = dp(((if (expanded) 290 else 185) * settings.pillScale).roundToInt())
        p.height = dp(((if (expanded) 90 else 42) * settings.pillScale).roundToInt())
        pillText?.textSize = 13 * settings.pillScale
        clamp(p)
    }
    private fun clamp(p: WindowManager.LayoutParams) {
        p.width = p.width.coerceAtMost(screenWidth() - dp(12))
        p.height = p.height.coerceAtMost(screenHeight() - dp(64))
        p.x = p.x.coerceIn(dp(4), (screenWidth() - p.width - dp(4)).coerceAtLeast(dp(4)))
        p.y = p.y.coerceIn(dp(28), (screenHeight() - p.height - dp(28)).coerceAtLeast(dp(28)))
    }
    private fun update(view: View, p: WindowManager.LayoutParams) { runCatching { wm.updateViewLayout(view, p) } }
    private suspend fun restorePosition(view: View, p: WindowManager.LayoutParams, key: String) {
        val (x, y) = preferences.position("${key}_${orientation()}")
        p.x = ((screenWidth() - p.width).coerceAtLeast(1) * x).roundToInt()
        p.y = ((screenHeight() - p.height).coerceAtLeast(1) * y).roundToInt()
        clamp(p); update(view, p)
    }
    private fun savePosition(p: WindowManager.LayoutParams, key: String) {
        val x = p.x.toFloat() / (screenWidth() - p.width).coerceAtLeast(1)
        val y = p.y.toFloat() / (screenHeight() - p.height).coerceAtLeast(1)
        val profile = "${key}_${orientation()}"
        scope.launch { preferences.savePosition(profile, x, y) }
    }
    private fun attachDrag(handle: View, root: View, p: WindowManager.LayoutParams, key: String, click: (() -> Unit)? = null) {
        var startX = 0f; var startY = 0f; var originalX = 0; var originalY = 0; var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY; originalX = p.x; originalY = p.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                    if (moved) { p.x = originalX + dx.roundToInt(); p.y = originalY + dy.roundToInt(); clamp(p); update(root, p) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (moved) savePosition(p, key) else click?.invoke(); true }
                else -> false
            }
        }
    }
    private fun attachResize(handle: View, root: View, p: WindowManager.LayoutParams) {
        var x = 0f; var y = 0f; var width = 0; var height = 0
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { x = event.rawX; y = event.rawY; width = p.width; height = p.height; true }
                MotionEvent.ACTION_MOVE -> {
                    p.width = (width + event.rawX - x).roundToInt().coerceIn(dp(240), maxOf(dp(240), screenWidth() - dp(16)))
                    p.height = (height + event.rawY - y).roundToInt().coerceIn(dp(230), maxOf(dp(230), screenHeight() - dp(72)))
                    clamp(p); update(root, p); true
                }
                MotionEvent.ACTION_UP -> {
                    val density = context.resources.displayMetrics.density
                    val w = (p.width / density).roundToInt(); val h = (p.height / density).roundToInt()
                    scope.launch { preferences.update { it.copy(recordWidthDp = w, recordHeightDp = h) } }
                    savePosition(p, "record"); true
                }
                else -> false
            }
        }
    }
    fun vibrate() {
        if (settings.vibration) context.getSystemService(Vibrator::class.java)
            .vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    private fun toast(value: String) = Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
}
