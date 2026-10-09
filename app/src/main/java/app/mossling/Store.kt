package app.mossling

import android.content.Context
import androidx.core.content.edit
import app.mossling.core.DayRecord
import app.mossling.core.GardenState
import org.json.JSONArray
import org.json.JSONObject

data class FocusSession(val startMillis: Long, val durationMin: Int) {
    val endMillis get() = startMillis + durationMin * 60_000L
}

/** Small key-value persistence for the garden; everything stays on the device. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("mossling", Context.MODE_PRIVATE)

    var tracked: Set<String>
        get() = prefs.getStringSet("tracked", emptySet())!!.toSet()
        set(value) = prefs.edit { putStringSet("tracked", value) }

    var garden: GardenState?
        get() = prefs.getString("garden", null)?.let { decode(JSONObject(it)) }
        set(value) = prefs.edit {
            if (value == null) remove("garden") else putString("garden", encode(value).toString())
        }

    var focus: FocusSession?
        get() {
            val start = prefs.getLong("focusStart", 0)
            return if (start == 0L) null else FocusSession(start, prefs.getInt("focusMin", 0))
        }
        set(value) = prefs.edit {
            putLong("focusStart", value?.startMillis ?: 0)
            putInt("focusMin", value?.durationMin ?: 0)
        }

    /** A message for the home screen, e.g. the result of a quiet-time session. */
    var pendingNote: String?
        get() = prefs.getString("note", null)
        set(value) = prefs.edit { putString("note", value) }

    /** Highest nudge level (0, 1 = 80%, 2 = 100%) already sent on [epochDay]. */
    fun nudgeLevel(epochDay: Long): Int =
        if (prefs.getLong("nudgeDay", -1) == epochDay) prefs.getInt("nudgeLevel", 0) else 0

    fun setNudgeLevel(epochDay: Long, level: Int) = prefs.edit {
        putLong("nudgeDay", epochDay)
        putInt("nudgeLevel", level)
    }

    private fun encode(s: GardenState) = JSONObject()
        .put("baselineMin", s.baselineMin)
        .put("floorMin", s.floorMin)
        .put("budgetMin", s.budgetMin)
        .put("sunlight", s.sunlight)
        .put("streak", s.streak)
        .put("bestStreak", s.bestStreak)
        .put("daysMet", s.daysMet)
        .put("restTokens", s.restTokens)
        .put("metSinceToken", s.metSinceToken)
        .put("focusSessions", s.focusSessions)
        .put("lastSettledDay", s.lastSettledDay)
        .put("history", JSONArray(s.history.map { d ->
            JSONObject()
                .put("day", d.epochDay)
                .put("budget", d.budgetMin)
                .put("used", d.usedMin)
                .put("met", d.met)
                .put("sun", d.sunlight)
                .put("rest", d.usedRestToken)
        }))

    private fun decode(o: JSONObject): GardenState {
        val h = o.getJSONArray("history")
        return GardenState(
            baselineMin = o.getInt("baselineMin"),
            floorMin = o.getInt("floorMin"),
            budgetMin = o.getInt("budgetMin"),
            sunlight = o.getInt("sunlight"),
            streak = o.getInt("streak"),
            bestStreak = o.getInt("bestStreak"),
            daysMet = o.getInt("daysMet"),
            restTokens = o.getInt("restTokens"),
            metSinceToken = o.getInt("metSinceToken"),
            focusSessions = o.getInt("focusSessions"),
            lastSettledDay = o.getLong("lastSettledDay"),
            history = (0 until h.length()).map { i ->
                val d = h.getJSONObject(i)
                DayRecord(
                    epochDay = d.getLong("day"),
                    budgetMin = d.getInt("budget"),
                    usedMin = d.getInt("used"),
                    met = d.getBoolean("met"),
                    sunlight = d.getInt("sun"),
                    usedRestToken = d.getBoolean("rest"),
                )
            },
        )
    }
}
