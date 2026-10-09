package app.mossling

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mossling.core.DayRecord
import app.mossling.core.Garden
import app.mossling.core.GardenState
import app.mossling.core.Keepsake
import app.mossling.core.Mood
import app.mossling.core.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val m = Mossling(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = MossDark, secondary = Sun, background = Cream, surface = Cream)) {
                Box(Modifier.fillMaxSize().background(Cream).safeDrawingPadding()) { App(m) }
            }
        }
    }
}

private enum class Screen { ONBOARDING, HOME, APPS, SETTINGS }

@Composable
private fun App(m: Mossling) {
    var screen by remember { mutableStateOf(if (m.store.garden == null) Screen.ONBOARDING else Screen.HOME) }
    BackHandler(screen == Screen.APPS || screen == Screen.SETTINGS) { screen = Screen.HOME }
    when (screen) {
        Screen.ONBOARDING -> Onboarding(m) { screen = Screen.HOME }
        Screen.HOME -> Home(m, onSettings = { screen = Screen.SETTINGS })
        Screen.APPS -> AppPicker(m, initial = m.store.tracked, confirm = "Save") { picked ->
            m.changeApps(picked)
            screen = Screen.HOME
        }
        Screen.SETTINGS -> Settings(m, onApps = { screen = Screen.APPS }, onDone = { screen = Screen.HOME })
    }
}

// ---------------------------------------------------------------- Onboarding

@Composable
private fun Onboarding(m: Mossling, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var hasAccess by remember { mutableStateOf(m.usage.hasAccess()) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var floor by remember { mutableFloatStateOf(30f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        hasAccess = m.usage.hasAccess()
        onPauseOrDispose { }
    }

    when (step) {
        0 -> Intro(
            title = "Meet Mossy",
            body = "Mossy is a little moss creature that grows in the quiet. " +
                "The less time you spend in the apps you choose, the more sunlight Mossy soaks up.\n\n" +
                "No punishments, ever. Mossy never gets sick or leaves. Busy days just make it drowsy, " +
                "and every minute you take back still helps it grow.",
            mood = Mood.GLOWING,
            button = "Hello, Mossy",
        ) { step = 1 }
        1 -> Intro(
            title = "Let Mossy see the glow",
            body = "Mossy needs Usage access to count minutes in the apps you pick. " +
                "Everything stays on this phone: no account, no servers, no ads.\n\n" +
                "Find Mossling in the list and switch it on, then come back.",
            mood = if (hasAccess) Mood.GLOWING else Mood.SQUINTING,
            button = if (hasAccess) "Continue" else "Open Usage access",
        ) {
            if (hasAccess) step = 2 else context.startActivity(m.usage.accessSettingsIntent())
        }
        2 -> AppPicker(m, initial = null, confirm = "These ones") { picked = it; step = 3 }
        else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Your lowest daily budget", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mossy measures your last week and starts your budget a little below it. " +
                    "Each day you stay within it, tomorrow's budget eases down 5%. If a day runs over, " +
                    "it eases back up. It will never go below this floor:",
                color = Ink.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            Text("${floor.toInt()} min / day", style = MaterialTheme.typography.titleLarge, color = MossDark)
            Slider(value = floor, onValueChange = { floor = it }, valueRange = 5f..180f, steps = 34)
            Spacer(Modifier.height(24.dp))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch {
                        withContext(Dispatchers.IO) { m.setUp(picked, floor.toInt()) }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Measuring your week…" else "Plant Mossy") }
        }
    }
}

@Composable
private fun Intro(title: String, body: String, mood: Mood, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mossy(Stage.MOSSLING, mood, Modifier.size(220.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

// ---------------------------------------------------------------- App picker

private data class AppRow(val app: InstalledApp, val weeklyMin: Int)

@Composable
private fun AppPicker(m: Mossling, initial: Set<String>?, confirm: String, onConfirm: (Set<String>) -> Unit) {
    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var picked by remember { mutableStateOf(initial ?: emptySet()) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val minutes = m.usage.weeklyMinutesByPackage()
            m.usage.launchableApps()
                .map { AppRow(it, minutes[it.packageName] ?: 0) }
                .sortedWith(compareByDescending<AppRow> { it.app.packageName in UsageSource.SUGGESTED }.thenByDescending { it.weeklyMin })
        }
        if (initial == null) {
            picked = loaded.filter { it.app.packageName in UsageSource.SUGGESTED }.map { it.app.packageName }.toSet()
        }
        rows = loaded
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Which apps dim Mossy's light?", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(
            "Pick the ones you'd like to use less. Time in other apps doesn't count.",
            color = Ink.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(8.dp))
        val list = rows
        if (list == null) {
            Text("Looking around your phone…", color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list.orEmpty(), key = { it.app.packageName }) { row ->
                val on = row.app.packageName in picked
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { picked = if (on) picked - row.app.packageName else picked + row.app.packageName }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.app.label, color = Ink)
                        if (row.weeklyMin > 0) {
                            Text("${formatMin(row.weeklyMin / 7)} a day last week", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
        Button(
            enabled = picked.isNotEmpty(),
            onClick = { onConfirm(picked) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text(if (picked.isEmpty()) "Pick at least one app" else "$confirm (${picked.size})") }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun Home(m: Mossling, onSettings: () -> Unit) {
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var resumed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    LaunchedEffect(resumed, tick) {
        if (!resumed) return@LaunchedEffect
        m.schedule()
        while (true) {
            snap = withContext(Dispatchers.IO) { m.refresh() }
            m.store.pendingNote?.let { note = it; m.store.pendingNote = null }
            delay(if (snap?.focus != null) 5_000 else 30_000)
        }
    }

    val s = snap
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waking Mossy…", color = Ink.copy(alpha = 0.6f))
        }
        return
    }
    val g = s.garden
    val stage = Stage.of(g.sunlight)
    val sky = when (s.mood) {
        Mood.RESTING -> Night.copy(alpha = 0.85f)
        Mood.DROWSY -> Color(0xFFD9D4C3)
        Mood.SQUINTING -> Color(0xFFE8E4D2)
        else -> Color(0xFFE4EED5)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stage.title, style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.SemiBold)
                    val next = Stage.next(g.sunlight)
                    Text(
                        if (next == null) "☀️ ${g.sunlight} sunlight, fully grown"
                        else "☀️ ${g.sunlight} sunlight · ${next.minSunlight - g.sunlight} to ${next.title}",
                        color = Ink.copy(alpha = 0.7f),
                    )
                }
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!s.hasAccess) item {
            Notice("Mossy can't see your usage right now. Turn Usage access back on so today counts.", "Open settings") {
                context.startActivity(m.usage.accessSettingsIntent())
            }
        }
        note?.let { n -> item { Notice(n, "Okay") { note = null } } }
        item {
            Box(
                Modifier.fillMaxWidth().height(260.dp).background(sky, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Mossy(stage, s.mood, Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Keepsake.unlocked(g).take(6).forEach { Text(it.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 4.dp)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(s.mood.line, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
        item { TodayCard(s.usedTodayMin, g.budgetMin) }
        item { FocusCard(s.focus, onStart = { min -> m.startFocus(min); tick++ }, onCancel = { m.cancelFocus(); tick++ }) }
        item { StreakCard(g.streak, g.bestStreak, g.restTokens, g.daysMet) }
        item { WeekCard(g.history, s.usedTodayMin, g.budgetMin) }
        item { KeepsakeCard(g) }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun TodayCard(used: Int, budget: Int) = Section {
    Text("Today", fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(6.dp))
    Text(
        if (used <= budget) "${formatMin(used)} of ${formatMin(budget)} · ${formatMin(budget - used)} of quiet left to keep"
        else "${formatMin(used)} of ${formatMin(budget)}. Time away from here still earns sunlight.",
        color = Ink.copy(alpha = 0.8f),
    )
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { (used.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f) },
        color = if (used <= budget) Moss else Sun,
        trackColor = Cream,
        modifier = Modifier.fillMaxWidth().height(10.dp),
    )
}

@Composable
private fun FocusCard(focus: FocusSession?, onStart: (Int) -> Unit, onCancel: () -> Unit) = Section {
    Text("Quiet time", fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(6.dp))
    if (focus == null) {
        Text(
            "Tuck Mossy in for a nap. Stay out of your picked apps until it wakes for bonus sunlight.",
            color = Ink.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 45, 90).forEach { min ->
                OutlinedButton(onClick = { onStart(min) }, modifier = Modifier.weight(1f)) { Text("$min min") }
            }
        }
    } else {
        val left = ((focus.endMillis - System.currentTimeMillis() + 59_999) / 60_000).coerceAtLeast(1)
        Text(
            "Mossy is asleep. Wakes in about $left min (+${Garden.focusReward(focus.durationMin, 0)} sunlight if it stays quiet).",
            color = Ink.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Wake Mossy early") }
    }
}

@Composable
private fun StreakCard(streak: Int, best: Int, tokens: Int, daysMet: Int) = Section {
    Row {
        Stat("🔥 $streak", "day streak", Modifier.weight(1f))
        Stat("🏆 $best", "best", Modifier.weight(1f))
        Stat("🛌 $tokens", "rest days", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Rest days protect your streak on an off day. You earn one every ${Garden.MET_DAYS_PER_TOKEN} days " +
            "within budget (holds ${Garden.MAX_REST_TOKENS}). $daysMet days within budget so far.",
        fontSize = 12.sp,
        color = Ink.copy(alpha = 0.6f),
    )
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
    }
}

@Composable
private fun WeekCard(history: List<DayRecord>, todayUsed: Int, todayBudget: Int) = Section {
    Text("This week", fontWeight = FontWeight.SemiBold, color = Ink)
    Text("Bars are minutes in your picked apps; the dashed line is that day's budget.", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
    Spacer(Modifier.height(10.dp))
    val today = LocalDate.now().toEpochDay()
    val days = (6 downTo 0).map { today - it }
    val byDay = history.associateBy { it.epochDay }
    val bars = days.map { d ->
        if (d == today) Triple(todayUsed, todayBudget, true) else byDay[d]?.let { Triple(it.usedMin, it.budgetMin, true) } ?: Triple(0, 0, false)
    }
    val max = bars.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1).toFloat()
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / 7
        val barW = slot * 0.5f
        bars.forEachIndexed { i, (used, budget, known) ->
            val x = slot * i + (slot - barW) / 2
            if (!known) return@forEachIndexed
            val bh = size.height * used / max
            drawRoundRect(
                if (used <= budget) Moss else Sun,
                Offset(x, size.height - bh),
                Size(barW, bh.coerceAtLeast(3f)),
                CornerRadius(8f, 8f),
            )
            val by = size.height - size.height * budget / max
            drawLine(
                Ink.copy(alpha = 0.5f),
                Offset(x - slot * 0.15f, by),
                Offset(x + barW + slot * 0.15f, by),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }
    }
    Row {
        days.forEach { d ->
            Text(
                LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                fontSize = 12.sp,
                color = Ink.copy(alpha = if (d == today) 1f else 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KeepsakeCard(g: GardenState) = Section {
    Text("Keepsakes Mossy found", fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Keepsake.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { k ->
                val has = k.unlockedBy(g)
                Column(Modifier.weight(1f).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (has) k.emoji else "❔", fontSize = 26.sp)
                    Text(
                        if (has) k.title else k.hint,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Ink.copy(alpha = if (has) 0.9f else 0.5f),
                    )
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun Settings(m: Mossling, onApps: () -> Unit, onDone: () -> Unit) {
    val g = m.store.garden ?: return
    var floor by remember { mutableFloatStateOf(g.floorMin.toFloat()) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(16.dp))
        Text("Tracking ${m.store.tracked.size} apps", color = Ink)
        OutlinedButton(onClick = onApps) { Text("Change apps") }
        Text(
            "Changing apps re-measures your last week and resets today's budget. Sunlight and keepsakes stay.",
            fontSize = 12.sp,
            color = Ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))
        Text("Lowest daily budget: ${floor.toInt()} min", color = Ink)
        Slider(value = floor, onValueChange = { floor = it }, valueRange = 5f..180f, steps = 34)
        Text(
            "Today's budget: ${formatMin(maxOf(g.budgetMin, floor.toInt()))} · before Mossling: ${formatMin(g.baselineMin)}",
            fontSize = 12.sp,
            color = Ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { m.setFloor(floor.toInt()); onDone() },
            colors = ButtonDefaults.buttonColors(containerColor = MossDark),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Done") }
    }
}

private fun formatMin(min: Int): String = if (min < 60) "$min min" else "${min / 60}h ${min % 60}m"
