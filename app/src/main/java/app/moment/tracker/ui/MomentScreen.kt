@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.moment.tracker.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.moment.tracker.core.SessionClock
import app.moment.tracker.core.UsageMath
import app.moment.tracker.data.*
import app.moment.tracker.service.MomentAccessibilityService
import app.moment.tracker.service.ShizukuStatus
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

private data class Permissions(val overlay: Boolean, val accessibility: Boolean, val notifications: Boolean, val shizuku: Boolean, val shizukuOnline: Boolean)
private fun permissions(c: Context) = Permissions(Settings.canDrawOverlays(c), MomentAccessibilityService.connected,
    NotificationManagerCompat.from(c).areNotificationsEnabled(), ShizukuStatus.authorized(), ShizukuStatus.online())
private fun clockLabel(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
private fun durationLabel(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return if (minutes >= 60) "${minutes / 60}时 ${minutes % 60}分" else "${minutes} 分钟"
}

@Composable
fun MomentScreen(resumeEpoch: Int, incomingEvent: String?, onConsumeEvent: () -> Unit,
                 openOverlayPermission: () -> Unit, openAccessibility: () -> Unit, requestNotifications: () -> Unit,
                 vm: MomentViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var recordEvent by rememberSaveable { mutableStateOf<String?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tracked by vm.trackedApps.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    val perms by produceState(permissions(context), resumeEpoch) {
        while (true) { value = permissions(context); delay(1500) }
    }
    fun openRecord(event: String? = null) {
        if (!vm.app.overlays.showRecord(event)) { recordEvent = event; showRecord = true }
    }
    LaunchedEffect(incomingEvent) {
        if (incomingEvent != null) { openRecord(incomingEvent); onConsumeEvent() }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                vm.app.repository.checkpoint()
                val content = Export.json(vm.app.database)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: error("文件无法写入") }
            }.onSuccess { Toast.makeText(context, "JSON 已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败，请重试", Toast.LENGTH_SHORT).show() }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                vm.app.repository.checkpoint()
                val content = Export.csv(vm.app.database)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: error("文件无法写入") }
            }.onSuccess { Toast.makeText(context, "CSV 已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败，请重试", Toast.LENGTH_SHORT).show() }
        }
    }
    Scaffold(containerColor = Paper, bottomBar = {
        NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            listOf("今天" to Icons.Outlined.Schedule, "应用" to Icons.Outlined.Apps,
                "时间线" to Icons.Outlined.History, "设置" to Icons.Outlined.Tune).forEachIndexed { index, item ->
                NavigationBarItem(selected = page == index, onClick = { page = index },
                    icon = { Icon(item.second, item.first) }, label = { Text(item.first, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFF1E5DB), selectedIconColor = Terracotta, selectedTextColor = Terracotta))
            }
        }
    }, floatingActionButton = {
        if (page == 0 || page == 2) ExtendedFloatingActionButton(onClick = { openRecord() },
            containerColor = Terracotta, contentColor = Color.White, shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("记录此刻") })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> TodayPage(now, checkins, entries, segments, tracked, running, perms,
                    onPermissions = { page = 3 }, onRecord = { openRecord() }, onStop = { vm.stopManual() })
                1 -> AppsPage(vm, tracked, settings)
                2 -> TimelinePage(now, checkins, entries, segments, tracked, vm)
                3 -> SettingsPage(settings, perms, vm, openOverlayPermission, openAccessibility, requestNotifications,
                    preview = { openRecord() }, exportJson = { exportJson.launch("时刻-${LocalDate.now()}.json") },
                    exportCsv = { exportCsv.launch("时刻-${LocalDate.now()}.csv") })
            }
        }
    }
    if (showRecord) RecordDialog(vm, recordEvent) { showRecord = false; recordEvent = null }
}

@Composable private fun PageTitle(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(7.dp)); Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        trailing?.invoke()
    }
}

@Composable private fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun SectionLabel(text: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = Muted, fontSize = 11.sp)
    }
}
@Composable private fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Outlined.Spa) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Terracotta.copy(alpha = .6f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable private fun TodayPage(now: Long, checkins: List<CheckIn>, entries: List<ManualEntry>, segments: List<UsageSegment>,
                                  tracked: List<TrackedApp>, running: RunningUsage?, perms: Permissions,
                                  onPermissions: () -> Unit, onRecord: () -> Unit, onStop: () -> Unit) {
    val today = dateOf(now)
    val zone = ZoneId.systemDefault()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val todaysCheckins = checkins.filter { it.answeredAt in start until end }
    val manual = entries.firstOrNull { it.endedAt == null }
    val totalApp = segments.sumOf { segment ->
        val stop = segment.endedAt ?: now
        val duration = if (segment.id == running?.segment?.id) running.elapsed() - running.session.elapsedMs else segment.durationMs
        UsageMath.overlapMs(segment.startedAt, stop, duration, start, end)
    }
    LazyColumn(contentPadding = PaddingValues(24.dp, 8.dp, 24.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("MOMENT  /  时刻", "时间，慢慢有迹可循") {
            Surface(shape = CircleShape, color = Color(0xFFF0EAE0)) { Icon(Icons.Outlined.WbSunny, null, tint = Terracotta, modifier = Modifier.padding(12.dp)) }
        } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                repeat(7) { offset ->
                    val day = monday.plusDays(offset.toLong()); val selected = day == today
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Terracotta else Color.Transparent)
                        .padding(horizontal = 11.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("一", "二", "三", "四", "五", "六", "日")[offset], color = if (selected) Color.White.copy(.75f) else Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp)); Text("${day.dayOfMonth}", fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ink)
                    }
                }
            }
        }
        item {
            Panel(color = Color(0xFFF3EDE3)) {
                SectionLabel("今日的时间足迹", today.format(DateTimeFormatter.ofPattern("M 月 d 日")))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(durationLabel(totalApp), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                        Text("APP 前台使用", fontSize = 11.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${todaysCheckins.size}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text("次此刻记录", fontSize = 11.sp, color = Muted)
                    }
                }
                HorizontalDivider(color = Color(0xFFE1D8CA))
                Text("时间无需填满，记得自己在做什么就好。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        if (!perms.overlay || !perms.accessibility) item {
            Surface(onClick = onPermissions, shape = RoundedCornerShape(20.dp), color = Color(0xFFEAF0E7)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Sage)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("让记录轻一点", color = Sage, fontWeight = FontWeight.SemiBold)
                        Text("开启悬浮窗与无障碍，自动记录应用使用", style = MaterialTheme.typography.bodyMedium, color = Sage)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Sage)
                }
            }
        }
        if (manual != null) item {
            Panel {
                SectionLabel("正在做", "人工任务")
                Text(manual.note, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SessionClock.format((now - manual.startedAt).coerceAtLeast(0)), fontFamily = FontFamily.Monospace,
                        fontSize = 27.sp, color = Terracotta, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onStop) { Icon(Icons.Outlined.Stop, null); Text("结束") }
                }
            }
        }
        item { SectionLabel("最近的此刻", "打点与任务分开记录") }
        if (todaysCheckins.isEmpty()) item {
            Panel { EmptyState("还没有记录", "点右下角，留住今天的第一刻") }
        } else items(todaysCheckins.take(5), key = { it.id }) { checkin ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(clockLabel(checkin.answeredAt), fontFamily = FontFamily.Monospace, color = Muted, modifier = Modifier.width(58.dp))
                Box(Modifier.padding(top = 7.dp).size(6.dp).background(Terracotta, CircleShape))
                Text(checkin.rawText, modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item { Text("只记录应用名称和时长，数据保存在这台手机。", fontSize = 11.sp, color = Muted) }
    }
}

@Composable private fun AppsPage(vm: MomentViewModel, tracked: List<TrackedApp>, settings: MomentSettings) {
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrackedApp?>(null) }
    val installed by vm.installed.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("AUTOMATIC  /  自动记录", "把计时交给应用") }
        item {
            Panel(color = Color(0xFFEAF0E7)) {
                ToggleRow("自动记录", "进入所选 APP 开始，离开或锁屏暂停", settings.monitoringEnabled) { value -> vm.updateSettings { it.copy(monitoringEnabled = value) } }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("记录中的应用", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { choosing = true }) { Icon(Icons.Outlined.Add, null); Text("添加") }
        } }
        if (tracked.isEmpty()) item { Panel { EmptyState("选几个常用的 APP", "自动计时从你选择的应用开始", Icons.Outlined.Apps) } }
        items(tracked, key = { it.packageName }) { app ->
            Surface(onClick = { editing = app }, shape = RoundedCornerShape(22.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(app.label)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(if (app.defaultMode == "COUNT_UP") "正计时 · 本次会话累计" else "倒计时 · ${app.targetMs / 60_000} 分钟", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = app.enabled, onCheckedChange = { vm.saveApp(app.copy(enabled = it)) })
                }
            }
        }
        item { Text("同一会话可以包含多次进入与离开。人工任务与 APP 使用时间独立统计。", color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
    if (choosing) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { choosing = false }, title = { Text("选择应用") }, text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("搜索名称或包名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(installed.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                        val existing = tracked.firstOrNull { it.packageName == app.packageName }
                        Row(Modifier.fillMaxWidth().clickable {
                            if (existing == null) vm.saveApp(TrackedApp(app.packageName, app.label)) else vm.removeApp(app.packageName)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label); Text(app.packageName, fontSize = 10.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Checkbox(checked = existing != null, onCheckedChange = {
                                if (it) vm.saveApp(TrackedApp(app.packageName, app.label)) else vm.removeApp(app.packageName)
                            })
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("完成") } })
    }
    editing?.let { app ->
        var mode by remember(app.packageName) { mutableStateOf(app.defaultMode) }
        var minutes by remember(app.packageName) { mutableStateOf((app.targetMs / 60_000).toString()) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(app.label) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("下次新会话使用以下默认设置；当前会话可在药丸中调整。", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "COUNT_UP", onClick = { mode = "COUNT_UP" }, label = { Text("正计时") })
                    FilterChip(selected = mode == "COUNT_DOWN", onClick = { mode = "COUNT_DOWN" }, label = { Text("倒计时") })
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("倒计时目标 / 分钟") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = {
            vm.saveApp(app.copy(defaultMode = mode, targetMs = (minutes.toLongOrNull() ?: 25).coerceIn(1, 1440) * 60_000)); editing = null
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = { vm.removeApp(app.packageName); editing = null }) { Text("移除应用") } })
    }
}

@Composable private fun AppBadge(label: String) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF2E9DF)), contentAlignment = Alignment.Center) {
        Text(label.take(1), color = Terracotta, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun TimelinePage(now: Long, checkins: List<CheckIn>, entries: List<ManualEntry>, segments: List<UsageSegment>,
                                      tracked: List<TrackedApp>, vm: MomentViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ManualEntry?>(null) }
    val labels = tracked.associate { it.packageName to it.label }
    LazyColumn(contentPadding = PaddingValues(24.dp, 8.dp, 24.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("TIMELINE  /  时间线", "每一段，都有来处") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("此刻打点", "人工任务", "APP 使用").forEachIndexed { index, title ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) })
                }
            }
        }
        val empty = when (tab) { 0 -> checkins.isEmpty(); 1 -> entries.isEmpty(); else -> segments.isEmpty() }
        if (empty) item { EmptyState("时间线还是空的", "记录之后，会在这里慢慢展开") }
        when (tab) {
            0 -> items(checkins, key = { it.id }) { c ->
                Panel { Text(c.rawText, style = MaterialTheme.typography.bodyLarge); Text("${dateOf(c.answeredAt)}  ${clockLabel(c.answeredAt)}", color = Muted, fontSize = 11.sp) }
            }
            1 -> items(entries, key = { it.id }) { e ->
                Surface(onClick = { editing = e }, shape = RoundedCornerShape(22.dp), color = Color.White) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row { Text(e.note, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Icon(Icons.Outlined.Edit, "修改", tint = Muted, modifier = Modifier.size(16.dp)) }
                        Text("${dateOf(e.startedAt)}  ${clockLabel(e.startedAt)} — ${e.endedAt?.let { clockLabel(it) } ?: "进行中"}", color = Muted, fontSize = 11.sp)
                        Text(durationLabel((e.endedAt ?: now) - e.startedAt), color = Terracotta)
                    }
                }
            }
            else -> items(segments.take(500), key = { it.id }) { s ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppBadge(labels[s.packageName] ?: s.packageName.substringAfterLast('.'))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(labels[s.packageName] ?: s.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${dateOf(s.startedAt)}  ${clockLabel(s.startedAt)} — ${s.endedAt?.let { clockLabel(it) } ?: "进行中"}", color = Muted, fontSize = 10.sp)
                        }
                        Text(durationLabel(s.durationMs), color = Sage, fontSize = 12.sp)
                    }
                    if (s.confidence == "partial") Text("记录曾中断，已保留到最后确认时刻。", color = Terracotta, fontSize = 11.sp)
                }
            }
        }
    }
    editing?.let { entry -> EntryDialog(entry, vm) { editing = null } }
}

@Composable private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked, onChange)
    }
}
@Composable private fun SettingsPage(s: MomentSettings, p: Permissions, vm: MomentViewModel,
                                     overlay: () -> Unit, accessibility: () -> Unit, notifications: () -> Unit,
                                     preview: () -> Unit, exportJson: () -> Unit, exportCsv: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("PREFERENCES  /  偏好设置", "照自己的节奏来") }
        item { Panel {
            SectionLabel("运行权限", "只开启需要的能力")
            PermissionRow("悬浮窗", "显示记录卡片与计时药丸", p.overlay, overlay)
            PermissionRow("无障碍", "识别前台 APP，不保存页面内容", p.accessibility, accessibility)
            PermissionRow("通知", "提醒与悬浮窗不可用时的备用入口", p.notifications, notifications)
            PermissionRow("Shizuku", if (p.shizukuOnline) "可选连接 · 当前识别使用无障碍" else "请先在 Shizuku 中启动服务", p.shizuku) {
                if (!ShizukuStatus.request()) Toast.makeText(context, "请先启动 Shizuku，再回来授权", Toast.LENGTH_LONG).show()
            }
        } }
        item { Panel {
            ToggleRow("定时问问自己", "在选定时段，轻轻提醒一次", s.remindersEnabled) { v -> vm.updateSettings { it.copy(remindersEnabled = v) } }
            Text("提醒间隔", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 120).forEach { minutes -> FilterChip(selected = s.intervalMinutes == minutes,
                    onClick = { vm.updateSettings { it.copy(intervalMinutes = minutes) } }, label = { Text("${minutes}分", fontSize = 12.sp) }) }
            }
            SettingSlider("开始时间", "${s.startHour}:00", s.startHour.toFloat(), 0f..23f, 22) { v -> vm.updateSettings { it.copy(startHour = v.toInt(), endHour = maxOf(it.endHour, v.toInt() + 1)) } }
            SettingSlider("结束时间", "${s.endHour}:00", s.endHour.toFloat(), 1f..24f, 22) { v -> vm.updateSettings { it.copy(endHour = maxOf(v.toInt(), it.startHour + 1)) } }
            ToggleRow("仅工作日", "周末留白", s.weekdaysOnly) { v -> vm.updateSettings { it.copy(weekdaysOnly = v) } }
            ToggleRow("轻震提醒", "记录提醒与倒计时到达时轻震", s.vibration) { v -> vm.updateSettings { it.copy(vibration = v) } }
            Text("系统省电与后台限制可能让提醒延后。", color = Muted, fontSize = 11.sp)
        } }
        item { Panel {
            SectionLabel("悬浮窗与药丸")
            Text("拖动卡片顶部调整位置，拖动右下角调整大小。横竖屏分别记忆位置。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            SettingSlider("记录窗宽度", "${s.recordWidthDp} dp", s.recordWidthDp.toFloat(), 240f..480f, 23) { v -> vm.updateSettings { it.copy(recordWidthDp = v.toInt()) } }
            SettingSlider("记录窗高度", "${s.recordHeightDp} dp", s.recordHeightDp.toFloat(), 230f..500f, 26) { v -> vm.updateSettings { it.copy(recordHeightDp = v.toInt()) } }
            SettingSlider("文字大小", "${s.fontSize} sp", s.fontSize.toFloat(), 12f..22f, 9) { v -> vm.updateSettings { it.copy(fontSize = v.toInt()) } }
            SettingSlider("不透明度", "${(s.opacity * 100).toInt()}%", s.opacity, .35f..1f, 12) { v -> vm.updateSettings { it.copy(opacity = v) } }
            ToggleRow("显示计时药丸", "点击展开操作，拖动改变位置", s.pillEnabled) { v -> vm.updateSettings { it.copy(pillEnabled = v) } }
            SettingSlider("药丸大小", "${(s.pillScale * 100).toInt()}%", s.pillScale, .7f..1.6f, 8) { v -> vm.updateSettings { it.copy(pillScale = v) } }
            OutlinedButton(onClick = preview, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("试一下记录窗口") }
            TextButton(onClick = { vm.updateSettings { it.copy(recordWidthDp = 320, recordHeightDp = 270, fontSize = 15, pillScale = 1f, opacity = .97f) } }) { Text("恢复默认外观") }
        } }
        item { Panel {
            SectionLabel("数据留在自己手里")
            Text("本地 SQLite 存储，无账号。导出文件包含你的原始记录，请自行妥善保存。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = exportJson, modifier = Modifier.weight(1f)) { Text("导出 JSON") }
                OutlinedButton(onClick = exportCsv, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
            }
        } }
        item { Text("时刻 0.1.0  ·  给时间一点形状", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) }
    }
}

@Composable private fun PermissionRow(title: String, detail: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (granted) Sage else Muted, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp); Text(detail, color = Muted, fontSize = 10.sp)
        }
        Text(if (granted) "已开启" else "去开启", color = if (granted) Sage else Terracotta, fontSize = 11.sp)
    }
}
@Composable private fun SettingSlider(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, change: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column {
        Row { Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(valueLabel, color = Muted, fontSize = 12.sp) }
        Slider(value = local, onValueChange = { local = it }, valueRange = range, steps = steps, onValueChangeFinished = { change(local) })
    }
}

@Composable private fun RecordDialog(vm: MomentViewModel, eventId: String?, close: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("此刻，在做什么？") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it.take(2000) }, placeholder = { Text("用一句话，留下这一刻") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(start, { start = it }); Text("同时开始 / 切换人工任务计时", fontSize = 12.sp)
            }
        }
    }, confirmButton = { TextButton(enabled = text.isNotBlank() && !busy, onClick = {
        busy = true
        vm.record(text, start, eventId) { success ->
            busy = false
            if (success) close() else Toast.makeText(context, "未保存，请重试或检查是否已经记录", Toast.LENGTH_SHORT).show()
        }
    }) { Text(if (busy) "保存中…" else "保存") } }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("稍后") } })
}

@Composable private fun EntryDialog(entry: ManualEntry, vm: MomentViewModel, close: () -> Unit) {
    var note by remember { mutableStateOf(entry.note) }
    var start by remember { mutableStateOf(Instant.ofEpochMilli(entry.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) }
    var end by remember { mutableStateOf(entry.endedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("修改时间记录") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(note, { note = it.take(2000) }, label = { Text("正在做什么") })
            OutlinedTextField(start, { start = it }, label = { Text("开始：yyyy-MM-dd HH:mm") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("结束：留空表示进行中") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)
        val parsed = runCatching {
            val startMs = LocalDateTime.parse(start.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs = end.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            require(note.isNotBlank())
            entry.copy(note = note.trim(), startedAt = startMs, endedAt = endMs)
        }.getOrNull()
        if (parsed == null) error = "请检查日期格式和记录内容"
        else vm.saveEntry(parsed) { success -> if (success) close() else error = "时间区间与其他任务重叠，或结束早于开始" }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}
