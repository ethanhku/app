package app.mossling

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import app.mossling.core.UsageEvent
import app.mossling.core.UsageMath
import java.time.LocalDate
import java.time.ZoneId

data class InstalledApp(val packageName: String, val label: String)

/** Reads foreground time from Android's UsageStatsManager. */
class UsageSource(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val zone get() = ZoneId.systemDefault()

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun today(): Long = LocalDate.now(zone).toEpochDay()

    private fun startOf(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun trackedMillis(tracked: Set<String>, start: Long, end: Long): Long {
        if (tracked.isEmpty()) return 0
        val to = minOf(end, System.currentTimeMillis())
        if (to <= start) return 0
        // Look back a little so an app already open at `start` is counted from `start`.
        val events = usm.queryEvents(start - LOOKBACK_MILLIS, to)
        val list = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEvent.Type.RESUMED
                UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEvent.Type.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Type.SCREEN_OFF
                else -> null
            } ?: continue
            list += UsageEvent(e.timeStamp, e.packageName, type)
        }
        return UsageMath.foregroundMillis(list, tracked, start, to)
    }

    fun usedMinOn(tracked: Set<String>, epochDay: Long): Int =
        (trackedMillis(tracked, startOf(epochDay), startOf(epochDay + 1)) / 60_000).toInt()

    /** Average daily minutes over the last [days] full days that have any recorded usage. */
    fun baselineMin(tracked: Set<String>, days: Int = 7): Int {
        val today = today()
        val samples = (1..days).map { usedMinOn(tracked, today - it) }
        val withData = samples.filter { it > 0 }
        return if (withData.isEmpty()) 0 else withData.sum() / withData.size
    }

    fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Minutes per package over the last week, used to suggest which apps to track. */
    fun weeklyMinutesByPackage(): Map<String, Int> {
        val end = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(end - 7 * DAY_MILLIS, end)
        return stats.mapValues { (it.value.totalTimeInForeground / 60_000).toInt() }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val LOOKBACK_MILLIS = 3 * 60 * 60 * 1000L

        /** Common feeds and short-video apps, pre-selected when installed. */
        val SUGGESTED = setOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.facebook.katana", "com.twitter.android", "com.reddit.frontpage",
            "com.google.android.youtube", "com.snapchat.android", "com.pinterest",
            "com.tumblr", "com.facebook.lite", "com.instagram.barcelona", "tv.twitch.android.app",
            "com.linkedin.android", "com.bereal.ft",
        )
    }
}
