package com.neb.ians.util

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object DevicePerformance {
    @Volatile
    private var cachedIsLowEnd: Boolean? = null

    fun isLowEndDevice(context: Context): Boolean {
        cachedIsLowEnd?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val maxMemMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val totalRamBytes = runCatching {
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem
        }.getOrDefault(0L)
        val cores = Runtime.getRuntime().availableProcessors()
        val isLow = am?.isLowRamDevice == true ||
            cores < 8 ||
            maxMemMb < 256 ||
            (am != null && am.memoryClass < 256) ||
            (totalRamBytes in 1L..(4L * 1024L * 1024L * 1024L + 512L * 1024L * 1024L))
        cachedIsLowEnd = isLow
        return isLow
    }
}

@Composable
fun rememberIsLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        DevicePerformance.isLowEndDevice(context)
    }
}
