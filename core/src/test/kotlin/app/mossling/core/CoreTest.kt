package app.mossling.core

import app.mossling.core.UsageEvent.Type.PAUSED
import app.mossling.core.UsageEvent.Type.RESUMED
import app.mossling.core.UsageEvent.Type.SCREEN_OFF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageMathTest {
    private val tracked = setOf("social")

    @Test
    fun countsOnlyTrackedForegroundTime() {
        val events = listOf(
            UsageEvent(0, "social", RESUMED),
            UsageEvent(100, "social", PAUSED),
            UsageEvent(100, "maps", RESUMED),
            UsageEvent(300, "maps", PAUSED),
        )
        assertEquals(100, UsageMath.foregroundMillis(events, tracked, 0, 1000))
    }

    @Test
    fun switchingAppsWithoutPauseClosesPrevious() {
        val events = listOf(
            UsageEvent(0, "social", RESUMED),
            UsageEvent(50, "maps", RESUMED),
            UsageEvent(80, "social", RESUMED),
            UsageEvent(90, "social", SCREEN_OFF),
        )
        assertEquals(60, UsageMath.foregroundMillis(events, tracked, 0, 1000))
    }

    @Test
    fun clipsToWindowAndCountsStillOpenApp() {
        val events = listOf(UsageEvent(-500, "social", RESUMED))
        assertEquals(200, UsageMath.foregroundMillis(events, tracked, 0, 200))
    }

    @Test
    fun ignoresStalePauseFromAnotherApp() {
        val events = listOf(
            UsageEvent(0, "social", RESUMED),
            UsageEvent(10, "maps", PAUSED),
            UsageEvent(40, "social", PAUSED),
        )
        assertEquals(40, UsageMath.foregroundMillis(events, tracked, 0, 1000))
    }
}

class GardenTest {
    private fun fresh() = Garden.start(baselineMin = 100, floorMin = 30, today = 10)

    @Test
    fun startsBelowBaseline() {
        val s = fresh()
        assertEquals(90, s.budgetMin)
        assertEquals(9, s.lastSettledDay)
        assertEquals(Garden.DEFAULT_BUDGET_MIN, Garden.start(0, 30, 10).budgetMin)
    }

    @Test
    fun metDayRewardsSavedMinutesAndTightensBudget() {
        val s = Garden.settleDay(fresh(), 10, usedMin = 60)
        assertEquals(10 + 30, s.sunlight)
        assertEquals(1, s.streak)
        assertEquals(86, s.budgetMin)
    }

    @Test
    fun missedDayNeverTakesSunlightAndEasesBudget() {
        val s0 = Garden.settleDay(fresh(), 10, 60)
        val s = Garden.settleDay(s0, 11, 95)
        assertEquals(s0.sunlight + 2, s.sunlight) // (100 - 95) / 2
        assertEquals(0, s.streak)
        assertEquals(90, s.budgetMin)
        assertFalse(s.history.last().met)
        assertEquals(s0.sunlight, Garden.settleDay(s0, 11, 500).sunlight)
    }

    @Test
    fun budgetRespectsFloorAndBaseline() {
        var s = fresh()
        repeat(100) { s = Garden.settleDay(s, 10L + it, 0) }
        assertEquals(30, s.budgetMin)
        repeat(100) { s = Garden.settleDay(s, 200L + it, 999) }
        assertEquals(100, s.budgetMin)
    }

    @Test
    fun restTokenProtectsStreak() {
        var s = fresh()
        repeat(7) { s = Garden.settleDay(s, 10L + it, 0) }
        assertEquals(1, s.restTokens)
        s = Garden.settleDay(s, 17, 999)
        assertEquals(7, s.streak)
        assertEquals(0, s.restTokens)
        assertTrue(s.history.last().usedRestToken)
        s = Garden.settleDay(s, 18, 999)
        assertEquals(0, s.streak)
        assertEquals(7, s.bestStreak)
    }

    @Test
    fun catchUpSettlesMissedDaysOnce() {
        val asked = mutableListOf<Long>()
        val s = Garden.catchUp(fresh(), today = 13) { asked += it; 0 }
        assertEquals(listOf(10L, 11L, 12L), asked)
        assertEquals(12, s.lastSettledDay)
        asked.clear()
        Garden.catchUp(s, today = 13) { asked += it; 0 }
        assertTrue(asked.isEmpty())
    }

    @Test
    fun catchUpSkipsDaysTooOldToMeasure() {
        val asked = mutableListOf<Long>()
        val s = Garden.catchUp(fresh(), today = 40) { asked += it; 0 }
        assertEquals(Garden.MAX_CATCH_UP_DAYS, asked.size)
        assertEquals(39, s.lastSettledDay)
    }

    @Test
    fun moodAndFocus() {
        assertEquals(Mood.GLOWING, Garden.mood(10, 90, false))
        assertEquals(Mood.DROWSY, Garden.mood(95, 90, false))
        assertEquals(Mood.RESTING, Garden.mood(95, 90, true))
        assertEquals(15, Garden.focusReward(60, 0))
        assertEquals(0, Garden.focusReward(60, 120_000))
    }

    @Test
    fun stagesAndKeepsakes() {
        assertEquals(Stage.SPORE, Stage.of(0))
        assertEquals(Stage.MOSSLING, Stage.of(250))
        assertEquals(Stage.BLOOM, Stage.next(250))
        val s = fresh().copy(sunlight = 120, focusSessions = 3)
        assertEquals(listOf(Keepsake.PEBBLE, Keepsake.DEWDROP, Keepsake.NIGHTCAP), Keepsake.unlocked(s))
    }
}
