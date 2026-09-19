package io.frontierlabs.namaz

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.frontierlabs.namaz.core.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val PK = ZoneId.of("Asia/Karachi")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NamazTheme { AppRoot(getSharedPreferences("namaz", Context.MODE_PRIVATE)) } }
    }
}

@Composable
fun NamazTheme(content: @Composable () -> Unit) {
    // The Surface is not decoration. Without one, Compose has no background to
    // derive a content colour from, so Text with no explicit colour falls back
    // to black -- which is why the month table was unreadable. The Surface
    // paints the dark background and sets the matching light text colour for
    // everything inside it.
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

private val GOLD = Color(0xFFD9B45B)
private val GREEN = Color(0xFF4EC27F)
private val MUTED = Color(0xFF9AA3AF)
private val FAINT = Color(0x1AFFFFFF)
private val FAINT2 = Color(0x40FFFFFF)
private val RED = Color(0xFFE05555)

@Composable
fun AppRoot(prefs: android.content.SharedPreferences) {
    var city by remember {
        mutableStateOf(
            Cities.byName(prefs.getString("city", null) ?: "") ?: Cities.default
        )
    }
    var settings by remember {
        mutableStateOf(
            Settings(
                method = runCatching {
                    CalcMethod.valueOf(prefs.getString("method", "KARACHI")!!)
                }.getOrDefault(CalcMethod.KARACHI),
                asr = runCatching {
                    AsrMethod.valueOf(prefs.getString("asr", "HANAFI")!!)
                }.getOrDefault(AsrMethod.HANAFI),
            )
        )
    }
    var tab by remember { mutableIntStateOf(0) }
    var pickingCity by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    fun saveCity(c: City) {
        city = c
        prefs.edit().putString("city", c.name).apply()
    }

    fun saveSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putString("method", s.method.name)
            .putString("asr", s.asr.name)
            .apply()
    }

    val today = remember { LocalDate.now(PK) }
    var shownMonth by remember { mutableIntStateOf(today.monthValue) }
    var shownYear by remember { mutableIntStateOf(today.year) }
    val times = remember(city, settings) {
        PrayerTimes.forDate(today.year, today.monthValue, today.dayOfMonth, city, settings)
    }

    Column(Modifier.fillMaxSize()) {
        // Header: city + settings
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 20.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable { pickingCity = true }) {
                Text("Namaz Timings", fontSize = 12.sp, color = MUTED)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(city.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("  ▼", fontSize = 12.sp, color = GOLD)
                }
                Text("${city.province}  ·  tap to change",
                    fontSize = 11.sp, color = MUTED)
            }
            TextButton(onClick = { showSettings = true }) {
                Text("Settings", color = GOLD, fontSize = 13.sp)
            }
        }

        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Today") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Month") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Qibla") })
        }

        when (tab) {
            0 -> TodayScreen(city, times, settings, prefs, today)
            2 -> QiblaScreen(city)
            else -> MonthScreen(
                city, settings, shownYear, shownMonth,
                isCurrentMonth = shownMonth == today.monthValue && shownYear == today.year,
                todayDay = today.dayOfMonth,
                onMonth = { shownMonth = it },
                onYear = { shownYear = it },
            )
        }
    }

    if (pickingCity) {
        CityPicker(onPick = { saveCity(it); pickingCity = false },
            onDismiss = { pickingCity = false })
    }
    if (showSettings) {
        SettingsDialog(settings, onSave = { saveSettings(it); showSettings = false },
            onDismiss = { showSettings = false })
    }
}

// --- tracking storage ------------------------------------------------------
// One StringSet per date. SharedPreferences hands back a set that must not be
// mutated, so every read copies it.

private fun dateKey(y: Int, m: Int, d: Int) = "done:%04d-%02d-%02d".format(y, m, d)

private fun readDone(prefs: android.content.SharedPreferences, y: Int, m: Int, d: Int)
    : Set<String> = prefs.getStringSet(dateKey(y, m, d), null)?.toSet() ?: emptySet()

private fun writeDone(
    prefs: android.content.SharedPreferences,
    y: Int, m: Int, d: Int, value: Set<String>,
) = prefs.edit().putStringSet(dateKey(y, m, d), value).apply()

@Composable
fun TodayScreen(
    city: City,
    t: DayTimes,
    settings: Settings,
    prefs: android.content.SharedPreferences,
    today: LocalDate,
) {
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
        mutableStateOf(readDone(prefs, today.year, today.monthValue, today.dayOfMonth))
    }
    fun toggle(name: String) {
        done = if (done.contains(name)) done - name else done + name
        writeDone(prefs, today.year, today.monthValue, today.dayOfMonth, done)
    }

    val streak = remember(done) {
        Tracker.currentStreak(today.year, today.monthValue, today.dayOfMonth) { y, m, d ->
            val set = if (y == today.year && m == today.monthValue && d == today.dayOfMonth)
                done else readDone(prefs, y, m, d)
            Tracker.isDayComplete(set)
        }
    }

    val current = Tracker.currentPrayer(nowClock, t)
    val (_, next, minsToNext) = PrayerTimes.nextPrayer(nowClock, t)
    val statuses = Tracker.dayStatuses(nowClock, t, done)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {

        // --- current prayer, its qaza time, and the countdown --------------
        Card(Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x22D9B45B))) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (current != null) {
                    val (name, startAt, qazaAt) = current
                    val left = Tracker.minutesUntil(nowClock, qazaAt)
                    val urgent = left <= 30
                    Text("CURRENT PRAYER", fontSize = 11.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = GOLD)
                    Text("started ${startAt.format12()}", fontSize = 12.sp, color = MUTED)
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = FAINT)
                    Spacer(Modifier.height(14.dp))
                    Text("BECOMES QAZA AT", fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Text(qazaAt.format12(), fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (urgent) RED else Color.Unspecified)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (left >= 60) "${left / 60}h ${left % 60}m left"
                        else "$left min left",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (urgent) RED else GREEN,
                    )
                    if (done.contains(name)) {
                        Spacer(Modifier.height(8.dp))
                        Text("\u2713 Marked as prayed", fontSize = 12.sp, color = GREEN)
                    }
                } else {
                    Text("NO PRAYER DUE NOW", fontSize = 11.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(next, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GOLD)
                    Text("begins in ${minsToNext / 60}h ${minsToNext % 60}m",
                        fontSize = 14.sp, color = MUTED)
                }
            }
        }

        // --- upcoming ------------------------------------------------------
        if (current != null) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("UP NEXT", fontSize = 10.sp, color = MUTED,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(next, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            t.list().firstOrNull { it.first == next }?.second?.format12()
                                ?: "\u2014",
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = GOLD)
                        Text("in ${minsToNext / 60}h ${minsToNext % 60}m",
                            fontSize = 11.sp, color = MUTED)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Sehri and Iftar ------------------------------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile("Sehri ends", t.sehriEnd.format12(), Modifier.weight(1f))
            InfoTile("Iftar", t.iftar.format12(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // --- streak ----------------------------------------------------------
        Card(Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (streak > 0) Color(0x224EC27F) else Color(0x14FFFFFF))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("STREAK", fontSize = 10.sp, color = MUTED,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (streak == 1) "1 day" else "$streak days",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (streak > 0) GREEN else MUTED,
                        )
                    }
                    Text("${Tracker.completedCount(done)} of 5 today",
                        fontSize = 13.sp, color = MUTED)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (Tracker.isDayComplete(done)) "All five prayed today."
                    else "Complete all five to extend your streak.",
                    fontSize = 11.5.sp, color = MUTED,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- the five prayers, tappable --------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("PRAYER", Modifier.weight(1.1f), fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Text("AZAN", Modifier.weight(0.9f), fontSize = 10.sp,
                        color = MUTED, fontWeight = FontWeight.SemiBold)
                    Text("QAZA AT", Modifier.weight(0.9f), fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(30.dp))
                }
                Spacer(Modifier.height(4.dp))
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
                        Modifier.fillMaxWidth().clickable { toggle(name) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1.1f)) {
                            Text(name, fontSize = 15.sp, color = tint,
                                fontWeight = if (status == PrayerStatus.DUE)
                                    FontWeight.Bold else FontWeight.Normal)
                            if (status == PrayerStatus.MISSED) {
                                Text("qaza \u2014 owed", fontSize = 10.sp, color = RED)
                            }
                        }
                        Text(startAt.format12(), Modifier.weight(0.9f), fontSize = 14.sp)
                        Text(endAt.format12(), Modifier.weight(0.9f), fontSize = 13.sp,
                            color = MUTED)
                        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when (status) {
                                    PrayerStatus.DONE -> "\u2713"
                                    PrayerStatus.MISSED -> "\u2715"
                                    else -> "\u25CB"
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
                Text("Tap a prayer to mark it prayed. Tap again to undo. " +
                    "Unmarked prayers turn red once their time has passed.",
                    fontSize = 11.5.sp, color = MUTED)
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ALSO TODAY", fontSize = 10.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                MiniRow("Sunrise", t.sunrise.format12())
                MiniRow("Tahajjud from", t.tahajjudStart.format12())
                MiniRow("Ishraq", "${t.ishraqStart.format12()} \u2013 ${t.ishraqEnd.format12()}")
                MiniRow("Chasht", "${t.chashtStart.format12()} \u2013 ${t.chashtEnd.format12()}")
                MiniRow("Avoid: after sunrise",
                    "${t.makruhAfterSunrise.first.format12()} \u2013 " +
                        t.makruhAfterSunrise.second.format12())
                MiniRow("Avoid: around midday",
                    "${t.makruhBeforeZuhr.first.format12()} \u2013 " +
                        t.makruhBeforeZuhr.second.format12())
                MiniRow("Avoid: before sunset",
                    "${t.makruhBeforeMaghrib.first.format12()} \u2013 " +
                        t.makruhBeforeMaghrib.second.format12())
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("${settings.method.label} \u00B7 Asr: ${settings.asr.label}",
            fontSize = 11.sp, color = MUTED)
        Spacer(Modifier.height(6.dp))
        Text("Calculated for ${city.name}. Local mosques sometimes adjust by a " +
            "few minutes \u2014 follow your masjid where they differ.",
            fontSize = 11.sp, color = MUTED)

        Spacer(Modifier.height(24.dp))
        Footer()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun InfoTile(label: String, value: String, mod: Modifier) {
    Card(mod) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = MUTED)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = GOLD)
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MUTED)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
fun MonthScreen(
    city: City,
    settings: Settings,
    year: Int,
    month: Int,
    isCurrentMonth: Boolean,
    todayDay: Int,
    onMonth: (Int) -> Unit,
    onYear: (Int) -> Unit,
) {
    val rows = remember(city, settings, year, month) {
        PrayerTimes.forMonth(year, month, city, settings)
    }
    var expanded by remember(year, month) { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize()) {

        // Month picker: all twelve, always reachable.
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MONTHS.size) { i ->
                val m = i + 1
                val on = m == month
                Box(
                    Modifier
                        .background(
                            if (on) GOLD else Color(0x22FFFFFF),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onMonth(m) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        MONTHS[i].take(3),
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color(0xFF1A1A1A) else MUTED,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${MONTHS[month - 1]} $year", Modifier.weight(1f),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onYear(year - 1) }) { Text("\u2039", color = GOLD) }
            Text("$year", fontSize = 13.sp, color = MUTED)
            TextButton(onClick = { onYear(year + 1) }) { Text("\u203A", color = GOLD) }
        }
        Text("Tap any day for the full timetable", Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
            fontSize = 11.sp, color = MUTED)

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            listOf("Day", "Fajr", "Zuhr", "Asr", "Maghrib", "Isha").forEachIndexed { i, h ->
                Text(h, Modifier.weight(if (i == 0) 0.6f else 1f), fontSize = 10.sp,
                    color = MUTED, fontWeight = FontWeight.SemiBold)
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                            "\u203A",
                            Modifier.width(18.dp).rotate(if (isOpen) 90f else 0f),
                            fontSize = 17.sp,
                            color = if (isOpen) GOLD else MUTED,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (isOpen) {
                        Column(Modifier.padding(16.dp, 2.dp, 16.dp, 16.dp)) {
                            Text(
                                "${MONTHS[month - 1]} $day, $year \u00B7 ${city.name}",
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
                                        Text(row.label, fontSize = 13.sp,
                                            color = if (avoid) Color(0xFFE8853F)
                                                    else Color.Unspecified)
                                        row.note?.let {
                                            Text(it, fontSize = 10.sp, color = MUTED)
                                        }
                                    }
                                    Text(row.start.format12(), Modifier.weight(0.85f),
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(row.end?.format12() ?: "\u2014",
                                        Modifier.weight(0.85f), fontSize = 12.sp,
                                        color = MUTED, textAlign = TextAlign.End)
                                }
                                HorizontalDivider(color = FAINT)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Left column is the start, right column is when the " +
                                    "window closes. After that a prayer becomes qaza.",
                                fontSize = 10.5.sp, color = MUTED,
                            )
                        }
                    }
                }
                HorizontalDivider(color = FAINT)
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Fajr is AM; Zuhr, Asr, Maghrib and Isha are PM.",
                        fontSize = 11.sp, color = MUTED)
                    Spacer(Modifier.height(20.dp))
                    Footer()
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@Composable
fun Footer() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = FAINT)
        Spacer(Modifier.height(14.dp))
        Text("Developed by Mansoor Ahmad", fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = GOLD)
        Spacer(Modifier.height(4.dp))
        Text("Times are calculated, not fetched. Follow your local masjid " +
            "where it differs.", fontSize = 10.5.sp, color = MUTED,
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
fun QiblaScreen(city: City) {
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
        Text("QIBLA FROM ${city.name.uppercase()}", fontSize = 11.sp, color = MUTED,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("${Math.round(qibla)}\u00B0  ${Qibla.compassPoint(qibla)}",
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GOLD)
        Text("from true north  \u00B7  ${Math.round(distance)} km to Makkah",
            fontSize = 12.sp, color = MUTED)

        Spacer(Modifier.height(24.dp))

        // The dial turns with the phone; the needle stays pointing at the Qibla.
        Box(contentAlignment = Alignment.Center) {
            CompassDial(
                headingTrue = trueHeading ?: 0f,
                qiblaBearing = qibla.toFloat(),
                live = trueHeading != null,
                aligned = aligned,
            )
        }

        Spacer(Modifier.height(20.dp))

        when {
            trueHeading == null -> {
                Card(colors = CardDefaults.cardColors(
                    containerColor = Color(0x22E8853F))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No compass on this phone", fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8853F), fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("The bearing above is still correct. Face true north, " +
                            "then turn ${Math.round(qibla)}\u00B0 clockwise.",
                            fontSize = 13.sp)
                    }
                }
            }
            aligned -> Text("You are facing the Qibla", fontSize = 17.sp,
                fontWeight = FontWeight.Bold, color = GREEN)
            turn != null && turn > 0 ->
                Text("Turn right ${Math.round(turn)}\u00B0", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = GOLD)
            turn != null ->
                Text("Turn left ${Math.round(-turn)}\u00B0", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = GOLD)
        }

        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("FOR AN ACCURATE READING", fontSize = 10.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("Hold the phone flat, screen up.", fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text("Move away from metal, speakers, laptops and magnets \u2014 " +
                    "they pull the compass badly.", fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text("If it drifts, wave the phone in a figure of eight to " +
                    "recalibrate.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("Magnetic declination here is ${"%.1f".format(declination)}\u00B0, " +
                    "already corrected for.", fontSize = 11.sp, color = MUTED)
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

        drawCircle(color = FAINT, radius = r, center = c,
            style = Stroke(width = 2f))
        drawCircle(color = FAINT, radius = r * 0.66f, center = c,
            style = Stroke(width = 1f))

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
            color = Color(0xFFE05555), radius = 7f,
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

@Composable
fun CityPicker(onPick: (City) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Cities.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose city", fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search city or province") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(340.dp)) {
                    items(results) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(c.name, Modifier.weight(1f), fontSize = 15.sp)
                            Text(c.province, fontSize = 12.sp, color = MUTED)
                        }
                        HorizontalDivider(color = FAINT)
                    }
                    if (results.isEmpty()) {
                        item {
                            Text("No city matched. Try a nearby larger city.",
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
fun SettingsDialog(current: Settings, onSave: (Settings) -> Unit, onDismiss: () -> Unit) {
    var method by remember { mutableStateOf(current.method) }
    var asr by remember { mutableStateOf(current.asr) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(current.copy(method = method, asr = asr)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Calculation", fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("ASR METHOD", fontSize = 10.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                AsrMethod.entries.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { asr = a }
                        .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = asr == a, onClick = { asr = a })
                        Text(a.label, fontSize = 14.sp)
                    }
                }
                Text("Most of Pakistan follows Hanafi. The two differ by around " +
                    "30–60 minutes.", fontSize = 11.sp, color = MUTED)

                Spacer(Modifier.height(16.dp))
                Text("FAJR / ISHA ANGLES", fontSize = 10.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                CalcMethod.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { method = m }
                        .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = method == m, onClick = { method = m })
                        Text(m.label, fontSize = 13.sp)
                    }
                }
                Text("Karachi is the standard across Pakistan. Change it only if " +
                    "your mosque follows something else.", fontSize = 11.sp, color = MUTED)
            }
        },
    )
}
