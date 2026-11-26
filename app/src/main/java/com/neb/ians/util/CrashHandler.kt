package com.neb.ians.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.neb.ians.ui.crash.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val launched = runCatching {
            val stackTrace = redactSensitive(getStackTraceString(throwable)).take(32_000)
            val crashType = throwable.javaClass.simpleName.ifBlank { "Crash" }
            val crashMessage = redactSensitive(throwable.localizedMessage ?: "Unknown error").take(2_000)
            val prefs = context.getSharedPreferences("nebians_crash_logs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_crash_log", stackTrace)
                .putString("last_crash_type", crashType)
                .putString("last_crash_message", crashMessage)
                .putLong("last_crash_timestamp", System.currentTimeMillis())
                .commit()

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("crash_log", stackTrace)
                putExtra("crash_type", crashType)
                putExtra("crash_message", crashMessage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (!launched) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    private fun redactSensitive(input: String): String =
        SENSITIVE_CRASH_REGEX.replace(input, "$1[REDACTED]")

    companion object {
        private val SENSITIVE_CRASH_REGEX = Regex(
            "(?i)(bearer\\s+|authtoken=|auth_token=|sessionid=|csrftoken=)[A-Za-z0-9._\\-:%]+"
        )

        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
