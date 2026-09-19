import io.frontierlabs.namaz.core.*
import kotlin.math.abs

var passed = 0
var failed = 0

fun check(name: String, cond: Boolean, detail: Any? = "") {
    if (cond) { passed++; println("  PASS  $name") }
    else { failed++; println("  FAIL  $name   $detail") }
}

fun near(a: Double, b: Double, tol: Double) = abs(a - b) <= tol

fun main() {
    val isb = Cities.byName("Islamabad")!!
    val khi = Cities.byName("Karachi")!!
    val lhr = Cities.byName("Lahore")!!
    val pew = Cities.byName("Peshawar")!!

    println("\n-- Julian day (known reference values) --")
    // J2000.0 epoch: 2000-01-01 12:00 UT = JD 2451545.0, so 00:00 = ...544.5
    check("J2000 epoch", PrayerTimes.julianDay(2000, 1, 1) == 2451544.5,
        PrayerTimes.julianDay(2000, 1, 1))
    check("2026-09-19", PrayerTimes.julianDay(2026, 9, 19) == 2461302.5,
        PrayerTimes.julianDay(2026, 9, 19))

    println("\n-- solar declination (objective astronomy) --")
    // At the solstices the sun's declination reaches ±23.44°, at the
    // equinoxes it passes through 0. If this is wrong, every time is wrong.
    val (decJun, _) = PrayerTimes.sunPosition(PrayerTimes.julianDay(2026, 6, 21))
    val (decDec, _) = PrayerTimes.sunPosition(PrayerTimes.julianDay(2026, 12, 21))
    val (decMar, _) = PrayerTimes.sunPosition(PrayerTimes.julianDay(2026, 3, 20))
    check("June solstice declination ≈ +23.44", near(decJun, 23.44, 0.15), decJun)
    check("December solstice declination ≈ -23.44", near(decDec, -23.44, 0.15), decDec)
    check("March equinox declination ≈ 0", abs(decMar) < 0.5, decMar)

    println("\n-- equinox day length (the strongest objective check) --")
    // On an equinox, sunrise to sunset is about 12h 07m everywhere, the extra
    // minutes coming from refraction and the sun's disc. True at any latitude.
    for (c in listOf(khi, isb, Cities.byName("Gilgit")!!)) {
        val t = PrayerTimes.forDate(2026, 3, 20, c)
        val dayLen = t.maghrib.minutes - t.sunrise.minutes
        check("${c.name}: equinox day ≈ 12h07m", dayLen in 719..735,
            "${dayLen / 60}h ${dayLen % 60}m")
    }

    println("\n-- solar noon symmetry --")
    // Sunrise and sunset must be equidistant from solar noon.
    val t1 = PrayerTimes.forDate(2026, 9, 19, isb)
    val beforeNoon = (t1.zuhr.minutes - 1) - t1.sunrise.minutes
    val afterNoon = t1.maghrib.minutes - (t1.zuhr.minutes - 1)
    check("sunrise/sunset symmetric about noon", abs(beforeNoon - afterNoon) <= 2,
        "$beforeNoon vs $afterNoon")

    println("\n-- ordering must always hold --")
    var orderOk = true
    var orderDetail = ""
    for (city in Cities.all) {
        for (month in 1..12) {
            val t = PrayerTimes.forDate(2026, month, 15, city)
            val seq = listOf(t.fajr.minutes, t.sunrise.minutes, t.zuhr.minutes,
                t.asr.minutes, t.maghrib.minutes, t.isha.minutes)
            if (seq != seq.sorted()) {
                orderOk = false
                orderDetail = "${city.name} month $month: $seq"
            }
        }
    }
    check("Fajr < Sunrise < Zuhr < Asr < Maghrib < Isha, all cities all months",
        orderOk, orderDetail)

    println("\n-- longitude shifts time correctly --")
    // 1° of longitude = 4 minutes. Peshawar is ~2.83° west of Lahore, so its
    // Maghrib should be ~11 minutes later.
    val tL = PrayerTimes.forDate(2026, 9, 19, lhr)
    val tP = PrayerTimes.forDate(2026, 9, 19, pew)
    val expected = ((lhr.lon - pew.lon) * 4).toInt()
    val actual = tP.maghrib.minutes - tL.maghrib.minutes
    check("Peshawar Maghrib ≈ ${expected}min after Lahore",
        abs(actual - expected) <= 3, "expected ~$expected, got $actual")

    println("\n-- Asr: Hanafi must be later than Shafi'i --")
    val hanafi = PrayerTimes.forDate(2026, 9, 19, lhr, Settings(asr = AsrMethod.HANAFI))
    val shafi = PrayerTimes.forDate(2026, 9, 19, lhr, Settings(asr = AsrMethod.STANDARD))
    val gap = hanafi.asr.minutes - shafi.asr.minutes
    check("Hanafi Asr is later", gap > 0, gap)
    check("gap is a realistic 25-75 min", gap in 25..75, gap)
    check("only Asr differs between the two",
        hanafi.fajr == shafi.fajr && hanafi.maghrib == shafi.maghrib)

    println("\n-- calculation methods differ as documented --")
    val karachi = PrayerTimes.forDate(2026, 9, 19, khi, Settings(method = CalcMethod.KARACHI))
    val isna = PrayerTimes.forDate(2026, 9, 19, khi, Settings(method = CalcMethod.ISNA))
    // ISNA uses 15° for Fajr vs Karachi's 18°, so ISNA's Fajr is later.
    check("ISNA Fajr later than Karachi (15° vs 18°)",
        isna.fajr.minutes > karachi.fajr.minutes,
        "${karachi.fajr.format24()} vs ${isna.fajr.format24()}")
    check("methods agree on Maghrib (sunset is sunset)",
        karachi.maghrib == isna.maghrib)
    val makkah = PrayerTimes.forDate(2026, 9, 19, khi, Settings(method = CalcMethod.MAKKAH))
    check("Umm al-Qura Isha is exactly 90 min after Maghrib",
        makkah.isha.minutes - makkah.maghrib.minutes == 90,
        makkah.isha.minutes - makkah.maghrib.minutes)

    println("\n-- prayer windows (the qaza boundaries) --")
    val w = PrayerTimes.forDate(2026, 9, 19, isb)
    check("Fajr ends at sunrise", w.fajrEnd == w.sunrise)
    check("Zuhr ends at Asr", w.zuhrEnd == w.asr)
    check("Asr ends at Maghrib", w.asrEnd == w.maghrib)
    check("Maghrib ends at Isha", w.maghribEnd == w.isha)
    check("Isha ends at next Fajr", w.ishaEnd == w.nextFajr)
    check("next Fajr is close to today's", abs(w.nextFajr.minutes - w.fajr.minutes) <= 5,
        "${w.fajr.format24()} vs ${w.nextFajr.format24()}")

    println("\n-- Sehri and Iftar --")
    val r = PrayerTimes.forDate(2027, 2, 20, khi)   // around Ramadan
    check("Sehri ends before Fajr", r.sehriEnd.minutes < r.fajr.minutes)
    check("Sehri margin is the configured 3 min",
        r.fajr.minutes - r.sehriEnd.minutes == 3)
    check("Iftar is just after Maghrib", r.iftar.minutes - r.maghrib.minutes == 2)
    val noMargin = PrayerTimes.forDate(2027, 2, 20, khi,
        Settings(sehriPrecautionMinutes = 0, iftarPrecautionMinutes = 0))
    check("zero margin means Iftar == Maghrib", noMargin.iftar == noMargin.maghrib)
    check("zero margin means Sehri end == Fajr", noMargin.sehriEnd == noMargin.fajr)

    println("\n-- seasonal behaviour --")
    val summer = PrayerTimes.forDate(2026, 6, 21, isb)
    val winter = PrayerTimes.forDate(2026, 12, 21, isb)
    val summerDay = summer.maghrib.minutes - summer.sunrise.minutes
    val winterDay = winter.maghrib.minutes - winter.sunrise.minutes
    check("summer day longer than winter", summerDay > winterDay,
        "$summerDay vs $winterDay")
    check("Islamabad summer day ~14h", summerDay in 820..870, summerDay)
    check("Islamabad winter day ~10h", winterDay in 590..640, winterDay)

    println("\n-- date arithmetic --")
    check("month rollover", PrayerTimes.addDays(2026, 1, 31, 1) == Triple(2026, 2, 1))
    check("year rollover", PrayerTimes.addDays(2026, 12, 31, 1) == Triple(2027, 1, 1))
    check("leap day exists in 2028",
        PrayerTimes.addDays(2028, 2, 28, 1) == Triple(2028, 2, 29))
    check("no leap day in 2026",
        PrayerTimes.addDays(2026, 2, 28, 1) == Triple(2026, 3, 1))
    check("2000 was a leap year", PrayerTimes.isLeapYear(2000))
    check("1900 was not", !PrayerTimes.isLeapYear(1900))
    check("Feb 2028 has 29 days", PrayerTimes.daysInMonth(2028, 2) == 29)

    println("\n-- month table --")
    val sep = PrayerTimes.forMonth(2026, 9, isb)
    check("September has 30 rows", sep.size == 30, sep.size)
    check("rows are numbered 1..30", sep.first().first == 1 && sep.last().first == 30)
    // Days shorten through September in the northern hemisphere.
    check("Maghrib gets earlier through September",
        sep.last().second.maghrib.minutes < sep.first().second.maghrib.minutes)

    println("\n-- clock formatting --")
    check("midnight is 12:00 AM", Clock(0).format12() == "12:00 AM", Clock(0).format12())
    check("noon is 12:00 PM", Clock(720).format12() == "12:00 PM", Clock(720).format12())
    check("05:07 is 5:07 AM", Clock(307).format12() == "5:07 AM", Clock(307).format12())
    check("18:45 is 6:45 PM", Clock(1125).format12() == "6:45 PM", Clock(1125).format12())
    check("24h format pads", Clock(307).format24() == "05:07", Clock(307).format24())
    check("adding wraps past midnight", (Clock(1430) + 20).minutes == 10)
    check("subtracting wraps before midnight", (Clock(5) - 10).minutes == 1435)

    println("\n-- next prayer countdown --")
    val d = PrayerTimes.forDate(2026, 9, 19, isb)
    val beforeFajr = Clock(d.fajr.minutes - 30)
    check("before Fajr, next is Fajr in 30",
        PrayerTimes.nextPrayer(beforeFajr, d).let { it.second == "Fajr" && it.third == 30 },
        PrayerTimes.nextPrayer(beforeFajr, d))
    val afterIsha = Clock(d.isha.minutes + 10)
    check("after Isha, next is Fajr",
        PrayerTimes.nextPrayer(afterIsha, d).second == "Fajr",
        PrayerTimes.nextPrayer(afterIsha, d))
    val midday = Clock(d.zuhr.minutes + 5)
    check("after Zuhr, current is Zuhr and next is Asr",
        PrayerTimes.nextPrayer(midday, d).let { it.first == "Zuhr" && it.second == "Asr" },
        PrayerTimes.nextPrayer(midday, d))

    println("\n-- cities --")
    check("all requested cities present",
        listOf("Peshawar", "Rawalpindi", "Islamabad", "Mardan", "Kohat",
            "Mingora (Swat)", "Lahore").all { Cities.byName(it) != null })
    check("60+ cities", Cities.all.size >= 60, Cities.all.size)
    check("no duplicate names", Cities.all.map { it.name }.toSet().size == Cities.all.size)
    check("all coordinates inside Pakistan's bounding box",
        Cities.all.all { it.lat in 23.0..37.5 && it.lon in 60.0..78.0 },
        Cities.all.filter { it.lat !in 23.0..37.5 || it.lon !in 60.0..78.0 }.map { it.name })
    check("all on UTC+5", Cities.all.all { it.timezone == 5.0 })
    check("search finds Swat", Cities.search("swat").isNotEmpty())
    check("search by province", Cities.search("KP").size >= 10, Cities.search("KP").size)

    println("\n-- printed sample: Islamabad, 19 Sep 2026 (Karachi method, Hanafi) --")
    val s = PrayerTimes.forDate(2026, 9, 19, isb)
    println("     Sehri ends  ${s.sehriEnd.format12()}")
    println("     Fajr        ${s.fajr.format12()}   until ${s.fajrEnd.format12()}")
    println("     Sunrise     ${s.sunrise.format12()}")
    println("     Zuhr        ${s.zuhr.format12()}   until ${s.zuhrEnd.format12()}")
    println("     Asr         ${s.asr.format12()}   until ${s.asrEnd.format12()}")
    println("     Maghrib     ${s.maghrib.format12()}   until ${s.maghribEnd.format12()}")
    println("     Iftar       ${s.iftar.format12()}")
    println("     Isha        ${s.isha.format12()}   until ${s.ishaEnd.format12()}")

    println("\n$passed passed, $failed failed\n")
    if (failed > 0) kotlin.system.exitProcess(1)
}
