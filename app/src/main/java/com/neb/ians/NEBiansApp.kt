package com.neb.ians

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import com.neb.ians.util.DevicePerformance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class NEBiansApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val isLowEnd = DevicePerformance.isLowEndDevice(this)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowEnd) 0.14 else 0.22)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowEnd) 32L * 1024 * 1024 else 80L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(isLowEnd)
            .allowHardware(!isLowEnd)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        if (chain.request().url.encodedPath.contains("/img/m3x/")) {
                            response.newBuilder().header("Cache-Control", "public, max-age=31536000, immutable").removeHeader("Pragma").build()
                        } else response
                    }
                    .build()
            }
            .crossfade(120)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.neb.ians.util.CrashHandler.init(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            runCatching {
                com.neb.ians.ui.components.M3xArt.covers(this@NEBiansApp)
                com.neb.ians.ui.components.M3xArt.noteRecipe(this@NEBiansApp, 0)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_RUNNING_LOW) {
            com.neb.ians.ui.components.v3art.V3Cache.trim(empty = level >= TRIM_MEMORY_BACKGROUND)
            com.neb.ians.ui.screens.notes.NoteBackdropCache.trim()
            com.neb.ians.ui.screens.notes.trimNoteCoverBitmapCache()
            if (level >= TRIM_MEMORY_BACKGROUND) {
                com.neb.ians.ui.components.M3xArt.trimMemory()
                com.neb.ians.speech.NativeSpeech.safeAdviseIdle()
                com.neb.ians.speech.PhononEngine.trimMemoryIfIdle()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.neb.ians.ui.components.v3art.V3Cache.trim(empty = true)
        com.neb.ians.ui.screens.notes.NoteBackdropCache.trim()
        com.neb.ians.ui.screens.notes.trimNoteCoverBitmapCache()
        com.neb.ians.ui.components.M3xArt.trimMemory()
        com.neb.ians.speech.NativeSpeech.safeAdviseIdle()
        com.neb.ians.speech.PhononEngine.trimMemoryIfIdle()
    }
}
