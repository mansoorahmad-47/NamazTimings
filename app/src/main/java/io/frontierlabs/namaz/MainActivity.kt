package io.frontierlabs.namaz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.frontierlabs.namaz.core.*
import java.time.LocalDate
import java.time.LocalTime

/** Current language's strings, available to every composable. */
val LocalStr = staticCompositionLocalOf { Strings.EN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        setContent { NamazTheme { AppRoot(Prefs.get(this)) } }
    }

    override fun onResume() {
        super.onResume()
        // Re-arm on every return to the app. This is the cheap safety net
        // that covers a dropped alarm, a day that rolled over while the app
        // sat in the background, or a permission that was granted in
        // Android's settings rather than in the app.
        runCatching { Scheduler.armAll(this) }
    }
}

// --- palette ---------------------------------------------------------------

private val GOLD = Color(0xFFD9B45B)
private val GOLD_DIM = Color(0xFF8C7433)
private val GREEN = Color(0xFF4EC27F)
private val MUTED = Color(0xFF9AA3AF)
private val FAINT = Color(0x1AFFFFFF)
private val FAINT2 = Color(0x40FFFFFF)
private val RED = Color(0xFFE05555)
private val AMBER = Color(0xFFE8853F)

private val BG_TOP = Color(0xFF0B0F16)
private val BG_BOTTOM = Color(0xFF13161D)
private val CARD = Color(0xFF181C25)
private val STROKE = Color(0x14FFFFFF)

private val R20 = RoundedCornerShape(20.dp)
private val R16 = RoundedCornerShape(16.dp)
private val R12 = RoundedCornerShape(12.dp)

@Composable
fun NamazTheme(content: @Composable () -> Unit) {
    // The Surface is not decoration. Without one, Compose has no background to
    // derive a content colour from, so Text with no explicit colour falls back
    // to black -- which is why the month table was unreadable. The Surface
    // paints the base and sets the matching light text colour for everything
    // inside it; the gradient on top is only there to stop a large dark screen
    // looking flat.
    MaterialTheme(colorScheme = darkColorScheme(background = BG_TOP, surface = CARD)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BG_TOP, BG_BOTTOM))),
            ) { content() }
        }
    }
}

/**
 * The one card shape used everywhere.
 *
 * A hairline border rather than a shadow: on a dark gradient an elevation
 * shadow is invisible, so the only thing separating a card from the page is
 * its edge.
 */
@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    fill: Color = CARD,
    stroke: Color = STROKE,
    shape: androidx.compose.ui.graphics.Shape = R20,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, stroke), shape),
        content = content,
    )
}

@Composable
private fun GradientPanel(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    stroke: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(R20)
            .background(Brush.verticalGradient(colors))
            .border(BorderStroke(1.dp, stroke), R20),
        content = content,
    )
}

@Composable
fun AppRoot(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current

    var city by remember { mutableStateOf(Prefs.city(prefs)) }
    var settings by remember { mutableStateOf(Prefs.settings(prefs)) }
    var lang by remember { mutableStateOf(Prefs.lang(prefs)) }
    var tab by remember { mutableIntStateOf(0) }
    var pickingCity by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateDecision?>(null) }

    // Android 13+ will not show anything at all until this is granted, and it
    // has to be asked for while a screen is up -- an alarm receiver cannot
    // request it. So it is asked once, on first launch.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { runCatching { Scheduler.armAll(context) } }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // Fails open by design: any network or parsing problem leaves `update`
    // null and the app fully usable. See UpdateCheck's documentation.
    LaunchedEffect(Unit) {
        val decision = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            UpdateChecker.fetch(context)
        }
        if (decision.action != UpdateAction.NONE) update = decision
    }

    fun saveLang(l: Lang) {
        lang = l
        prefs.edit().putString("lang", l.name).apply()
        // The channel names in Android's own settings are in the app's
        // language, so they have to be rewritten when it changes.
        Notifications.ensureChannels(context)
    }

    fun saveCity(c: City) {
        city = c
        prefs.edit().putString("city", c.name).apply()
        // Moving 300 km west shifts every alarm by twenty minutes.
        runCatching { Scheduler.armAll(context) }
    }

    fun saveSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putString("method", s.method.name)
            .putString("asr", s.asr.name)
            .apply()
        runCatching { Scheduler.armAll(context) }
    }

    val today = remember { LocalDate.now(PK) }
    var shownMonth by remember { mutableIntStateOf(today.monthValue) }
    var shownYear by remember { mutableIntStateOf(today.year) }
    val times = remember(city, settings) {
        PrayerTimes.forDate(today.year, today.monthValue, today.dayOfMonth, city, settings)
    }

    CompositionLocalProvider(
        LocalStr provides Strings.of(lang),
        LocalLayoutDirection provides
            if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        val S = LocalStr.current

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp, 22.dp, 16.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable { pickingCity = true }) {
                    Text(
                        S.appName, fontSize = 11.sp, color = GOLD_DIM,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Strings.cityName(lang, city.name), fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0x22D9B45B))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) { Text("▾", fontSize = 11.sp, color = GOLD) }
                    }
                    Text(
                        "${Strings.provinceName(lang, city.province)}  ·  ${S.tapToChange}",
                        fontSize = 11.sp, color = MUTED,
                    )
                }
                TextButton(onClick = { showSettings = true }) {
                    Text(S.settings, color = GOLD, fontSize = 13.sp)
                }
            }

            PillTabs(
                selected = tab,
                labels = listOf(S.tabToday, S.tabMonth, S.tabQibla),
                onSelect = { tab = it },
            )

            when (tab) {
                0 -> TodayScreen(city, times, settings, prefs, today, lang)
                2 -> QiblaScreen(city, lang)
                else -> MonthScreen(
                    city, settings, shownYear, shownMonth, lang,
                    isCurrentMonth = shownMonth == today.monthValue && shownYear == today.year,
                    todayDay = today.dayOfMonth,
                    onMonth = { shownMonth = it },
                    onYear = { shownYear = it },
                )
            }
        }

        if (pickingCity) {
            CityPicker(
                lang,
                onPick = { saveCity(it); pickingCity = false },
                onDismiss = { pickingCity = false },
            )
        }
        if (showSettings) {
            SettingsDialog(
                settings, lang, prefs,
                onSave = { st, l -> saveSettings(st); saveLang(l); showSettings = false },
                onDismiss = { showSettings = false },
            )
        }
        update?.let { UpdateDialog(it) { update = null } }
    }
}

/**
 * A segmented control instead of a Material TabRow.
 *
 * The underline indicator sat oddly against the cards, and this also mirrors
 * cleanly in Urdu without the indicator animating to the wrong side.
 */
@Composable
private fun PillTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(R16)
            .background(Color(0x14FFFFFF))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(R12)
                    .background(if (on) GOLD else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color(0xFF14161C) else MUTED,
                )
            }
        }
    }
}

@Composable
fun TodayScreen(
    city: City,
    t: DayTimes,
    settings: Settings,
    prefs: android.content.SharedPreferences,
    today: LocalDate,
    lang: Lang,
) {
    val S = LocalStr.current
    // Ticks so the countdown stays live without a service.
    var nowClock by remember {
        mutableStateOf(LocalTime.now(PK).let { Clock(it.hour * 60 + it.minute) })
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            val n = LocalTime.now(PK)
            nowClock = Clock(n.hour * 60 + n.minute)
        }
    }

    var done by remember {
        mutableStateOf(Prefs.readDone(prefs, today.year, today.monthValue, today.dayOfMonth))
    }
    fun toggle(name: String) {
        done = if (done.contains(name)) done - name else done + name
        Prefs.writeDone(prefs, today.year, today.monthValue, today.dayOfMonth, done)
    }

    var showHistory by remember { mutableStateOf(false) }
    var historyVersion by remember { mutableIntStateOf(0) }

    val streak = remember(done, historyVersion) {
        streakSummary(today.year, today.monthValue, today.dayOfMonth) { y, m, d ->
            val set = if (y == today.year && m == today.monthValue && d == today.dayOfMonth)
                done else Prefs.readDone(prefs, y, m, d)
            Tracker.isDayComplete(set)
        }
    }

    val current = Tracker.currentPrayer(nowClock, t)
    val (_, next, minsToNext) = PrayerTimes.nextPrayer(nowClock, t)
    val statuses = Tracker.dayStatuses(nowClock, t, done)

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
    ) {

        // --- current prayer, its qaza time, and the countdown --------------
        if (current != null) {
            val (name, startAt, qazaAt) = current
            val left = Tracker.minutesUntil(nowClock, qazaAt)
            val total = ((qazaAt.minutes - startAt.minutes) + 1440) % 1440
            val fraction = if (total <= 0) 0f else left.toFloat() / total.toFloat()
            val urgent = left <= 30

            GradientPanel(
                Modifier.fillMaxWidth(),
                colors = if (urgent) listOf(Color(0x33E05555), Color(0x11E05555))
                         else listOf(Color(0x33D9B45B), Color(0x0FD9B45B)),
                stroke = if (urgent) Color(0x44E05555) else Color(0x33D9B45B),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        S.currentPrayer, fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(14.dp))

                    // The ring drains as the window closes -- how much time is
                    // left is the one thing worth seeing without reading.
                    CountdownRing(fraction = fraction, urgent = urgent) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Strings.prayerName(lang, name), fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (urgent) RED else GOLD,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (left >= 60) "${left / 60}h ${left % 60}m"
                                else "$left min",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = if (urgent) RED else GREEN,
                            )
                            Text(S.left, fontSize = 10.sp, color = MUTED)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(S.started, fontSize = 9.sp, color = MUTED,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(startAt.format12(), fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(S.becomesQazaAt, fontSize = 9.sp, color = MUTED,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(
                                qazaAt.format12(), fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (urgent) RED else Color.Unspecified,
                            )
                        }
                    }

                    if (done.contains(name)) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0x224EC27F))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text("✓  ${S.markedAsPrayed}", fontSize = 12.sp,
                                color = GREEN, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            GradientPanel(
                Modifier.fillMaxWidth(),
                colors = listOf(Color(0x22D9B45B), Color(0x0AD9B45B)),
                stroke = Color(0x26D9B45B),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(S.noPrayerDue, fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(Strings.prayerName(lang, next), fontSize = 32.sp,
                        fontWeight = FontWeight.Bold, color = GOLD)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${S.beginsIn} ${minsToNext / 60}h ${minsToNext % 60}m",
                        fontSize = 13.sp, color = MUTED,
                    )
                }
            }
        }

        // --- upcoming ------------------------------------------------------
        if (current != null) {
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(S.upNext, fontSize = 9.sp, color = MUTED,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(Strings.prayerName(lang, next), fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            t.list().firstOrNull { it.first == next }?.second?.format12()
                                ?: "—",
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = GOLD,
                        )
                        Text("${minsToNext / 60}h ${minsToNext % 60}m",
                            fontSize = 11.sp, color = MUTED)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Sehri and Iftar ------------------------------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(S.sehriEnds, t.sehriEnd.format12(), Modifier.weight(1f))
            InfoTile(S.iftar, t.iftar.format12(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        StreakCard(
            summary = streak,
            doneToday = done,
            onOpenHistory = { showHistory = true },
        )

        Spacer(Modifier.height(12.dp))

        // --- the five prayers, tappable --------------------------------------
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Text(S.prayer, Modifier.weight(1.1f), fontSize = 9.sp, color = MUTED,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(S.azan, Modifier.weight(0.9f), fontSize = 9.sp,
                        color = MUTED, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(S.qazaAt, Modifier.weight(0.9f), fontSize = 9.sp, color = MUTED,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.width(30.dp))
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = FAINT)

                t.list().forEachIndexed { i, (name, startAt, endAt) ->
                    val status = statuses[i].second
                    val tint = when (status) {
                        PrayerStatus.DONE -> GREEN
                        PrayerStatus.MISSED -> RED
                        PrayerStatus.DUE -> GOLD
                        PrayerStatus.UPCOMING -> Color.Unspecified
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggle(name) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // A coloured spine on the active row, so the prayer
                        // you are in is findable at a glance.
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(if (status == PrayerStatus.DUE) 30.dp else 18.dp)
                                .clip(CircleShape)
                                .background(
                                    when (status) {
                                        PrayerStatus.DONE -> GREEN
                                        PrayerStatus.MISSED -> RED
                                        PrayerStatus.DUE -> GOLD
                                        PrayerStatus.UPCOMING -> Color.Transparent
                                    }
                                ),
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1.1f)) {
                            Text(
                                Strings.prayerName(lang, name), fontSize = 15.sp, color = tint,
                                fontWeight = if (status == PrayerStatus.DUE)
                                    FontWeight.Bold else FontWeight.Normal,
                            )
                            if (status == PrayerStatus.MISSED) {
                                Text(S.qazaOwed, fontSize = 10.sp, color = RED)
                            }
                        }
                        Text(startAt.format12(), Modifier.weight(0.9f), fontSize = 14.sp)
                        Text(endAt.format12(), Modifier.weight(0.9f), fontSize = 13.sp,
                            color = MUTED)
                        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when (status) {
                                    PrayerStatus.DONE -> "✓"
                                    PrayerStatus.MISSED -> "✕"
                                    else -> "○"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status == PrayerStatus.UPCOMING) FAINT2 else tint,
                            )
                        }
                    }
                    HorizontalDivider(color = FAINT)
                }
                Spacer(Modifier.height(10.dp))
                Text(S.tapToMark, fontSize = 11.5.sp, color = MUTED)
            }
        }

        Spacer(Modifier.height(12.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(S.alsoToday, fontSize = 9.sp, color = MUTED,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                MiniRow(S.sunrise, t.sunrise.format12())
                MiniRow(S.tahajjud, t.tahajjudStart.format12())
                MiniRow(S.ishraq,
                    "${t.ishraqStart.format12()} – ${t.ishraqEnd.format12()}")
                MiniRow(S.chasht,
                    "${t.chashtStart.format12()} – ${t.chashtEnd.format12()}")
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = FAINT)
                Spacer(Modifier.height(6.dp))
                MiniRow(S.avoidPrayer,
                    "${t.makruhAfterSunrise.first.format12()} – " +
                        t.makruhAfterSunrise.second.format12(), AMBER)
                MiniRow(S.avoidPrayer,
                    "${t.makruhBeforeZuhr.first.format12()} – " +
                        t.makruhBeforeZuhr.second.format12(), AMBER)
                MiniRow(S.avoidPrayer,
                    "${t.makruhBeforeMaghrib.first.format12()} – " +
                        t.makruhBeforeMaghrib.second.format12(), AMBER)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("${settings.method.label} · ${S.asr}: ${settings.asr.label}",
            fontSize = 11.sp, color = MUTED)
        Spacer(Modifier.height(4.dp))
        Text(S.followMasjid, fontSize = 11.sp, color = MUTED)

        Spacer(Modifier.height(24.dp))
        Footer()
        Spacer(Modifier.height(40.dp))
    }

    if (showHistory) {
        CalendarDialog(
            prefs = prefs,
            today = today,
            lang = lang,
            onDismiss = { showHistory = false },
            onChanged = {
                historyVersion++
                done = Prefs.readDone(prefs, today.year, today.monthValue, today.dayOfMonth)
            },
        )
    }
}

/**
 * The ring around the current prayer. [fraction] is how much of the window is
 * still open, so it empties as the deadline approaches rather than filling.
 */
@Composable
private fun CountdownRing(
    fraction: Float,
    urgent: Boolean,
    content: @Composable () -> Unit,
) {
    val ring = if (urgent) RED else GOLD
    Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 9.dp.toPx()
            val inset = width / 2f
            val arc = Size(size.width - width, size.height - width)
            drawArc(
                color = Color(0x14FFFFFF), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = Offset(inset, inset), size = arc,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            drawArc(
                color = ring, startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false, topLeft = Offset(inset, inset), size = arc,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Current streak and longest streak together.
 *
 * When the run going on now is the best there has been, saying so is the
 * whole reward; when an older run was longer, showing both gives the number
 * to aim at. Showing only one of the two would waste whichever case applies.
 */
@Composable
private fun StreakCard(
    summary: StreakSummary,
    doneToday: Set<String>,
    onOpenHistory: () -> Unit,
) {
    val S = LocalStr.current
    val live = summary.current > 0
    val record = live && summary.currentIsBest

    GradientPanel(
        Modifier.fillMaxWidth().clickable { onOpenHistory() },
        colors = when {
            record -> listOf(Color(0x334EC27F), Color(0x0F4EC27F))
            live -> listOf(Color(0x224EC27F), Color(0x0A4EC27F))
            else -> listOf(Color(0x12FFFFFF), Color(0x08FFFFFF))
        },
        stroke = if (live) Color(0x334EC27F) else STROKE,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(S.streak, fontSize = 9.sp, color = MUTED,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${summary.current} ${if (summary.current == 1) S.day else S.days}",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = if (live) GREEN else MUTED,
                    )
                }

                // The record column only earns its space once there is a
                // record to show.
                if (summary.best > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(S.longest, fontSize = 9.sp, color = MUTED,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (record) {
                                Text("★ ", fontSize = 14.sp, color = GOLD)
                            }
                            Text(
                                "${summary.best} ${if (summary.best == 1) S.day else S.days}",
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                color = GOLD,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Dots(Tracker.completedCount(doneToday))
                Spacer(Modifier.width(9.dp))
                Text(
                    "${Tracker.completedCount(doneToday)} ${S.ofFiveToday}",
                    Modifier.weight(1f), fontSize = 12.sp, color = MUTED,
                )
                Text("${S.pastDays} ›", fontSize = 12.sp, color = GOLD,
                    fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    record -> S.thisIsYourBest
                    live -> "${summary.best - summary.current + 1} " +
                        "${S.days} ${S.toBeatYourBest}"
                    else -> S.noStreakYet
                },
                fontSize = 11.5.sp,
                color = if (record) GREEN else MUTED,
            )
        }
    }
}

/** Five dots: today's five prayers, filled as they are marked. */
@Composable
private fun Dots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (i < count) GREEN else Color(0x33FFFFFF)),
            )
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, mod: Modifier) {
    Panel(mod) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontSize = 11.sp, color = MUTED)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = GOLD)
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp,
            color = if (color == Color.Unspecified) MUTED else color)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
fun MonthScreen(
    city: City,
    settings: Settings,
    year: Int,
    month: Int,
    lang: Lang,
    isCurrentMonth: Boolean,
    todayDay: Int,
    onMonth: (Int) -> Unit,
    onYear: (Int) -> Unit,
) {
    val S = LocalStr.current
    val rows = remember(city, settings, year, month) {
        PrayerTimes.forMonth(year, month, city, settings)
    }
    var expanded by remember(year, month) { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize()) {

        // Month picker: all twelve, always reachable.
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(12) { i ->
                val m = i + 1
                val on = m == month
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) GOLD else Color(0x14FFFFFF))
                        .border(
                            BorderStroke(1.dp, if (on) Color.Transparent else STROKE),
                            CircleShape,
                        )
                        .clickable { onMonth(m) }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                ) {
                    Text(
                        Strings.monthName(lang, m).take(if (lang.rtl) 4 else 3),
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color(0xFF14161C) else MUTED,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${Strings.monthName(lang, month)} $year", Modifier.weight(1f),
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onYear(year - 1) }) { Text("‹", color = GOLD) }
            Text("$year", fontSize = 13.sp, color = MUTED)
            TextButton(onClick = { onYear(year + 1) }) { Text("›", color = GOLD) }
        }
        Text(S.tapAnyDay, Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
            fontSize = 11.sp, color = MUTED)

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            listOf(S.day, S.fajr, S.zuhr, S.asr, S.maghrib, S.isha)
                .forEachIndexed { i, h ->
                    Text(h, Modifier.weight(if (i == 0) 0.6f else 1f), fontSize = 10.sp,
                        color = MUTED, fontWeight = FontWeight.Bold)
                }
            Spacer(Modifier.width(18.dp))
        }
        HorizontalDivider(color = FAINT)

        LazyColumn(Modifier.weight(1f)) {
            items(rows) { (day, t) ->
                val isToday = isCurrentMonth && day == todayDay
                val isOpen = expanded == day
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isOpen -> Color(0x18FFFFFF)
                                isToday -> Color(0x22D9B45B)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { expanded = if (isOpen) -1 else day }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text("$day", Modifier.weight(0.6f), fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) GOLD else MUTED)
                        listOf(t.fajr, t.zuhr, t.asr, t.maghrib, t.isha).forEach {
                            Text(
                                it.format12().removeSuffix(" AM").removeSuffix(" PM"),
                                Modifier.weight(1f), fontSize = 12.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        // Chevron: points right when closed, down when open, so
                        // it is obvious the row opens rather than just sitting there.
                        Text(
                            "›",
                            Modifier.width(18.dp).rotate(if (isOpen) 90f else 0f),
                            fontSize = 17.sp,
                            color = if (isOpen) GOLD else MUTED,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (isOpen) {
                        Column(Modifier.padding(16.dp, 2.dp, 16.dp, 16.dp)) {
                            Text(
                                "${Strings.monthName(lang, month)} $day, $year · " +
                                    Strings.cityName(lang, city.name),
                                fontSize = 12.sp, color = GOLD,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(10.dp))
                            t.fullDay().forEach { row ->
                                val avoid = row.label == "Avoid prayer"
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1.25f)) {
                                        Text(
                                            Strings.prayerName(lang, row.label),
                                            fontSize = 13.sp,
                                            color = if (avoid) AMBER else Color.Unspecified,
                                        )
                                        row.note?.let {
                                            Text(it, fontSize = 10.sp, color = MUTED)
                                        }
                                    }
                                    Text(row.start.format12(), Modifier.weight(0.85f),
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(row.end?.format12() ?: "—",
                                        Modifier.weight(0.85f), fontSize = 12.sp,
                                        color = MUTED, textAlign = TextAlign.End)
                                }
                                HorizontalDivider(color = FAINT)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(S.windowNote, fontSize = 10.5.sp, color = MUTED)
                        }
                    }
                }
                HorizontalDivider(color = FAINT)
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(S.amPmNote, fontSize = 11.sp, color = MUTED)
                    Spacer(Modifier.height(20.dp))
                    Footer()
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun Footer() {
    val S = LocalStr.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = FAINT)
        Spacer(Modifier.height(14.dp))
        Text(S.developedBy, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = GOLD)
        Spacer(Modifier.height(4.dp))
        Text("${S.footerNote} ${S.followMasjid}", fontSize = 10.5.sp, color = MUTED,
            textAlign = TextAlign.Center)
    }
}

/**
 * Live compass heading in degrees from MAGNETIC north, or null when the phone
 * has no rotation-vector sensor. The listener is unregistered on dispose, so
 * the sensor does not keep running once this screen is left.
 */
@Composable
fun rememberMagneticHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var listener: SensorEventListener? = null

        if (sm != null && sensor != null) {
            val l = object : SensorEventListener {
                private val rotation = FloatArray(9)
                private val orientation = FloatArray(3)
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    heading = (deg + 360f) % 360f
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            listener = l
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose { listener?.let { sm?.unregisterListener(it) } }
    }
    return heading
}

@Composable
fun QiblaScreen(city: City, lang: Lang) {
    val S = LocalStr.current
    val qibla = remember(city) { Qibla.bearing(city) }
    val distance = remember(city) { Qibla.distanceKm(city) }

    // A compass reads magnetic north; the Qibla bearing is from TRUE north.
    // Without this correction the arrow is a couple of degrees out in Pakistan
    // and much further out elsewhere.
    val declination = remember(city) {
        GeomagneticField(
            city.lat.toFloat(), city.lon.toFloat(), 0f, System.currentTimeMillis()
        ).declination
    }

    val magnetic = rememberMagneticHeading()
    val trueHeading = magnetic?.let { (it + declination + 360f) % 360f }
    val turn = trueHeading?.let { Qibla.turnFrom(it.toDouble(), qibla) }
    val aligned = turn != null && kotlin.math.abs(turn) <= 5.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${S.qiblaFrom} ${Strings.cityName(lang, city.name)}", fontSize = 10.sp,
            color = MUTED, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Text("${Math.round(qibla)}°  ${Qibla.compassPoint(qibla)}",
            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = GOLD)
        Text("${S.fromTrueNorth}  ·  ${Math.round(distance)} ${S.kmToMakkah}",
            fontSize = 12.sp, color = MUTED)

        Spacer(Modifier.height(22.dp))

        // The dial turns with the phone; the needle stays pointing at the Qibla.
        Box(contentAlignment = Alignment.Center) {
            CompassDial(
                headingTrue = trueHeading ?: 0f,
                qiblaBearing = qibla.toFloat(),
                live = trueHeading != null,
                aligned = aligned,
            )
        }

        Spacer(Modifier.height(18.dp))

        when {
            trueHeading == null -> {
                Panel(fill = Color(0x1AE8853F), stroke = Color(0x33E8853F)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(S.noCompass, fontWeight = FontWeight.SemiBold,
                            color = AMBER, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(S.noCompassHelp, fontSize = 13.sp)
                    }
                }
            }
            aligned -> Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0x224EC27F))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(S.facingQibla, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = GREEN)
            }
            turn != null && turn > 0 ->
                Text("${S.turnRight} ${Math.round(turn)}°", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = GOLD)
            turn != null ->
                Text("${S.turnLeft} ${Math.round(-turn)}°", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = GOLD)
        }

        Spacer(Modifier.height(22.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(S.accurateReading, fontSize = 9.sp, color = MUTED,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Text(S.holdFlat, fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text(S.awayFromMetal, fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text(S.figureEight, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("${S.declinationNote} ${"%.1f".format(declination)}°.",
                    fontSize = 11.sp, color = MUTED)
            }
        }

        Spacer(Modifier.height(24.dp))
        Footer()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun CompassDial(headingTrue: Float, qiblaBearing: Float, live: Boolean, aligned: Boolean) {
    // Rotating the whole dial by -heading keeps north pointing at real north,
    // so the Qibla needle sits where you must physically turn to.
    val needle = if (live) qiblaBearing - headingTrue else qiblaBearing
    val ring = if (aligned) GREEN else GOLD

    Canvas(Modifier.size(260.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 14f

        drawCircle(color = FAINT, radius = r, center = c, style = Stroke(width = 2f))
        drawCircle(color = FAINT, radius = r * 0.66f, center = c, style = Stroke(width = 1f))

        // Tick marks every 15 degrees, turning with the phone.
        for (i in 0 until 24) {
            val a = Math.toRadians((i * 15f - if (live) headingTrue else 0f).toDouble())
            val long = i % 6 == 0
            val outer = r
            val inner = r - if (long) 16f else 8f
            drawLine(
                color = if (long) MUTED else FAINT,
                start = Offset(c.x + (inner * kotlin.math.sin(a)).toFloat(),
                               c.y - (inner * kotlin.math.cos(a)).toFloat()),
                end = Offset(c.x + (outer * kotlin.math.sin(a)).toFloat(),
                             c.y - (outer * kotlin.math.cos(a)).toFloat()),
                strokeWidth = if (long) 3f else 1.5f,
            )
        }

        // North marker.
        val na = Math.toRadians((-(if (live) headingTrue else 0f)).toDouble())
        drawCircle(
            color = RED, radius = 7f,
            center = Offset(c.x + ((r - 28f) * kotlin.math.sin(na)).toFloat(),
                            c.y - ((r - 28f) * kotlin.math.cos(na)).toFloat()),
        )

        // The Qibla needle.
        val qa = Math.toRadians(needle.toDouble())
        val tip = Offset(c.x + ((r - 24f) * kotlin.math.sin(qa)).toFloat(),
                         c.y - ((r - 24f) * kotlin.math.cos(qa)).toFloat())
        val leftA = qa + Math.PI * 0.82
        val rightA = qa - Math.PI * 0.82
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(c.x + (34f * kotlin.math.sin(leftA)).toFloat(),
                   c.y - (34f * kotlin.math.cos(leftA)).toFloat())
            lineTo(c.x, c.y)
            lineTo(c.x + (34f * kotlin.math.sin(rightA)).toFloat(),
                   c.y - (34f * kotlin.math.cos(rightA)).toFloat())
            close()
        }
        drawPath(path, color = ring)
        drawCircle(color = ring, radius = 9f, center = c)
    }
}

/**
 * Past days as a calendar, so a prayer can be marked once it has actually
 * been made up.
 *
 * A qaza prayer is still owed and still prayed. Without this the streak
 * punishes someone permanently for one late Fajr, which is the opposite of
 * what a streak is for.
 *
 * A calendar rather than a list because a month is the unit people think in:
 * the gaps in a streak are visible as gaps, and reaching back three weeks is
 * two taps instead of a long scroll. Future days are drawn but not tappable —
 * marking tomorrow's Fajr as prayed should not be possible.
 */
@Composable
fun CalendarDialog(
    prefs: android.content.SharedPreferences,
    today: LocalDate,
    lang: Lang,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val S = LocalStr.current
    var version by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var selected by remember { mutableStateOf<LocalDate?>(today) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.done) } },
        title = { Text(S.pastDays, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // --- month header ------------------------------------------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { shown = shown.minusMonths(1) }) {
                        Text("‹", fontSize = 20.sp, color = GOLD)
                    }
                    Text(
                        "${Strings.monthName(lang, shown.monthValue)} ${shown.year}",
                        Modifier.weight(1f),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    // Never page past the current month: there is nothing
                    // there to mark.
                    val canGoForward = shown.isBefore(today.withDayOfMonth(1))
                    TextButton(
                        onClick = { if (canGoForward) shown = shown.plusMonths(1) },
                        enabled = canGoForward,
                    ) {
                        Text("›", fontSize = 20.sp,
                            color = if (canGoForward) GOLD else Color(0x33FFFFFF))
                    }
                }

                Spacer(Modifier.height(4.dp))

                // --- weekday header, Sunday first --------------------------
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        Text(
                            Strings.weekdayShort(lang, i),
                            Modifier.weight(1f),
                            fontSize = 10.sp, color = MUTED,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // --- the grid ----------------------------------------------
                // dayOfWeek.value counts Monday as 1; %7 turns that into a
                // Sunday-first column index, which is how calendars are
                // printed in Pakistan.
                val lead = shown.dayOfWeek.value % 7
                val length = shown.lengthOfMonth()
                val cells = lead + length
                val weeks = (cells + 6) / 7

                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val index = w * 7 + c
                            val dayNumber = index - lead + 1
                            if (dayNumber < 1 || dayNumber > length) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val date = shown.withDayOfMonth(dayNumber)
                                val future = date.isAfter(today)
                                val count = run {
                                    version   // read so a write recomposes the grid
                                    Tracker.completedCount(
                                        Prefs.readDone(prefs, date.year,
                                            date.monthValue, date.dayOfMonth)
                                    )
                                }
                                DayCell(
                                    modifier = Modifier.weight(1f),
                                    day = dayNumber,
                                    count = count,
                                    isToday = date == today,
                                    isSelected = date == selected,
                                    future = future,
                                    onClick = { if (!future) selected = date },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(S.pastDaysHelp, fontSize = 11.sp, color = MUTED)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = FAINT)
                Spacer(Modifier.height(8.dp))

                // --- the selected day's prayers ----------------------------
                val d = selected
                if (d == null) {
                    Text(S.pickADate, fontSize = 13.sp, color = MUTED)
                } else {
                    val doneSet = run {
                        version
                        Prefs.readDone(prefs, d.year, d.monthValue, d.dayOfMonth)
                    }
                    Text(
                        "${Strings.weekdayShort(lang, d.dayOfWeek.value % 7)} " +
                            "${d.dayOfMonth} ${Strings.monthName(lang, d.monthValue)} " +
                            (if (d == today) "· ${S.today}" else ""),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GOLD,
                    )
                    Spacer(Modifier.height(4.dp))
                    Tracker.FARD.forEach { name ->
                        val on = doneSet.contains(name)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(R12)
                                .clickable {
                                    val next = if (on) doneSet - name else doneSet + name
                                    Prefs.writeDone(prefs, d.year, d.monthValue,
                                        d.dayOfMonth, next)
                                    version++
                                    onChanged()
                                }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (on) "✓" else "○",
                                Modifier.width(28.dp),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (on) GREEN else FAINT2,
                            )
                            Text(Strings.prayerName(lang, name), fontSize = 14.sp,
                                color = if (on) GREEN else Color.Unspecified)
                        }
                    }
                }
            }
        },
    )
}

/**
 * One square in the calendar. The fill says how the day went at a glance:
 * green for all five, gold for a partial day, nothing for a blank one.
 */
@Composable
private fun DayCell(
    modifier: Modifier,
    day: Int,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    future: Boolean,
    onClick: () -> Unit,
) {
    val fill = when {
        future -> Color.Transparent
        count == 5 -> Color(0x334EC27F)
        count > 0 -> Color(0x22D9B45B)
        else -> Color(0x0DFFFFFF)
    }
    val edge = when {
        isSelected -> GOLD
        isToday -> Color(0x66D9B45B)
        else -> Color.Transparent
    }

    Box(
        modifier
            .height(44.dp)
            .padding(2.dp)
            .clip(R12)
            .background(fill)
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, edge), R12)
            .clickable(enabled = !future) { onClick() }
            .alpha(if (future) 0.28f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    count == 5 -> GREEN
                    isToday -> GOLD
                    else -> Color.Unspecified
                },
            )
            if (!future && count in 1..4) {
                Text("$count", fontSize = 8.sp, color = GOLD)
            }
        }
    }
}

@Composable
fun UpdateDialog(decision: UpdateDecision, onDismiss: () -> Unit) {
    val S = LocalStr.current
    val context = LocalContext.current
    val info = decision.info ?: return
    val forced = decision.action == UpdateAction.REQUIRED

    AlertDialog(
        // A forced update cannot be dismissed by tapping away or pressing back.
        onDismissRequest = { if (!forced) onDismiss() },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                }
            }) { Text(S.downloadUpdate, color = GOLD, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (!forced) TextButton(onClick = onDismiss) { Text(S.later) }
        },
        title = {
            Text(if (forced) S.updateRequired else S.updateAvailable, fontSize = 17.sp)
        },
        text = {
            Column {
                if (forced) {
                    Text(S.updateBlockedNote, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Text(info.latestVersionName, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = GOLD)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.notes, fontSize = 13.sp)
                }
            }
        },
    )
}

@Composable
fun CityPicker(lang: Lang, onPick: (City) -> Unit, onDismiss: () -> Unit) {
    val S = LocalStr.current
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Cities.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.close) } },
        title = { Text(S.chooseCity, fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(S.searchCity) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(340.dp)) {
                    items(results) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(Strings.cityName(lang, c.name), Modifier.weight(1f),
                                fontSize = 15.sp)
                            Text(Strings.provinceName(lang, c.province), fontSize = 12.sp,
                                color = MUTED)
                        }
                        HorizontalDivider(color = FAINT)
                    }
                    if (results.isEmpty()) {
                        item {
                            Text(S.noCityMatched,
                                Modifier.padding(vertical = 16.dp),
                                fontSize = 13.sp, color = MUTED)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SettingsDialog(
    current: Settings,
    currentLang: Lang,
    prefs: android.content.SharedPreferences,
    onSave: (Settings, Lang) -> Unit,
    onDismiss: () -> Unit,
) {
    val S = LocalStr.current
    val context = LocalContext.current
    var method by remember { mutableStateOf(current.method) }
    var asr by remember { mutableStateOf(current.asr) }
    var lang by remember { mutableStateOf(currentLang) }
    var notifyPrayer by remember { mutableStateOf(Prefs.prayerAlerts(prefs)) }
    var notifyQaza by remember { mutableStateOf(Prefs.qazaAlerts(prefs)) }
    var notifyUpdate by remember { mutableStateOf(Prefs.updateAlerts(prefs)) }

    val blocked = !Notifications.allowed(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                Prefs.setPrayerAlerts(prefs, notifyPrayer)
                Prefs.setQazaAlerts(prefs, notifyQaza)
                Prefs.setUpdateAlerts(prefs, notifyUpdate)
                onSave(current.copy(method = method, asr = asr), lang)
                // The switches only take effect once the alarms are redrawn.
                runCatching { Scheduler.armAll(context) }
            }) { Text(S.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
        title = { Text(S.settings, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                SectionLabel(S.language)
                Lang.entries.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { lang = l }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == l, onClick = { lang = l })
                        Text(l.label, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel(S.notifications)
                if (blocked) {
                    // Every switch below is dead until Android's own
                    // permission is granted, so say so rather than letting
                    // someone turn them all on and wonder why nothing arrives.
                    Text(
                        "${S.allowNotifications}. ${S.allowNotificationsHelp}",
                        fontSize = 11.5.sp, color = AMBER,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                SwitchRow(S.notifyPrayer, S.notifyPrayerHelp, notifyPrayer) {
                    notifyPrayer = it
                }
                SwitchRow(S.notifyQaza, S.notifyQazaHelp, notifyQaza) { notifyQaza = it }
                SwitchRow(S.notifyUpdate, S.notifyUpdateHelp, notifyUpdate) {
                    notifyUpdate = it
                }
                Spacer(Modifier.height(4.dp))
                Text(S.exactAlarmsNote, fontSize = 10.5.sp, color = MUTED)

                Spacer(Modifier.height(14.dp))
                SectionLabel(S.asrMethod)
                AsrMethod.entries.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { asr = a }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = asr == a, onClick = { asr = a })
                        Text(a.label, fontSize = 14.sp)
                    }
                }
                Text(S.asrNote, fontSize = 11.sp, color = MUTED)

                Spacer(Modifier.height(14.dp))
                SectionLabel(S.fajrIshaAngles)
                CalcMethod.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { method = m }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = method == m, onClick = { method = m })
                        Text(m.label, fontSize = 13.sp)
                    }
                }
                Text(S.methodNote, fontSize = 11.sp, color = MUTED)
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.sp, color = GOLD,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    help: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(help, fontSize = 10.5.sp, color = MUTED)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
