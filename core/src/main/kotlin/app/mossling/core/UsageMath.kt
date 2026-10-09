package app.mossling.core

/** A platform-neutral slice of Android's UsageEvents stream. */
data class UsageEvent(val timeMillis: Long, val packageName: String, val type: Type) {
    enum class Type { RESUMED, PAUSED, SCREEN_OFF }
}

object UsageMath {
    /**
     * Foreground time spent in [tracked] packages within [start, end).
     *
     * Only one app is in the foreground at a time, so we follow the current foreground package:
     * a RESUMED closes whatever was open before it, and PAUSED / SCREEN_OFF close the open app.
     * Callers should pass events from somewhat before [start] so an app that was already open
     * when the window began is still counted; intervals are clipped to the window.
     */
    fun foregroundMillis(
        events: List<UsageEvent>,
        tracked: Set<String>,
        start: Long,
        end: Long,
    ): Long {
        if (end <= start || tracked.isEmpty()) return 0
        var total = 0L
        var current: String? = null
        var openedAt = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            if (pkg in tracked) {
                val from = maxOf(openedAt, start)
                val to = minOf(at, end)
                if (to > from) total += to - from
            }
            current = null
        }

        for (e in events.sortedBy { it.timeMillis }) {
            if (e.timeMillis >= end) break
            when (e.type) {
                UsageEvent.Type.RESUMED -> {
                    if (current != e.packageName) {
                        close(e.timeMillis)
                        current = e.packageName
                        openedAt = e.timeMillis
                    }
                }
                UsageEvent.Type.PAUSED -> if (current == e.packageName) close(e.timeMillis)
                UsageEvent.Type.SCREEN_OFF -> close(e.timeMillis)
            }
        }
        close(end)
        return total
    }
}
