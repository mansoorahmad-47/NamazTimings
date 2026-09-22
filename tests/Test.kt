import io.frontierlabs.namaz.core.*
import kotlin.math.abs

var passed = 0
var failed = 0

fun check(name: String, cond: Boolean, detail: Any? = "") {
    if (cond) { passed++; println("  PASS  $name") }
    else { failed++; println("  FAIL  $name   $detail") }
}

fun near(a: Double, b: Double, tol: Double) = abs(a - b) <= tol

/** Whole days from [y2]-[m2]-[d2] back to the reference date, 0 being the reference. */
fun daysBetween(ry: Int, rm: Int, rd: Int, y2: Int, m2: Int, d2: Int): Int {
    var y = ry; var m = rm; var d = rd
    for (i in 0..800) {
        if (y == y2 && m == m2 && d == d2) return i
        val p = PrayerTimes.addDays(y, m, d, -1)
        y = p.first; m = p.second; d = p.third
    }
    return -1
}

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
    check("Isha ends one minute before the next Fajr",
        w.ishaEnd == w.nextFajr - 1, "${w.ishaEnd.format24()} vs ${w.nextFajr.format24()}")
    check("next Fajr is close to today's", abs(w.nextFajr.minutes - w.fajr.minutes) <= 5,
        "${w.fajr.format24()} vs ${w.nextFajr.format24()}")


    println("\n-- Isha must never end after Fajr begins --")
    // The reported bug: Peshawar showed Isha ending 4:37 AM with Fajr at
    // 4:36 AM. Checked across every city and every day of a year.
    var ishaOk = true
    var ishaDetail = ""
    for (c in listOf(pew, khi, lhr, isb, Cities.byName("Gilgit")!!,
                     Cities.byName("Gwadar")!!)) {
        for (mth in 1..12) {
            for (dy in listOf(1, 10, 20, 28)) {
                val d = PrayerTimes.forDate(2026, mth, dy, c)
                // strictly before the next Fajr
                if (d.ishaEnd.minutes >= d.nextFajr.minutes) {
                    ishaOk = false
                    ishaDetail = "${c.name} $dy/$mth: end ${d.ishaEnd.format24()} " +
                        "vs next Fajr ${d.nextFajr.format24()}"
                }
                // and never later than the Fajr shown on the same card
                if (d.ishaEnd.minutes > d.fajr.minutes) {
                    ishaOk = false
                    ishaDetail = "${c.name} $dy/$mth: end ${d.ishaEnd.format24()} " +
                        "after same-day Fajr ${d.fajr.format24()}"
                }
            }
        }
    }
    check("Isha end is before both the next Fajr and the displayed Fajr, " +
        "all cities all year", ishaOk, ishaDetail)

    val pw = PrayerTimes.forDate(2026, 9, 19, pew)
    check("Peshawar: Isha end is not after Fajr",
        pw.ishaEnd.minutes <= pw.fajr.minutes,
        "Isha until ${pw.ishaEnd.format12()}, Fajr ${pw.fajr.format12()}")
    check("Isha is still open just before its end",
        Tracker.statusOf(Tracker.endInDay(pw.isha, pw.ishaEnd) - 1,
            pw.isha, pw.ishaEnd, false) == PrayerStatus.DUE)
    check("Isha is closed once Fajr arrives",
        Tracker.statusOf(pw.fajr.minutes + 1440, pw.isha, pw.ishaEnd, false)
            == PrayerStatus.MISSED)

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


    println("\n-- nawafil and night times --")
    val n = PrayerTimes.forDate(2026, 9, 19, isb)
    check("night length is 10-14 hours", n.nightLength in 600..840, n.nightLength)
    check("Islamic midnight sits between Maghrib and Fajr",
        n.islamicMidnight.minutes > n.maghrib.minutes || n.islamicMidnight.minutes < n.fajr.minutes,
        n.islamicMidnight.format24())
    check("last third is after Islamic midnight",
        ((n.lastThirdOfNight.minutes - n.maghrib.minutes + 1440) % 1440) >
        ((n.islamicMidnight.minutes - n.maghrib.minutes + 1440) % 1440))
    check("Tahajjud ends at Fajr", n.tahajjudEnd == n.fajr)
    check("Sehri starts at Islamic midnight", n.sehriStart == n.islamicMidnight)
    check("Ishraq is 20 min after sunrise", n.ishraqStart.minutes - n.sunrise.minutes == 20)
    check("Ishraq ends before Chasht begins or at it",
        n.ishraqEnd.minutes <= n.chashtStart.minutes)
    check("Chasht ends before Zawal", n.chashtEnd.minutes < n.zawal.minutes,
        "${n.chashtEnd.format24()} vs ${n.zawal.format24()}")
    check("Zawal is just before Zuhr", n.zuhr.minutes - n.zawal.minutes == 1)

    println("\n-- full day table --")
    val fd = n.fullDay()
    check("15 rows", fd.size == 15, fd.size)
    check("every requested item present",
        listOf("Sehri starts", "Sehri ends", "Tahajjud", "Fajr", "Ishraq",
               "Chasht (Duha)", "Zuhr", "Asr", "Iftar", "Maghrib", "Isha",
               "Avoid prayer", "Sunrise")
            .all { lbl -> fd.any { it.label == lbl } },
        fd.map { it.label })
    check("daytime rows run in order",
        fd.filter { it.label in listOf("Fajr","Sunrise","Zuhr","Asr","Maghrib","Isha") }
          .map { it.start.minutes }.let { it == it.sorted() },
        fd.filter { it.label in listOf("Fajr","Sunrise","Zuhr","Asr","Maghrib","Isha") }
          .map { it.start.format24() })
    check("single-moment rows have no end",
        fd.filter { it.label in listOf("Sunrise","Iftar","Sehri ends","Sehri starts") }
          .all { it.end == null })

    println("\n-- every month selectable --")
    var allMonthsOk = true
    for (mth in 1..12) {
        val rows = PrayerTimes.forMonth(2026, mth, isb)
        if (rows.size != PrayerTimes.daysInMonth(2026, mth)) allMonthsOk = false
        if (rows.any { it.second.fullDay().size != 15 }) allMonthsOk = false
    }
    check("months 1-12 all produce full tables", allMonthsOk)


    println("\n-- Qibla bearing --")
    // From Pakistan the Kaaba is west and a little south, so every city must
    // point into the western half of the compass.
    var allWest = true
    var westDetail = ""
    for (c in Cities.all) {
        val b = Qibla.bearing(c)
        if (b < 230.0 || b > 290.0) { allWest = false; westDetail = "${c.name} -> $b" }
    }
    check("every Pakistani city points WSW-to-W (230-290 degrees)", allWest, westDetail)

    // Standing at the Kaaba's longitude but further north, the Qibla is due south.
    check("due south from directly north of the Kaaba",
        abs(Qibla.bearing(40.0, Qibla.KAABA_LON) - 180.0) < 0.01,
        Qibla.bearing(40.0, Qibla.KAABA_LON))
    // Directly south of it, the Qibla is due north.
    check("due north from directly south of the Kaaba",
        abs(Qibla.bearing(0.0, Qibla.KAABA_LON) - 0.0) < 0.01,
        Qibla.bearing(0.0, Qibla.KAABA_LON))
    // From the SAME latitude, due east, the great circle starts slightly
    // north of due west -- great circles bend poleward. A flat-map answer
    // would say exactly 270, and would be wrong. Verified independently.
    val sameLat = Qibla.bearing(Qibla.KAABA_LAT, Qibla.KAABA_LON + 20.0)
    check("great circle from the same latitude starts north of due west",
        sameLat > 270.0 && sameLat < 280.0, sameLat)

    // Known published values, to about a degree.
    val bKhi = Qibla.bearing(khi)
    val bLhr = Qibla.bearing(lhr)
    val bIsb = Qibla.bearing(isb)
    check("Karachi Qibla ~267 degrees", abs(bKhi - 267.0) < 2.5, bKhi)
    check("Lahore Qibla ~260 degrees", abs(bLhr - 260.0) < 2.5, bLhr)
    check("Islamabad Qibla ~255 degrees", abs(bIsb - 255.0) < 2.5, bIsb)
    // Further north-east means turning further south to face Makkah.
    check("Islamabad bearing is further south than Karachi's", bIsb < bKhi,
        "$bIsb vs $bKhi")

    println("\n-- distance to the Kaaba --")
    val dKhi = Qibla.distanceKm(khi)
    val dIsb = Qibla.distanceKm(isb)
    check("Karachi ~2800 km from Makkah", abs(dKhi - 2800.0) < 25.0, dKhi)
    check("Islamabad ~3528 km from Makkah", abs(dIsb - 3528.0) < 25.0, dIsb)
    check("Islamabad is further than Karachi", dIsb > dKhi)
    check("zero distance at the Kaaba itself",
        Qibla.distanceKm(Qibla.KAABA_LAT, Qibla.KAABA_LON) < 0.001)

    println("\n-- compass points --")
    check("0 is N", Qibla.compassPoint(0.0) == "N")
    check("90 is E", Qibla.compassPoint(90.0) == "E")
    check("270 is W", Qibla.compassPoint(270.0) == "W")
    check("247.5 is WSW", Qibla.compassPoint(247.5) == "WSW", Qibla.compassPoint(247.5))
    check("359 wraps back to N", Qibla.compassPoint(359.0) == "N", Qibla.compassPoint(359.0))
    check("Karachi reads as W or WSW",
        Qibla.compassPoint(bKhi) in listOf("W", "WSW"), Qibla.compassPoint(bKhi))

    println("\n-- which way to turn --")
    check("facing the Qibla means no turn", Qibla.turnFrom(255.0, 255.0) == 0.0)
    check("turn right when the Qibla is clockwise",
        Qibla.turnFrom(250.0, 255.0) == 5.0, Qibla.turnFrom(250.0, 255.0))
    check("turn left when the Qibla is anticlockwise",
        Qibla.turnFrom(260.0, 255.0) == -5.0, Qibla.turnFrom(260.0, 255.0))
    // The short way round: facing 10 degrees with the Qibla at 350 is a 20
    // degree turn left, not 340 to the right.
    check("takes the short way round the dial",
        Qibla.turnFrom(10.0, 350.0) == -20.0, Qibla.turnFrom(10.0, 350.0))
    check("never asks for more than a half turn",
        (0..359).all { abs(Qibla.turnFrom(it.toDouble(), 255.0)) <= 180.0 })


    println("\n-- the prayer day turns at Fajr, not midnight --")
    val td = PrayerTimes.forDate(2026, 9, 19, isb)   // Isha 7:33 PM, next Fajr 4:31 AM
    run {
        val fajr = td.fajr.minutes

        check("mid-afternoon belongs to today",
            Tracker.prayerDay(15 * 60, fajr) == PrayerDay(0, 15 * 60))
        check("late evening belongs to today",
            Tracker.prayerDay(23 * 60, fajr) == PrayerDay(0, 23 * 60))

        // The reported bug: at 00:01 the five prayers flipped to the next
        // day's, wiping four already-offered prayers off the screen and
        // recording a finished day as unfinished.
        check("one minute past midnight still belongs to yesterday",
            Tracker.prayerDay(1, fajr) == PrayerDay(-1, 1441),
            Tracker.prayerDay(1, fajr))
        check("three in the morning still belongs to yesterday",
            Tracker.prayerDay(3 * 60, fajr) == PrayerDay(-1, 3 * 60 + 1440))
        check("one minute before Fajr still belongs to yesterday",
            Tracker.prayerDay(fajr - 1, fajr).dayOffset == -1)
        check("Fajr itself starts the new day",
            Tracker.prayerDay(fajr, fajr) == PrayerDay(0, fajr))
        check("the count never goes backwards across the boundary",
            Tracker.prayerDay(fajr - 1, fajr).dayMinutes >
                Tracker.prayerDay(23 * 60, fajr).dayMinutes)
    }

    println("\n-- prayer status on the prayer-day scale --")
    check("before its time: upcoming",
        Tracker.statusOf(td.fajr.minutes - 30, td.fajr, td.fajrEnd, false)
            == PrayerStatus.UPCOMING)
    check("during its window: due",
        Tracker.statusOf(td.fajr.minutes + 10, td.fajr, td.fajrEnd, false)
            == PrayerStatus.DUE)
    check("after its window, unmarked: missed",
        Tracker.statusOf(td.sunrise.minutes + 30, td.fajr, td.fajrEnd, false)
            == PrayerStatus.MISSED)
    check("marked stays done even after the window",
        Tracker.statusOf(td.sunrise.minutes + 30, td.fajr, td.fajrEnd, true)
            == PrayerStatus.DONE)
    check("marked is done even before its time",
        Tracker.statusOf(0, td.fajr, td.fajrEnd, true) == PrayerStatus.DONE)
    check("inside Zuhr just after it starts",
        Tracker.statusOf(td.zuhr.minutes + 5, td.zuhr, td.zuhrEnd, false)
            == PrayerStatus.DUE)
    check("Zuhr is missed once Asr begins",
        Tracker.statusOf(td.asr.minutes, td.zuhr, td.zuhrEnd, false)
            == PrayerStatus.MISSED)
    check("Isha is due at 9 PM",
        Tracker.statusOf(21 * 60, td.isha, td.ishaEnd, false) == PrayerStatus.DUE)
    // A naive start<=now<end would mark Isha missed the moment midnight
    // passed, breaking a user's streak every single night.
    check("Isha is still due at 1 AM on its own prayer day",
        Tracker.statusOf(1440 + 60, td.isha, td.ishaEnd, false) == PrayerStatus.DUE)
    check("at 1 AM, yesterday's morning prayers read as missed, not upcoming",
        listOf(td.fajr to td.fajrEnd, td.zuhr to td.zuhrEnd, td.asr to td.asrEnd)
            .all { (st, en) ->
                Tracker.statusOf(1440 + 60, st, en, false) == PrayerStatus.MISSED
            })

    println("\n-- marking is only offered once the azan has been called --")
    check("Fajr cannot be marked before Fajr",
        !Tracker.hasStarted(td.fajr.minutes - 1, td.fajr))
    check("Fajr can be marked from its first minute",
        Tracker.hasStarted(td.fajr.minutes, td.fajr))
    check("Isha cannot be marked at noon",
        !Tracker.hasStarted(12 * 60, td.isha))
    check("Isha can be marked at 1 AM on its own prayer day",
        Tracker.hasStarted(1440 + 60, td.isha))
    check("every prayer of a finished day can be marked",
        td.list().all { (_, st, _) -> Tracker.hasStarted(2880, st) })
    check("nothing after Zuhr can be marked at midday",
        td.list().filter { it.second.minutes > td.zuhr.minutes }
            .none { Tracker.hasStarted(td.zuhr.minutes, it.second) })

    println("\n-- current prayer on the front page --")
    val cur = Tracker.currentPrayer(td.asr.minutes + 20, td)
    check("mid-afternoon the current prayer is Asr", cur?.first == "Asr", cur?.first)
    check("its end is Maghrib", cur?.third == td.maghrib)
    check("countdown to qaza is positive",
        Tracker.minutesLeft(td.asr.minutes + 20, td.asr, td.maghrib) > 0)
    check("nothing is due between sunrise and Zuhr",
        Tracker.currentPrayer(td.sunrise.minutes + 60, td) == null,
        Tracker.currentPrayer(td.sunrise.minutes + 60, td)?.first)
    check("Isha is current at 11 PM",
        Tracker.currentPrayer(23 * 60, td)?.first == "Isha")
    check("Isha is still current at 2 AM on its own prayer day",
        Tracker.currentPrayer(1440 + 2 * 60, td)?.first == "Isha",
        Tracker.currentPrayer(1440 + 2 * 60, td)?.first)
    check("Isha's remaining time shrinks as the night goes on",
        Tracker.minutesLeft(1440 + 60, td.isha, td.ishaEnd) <
            Tracker.minutesLeft(23 * 60, td.isha, td.ishaEnd))
    check("nothing is left once the window has closed",
        Tracker.minutesLeft(2000, td.fajr, td.fajrEnd) == 0)
    check("countdown wraps correctly across midnight",
        Tracker.minutesUntil(Clock(23 * 60), Clock(60)) == 120,
        Tracker.minutesUntil(Clock(23 * 60), Clock(60)))

    println("\n-- completion --")
    check("empty is zero", Tracker.completedCount(emptySet()) == 0)
    check("three marked", Tracker.completedCount(setOf("Fajr", "Asr", "Isha")) == 3)
    check("all five is complete", Tracker.isDayComplete(Tracker.FARD.toSet()))
    check("four is not complete", !Tracker.isDayComplete(setOf("Fajr","Zuhr","Asr","Isha")))
    check("unknown names do not count",
        Tracker.completedCount(setOf("Tahajjud", "Ishraq")) == 0)

    println("\n-- streaks --")
    // Every day complete.
    check("unbroken history gives a long streak",
        Tracker.currentStreak(2026, 9, 19) { _, _, _ -> true } > 100)
    // Nothing ever complete.
    check("no history gives zero",
        Tracker.currentStreak(2026, 9, 19) { _, _, _ -> false } == 0)
    // Today incomplete but the previous three days complete: streak is 3, and
    // an unfinished today must NOT break it.
    val done3 = setOf(Triple(2026, 9, 18), Triple(2026, 9, 17), Triple(2026, 9, 16))
    check("an unfinished today does not break the streak",
        Tracker.currentStreak(2026, 9, 19) { y, m, d -> Triple(y, m, d) in done3 } == 3,
        Tracker.currentStreak(2026, 9, 19) { y, m, d -> Triple(y, m, d) in done3 })
    // Today complete too: streak is 4.
    val done4 = done3 + Triple(2026, 9, 19)
    check("finishing today extends it",
        Tracker.currentStreak(2026, 9, 19) { y, m, d -> Triple(y, m, d) in done4 } == 4)
    // A gap two days back ends the chain there.
    val gapDays = setOf(Triple(2026, 9, 18), Triple(2026, 9, 16), Triple(2026, 9, 15))
    check("a missed day ends the streak",
        Tracker.currentStreak(2026, 9, 19) { y, m, d -> Triple(y, m, d) in gapDays } == 1,
        Tracker.currentStreak(2026, 9, 19) { y, m, d -> Triple(y, m, d) in gapDays })
    // Streaks must count back across a month boundary.
    val across = setOf(Triple(2026, 9, 1), Triple(2026, 8, 31), Triple(2026, 8, 30))
    check("streak counts back over a month boundary",
        Tracker.currentStreak(2026, 9, 1) { y, m, d -> Triple(y, m, d) in across } == 3,
        Tracker.currentStreak(2026, 9, 1) { y, m, d -> Triple(y, m, d) in across })
    check("best streak finds the longest run",
        Tracker.bestStreak(2026, 9, 19, 10) { y, m, d -> Triple(y, m, d) in gapDays } == 2,
        Tracker.bestStreak(2026, 9, 19, 10) { y, m, d -> Triple(y, m, d) in gapDays })

    println("\n-- qaza notes removed from the day table --")
    check("no prayer row repeats the end time as a note",
        td.fullDay().filter { it.label in Tracker.FARD }.all { it.note == null },
        td.fullDay().filter { it.label in Tracker.FARD }.map { it.note })
    check("useful notes are kept",
        td.fullDay().first { it.label == "Sehri starts" }.note == "Islamic midnight")


    println("\n-- translations --")
    val en = Strings.fields(Strings.EN)
    val ur = Strings.fields(Strings.UR)
    check("same number of strings in both languages", en.size == ur.size,
        "${en.size} vs ${ur.size}")
    check("no English string is blank", en.none { it.isBlank() })
    check("no Urdu string is blank", ur.none { it.isBlank() })
    // The point of the data class: a forgotten translation shows up as an
    // identical English string, so almost nothing should match.
    val untranslated = en.indices.filter { en[it] == ur[it] }
    check("every string is actually translated", untranslated.isEmpty(),
        untranslated.map { en[it] })
    check("Urdu strings use Arabic script",
        ur.count { t -> t.any { it.code in 0x0600..0x06FF } } >= ur.size - 2,
        ur.filterNot { t -> t.any { it.code in 0x0600..0x06FF } })

    check("Urdu is marked right-to-left", Lang.UR.rtl && !Lang.EN.rtl)
    check("prayer names translate",
        Strings.prayerName(Lang.UR, "Fajr") == "فجر" &&
        Strings.prayerName(Lang.UR, "Maghrib") == "مغرب")
    check("English prayer names pass through",
        Strings.prayerName(Lang.EN, "Fajr") == "Fajr")
    check("unknown labels fall back rather than vanish",
        Strings.prayerName(Lang.UR, "Something") == "Something")
    check("months translate", Strings.monthName(Lang.UR, 1) == "جنوری" &&
        Strings.monthName(Lang.EN, 12) == "December")
    check("all 66 cities have an Urdu name",
        Cities.all.all { Strings.CITY_UR.containsKey(it.name) },
        Cities.all.filterNot { Strings.CITY_UR.containsKey(it.name) }.map { it.name })
    check("every province translates",
        Cities.provinces.all { Strings.provinceName(Lang.UR, it) != it },
        Cities.provinces.filter { Strings.provinceName(Lang.UR, it) == it })
    check("city lookup falls back for an unknown city",
        Strings.cityName(Lang.UR, "Nowhere") == "Nowhere")

    println("\n-- update check --")
    val good = """{"latestVersionCode": 5, "latestVersionName": "1.4",
        "minSupportedVersionCode": 3,
        "downloadUrl": "https://github.com/u/r/releases/download/v1.4/app.apk",
        "notes": "Adds Urdu"}"""
    val info = UpdateCheck.parse(good)
    check("parses the version", info?.latestVersionCode == 5, info?.latestVersionCode)
    check("parses the floor", info?.minSupportedVersionCode == 3)
    check("parses the url", info?.downloadUrl?.endsWith("app.apk") == true)
    check("parses the notes", info?.notes == "Adds Urdu")

    check("below the floor is a forced update",
        UpdateCheck.check(2, good).action == UpdateAction.REQUIRED)
    check("at the floor but behind latest is optional",
        UpdateCheck.check(3, good).action == UpdateAction.OPTIONAL)
    check("one behind latest is optional",
        UpdateCheck.check(4, good).action == UpdateAction.OPTIONAL)
    check("current version needs nothing",
        UpdateCheck.check(5, good).action == UpdateAction.NONE)
    check("a newer build than published needs nothing",
        UpdateCheck.check(9, good).action == UpdateAction.NONE)

    // Failing open is the rule that matters most: a prayer app must never be
    // bricked by a network problem or a bad file.
    check("no network (null) never blocks",
        UpdateCheck.check(1, null).action == UpdateAction.NONE)
    check("empty response never blocks",
        UpdateCheck.check(1, "").action == UpdateAction.NONE)
    check("garbage never blocks",
        UpdateCheck.check(1, "<html>404 not found</html>").action == UpdateAction.NONE)
    check("truncated json never blocks",
        UpdateCheck.check(1, """{"latestVersionCode": 5""").action == UpdateAction.NONE)
    check("missing download url never blocks",
        UpdateCheck.check(1, """{"latestVersionCode": 9}""").action == UpdateAction.NONE)
    check("plain http url is rejected",
        UpdateCheck.parse("""{"latestVersionCode":9,"downloadUrl":"http://x.com/a.apk"}""")
            == null)
    check("absent floor means nothing is forced",
        UpdateCheck.check(1, """{"latestVersionCode": 9,
            "downloadUrl": "https://x.com/a.apk"}""").action == UpdateAction.OPTIONAL)

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


    println("\n-- translations: nothing left behind --")
    run {
        // fields() is hand-written, so it silently rots when a string is added
        // to Str and not to the list. Java reflection counts the real backing
        // fields without needing kotlin-reflect on the classpath.
        val declared = Str::class.java.declaredFields.count { !it.isSynthetic }
        check("fields() lists every string in Str",
            Strings.fields(Strings.EN).size == declared,
            "listed ${Strings.fields(Strings.EN).size}, declared $declared")
        check("no English string leaked into Urdu",
            Strings.fields(Strings.EN).zip(Strings.fields(Strings.UR))
                .count { (e, u) -> e == u } == 0,
            Strings.fields(Strings.EN).zip(Strings.fields(Strings.UR))
                .filter { (e, u) -> e == u }.map { it.first })
        check("seven weekday names in both languages",
            (0..6).map { Strings.weekdayShort(Lang.EN, it) }.toSet().size == 7 &&
                (0..6).map { Strings.weekdayShort(Lang.UR, it) }.toSet().size == 7)
        check("weekdays are translated",
            (0..6).none {
                Strings.weekdayShort(Lang.EN, it) == Strings.weekdayShort(Lang.UR, it)
            })
    }

    println("\n-- finding the nearest city from a GPS fix --")
    run {
        // The strongest check: from a city's own coordinates, the nearest city
        // must be that city. If any pair of entries has swapped or mistyped
        // coordinates, this is what catches it.
        val wrong = Cities.all.filter { c ->
            Cities.nearest(c.lat, c.lon).first.name != c.name
        }
        check("every city is its own nearest city", wrong.isEmpty(),
            wrong.map { it.name })
        check("and the distance to itself is zero",
            Cities.all.all { Cities.nearest(it.lat, it.lon).second < 0.01 })

        // A point a few km outside a city still resolves to it.
        val nearPew = Cities.nearest(34.05, 71.58)
        check("just outside Peshawar resolves to Peshawar",
            nearPew.first.name == "Peshawar", nearPew.first.name)
        check("and reports a small distance", nearPew.second < 10, nearPew.second)

        // Somewhere between two cities must pick the genuinely closer one,
        // measured properly rather than by comparing raw coordinates.
        val isb = Cities.byName("Islamabad")!!
        val lhr = Cities.byName("Lahore")!!
        val midpoint = Cities.nearest((isb.lat + lhr.lat) / 2, (isb.lon + lhr.lon) / 2)
        check("a point between Islamabad and Lahore picks a real neighbour",
            Cities.distanceKm((isb.lat + lhr.lat) / 2, (isb.lon + lhr.lon) / 2,
                midpoint.first) <= Cities.all.minOf {
                    Cities.distanceKm((isb.lat + lhr.lat) / 2,
                        (isb.lon + lhr.lon) / 2, it)
                } + 0.001,
            midpoint.first.name)

        // Distances against known separations, so the maths is not just
        // self-consistent but actually right.
        check("Karachi to Lahore is about 1020 km",
            abs(Cities.distanceKm(khi.lat, khi.lon, lhr) - 1020) < 40,
            Cities.distanceKm(khi.lat, khi.lon, lhr))
        check("Islamabad to Lahore is about 270 km",
            abs(Cities.distanceKm(isb.lat, isb.lon, lhr) - 270) < 25,
            Cities.distanceKm(isb.lat, isb.lon, lhr))
        // 145 km as the crow flies, not the ~180 km by road that gets quoted.
        // Checked by hand: 1.52 degrees of longitude at this latitude is
        // 141 km, and the 0.33 degrees of latitude adds 37 km.
        check("Peshawar to Islamabad is about 145 km",
            abs(Cities.distanceKm(pew.lat, pew.lon, isb) - 145) < 10,
            Cities.distanceKm(pew.lat, pew.lon, isb))

        // Abroad must not silently resolve to a Pakistani city. Without the
        // distance check the app would put someone in Dubai on Gwadar's
        // timetable and show nothing to explain why every time was wrong.
        val dubai = Cities.nearest(25.2048, 55.2708)
        check("Dubai is further than the trusted radius",
            dubai.second > Cities.MAX_TRUSTED_KM, dubai.second)
        val london = Cities.nearest(51.5074, -0.1278)
        check("London is far outside the trusted radius",
            london.second > 4000, london.second)
        check("everywhere inside Pakistan is within the trusted radius",
            Cities.all.all { Cities.nearest(it.lat, it.lon).second <= Cities.MAX_TRUSTED_KM })
    }

    println("\n-- the Islamic calendar --")
    run {
        // The property that matters is not any single lookup but that the
        // conversion is exactly reversible. A one-day drift anywhere inside
        // the 30-year cycle would show up here and nowhere else.
        var roundTripFails = 0
        var rangeFails = 0
        var backwards = 0
        var previous = -1
        var jdn = Hijri.gregorianToJdn(1900, 1, 1)
        val end = Hijri.gregorianToJdn(2100, 1, 1)
        while (jdn < end) {
            val h = Hijri.fromJdn(jdn)
            if (Hijri.toJdn(h.year, h.month, h.day) != jdn) roundTripFails++
            if (h.month !in 1..12 || h.day !in 1..30) rangeFails++
            if (h.ordinal <= previous) backwards++
            previous = h.ordinal
            jdn++
        }
        check("every day from 1900 to 2100 converts back exactly",
            roundTripFails == 0, "$roundTripFails failures")
        check("every month is 1-12 and every day 1-30", rangeFails == 0, rangeFails)
        check("the date never goes backwards", backwards == 0, backwards)

        // The reference everyone checks a Hijri implementation against.
        val y2k = Hijri.fromGregorian(2000, 1, 1)
        check("1 January 2000 is 24 Ramadan 1420",
            y2k == HijriDate(1420, 9, 24), y2k)

        // Gregorian conversion is its own well-known trap: 1900 was not a leap
        // year and 2000 was, and a formula that gets either wrong is off by a
        // day for every date after it.
        check("1 Jan 1970 is JDN 2440588",
            Hijri.gregorianToJdn(1970, 1, 1) == 2440588L,
            Hijri.gregorianToJdn(1970, 1, 1))
        check("1 Jan 1970 was a Thursday",
            Hijri.weekdayIndex(2440588L) == 4, Hijri.weekdayIndex(2440588L))
        check("29 Feb 2000 exists and 29 Feb 1900 does not",
            Hijri.gregorianToJdn(2000, 3, 1) - Hijri.gregorianToJdn(2000, 2, 28) == 2L &&
            Hijri.gregorianToJdn(1900, 3, 1) - Hijri.gregorianToJdn(1900, 2, 28) == 1L)
        check("Gregorian conversion round-trips too",
            (0 until 40000).all {
                val j = 2400000L + it
                val (gy, gm, gd) = Hijri.jdnToGregorian(j)
                Hijri.gregorianToJdn(gy, gm, gd) == j
            })

        // Month lengths must add up, or a month grid will draw a wrong shape.
        var lengthFails = 0
        for (hy in 1440..1480) {
            val total = (1..12).sumOf { Hijri.monthLength(hy, it) }
            if (total != Hijri.yearLength(hy)) lengthFails++
            if (Hijri.toJdn(hy + 1, 1, 1) - Hijri.toJdn(hy, 1, 1) != total.toLong())
                lengthFails++
        }
        check("month lengths sum to the year, 1440-1480", lengthFails == 0, lengthFails)
        check("a leap year is 355 days and has a 30-day Dhul Hijjah",
            (1440..1480).filter { Hijri.isLeapYear(it) }.all {
                Hijri.yearLength(it) == 355 && Hijri.monthLength(it, 12) == 30
            })
        check("11 leap years in every 30",
            (1440 until 1470).count { Hijri.isLeapYear(it) } == 11,
            (1440 until 1470).count { Hijri.isLeapYear(it) })

        // The adjustment is the whole answer to moon sighting, so it has to
        // behave exactly: shift the date, and still round-trip.
        val plain = Hijri.fromGregorian(2026, 9, 22, 0)
        val plusOne = Hijri.fromGregorian(2026, 9, 22, 1)
        val minusOne = Hijri.fromGregorian(2026, 9, 22, -1)
        check("+1 moves the Islamic date one day forward",
            plusOne.day == plain.day + 1, "$plain vs $plusOne")
        check("-1 moves it one day back",
            minusOne.day == plain.day - 1, "$plain vs $minusOne")
        check("an adjusted date still converts back to the same Gregorian day",
            (-Hijri.MAX_ADJUST..Hijri.MAX_ADJUST).all { adj ->
                val h = Hijri.fromGregorian(2026, 9, 22, adj)
                Hijri.toGregorian(h.year, h.month, h.day, adj) == Triple(2026, 9, 22)
            })

        // Occasions.
        val next = Hijri.upcoming(2026, 9, 22, count = 5)
        check("five occasions come back", next.size == 5, next.size)
        check("they are in order",
            next.map { it.daysAway } == next.map { it.daysAway }.sorted(),
            next.map { it.daysAway })
        check("none of them is in the past", next.all { it.daysAway >= 0 })
        check("each one lands on its own Hijri date",
            next.all { u ->
                u.hijri.month == u.event.month && u.hijri.day == u.event.day
            })
        check("and on the Gregorian day it says",
            next.all { u ->
                val (gy, gm, gd) = u.gregorian
                Hijri.gregorianToJdn(gy, gm, gd) -
                    Hijri.gregorianToJdn(2026, 9, 22) == u.daysAway.toLong()
            })

        // The case that would otherwise return an empty list exactly when it
        // matters: standing inside Dhul Hijjah, everything left is next year.
        val inDhulHijjah = Hijri.toGregorian(1448, 12, 20)
        val fromThere = Hijri.upcoming(
            inDhulHijjah.first, inDhulHijjah.second, inDhulHijjah.third, count = 3)
        check("late in Dhul Hijjah it still finds next year's occasions",
            fromThere.size == 3 && fromThere.all { it.daysAway >= 0 },
            fromThere.map { "${it.event} +${it.daysAway}" })

        println("     today (22 Sep 2026) is " +
            Hijri.fromGregorian(2026, 9, 22).let {
                "${it.day} ${Strings.hijriMonth(Lang.EN, it.month)} ${it.year}"
            })
        for (u in next) {
            val (gy, gm, gd) = u.gregorian
            println("     ${Strings.eventName(Lang.EN, u.event).padEnd(18)} " +
                "${u.hijri.day} ${Strings.hijriMonth(Lang.EN, u.hijri.month)} " +
                "${u.hijri.year}  =  $gd/$gm/$gy  (+${u.daysAway} days)")
        }
    }

    println("\n-- the daily check-in --")
    run {
        val id = "7f3a91c2-4de8-4a10-9b77-2c5e1f0a8d34"

        check("a fresh install is due", Ping.isDue(0L, 20_352L))
        check("not due twice in the same day", !Ping.isDue(20_352L, 20_352L))
        check("due again the next day", Ping.isDue(20_352L, 20_353L))
        // A phone whose clock was wrong and has since been corrected would
        // otherwise never ping again until the date caught up.
        check("a clock set into the future does not lock it out",
            Ping.isDue(29_000L, 20_353L))

        val u = Ping.url("https://script.google.com/macros/s/abc/exec", id, 4, "1.3")
        check("builds a URL", u != null, u)
        check("carries the install id", u!!.contains("id=$id"), u)
        check("carries both version fields", u.contains("vc=4") && u.contains("v=1.3"), u)
        check("starts the query with ?", u.contains("exec?id="), u)
        check("a base that already has a query gets & instead",
            Ping.url("https://x.example/p?k=1", id, 4, "1.3")!!.contains("?k=1&id="),
            Ping.url("https://x.example/p?k=1", id, 4, "1.3"))

        // Every one of these must silently produce nothing rather than throw
        // or, worse, send something unencrypted.
        check("no URL configured means no ping", Ping.url("", id, 4, "1.3") == null)
        check("plain http is refused",
            Ping.url("http://x.example/p", id, 4, "1.3") == null)
        check("a malformed id is refused",
            Ping.url("https://x.example/p", "tiny", 4, "1.3") == null)
        check("an id with a query separator in it is refused",
            Ping.url("https://x.example/p", "abc&evil=1&x", 4, "1.3") == null)

        check("a UUID is a valid id", Ping.isValidId(id))
        check("something short is not", !Ping.isValidId("abc"))
        check("spaces are not", !Ping.isValidId("abcdefgh ijkl"))

        // The version name is the one field a person could put anything into.
        check("spaces encode as %20, never +", Ping.encode("1.3 beta") == "1.3%20beta",
            Ping.encode("1.3 beta"))
        check("separators are escaped",
            Ping.encode("a&b=c?d") == "a%26b%3Dc%3Fd", Ping.encode("a&b=c?d"))
        check("safe characters pass through",
            Ping.encode("Abc-1.3_x~y") == "Abc-1.3_x~y", Ping.encode("Abc-1.3_x~y"))
        check("non-Latin text survives as UTF-8",
            Ping.encode("نماز").startsWith("%D9%86"), Ping.encode("نماز"))
        check("an odd version name cannot break out of the query",
            Ping.url("https://x.example/p", id, 4, "1.3&admin=1")!!
                .endsWith("v=1.3%26admin%3D1"),
            Ping.url("https://x.example/p", id, 4, "1.3&admin=1"))
    }

    println("\n-- alerts: what fires and when --")
    run {
        val t = PrayerTimes.forDate(2026, 9, 19, pew)
        val a = Alerts.forDay(t)

        check("five starts and five warnings", a.size == 10, a.size)
        check("all sorted by time", a.map { it.at } == a.map { it.at }.sorted())

        val starts = a.filter { it.kind == AlertKind.PRAYER_START }
        check("a start for every fard prayer",
            starts.map { it.prayer }.toSet() == Tracker.FARD.toSet(),
            starts.map { it.prayer })
        check("each start is at the azan time",
            t.list().all { (name, s, _) ->
                starts.first { it.prayer == name }.at == s.minutes
            })

        val warns = a.filter { it.kind == AlertKind.QAZA_WARNING }
        check("a warning for every fard prayer",
            warns.map { it.prayer }.toSet() == Tracker.FARD.toSet(),
            warns.map { it.prayer })
        check("each warning is exactly 15 minutes before the window closes",
            t.list().all { (name, _, e) ->
                warns.first { it.prayer == name }.minuteOfDay ==
                    ((e.minutes - 15) + 1440) % 1440
            })

        // The one that midnight breaks. Isha ends at tomorrow's Subh Sadiq, so
        // its warning must land on the NEXT day, not at 4 AM this morning.
        val ishaWarn = warns.first { it.prayer == "Isha" }
        check("Isha's warning lands tomorrow", ishaWarn.at >= 1440, ishaWarn.at)
        check("Isha's warning is after Isha starts",
            ishaWarn.at > t.isha.minutes, "${ishaWarn.at} vs ${t.isha.minutes}")
        check("Isha's warning day offset is 1", ishaWarn.dayOffset == 1, ishaWarn.dayOffset)
        check("the other four warnings land today",
            warns.filter { it.prayer != "Isha" }.all { it.dayOffset == 0 })

        check("request codes are unique",
            a.map { it.requestCode }.toSet().size == 10,
            a.map { it.requestCode })
    }

    println("\n-- alerts: sweep, a warning must never precede its own azan --")
    run {
        var bad = 0
        for (c in listOf(khi, isb, lhr, pew, Cities.byName("Gilgit")!!,
                         Cities.byName("Gwadar")!!)) {
            for (mo in 1..12) for (day in listOf(1, 21)) {
                val t = PrayerTimes.forDate(2026, mo, day, c)
                val a = Alerts.forDay(t)
                for ((name, s, e) in t.list()) {
                    val w = a.firstOrNull {
                        it.kind == AlertKind.QAZA_WARNING && it.prayer == name
                    } ?: continue
                    val endAbs = if (e.minutes <= s.minutes) e.minutes + 1440 else e.minutes
                    if (w.at <= s.minutes || w.at >= endAbs) bad++
                }
            }
        }
        check("every warning sits inside its own window, 6 cities x 12 months",
            bad == 0, "$bad violations")
    }

    println("\n-- alerts: what gets armed --")
    run {
        val t = PrayerTimes.forDate(2026, 9, 19, isb)
        val tm = PrayerTimes.forDate(2026, 9, 20, isb)

        val yd = PrayerTimes.forDate(2026, 9, 18, isb)
        val atDawn = Alerts.toArm(yd, t, tm, nowMinutes = 3 * 60)
        check("arming at 3 AM gives ten alarms", atDawn.size == 10, atDawn.size)
        // At 3 AM the Isha actually running belongs to YESTERDAY, and its
        // qaza warning is roughly ninety minutes away. Building the list from
        // today and tomorrow alone puts that warning a full day late, so the
        // one alert the user needed never arrives.
        val imminent = atDawn.first()
        check("the next alarm at 3 AM is yesterday's Isha qaza warning",
            imminent.kind == AlertKind.QAZA_WARNING &&
                imminent.prayer == "Isha" && imminent.fromDayOffset == -1,
            imminent)
        check("and it is within two hours, not a day away",
            imminent.at - 3 * 60 in 1..120, imminent.at - 3 * 60)
        check("no two alarms share a slot",
            atDawn.map { it.requestCode }.toSet().size == atDawn.size)
        check("everything armed is in the future",
            atDawn.all { it.at > 3 * 60 })
        check("armed list is in time order",
            atDawn.map { it.at } == atDawn.map { it.at }.sorted())

        // Late at night almost nothing is left today, so the list has to reach
        // into tomorrow or the chain dies overnight.
        val lateNight = Alerts.toArm(yd, t, tm, nowMinutes = 23 * 60 + 30)
        check("arming at 11:30 PM still covers tomorrow",
            lateNight.size == 10, lateNight.size)
        check("tomorrow's Fajr is armed",
            lateNight.any { it.prayer == "Fajr" && it.kind == AlertKind.PRAYER_START &&
                it.at == tm.fajr.minutes + 1440 },
            lateNight.filter { it.prayer == "Fajr" }.map { it.at })
        check("nothing armed in the past", lateNight.all { it.at > 23 * 60 + 30 })

        // The distinction that caused a real bug: an alert can ring tomorrow
        // about a prayer that belongs to today. Whoever fires it has to read
        // the right day's marked prayers. Arming at midday, when yesterday is
        // entirely behind us, isolates that case.
        val atNoon = Alerts.toArm(yd, t, tm, nowMinutes = 12 * 60)
        check("nothing from yesterday survives a midday arming",
            atNoon.none { it.fromDayOffset == -1 },
            atNoon.filter { it.fromDayOffset == -1 })
        val ishaWarn = atNoon.first {
            it.prayer == "Isha" && it.kind == AlertKind.QAZA_WARNING
        }
        check("today's Isha warning rings tomorrow about today's prayer",
            ishaWarn.dayOffset == 1 && ishaWarn.fromDayOffset == 0, ishaWarn)
        val tomorrowFajr = lateNight.first {
            it.prayer == "Fajr" && it.kind == AlertKind.PRAYER_START
        }
        check("tomorrow's Fajr rings tomorrow about tomorrow's prayer",
            tomorrowFajr.dayOffset == 1 && tomorrowFajr.fromDayOffset == 1,
            tomorrowFajr)
        check("anything still ringing today belongs to today or earlier",
            atNoon.filter { it.dayOffset == 0 }.all { it.fromDayOffset <= 0 })
        check("a prayer never belongs to a day after the one it rings on",
            (atDawn + atNoon + lateNight).all { it.fromDayOffset <= it.dayOffset })
    }

    println("\n-- alerts: staying quiet when it should --")
    run {
        val a = Alert(AlertKind.QAZA_WARNING, "Asr", 17 * 60)
        check("silent once the prayer is marked",
            !Alerts.stillRelevant(a, 17 * 60, setOf("Asr")))
        check("fires on time when unmarked",
            Alerts.stillRelevant(a, 17 * 60, emptySet()))
        check("fires three minutes late",
            Alerts.stillRelevant(a, 17 * 60 + 3, emptySet()))
        check("stays silent an hour late",
            !Alerts.stillRelevant(a, 18 * 60, emptySet()))
        // An Isha warning lands after midnight; the grace check must not treat
        // "one minute early" as "1439 minutes late".
        val night = Alert(AlertKind.QAZA_WARNING, "Isha", 1440 + 4 * 60 + 20)
        check("a post-midnight warning fires at its own time",
            Alerts.stillRelevant(night, 4 * 60 + 20, emptySet()))
        check("a post-midnight warning fires a minute early",
            Alerts.stillRelevant(night, 4 * 60 + 19, emptySet()))
    }

    println("\n-- streaks: current and longest together --")
    run {
        val all = { _: Int, _: Int, _: Int -> true }
        val none = { _: Int, _: Int, _: Int -> false }

        val lastThirty = { y: Int, m: Int, d: Int ->
            daysBetween(2026, 9, 19, y, m, d) in 0..29
        }
        val perfect = streakSummary(2026, 9, 19, 60, lastThirty)
        check("an unbroken month is both current and best",
            perfect.current == 30 && perfect.best == 30 && perfect.currentIsBest,
            perfect)
        // currentStreak deliberately has no window: it walks back until it
        // finds an incomplete day. Only the *best* search is windowed.
        val forever = streakSummary(2026, 9, 19, 30, all)
        check("an endless history does not spin forever",
            forever.current in 3650..3651 && forever.best == forever.current, forever)

        val empty = streakSummary(2026, 9, 19, 30, none)
        check("nothing prayed means no streak at all",
            empty.current == 0 && empty.best == 0 && !empty.currentIsBest, empty)

        // Complete for the last 3 days, a gap, then a 10-day run before it.
        val gapped = { y: Int, m: Int, d: Int ->
            val days = daysBetween(2026, 9, 19, y, m, d)   // 0 = today
            days in 0..2 || days in 4..13
        }
        val mixed = streakSummary(2026, 9, 19, 60, gapped)
        check("current streak is the recent run", mixed.current == 3, mixed)
        check("best streak is the older, longer run", mixed.best == 10, mixed)
        check("current is correctly not the best", !mixed.currentIsBest, mixed)

        // Today unfinished must not zero the streak, and must not zero the best.
        val exceptToday = { y: Int, m: Int, d: Int ->
            daysBetween(2026, 9, 19, y, m, d) in 1..7
        }
        val pending = streakSummary(2026, 9, 19, 60, exceptToday)
        check("an unfinished today leaves the streak standing",
            pending.current == 7, pending)
        check("best still sees the run behind today", pending.best == 7, pending)
        check("current counts as best when they match",
            pending.currentIsBest, pending)

        // A run longer than the look-back window must never report best < current.
        val longRun = streakSummary(2026, 9, 19, 5, all)
        check("best is never shorter than current",
            longRun.best >= longRun.current, longRun)
    }

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
