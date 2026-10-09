package app.mossling.core

import kotlin.math.roundToInt

/** One settled day. Days are identified by epoch day (days since 1970-01-01, local time). */
data class DayRecord(
    val epochDay: Long,
    val budgetMin: Int,
    val usedMin: Int,
    val met: Boolean,
    val sunlight: Int,
    val usedRestToken: Boolean,
)

data class GardenState(
    /** Average daily use of the tracked apps before Mossling; budgets never rise above it. */
    val baselineMin: Int,
    /** The lowest the adaptive budget will go, chosen by the user. */
    val floorMin: Int,
    /** Today's budget. */
    val budgetMin: Int,
    /** Lifetime sunlight; it only ever grows. */
    val sunlight: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val daysMet: Int = 0,
    val restTokens: Int = 0,
    val metSinceToken: Int = 0,
    val focusSessions: Int = 0,
    val lastSettledDay: Long,
    val history: List<DayRecord> = emptyList(),
)

enum class Stage(val title: String, val minSunlight: Int) {
    SPORE("Spore", 0),
    SPROUT("Sprout", 50),
    MOSSLING("Mossling", 200),
    BLOOM("Blooming Moss", 600),
    GROVE("Little Grove", 1500),
    ANCIENT("Ancient Grove", 4000);

    companion object {
        fun of(sunlight: Int): Stage = entries.last { sunlight >= it.minSunlight }
        fun next(sunlight: Int): Stage? = entries.firstOrNull { it.minSunlight > sunlight }
    }
}

/**
 * How Mossy feels about today so far. There is no damage and no decay: going over budget only
 * makes Mossy drowsy from the screen glow, and it recovers fully tomorrow.
 */
enum class Mood(val line: String) {
    RESTING("Mossy is fast asleep while you're unplugged."),
    GLOWING("Mossy is soaking up the quiet. So much room to grow!"),
    CONTENT("Mossy is calm and happy."),
    SQUINTING("The screen glow is getting bright for Mossy."),
    DROWSY("Mossy curled up to rest its eyes. Some time away will help."),
}

object Garden {
    const val MAX_REST_TOKENS = 2
    const val MET_DAYS_PER_TOKEN = 7
    const val DEFAULT_BUDGET_MIN = 120
    const val MIN_FLOOR_MIN = 5
    private const val HISTORY_DAYS = 30
    /** Days older than this aren't settled after a long absence; the OS no longer has their events. */
    const val MAX_CATCH_UP_DAYS = 7

    fun start(baselineMin: Int, floorMin: Int, today: Long): GardenState {
        val floor = floorMin.coerceAtLeast(MIN_FLOOR_MIN)
        val budget = if (baselineMin <= 0) DEFAULT_BUDGET_MIN else (baselineMin * 0.9).roundToInt()
        val clamped = budget.coerceAtLeast(floor)
        return GardenState(
            baselineMin = maxOf(baselineMin, clamped),
            floorMin = floor,
            budgetMin = clamped,
            lastSettledDay = today - 1,
        )
    }

    /**
     * Sunlight for a finished day. Meeting the budget earns a base plus every minute saved;
     * a missed day that still beat the old baseline earns half the improvement. Never negative.
     */
    fun sunlightFor(budgetMin: Int, usedMin: Int, baselineMin: Int): Int = when {
        usedMin <= budgetMin -> 10 + (budgetMin - usedMin)
        usedMin < baselineMin -> (baselineMin - usedMin) / 2
        else -> 0
    }

    /** Adaptive difficulty: ease the budget down 5% after a met day, back up 5% after a miss. */
    fun nextBudget(state: GardenState, met: Boolean): Int {
        val b = state.budgetMin
        return if (met) {
            minOf(b - 1, (b * 0.95).roundToInt()).coerceAtLeast(state.floorMin)
        } else {
            maxOf(b + 1, (b * 1.05).roundToInt()).coerceAtMost(maxOf(state.baselineMin, state.floorMin))
        }
    }

    fun settleDay(state: GardenState, epochDay: Long, usedMin: Int): GardenState {
        val met = usedMin <= state.budgetMin
        val earned = sunlightFor(state.budgetMin, usedMin, state.baselineMin)
        val useToken = !met && state.restTokens > 0 && state.streak > 0

        var streak = state.streak
        var tokens = state.restTokens
        var metSinceToken = state.metSinceToken
        when {
            met -> {
                streak += 1
                metSinceToken += 1
                if (metSinceToken >= MET_DAYS_PER_TOKEN) {
                    metSinceToken = 0
                    tokens = minOf(MAX_REST_TOKENS, tokens + 1)
                }
            }
            useToken -> tokens -= 1
            else -> streak = 0
        }

        val record = DayRecord(epochDay, state.budgetMin, usedMin, met, earned, useToken)
        return state.copy(
            budgetMin = nextBudget(state, met),
            sunlight = state.sunlight + earned,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            daysMet = state.daysMet + if (met) 1 else 0,
            restTokens = tokens,
            metSinceToken = metSinceToken,
            lastSettledDay = epochDay,
            history = (state.history + record).takeLast(HISTORY_DAYS),
        )
    }

    /** Settles every finished day since the last settlement, asking [usedMinOn] for each day's use. */
    fun catchUp(state: GardenState, today: Long, usedMinOn: (Long) -> Int): GardenState {
        var s = state
        val first = maxOf(state.lastSettledDay + 1, today - MAX_CATCH_UP_DAYS)
        for (day in first until today) s = settleDay(s, day, usedMinOn(day))
        return if (s.lastSettledDay < today - 1) s.copy(lastSettledDay = today - 1) else s
    }

    fun mood(usedMin: Int, budgetMin: Int, focusActive: Boolean): Mood {
        if (focusActive) return Mood.RESTING
        val ratio = usedMin.toDouble() / budgetMin.coerceAtLeast(1)
        return when {
            ratio < 0.5 -> Mood.GLOWING
            ratio < 0.8 -> Mood.CONTENT
            ratio < 1.0 -> Mood.SQUINTING
            else -> Mood.DROWSY
        }
    }

    /** Sunlight for a completed quiet-time session, or 0 if a tracked app was opened during it. */
    fun focusReward(durationMin: Int, trackedUseMillis: Long): Int =
        if (trackedUseMillis < 60_000) maxOf(1, durationMin / 4) else 0

    fun completeFocus(state: GardenState, reward: Int): GardenState =
        if (reward > 0) state.copy(sunlight = state.sunlight + reward, focusSessions = state.focusSessions + 1)
        else state
}

/** Keepsakes Mossy finds along the way, shown in the garden. */
enum class Keepsake(val title: String, val emoji: String, val unlockedBy: (GardenState) -> Boolean, val hint: String) {
    PEBBLE("Smooth pebble", "🪨", { it.sunlight >= 30 }, "30 sunlight"),
    DEWDROP("Dewdrop", "💧", { it.sunlight >= 100 }, "100 sunlight"),
    NIGHTCAP("Sleepy nightcap", "🌙", { it.focusSessions >= 3 }, "3 quiet-time sessions"),
    MUSHROOM("Tiny mushroom", "🍄", { it.sunlight >= 250 }, "250 sunlight"),
    SNAIL("Snail friend", "🐌", { it.bestStreak >= 7 }, "a 7-day streak"),
    FIREFLY("Firefly", "✨", { it.sunlight >= 450 }, "450 sunlight"),
    FERN("Fern", "🌿", { it.daysMet >= 14 }, "14 days within budget"),
    LANTERN("Paper lantern", "🏮", { it.sunlight >= 900 }, "900 sunlight"),
    BUTTERFLY("Butterfly", "🦋", { it.focusSessions >= 15 }, "15 quiet-time sessions"),
    POND("Little pond", "🪷", { it.sunlight >= 2000 }, "2000 sunlight"),
    MOTH("Moon moth", "🌕", { it.bestStreak >= 30 }, "a 30-day streak");

    companion object {
        fun unlocked(state: GardenState) = entries.filter { it.unlockedBy(state) }
    }
}
