package com.neb.ians

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.neb.ians.ui.NoteNavHost
import com.neb.ians.ui.components.NebClearFocusWhenKeyboardHides
import com.neb.ians.ui.components.NebToastBusHost
import com.neb.ians.ui.components.nebFocusOutOnTap
import com.neb.ians.ui.theme.NEBiansTheme
import com.neb.ians.util.nebSafeSides
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        scheduleNotificationPermissionCheck()

        val prefs = getSharedPreferences("note_theme_prefs", MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", -1)

        setContent {
            val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            var isDarkMode by remember {
                mutableStateOf(if (savedMode == 0) false else if (savedMode == 1) true else sysDark)
            }

            DisposableEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkMode) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkMode) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkMode
                insetsController.isAppearanceLightNavigationBars = !isDarkMode
                onDispose {}
            }

            NEBiansTheme(darkTheme = isDarkMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nebSafeSides()
                        .nebFocusOutOnTap()
                ) {
                    NebClearFocusWhenKeyboardHides()
                    NoteNavHost(
                        isDarkMode = isDarkMode,
                        onToggleTheme = {
                            val next = !isDarkMode
                            isDarkMode = next
                            prefs.edit().putInt("theme_mode", if (next) 1 else 0).apply()
                        }
                    )
                    NebToastBusHost()
                }
            }
        }
    }

    private fun scheduleNotificationPermissionCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            delay(2_200L)
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                val prefs = getSharedPreferences("note_startup_prefs", MODE_PRIVATE)
                if (!prefs.getBoolean("notif_prompted", false)) {
                    prefs.edit().putBoolean("notif_prompted", true).apply()
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1001
                    )
                }
            }
        }
    }
}
