package app.mossling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mossling.core.Garden
import app.mossling.core.GardenState
import app.mossling.core.Mood
import java.util.concurrent.TimeUnit

data class Snapshot(
    val garden: GardenState,
    val usedTodayMin: Int,
    val mood: Mood,
    val focus: FocusSession?,
    val hasAccess: Boolean,
)

/** Glue between the rules engine, usage data, storage and background work. */
class Mossling(context: Context) {
    private val ctx = context.applicationContext
    val store = Store(ctx)
    val usage = UsageSource(ctx)

    fun setUp(tracked: Set<String>, floorMin: Int) {
        store.tracked = tracked
        val baseline = usage.baselineMin(tracked)
        store.garden = Garden.start(baseline, floorMin, usage.today())
        schedule()
    }

    /** Re-measures tracked apps; keeps progress but re-bases the budget on the new selection. */
    fun changeApps(tracked: Set<String>) {
        val g = store.garden ?: return
        store.tracked = tracked
        val fresh = Garden.start(usage.baselineMin(tracked), g.floorMin, usage.today())
        store.garden = g.copy(baselineMin = fresh.baselineMin, budgetMin = fresh.budgetMin)
    }

    fun setFloor(floorMin: Int) {
        val g = store.garden ?: return
        val floor = floorMin.coerceAtLeast(Garden.MIN_FLOOR_MIN)
        store.garden = g.copy(
            floorMin = floor,
            budgetMin = g.budgetMin.coerceAtLeast(floor),
            baselineMin = maxOf(g.baselineMin, floor),
        )
    }

    fun refresh(background: Boolean = false): Snapshot? {
        var g = store.garden ?: return null
        val access = usage.hasAccess()
        val tracked = store.tracked
        val today = usage.today()

        // Without usage access every day would read as zero minutes, so don't settle anything.
        if (access) {
            val before = g
            g = Garden.catchUp(g, today) { usage.usedMinOn(tracked, it) }
            val settled = g.history.lastOrNull()
            if (background && settled != null && settled != before.history.lastOrNull()) {
                notify(RECAP_ID, recapText(settled.usedMin, settled.budgetMin, settled.sunlight))
            }

            val focus = store.focus
            if (focus != null && System.currentTimeMillis() >= focus.endMillis) {
                val used = usage.trackedMillis(tracked, focus.startMillis, focus.endMillis)
                val reward = Garden.focusReward(focus.durationMin, used)
                g = Garden.completeFocus(g, reward)
                store.focus = null
                val note = if (reward > 0) {
                    "Mossy woke up well rested from ${focus.durationMin} quiet minutes. +$reward sunlight ☀️"
                } else {
                    "Mossy stirred when the glow came back during quiet time. Try again whenever you like."
                }
                store.pendingNote = note
                if (background) notify(FOCUS_ID, note)
            }
            store.garden = g
        }

        val used = if (access) usage.usedMinOn(tracked, today) else 0
        val focus = store.focus
        return Snapshot(g, used, Garden.mood(used, g.budgetMin, focus != null), focus, access)
    }

    private fun recapText(used: Int, budget: Int, sun: Int) = when {
        used <= budget -> "Yesterday: $used of $budget min. Mossy soaked up +$sun sunlight 🌱"
        sun > 0 -> "Yesterday: $used min, still less than before Mossling. +$sun sunlight 🌱"
        else -> "A fresh day for Mossy. Today's budget is ready whenever you are."
    }

    fun nudgeIfNeeded(snap: Snapshot) {
        if (!snap.hasAccess || snap.focus != null) return
        val today = usage.today()
        val budget = snap.garden.budgetMin
        val level = when {
            snap.usedTodayMin >= budget -> 2
            snap.usedTodayMin >= budget * 0.8 -> 1
            else -> 0
        }
        if (level <= store.nudgeLevel(today)) return
        store.setNudgeLevel(today, level)
        val left = budget - snap.usedTodayMin
        notify(
            NUDGE_ID,
            if (level == 1) "Mossy is squinting at the glow. About $left min left in today's budget."
            else "Mossy curled up for a nap. Every minute away from here still earns sunlight.",
        )
    }

    fun startFocus(minutes: Int) {
        store.focus = FocusSession(System.currentTimeMillis(), minutes)
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "focus",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CheckWorker>().setInitialDelay(minutes.toLong(), TimeUnit.MINUTES).build(),
        )
    }

    fun cancelFocus() {
        store.focus = null
        store.pendingNote = "Mossy woke up early. That's okay, rest whenever you're ready."
        WorkManager.getInstance(ctx).cancelUniqueWork("focus")
    }

    fun schedule() {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private fun notify(id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mossy", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mossy")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        private const val CHANNEL = "mossy"
        private const val NUDGE_ID = 1
        private const val FOCUS_ID = 2
        private const val RECAP_ID = 3
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val m = Mossling(applicationContext)
        val snap = m.refresh(background = true) ?: return Result.success()
        m.nudgeIfNeeded(snap)
        return Result.success()
    }
}
