package io.frontierlabs.namaz.core

/**
 * English and Urdu text for the whole app.
 *
 * Deliberately a data class rather than a map of keys. A map lets a missing
 * translation slip through and surface as a blank label or a crash at runtime;
 * with named fields the compiler refuses to build until every string exists in
 * both languages. Adding a string to [Str] forces you to translate it.
 */

enum class Lang(val code: String, val label: String, val rtl: Boolean) {
    EN("en", "English", false),
    UR("ur", "اردو", true),
}

data class Str(
    // app + navigation
    val appName: String,
    val tabToday: String,
    val tabMonth: String,
    val tabQibla: String,
    val settings: String,
    val tapToChange: String,
    val done: String,
    val cancel: String,
    val save: String,
    val close: String,

    // prayers
    val fajr: String,
    val zuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val sunrise: String,
    val tahajjud: String,
    val ishraq: String,
    val chasht: String,
    val sehriStarts: String,
    val sehriEnds: String,
    val iftar: String,
    val avoidPrayer: String,

    // today
    val currentPrayer: String,
    val noPrayerDue: String,
    val started: String,
    val becomesQazaAt: String,
    val left: String,
    val upNext: String,
    val beginsIn: String,
    val markedAsPrayed: String,
    val markPrayed: String,
    val prayer: String,
    val azan: String,
    val qazaAt: String,
    val qazaOwed: String,
    val tapToMark: String,
    val alsoToday: String,

    // streak
    val streak: String,
    val day: String,
    val days: String,
    val ofFiveToday: String,
    val allFivePrayed: String,
    val completeAllFive: String,
    val pastDays: String,
    val pastDaysHelp: String,
    val today: String,
    val stillPreviousDay: String,
    val longest: String,
    val thisIsYourBest: String,
    val toBeatYourBest: String,
    val noStreakYet: String,

    // first run and location
    val welcomeTitle: String,
    val welcomeBody: String,
    val useMyLocation: String,
    val chooseMyCity: String,
    val finding: String,
    val foundYouIn: String,
    val locationFailed: String,
    val locationTooFar: String,
    val detectCity: String,
    val locationPrivacy: String,
    val changeAnyTime: String,

    // Islamic calendar
    val tabHijri: String,
    val islamicDate: String,
    val hijriAdjust: String,
    val hijriAdjustHelp: String,
    val hijriNote: String,
    val comingUp: String,
    val todayIs: String,
    val tomorrow: String,
    val inDays: String,

    // calendar
    val pickADate: String,
    val futureDay: String,
    val markedPrayers: String,

    // notifications
    val notifications: String,
    val notifyPrayer: String,
    val notifyPrayerHelp: String,
    val notifyQaza: String,
    val notifyQazaHelp: String,
    val allowNotifications: String,
    val allowNotificationsHelp: String,
    val exactAlarmsNote: String,
    val countMe: String,
    val countMeHelp: String,
    val hasBegun: String,
    val timeToPray: String,
    val aboutToBecomeQaza: String,
    val minutesLeft: String,
    val prayItNow: String,
    val channelPrayerName: String,
    val channelPrayerDesc: String,
    val channelQazaName: String,
    val channelQazaDesc: String,
    val channelUpdateName: String,
    val channelUpdateDesc: String,
    val newVersionReady: String,
    val tapToDownload: String,

    // month
    val tapAnyDay: String,
    val windowNote: String,
    val amPmNote: String,

    // qibla
    val qiblaFrom: String,
    val fromTrueNorth: String,
    val kmToMakkah: String,
    val facingQibla: String,
    val turnRight: String,
    val turnLeft: String,
    val noCompass: String,
    val noCompassHelp: String,
    val accurateReading: String,
    val holdFlat: String,
    val awayFromMetal: String,
    val figureEight: String,
    val declinationNote: String,

    // city + settings
    val chooseCity: String,
    val searchCity: String,
    val noCityMatched: String,
    val calculation: String,
    val language: String,
    val asrMethod: String,
    val asrNote: String,
    val fajrIshaAngles: String,
    val methodNote: String,

    // footer
    val developedBy: String,
    val footerNote: String,
    val followMasjid: String,

    // updates
    val updateRequired: String,
    val updateAvailable: String,
    val updateBlockedNote: String,
    val downloadUpdate: String,
    val later: String,
    val currentVersion: String,
)

object Strings {

    val EN = Str(
        appName = "Namaz Timings",
        tabToday = "Today",
        tabMonth = "Month",
        tabQibla = "Qibla",
        settings = "Settings",
        tapToChange = "tap to change",
        done = "Done",
        cancel = "Cancel",
        save = "Save",
        close = "Close",

        fajr = "Fajr",
        zuhr = "Zuhr",
        asr = "Asr",
        maghrib = "Maghrib",
        isha = "Isha",
        sunrise = "Sunrise",
        tahajjud = "Tahajjud",
        ishraq = "Ishraq",
        chasht = "Chasht (Duha)",
        sehriStarts = "Sehri starts",
        sehriEnds = "Sehri ends",
        iftar = "Iftar",
        avoidPrayer = "Avoid prayer",

        currentPrayer = "CURRENT PRAYER",
        noPrayerDue = "NO PRAYER DUE NOW",
        started = "started",
        becomesQazaAt = "BECOMES QAZA AT",
        left = "left",
        upNext = "UP NEXT",
        beginsIn = "begins in",
        markedAsPrayed = "Marked as prayed",
        markPrayed = "Mark as prayed",
        prayer = "PRAYER",
        azan = "AZAN",
        qazaAt = "QAZA AT",
        qazaOwed = "qaza — owed",
        tapToMark = "Tap a prayer to mark it prayed. Tap again to undo. " +
            "Unmarked prayers turn red once their time has passed.",
        alsoToday = "ALSO TODAY",

        streak = "STREAK",
        day = "day",
        days = "days",
        ofFiveToday = "of 5 today",
        allFivePrayed = "All five prayed today.",
        completeAllFive = "Complete all five to extend your streak.",
        pastDays = "Past days",
        pastDaysHelp = "Tap a day, then tap each prayer you have offered — " +
            "including qaza you have since made up. A day with all five counts " +
            "towards your streak.",
        today = "today",
        stillPreviousDay = "Still yesterday's prayers — the day turns at Fajr, "
            + "not at midnight.",
        longest = "LONGEST",
        thisIsYourBest = "This is your longest run yet.",
        toBeatYourBest = "to go to beat your record.",
        noStreakYet = "No streak yet — today can start one.",

        welcomeTitle = "Where are you praying?",
        welcomeBody = "Prayer times change from city to city. Peshawar's Maghrib " +
            "is about eleven minutes after Lahore's, so picking the right place " +
            "matters more than it sounds.",
        useMyLocation = "Use my location",
        chooseMyCity = "Choose my city",
        finding = "Finding your location\u2026",
        foundYouIn = "Nearest city",
        locationFailed = "Could not get your location. Please choose your city.",
        locationTooFar = "You seem to be outside Pakistan. Please choose the city " +
            "whose times you want.",
        detectCity = "Detect my city",
        locationPrivacy = "Used only to pick the nearest city from the list. " +
            "Nothing is sent anywhere and nothing is stored but the city name.",
        changeAnyTime = "You can change this any time by tapping the city name.",

        tabHijri = "Hijri",
        islamicDate = "ISLAMIC DATE",
        hijriAdjust = "Adjust by",
        hijriAdjustHelp = "Nudge the Islamic date to match what was announced " +
            "where you live.",
        hijriNote = "This date is calculated, not sighted. The Ruet-e-Hilal " +
            "Committee announces the month when the crescent is actually seen, " +
            "which can be a day or two from this. Follow the announcement and " +
            "set the adjustment to match.",
        comingUp = "COMING UP",
        todayIs = "Today",
        tomorrow = "Tomorrow",
        inDays = "in",

        pickADate = "Pick a day",
        futureDay = "Not here yet",
        markedPrayers = "marked",

        notifications = "NOTIFICATIONS",
        notifyPrayer = "Prayer time alerts",
        notifyPrayerHelp = "A notification the moment each prayer's time begins.",
        notifyQaza = "Qaza warnings",
        notifyQazaHelp = "A reminder 15 minutes before a prayer's window closes, " +
            "but only if you have not marked it yet.",
        allowNotifications = "Allow notifications",
        allowNotificationsHelp = "Android needs your permission before the app " +
            "can show anything.",
        exactAlarmsNote = "If alerts arrive late, turn off battery optimisation " +
            "for this app in Android settings.",
        countMe = "Count this phone",
        countMeHelp = "Once a day the app sends a random number so the developer " +
            "knows how many phones still use it. No name, no location, no times \u2014 " +
            "nothing that says who you are.",
        hasBegun = "has begun",
        timeToPray = "Time to pray.",
        aboutToBecomeQaza = "is about to become qaza",
        minutesLeft = "minutes left",
        prayItNow = "Pray it now, then mark it in the app.",
        channelPrayerName = "Prayer times",
        channelPrayerDesc = "Fires when each prayer's time begins.",
        channelQazaName = "Qaza warnings",
        channelQazaDesc = "Fires shortly before an unmarked prayer becomes qaza.",
        channelUpdateName = "App updates",
        channelUpdateDesc = "Tells you when a new version of the app is ready.",
        newVersionReady = "A new version is ready",
        tapToDownload = "Tap to download and install it.",

        tapAnyDay = "Tap any day for the full timetable",
        windowNote = "Left column is the start, right column is when the window " +
            "closes. After that a prayer becomes qaza.",
        amPmNote = "Fajr is AM; Zuhr, Asr, Maghrib and Isha are PM.",

        qiblaFrom = "QIBLA FROM",
        fromTrueNorth = "from true north",
        kmToMakkah = "km to Makkah",
        facingQibla = "You are facing the Qibla",
        turnRight = "Turn right",
        turnLeft = "Turn left",
        noCompass = "No compass on this phone",
        noCompassHelp = "The bearing above is still correct. Face true north, " +
            "then turn clockwise by that many degrees.",
        accurateReading = "FOR AN ACCURATE READING",
        holdFlat = "Hold the phone flat, screen up.",
        awayFromMetal = "Move away from metal, speakers, laptops and magnets — " +
            "they pull the compass badly.",
        figureEight = "If it drifts, wave the phone in a figure of eight to recalibrate.",
        declinationNote = "Magnetic declination here is",

        chooseCity = "Choose city",
        searchCity = "Search city or province",
        noCityMatched = "No city matched. Try a nearby larger city.",
        calculation = "Calculation",
        language = "LANGUAGE",
        asrMethod = "ASR METHOD",
        asrNote = "Most of Pakistan follows Hanafi. The two differ by around " +
            "30–60 minutes.",
        fajrIshaAngles = "FAJR / ISHA ANGLES",
        methodNote = "Karachi is the standard across Pakistan. Change it only if " +
            "your mosque follows something else.",

        developedBy = "Developed by Mansoor Ahmad",
        footerNote = "Times are calculated, not fetched.",
        followMasjid = "Follow your local masjid where it differs.",

        updateRequired = "Update required",
        updateAvailable = "Update available",
        updateBlockedNote = "This version is out of date and can no longer be used. " +
            "Download the new version to continue.",
        downloadUpdate = "Download update",
        later = "Later",
        currentVersion = "You have version",
    )

    val UR = Str(
        appName = "نماز اوقات",
        tabToday = "آج",
        tabMonth = "مہینہ",
        tabQibla = "قبلہ",
        settings = "ترتیبات",
        tapToChange = "تبدیل کرنے کے لیے دبائیں",
        done = "مکمل",
        cancel = "منسوخ",
        save = "محفوظ کریں",
        close = "بند کریں",

        fajr = "فجر",
        zuhr = "ظہر",
        asr = "عصر",
        maghrib = "مغرب",
        isha = "عشاء",
        sunrise = "طلوعِ آفتاب",
        tahajjud = "تہجد",
        ishraq = "اشراق",
        chasht = "چاشت",
        sehriStarts = "سحری کا آغاز",
        sehriEnds = "سحری ختم",
        iftar = "افطار",
        avoidPrayer = "نماز سے پرہیز",

        currentPrayer = "موجودہ نماز",
        noPrayerDue = "اس وقت کوئی نماز واجب نہیں",
        started = "شروع ہوئی",
        becomesQazaAt = "قضا ہونے کا وقت",
        left = "باقی",
        upNext = "اگلی نماز",
        beginsIn = "شروع ہونے میں",
        markedAsPrayed = "ادا شدہ نشان زد",
        markPrayed = "ادا شدہ نشان لگائیں",
        prayer = "نماز",
        azan = "اذان",
        qazaAt = "قضا وقت",
        qazaOwed = "قضا — واجب الادا",
        tapToMark = "نماز ادا کرنے کے بعد اس پر دبائیں۔ واپس لینے کے لیے دوبارہ دبائیں۔ " +
            "وقت گزرنے کے بعد غیر نشان زد نمازیں سرخ ہو جاتی ہیں۔",
        alsoToday = "آج مزید",

        streak = "تسلسل",
        day = "دن",
        days = "دن",
        ofFiveToday = "آج ۵ میں سے",
        allFivePrayed = "آج پانچوں نمازیں ادا ہو گئیں۔",
        completeAllFive = "تسلسل بڑھانے کے لیے پانچوں نمازیں مکمل کریں۔",
        pastDays = "گزشتہ دن",
        pastDaysHelp = "دن پر دبائیں، پھر ہر وہ نماز منتخب کریں جو آپ نے ادا کی — " +
            "بشمول وہ قضا نمازیں جو بعد میں ادا کیں۔ پانچوں نمازوں والا دن تسلسل میں شمار ہوتا ہے۔",
        today = "آج",
        stillPreviousDay = "یہ اب بھی گزشتہ دن کی نمازیں ہیں — دن رات بارہ بجے نہیں، "
            + "فجر سے بدلتا ہے۔",
        longest = "سب سے طویل",
        thisIsYourBest = "یہ اب تک کا آپ کا سب سے طویل سلسلہ ہے۔",
        toBeatYourBest = "اپنا ریکارڈ توڑنے کے لیے باقی",
        noStreakYet = "ابھی کوئی تسلسل نہیں — آج سے شروع کریں۔",

        welcomeTitle = "آپ کہاں نماز ادا کرتے ہیں؟",
        welcomeBody = "نماز کے اوقات ہر شہر میں مختلف ہوتے ہیں۔ پشاور کی مغرب " +
            "لاہور سے تقریباً گیارہ منٹ بعد ہوتی ہے، اس لیے درست شہر کا انتخاب اہم ہے۔",
        useMyLocation = "میری لوکیشن استعمال کریں",
        chooseMyCity = "میں خود شہر منتخب کروں گا",
        finding = "آپ کی لوکیشن تلاش کی جا رہی ہے\u2026",
        foundYouIn = "قریب ترین شہر",
        locationFailed = "لوکیشن حاصل نہیں ہو سکی۔ براہِ کرم اپنا شہر منتخب کریں۔",
        locationTooFar = "لگتا ہے آپ پاکستان سے باہر ہیں۔ براہِ کرم وہ شہر منتخب کریں " +
            "جس کے اوقات آپ دیکھنا چاہتے ہیں۔",
        detectCity = "میرا شہر خود معلوم کریں",
        locationPrivacy = "صرف فہرست میں سے قریب ترین شہر منتخب کرنے کے لیے استعمال ہوتی ہے۔ " +
            "کچھ بھی کہیں نہیں بھیجا جاتا، صرف شہر کا نام محفوظ ہوتا ہے۔",
        changeAnyTime = "آپ شہر کے نام پر دبا کر اسے کسی بھی وقت تبدیل کر سکتے ہیں۔",

        tabHijri = "ہجری",
        islamicDate = "اسلامی تاریخ",
        hijriAdjust = "تبدیلی",
        hijriAdjustHelp = "اسلامی تاریخ کو اپنے علاقے کے اعلان کے مطابق آگے پیچھے کریں۔",
        hijriNote = "یہ تاریخ حساب سے نکالی گئی ہے، رویتِ ہلال سے نہیں۔ رویتِ ہلال " +
            "کمیٹی چاند نظر آنے پر مہینے کا اعلان کرتی ہے، جو اس سے ایک دو دن آگے " +
            "پیچھے ہو سکتا ہے۔ اعلان کی پیروی کریں اور تبدیلی اسی کے مطابق رکھیں۔",
        comingUp = "آنے والے دن",
        todayIs = "آج",
        tomorrow = "کل",
        inDays = "میں",

        pickADate = "دن منتخب کریں",
        futureDay = "ابھی نہیں آیا",
        markedPrayers = "نشان زد",

        notifications = "اطلاعات",
        notifyPrayer = "نماز کے وقت کی اطلاع",
        notifyPrayerHelp = "ہر نماز کا وقت شروع ہوتے ہی اطلاع ملے گی۔",
        notifyQaza = "قضا ہونے کی وارننگ",
        notifyQazaHelp = "نماز کا وقت ختم ہونے سے ۱۵ منٹ پہلے یاد دہانی، " +
            "لیکن صرف اُس صورت میں جب آپ نے اسے ادا شدہ نشان زد نہ کیا ہو۔",
        allowNotifications = "اطلاعات کی اجازت دیں",
        allowNotificationsHelp = "اطلاعات دکھانے سے پہلے اینڈرائیڈ کو آپ کی اجازت درکار ہے۔",
        exactAlarmsNote = "اگر اطلاعات دیر سے آئیں تو اینڈرائیڈ کی ترتیبات میں " +
            "اس ایپ کے لیے بیٹری آپٹیمائزیشن بند کر دیں۔",
        countMe = "اس فون کو شمار کریں",
        countMeHelp = "دن میں ایک بار ایپ ایک بے ترتیب نمبر بھیجتی ہے تاکہ ڈویلپر کو " +
            "معلوم ہو کہ کتنے فون ابھی استعمال کر رہے ہیں۔ نہ نام، نہ مقام، نہ اوقات \u2014 " +
            "ایسی کوئی چیز نہیں جو بتائے کہ آپ کون ہیں۔",
        hasBegun = "کا وقت ہو گیا",
        timeToPray = "نماز کا وقت ہے۔",
        aboutToBecomeQaza = "قضا ہونے والی ہے",
        minutesLeft = "منٹ باقی",
        prayItNow = "ابھی ادا کریں، پھر ایپ میں نشان زد کریں۔",
        channelPrayerName = "نماز کے اوقات",
        channelPrayerDesc = "ہر نماز کا وقت شروع ہونے پر اطلاع دیتا ہے۔",
        channelQazaName = "قضا وارننگ",
        channelQazaDesc = "غیر ادا شدہ نماز کے قضا ہونے سے کچھ دیر پہلے اطلاع دیتا ہے۔",
        channelUpdateName = "ایپ اپ ڈیٹ",
        channelUpdateDesc = "ایپ کا نیا ورژن تیار ہونے پر اطلاع دیتا ہے۔",
        newVersionReady = "نیا ورژن تیار ہے",
        tapToDownload = "ڈاؤن لوڈ اور انسٹال کرنے کے لیے دبائیں۔",

        tapAnyDay = "مکمل نقشے کے لیے کسی بھی دن پر دبائیں",
        windowNote = "بائیں جانب آغاز کا وقت ہے، دائیں جانب وقت ختم ہونے کا۔ " +
            "اس کے بعد نماز قضا ہو جاتی ہے۔",
        amPmNote = "فجر صبح ہے؛ ظہر، عصر، مغرب اور عشاء شام کے اوقات ہیں۔",

        qiblaFrom = "قبلہ کی سمت",
        fromTrueNorth = "حقیقی شمال سے",
        kmToMakkah = "کلومیٹر مکہ تک",
        facingQibla = "آپ قبلہ رخ ہیں",
        turnRight = "دائیں مڑیں",
        turnLeft = "بائیں مڑیں",
        noCompass = "اس فون میں قطب نما نہیں ہے",
        noCompassHelp = "اوپر دی گئی سمت درست ہے۔ حقیقی شمال کی طرف رخ کریں، " +
            "پھر اتنے درجے دائیں مڑیں۔",
        accurateReading = "درست پیمائش کے لیے",
        holdFlat = "فون کو سیدھا رکھیں، اسکرین اوپر کی جانب۔",
        awayFromMetal = "دھات، اسپیکر، لیپ ٹاپ اور مقناطیس سے دور ہو جائیں — " +
            "یہ قطب نما کو بہت متاثر کرتے ہیں۔",
        figureEight = "اگر سمت بدلتی رہے تو فون کو آٹھ کے ہندسے کی شکل میں گھمائیں۔",
        declinationNote = "یہاں مقناطیسی انحراف ہے",

        chooseCity = "شہر منتخب کریں",
        searchCity = "شہر یا صوبہ تلاش کریں",
        noCityMatched = "کوئی شہر نہیں ملا۔ قریبی بڑا شہر منتخب کریں۔",
        calculation = "حساب کا طریقہ",
        language = "زبان",
        asrMethod = "عصر کا طریقہ",
        asrNote = "پاکستان میں زیادہ تر حنفی طریقہ رائج ہے۔ دونوں میں تقریباً " +
            "۳۰ سے ۶۰ منٹ کا فرق ہوتا ہے۔",
        fajrIshaAngles = "فجر / عشاء کے زاویے",
        methodNote = "کراچی کا طریقہ پورے پاکستان میں معیاری ہے۔ صرف اسی صورت " +
            "تبدیل کریں جب آپ کی مسجد کوئی اور طریقہ اختیار کرتی ہو۔",

        developedBy = "منصور احمد کی تیار کردہ",
        footerNote = "اوقات حساب سے نکالے گئے ہیں، کہیں سے حاصل نہیں کیے گئے۔",
        followMasjid = "فرق ہونے کی صورت میں اپنی مقامی مسجد کی پیروی کریں۔",

        updateRequired = "اپ ڈیٹ ضروری ہے",
        updateAvailable = "نیا ورژن دستیاب ہے",
        updateBlockedNote = "یہ ورژن پرانا ہو چکا ہے اور مزید استعمال نہیں ہو سکتا۔ " +
            "جاری رکھنے کے لیے نیا ورژن ڈاؤن لوڈ کریں۔",
        downloadUpdate = "اپ ڈیٹ ڈاؤن لوڈ کریں",
        later = "بعد میں",
        currentVersion = "آپ کے پاس ورژن ہے",
    )

    fun of(lang: Lang): Str = if (lang == Lang.UR) UR else EN

    /** Every field, in declaration order. Used by the translation tests. */
    fun fields(s: Str): List<String> = listOf(
        s.appName, s.tabToday, s.tabMonth, s.tabQibla, s.settings, s.tapToChange,
        s.done, s.cancel, s.save, s.close,
        s.fajr, s.zuhr, s.asr, s.maghrib, s.isha, s.sunrise, s.tahajjud,
        s.ishraq, s.chasht, s.sehriStarts, s.sehriEnds, s.iftar, s.avoidPrayer,
        s.currentPrayer, s.noPrayerDue, s.started, s.becomesQazaAt, s.left,
        s.upNext, s.beginsIn, s.markedAsPrayed, s.markPrayed, s.prayer, s.azan, s.qazaAt,
        s.qazaOwed, s.tapToMark, s.alsoToday,
        s.streak, s.day, s.days, s.ofFiveToday, s.allFivePrayed,
        s.completeAllFive, s.pastDays, s.pastDaysHelp, s.today,
        s.stillPreviousDay,
        s.longest, s.thisIsYourBest, s.toBeatYourBest, s.noStreakYet,
        s.welcomeTitle, s.welcomeBody, s.useMyLocation, s.chooseMyCity,
        s.finding, s.foundYouIn, s.locationFailed, s.locationTooFar,
        s.detectCity, s.locationPrivacy, s.changeAnyTime,
        s.tabHijri, s.islamicDate, s.hijriAdjust, s.hijriAdjustHelp,
        s.hijriNote, s.comingUp, s.todayIs, s.tomorrow, s.inDays,
        s.pickADate, s.futureDay, s.markedPrayers,
        s.notifications, s.notifyPrayer, s.notifyPrayerHelp, s.notifyQaza,
        s.notifyQazaHelp,
        s.allowNotifications, s.allowNotificationsHelp, s.exactAlarmsNote,
        s.countMe, s.countMeHelp,
        s.hasBegun, s.timeToPray, s.aboutToBecomeQaza, s.minutesLeft,
        s.prayItNow, s.channelPrayerName, s.channelPrayerDesc,
        s.channelQazaName, s.channelQazaDesc, s.channelUpdateName,
        s.channelUpdateDesc, s.newVersionReady, s.tapToDownload,
        s.tapAnyDay, s.windowNote, s.amPmNote,
        s.qiblaFrom, s.fromTrueNorth, s.kmToMakkah, s.facingQibla, s.turnRight,
        s.turnLeft, s.noCompass, s.noCompassHelp, s.accurateReading, s.holdFlat,
        s.awayFromMetal, s.figureEight, s.declinationNote,
        s.chooseCity, s.searchCity, s.noCityMatched, s.calculation, s.language,
        s.asrMethod, s.asrNote, s.fajrIshaAngles, s.methodNote,
        s.developedBy, s.footerNote, s.followMasjid,
        s.updateRequired, s.updateAvailable, s.updateBlockedNote,
        s.downloadUpdate, s.later, s.currentVersion,
    )

    /** Prayer name in the chosen language. */
    fun prayerName(lang: Lang, english: String): String {
        val s = of(lang)
        return when (english) {
            "Fajr" -> s.fajr
            "Zuhr" -> s.zuhr
            "Asr" -> s.asr
            "Maghrib" -> s.maghrib
            "Isha" -> s.isha
            "Sunrise" -> s.sunrise
            "Tahajjud" -> s.tahajjud
            "Ishraq" -> s.ishraq
            "Chasht (Duha)" -> s.chasht
            "Sehri starts" -> s.sehriStarts
            "Sehri ends" -> s.sehriEnds
            "Iftar" -> s.iftar
            "Avoid prayer" -> s.avoidPrayer
            else -> english
        }
    }

    /** Month name in the chosen language. */
    fun monthName(lang: Lang, month: Int): String =
        if (lang == Lang.UR) MONTHS_UR[month - 1] else MONTHS_EN[month - 1]

    private val MONTHS_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val MONTHS_UR = listOf(
        "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
        "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر",
    )

    /**
     * Short weekday name for the calendar header, [index] 0 = Sunday.
     *
     * Sunday first, because that is how printed calendars in Pakistan are laid
     * out. Note this is not `DayOfWeek.value`, which counts Monday as 1.
     */
    fun weekdayShort(lang: Lang, index: Int): String =
        if (lang == Lang.UR) WEEK_UR[index] else WEEK_EN[index]

    private val WEEK_EN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val WEEK_UR = listOf("اتو", "پیر", "منگ", "بدھ", "جمعر", "جمعہ", "ہفتہ")

    /** Islamic month name, [month] 1 = Muharram. */
    fun hijriMonth(lang: Lang, month: Int): String =
        if (lang == Lang.UR) HIJRI_UR[month - 1] else HIJRI_EN[month - 1]

    private val HIJRI_EN = listOf(
        "Muharram", "Safar", "Rabi\u2019 al-Awwal", "Rabi\u2019 al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha\u2019ban",
        "Ramadan", "Shawwal", "Dhul Qa\u2019dah", "Dhul Hijjah",
    )
    private val HIJRI_UR = listOf(
        "محرم", "صفر", "ربیع الاول", "ربیع الثانی",
        "جمادی الاول", "جمادی الثانی", "رجب", "شعبان",
        "رمضان", "شوال", "ذوالقعدہ", "ذوالحجہ",
    )

    /** Name of an Islamic occasion, in the chosen language. */
    fun eventName(lang: Lang, event: IslamicEvent): String =
        if (lang == Lang.UR) when (event) {
            IslamicEvent.NEW_YEAR -> "اسلامی نیا سال"
            IslamicEvent.ASHURA -> "یومِ عاشورہ"
            IslamicEvent.MILAD -> "عید میلاد النبی ﷺ"
            IslamicEvent.MIRAJ -> "شبِ معراج"
            IslamicEvent.BARAT -> "شبِ برات"
            IslamicEvent.RAMADAN_BEGINS -> "رمضان کا آغاز"
            IslamicEvent.QADR -> "شبِ قدر"
            IslamicEvent.EID_FITR -> "عید الفطر"
            IslamicEvent.ARAFAH -> "یومِ عرفہ"
            IslamicEvent.EID_ADHA -> "عید الاضحیٰ"
        } else when (event) {
            IslamicEvent.NEW_YEAR -> "Islamic New Year"
            IslamicEvent.ASHURA -> "Ashura"
            IslamicEvent.MILAD -> "Eid Milad-un-Nabi"
            IslamicEvent.MIRAJ -> "Shab-e-Miraj"
            IslamicEvent.BARAT -> "Shab-e-Barat"
            IslamicEvent.RAMADAN_BEGINS -> "Ramadan begins"
            IslamicEvent.QADR -> "Shab-e-Qadr"
            IslamicEvent.EID_FITR -> "Eid-ul-Fitr"
            IslamicEvent.ARAFAH -> "Day of Arafah"
            IslamicEvent.EID_ADHA -> "Eid-ul-Adha"
        }

    /** City name in the chosen language, falling back to English. */
    fun cityName(lang: Lang, english: String): String =
        if (lang == Lang.UR) CITY_UR[english] ?: english else english

    val CITY_UR: Map<String, String> = mapOf(
        "Peshawar" to "پشاور", "Mardan" to "مردان", "Mingora (Swat)" to "مینگورہ (سوات)",
        "Kohat" to "کوہاٹ", "Abbottabad" to "ایبٹ آباد", "Mansehra" to "مانسہرہ",
        "Haripur" to "ہری پور", "Nowshera" to "نوشہرہ", "Charsadda" to "چارسدہ",
        "Swabi" to "صوابی", "Bannu" to "بنوں", "Dera Ismail Khan" to "ڈیرہ اسماعیل خان",
        "Hangu" to "ہنگو", "Timergara" to "تیمرگرہ", "Chitral" to "چترال",
        "Batkhela" to "بٹ خیلہ",
        "Lahore" to "لاہور", "Rawalpindi" to "راولپنڈی", "Faisalabad" to "فیصل آباد",
        "Multan" to "ملتان", "Gujranwala" to "گوجرانوالہ", "Sialkot" to "سیالکوٹ",
        "Sargodha" to "سرگودھا", "Bahawalpur" to "بہاولپور", "Sahiwal" to "ساہیوال",
        "Sheikhupura" to "شیخوپورہ", "Jhang" to "جھنگ", "Rahim Yar Khan" to "رحیم یار خان",
        "Kasur" to "قصور", "Okara" to "اوکاڑہ", "Jhelum" to "جہلم",
        "Chakwal" to "چکوال", "Attock" to "اٹک", "Khanewal" to "خانیوال",
        "Vehari" to "وہاڑی", "Dera Ghazi Khan" to "ڈیرہ غازی خان", "Gujrat" to "گجرات",
        "Mianwali" to "میانوالی", "Bhakkar" to "بھکر",
        "Islamabad" to "اسلام آباد",
        "Karachi" to "کراچی", "Hyderabad" to "حیدرآباد", "Sukkur" to "سکھر",
        "Larkana" to "لاڑکانہ", "Nawabshah" to "نوابشاہ", "Mirpur Khas" to "میرپور خاص",
        "Dadu" to "دادو", "Thatta" to "ٹھٹھہ", "Shikarpur" to "شکارپور",
        "Jacobabad" to "جیکب آباد",
        "Quetta" to "کوئٹہ", "Gwadar" to "گوادر", "Turbat" to "تربت",
        "Khuzdar" to "خضدار", "Zhob" to "ژوب", "Chaman" to "چمن",
        "Sibi" to "سبی", "Loralai" to "لورالائی",
        "Muzaffarabad" to "مظفرآباد", "Mirpur" to "میرپور", "Rawalakot" to "راولاکوٹ",
        "Gilgit" to "گلگت", "Skardu" to "سکردو", "Hunza" to "ہنزہ",
    )

    /** Province name in the chosen language. */
    fun provinceName(lang: Lang, english: String): String =
        if (lang == Lang.UR) when (english) {
            "KP" -> "خیبر پختونخوا"
            "Punjab" -> "پنجاب"
            "Sindh" -> "سندھ"
            "Balochistan" -> "بلوچستان"
            "ICT" -> "اسلام آباد"
            "AJK" -> "آزاد کشمیر"
            "GB" -> "گلگت بلتستان"
            else -> english
        } else english
}
