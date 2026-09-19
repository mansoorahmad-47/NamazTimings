package io.frontierlabs.namaz

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            Tab(tab == 1, { tab = 1 }, text = { Text("This month") })
        }

        when (tab) {
            0 -> TodayScreen(city, times, settings)
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

@Composable
fun TodayScreen(city: City, t: DayTimes, settings: Settings) {
    val now = remember { LocalTime.now(PK) }
    val nowClock = Clock(now.hour * 60 + now.minute)
    val (current, next, mins) = PrayerTimes.nextPrayer(nowClock, t)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {

        // Next prayer
        Card(Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x22D9B45B))) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEXT PRAYER", fontSize = 11.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(next, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GOLD)
                Text("in ${mins / 60}h ${mins % 60}m", fontSize = 15.sp, color = MUTED)
                Spacer(Modifier.height(8.dp))
                Text("Currently in $current", fontSize = 12.sp, color = MUTED)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Sehri and Iftar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile("Sehri ends", t.sehriEnd.format12(), Modifier.weight(1f))
            InfoTile("Iftar", t.iftar.format12(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // Prayer windows
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("PRAYER", Modifier.weight(1.1f), fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold)
                    Text("AZAN / START", Modifier.weight(1f), fontSize = 10.sp,
                        color = MUTED, fontWeight = FontWeight.SemiBold)
                    Text("ENDS", Modifier.weight(1f), fontSize = 10.sp, color = MUTED,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = FAINT)
                t.list().forEach { (name, start, end) ->
                    val active = nowClock.minutes >= start.minutes &&
                        nowClock.minutes < end.minutes
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, Modifier.weight(1.1f), fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) GREEN else Color.Unspecified)
                        Text(start.format12(), Modifier.weight(1f), fontSize = 14.sp,
                            fontWeight = FontWeight.Medium)
                        Text(end.format12(), Modifier.weight(1f), fontSize = 13.sp,
                            color = MUTED, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = FAINT)
                }
                Spacer(Modifier.height(10.dp))
                Text("After the “ends” time the prayer becomes qaza and must be " +
                    "made up.", fontSize = 11.5.sp, color = MUTED)
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ALSO TODAY", fontSize = 10.sp, color = MUTED,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                MiniRow("Sunrise", t.sunrise.format12())
                MiniRow("Last third of night (Tahajjud)", t.lastThirdOfNight.format12())
                MiniRow("Avoid: after sunrise",
                    "${t.makruhAfterSunrise.first.format12()} – " +
                        t.makruhAfterSunrise.second.format12())
                MiniRow("Avoid: around midday",
                    "${t.makruhBeforeZuhr.first.format12()} – " +
                        t.makruhBeforeZuhr.second.format12())
                MiniRow("Avoid: before sunset",
                    "${t.makruhBeforeMaghrib.first.format12()} – " +
                        t.makruhBeforeMaghrib.second.format12())
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("${settings.method.label} · Asr: ${settings.asr.label}",
            fontSize = 11.sp, color = MUTED)
        Spacer(Modifier.height(6.dp))
        Text("Calculated for ${city.name} (${city.lat}°N, ${city.lon}°E). " +
            "Local mosques sometimes adjust by a few minutes — follow your " +
            "masjid where they differ.", fontSize = 11.sp, color = MUTED)
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
