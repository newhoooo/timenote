package app.moment.tracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.moment.tracker.ui.MomentScreen
import app.moment.tracker.ui.MomentTheme

class MainActivity : ComponentActivity() {
    private val resumeEpoch = mutableIntStateOf(0)
    private val recordEvent = mutableStateOf<String?>(null)
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeEpoch.intValue++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordEvent.value = intent?.getStringExtra("record_event")
        setContent {
            MomentTheme {
                MomentScreen(resumeEpoch.intValue, recordEvent.value,
                    onConsumeEvent = { recordEvent.value = null },
                    openOverlayPermission = { openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                    openAccessibility = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    requestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
    }
    private fun openSettings(intent: Intent) { runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    override fun onResume() { super.onResume(); resumeEpoch.intValue++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recordEvent.value = intent.getStringExtra("record_event") }
}
