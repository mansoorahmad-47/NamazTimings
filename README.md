# Namaz Timings

Prayer times for Pakistan. Works completely offline, for any date, with no
account, no internet permission and no location access.

---

## Getting the APK

Same route that worked for Makentication.

1. Create a **private** repo on GitHub.
2. Upload this folder — **drag the `NamazTimings` folder itself**, not its
   contents, so the hidden `.github` folder comes along. If it doesn't, add
   `.github/workflows/build-apk.yml` by hand with **Add file → Create new file**.
3. Open **Actions**, wait for the green tick, and download **namaz-apk** from
   the Artifacts section at the bottom of the run.

Or open the folder in Android Studio and **Build → Build APK(s)**.

---

## What it shows

**Every prayer with a start *and* an end.** The end is the part most apps
leave out, and it's the one that matters: once that time passes the prayer
becomes **qaza** and has to be made up.

| Prayer | Starts | Window closes at |
| --- | --- | --- |
| Fajr | Subh Sadiq | Sunrise |
| Zuhr | After solar noon | Asr |
| Asr | Shadow length rule | Maghrib |
| Maghrib | Sunset | Isha |
| Isha | Twilight ends | One minute before the next Fajr |

Azan is called at the start time, so the "start" column is your azan column.

**Sehri and Iftar** are shown on their own, with a small caution margin — three
minutes before Subh Sadiq for Sehri, two minutes after sunset for Iftar. Set
both to zero in the code if you'd rather have the exact astronomical moment.

**The front page leads with the prayer you are in**, when it becomes qaza, and
a live countdown to that moment — turning red in the last half hour. The next
prayer sits below it. Between sunrise and Zuhr, when no fard prayer is due, it
says so instead of inventing one.

**The day turns at Fajr, not at midnight.** Isha runs until Subh Sadiq, so at
one in the morning the prayer being offered is still the previous day's — and
so are the four already prayed. Rolling the screen over at 00:00 would wipe
that progress from view and put a finished day on record as unfinished. Until
Fajr the app stays on the previous day and says so in a line at the top.

**A prayer can only be marked once its time has come.** Prayers whose azan has
not been called are dimmed and not tappable, on the front page and in the
calendar alike — there is no marking tomorrow's Asr this morning.

**Tracking and streaks.** Tap a prayer to mark it prayed; tap again to undo.
Unmarked prayers turn red and are labelled qaza once their window closes. Your
streak counts consecutive days on which all five were prayed — an unfinished
today never breaks it, only a finished day that was missed.

**Current streak and longest streak, side by side.** When the run going on now
is the longest there has been, the card says so and marks it with a star. When
an older run was longer, both numbers show, with how many days are left to beat
it. A record you cannot see is not much of a record.

**Tap the streak card for a calendar.** A month at a time, with each day
coloured by how it went — green for all five, gold for a partial day — so the
gaps in a streak are visible as gaps. Page back through the months, tap any
past day, and mark the prayers you have since made up. Days that have not
happened yet are drawn but cannot be tapped. A qaza prayer is still prayed, so
the streak recovers once you have offered it. Everything is stored on the
device.

**Also shown:** sunrise, Tahajjud, Ishraq, Chasht, and the three times when
prayer should be avoided.

**Qibla compass, monthly chart, English and Urdu** — all as before.

**Monthly chart** — every day of any month in one scrollable table, today's
row highlighted. Pick any month from January to December, step through years,
and tap a row (the chevron on the right marks it as tappable) to open that
day's complete timetable: Sehri start and end, Tahajjud, Fajr, sunrise, Ishraq,
Chasht, Zuhr, Asr, Iftar, Maghrib and Isha, each with its start and the time
its window closes, plus all three avoid-prayer periods.

**English and Urdu.** Switch in Settings; the whole interface flips to
right-to-left in Urdu, including prayer names, month names, all 66 city names
and the provinces.

**Qibla compass** — the true great-circle bearing to the Kaaba from your
selected city, with a live needle. Magnetic declination is applied, so the
needle points at true Qibla rather than a couple of degrees off. The bearing
and distance still show on phones with no magnetometer.

**66 cities**, including all the ones you asked for: Peshawar, Rawalpindi,
Islamabad, Mardan, Kohat, Mingora (Swat) and Lahore — plus Karachi, Quetta,
Faisalabad, Multan, Gujranwala, Sialkot, Abbottabad, Bannu, D.I. Khan, Chitral,
Gilgit, Skardu, Muzaffarabad, Gwadar, Turbat and the rest.

Longitude is what moves the clock: one degree is four minutes, so Peshawar's
Maghrib is about eleven minutes after Lahore's. That's why the list is long —
picking a nearby big city instead of your own town is the main source of error
in practice.

---

## How the times are calculated

Not downloaded, not a stored table. The app computes the sun's declination and
the equation of time for the date, finds solar noon for your longitude, and
places each prayer at the hour angle where the sun sits at the defining
altitude. That's why it works offline and for any year.

**Two settings decide everything, and the defaults are the Pakistani ones.**

**Asr — Hanafi (default).** The shadow rule: Hanafi uses a shadow twice the
object's length, everyone else uses once. The difference is 30 to 60 minutes,
and it's the single most common error in prayer apps. Most of Pakistan is
Hanafi. Switchable in Settings.

**Fajr and Isha — University of Islamic Sciences, Karachi (default).** 18° below
the horizon for both. This is the standard across Pakistan. Muslim World League,
ISNA, Egyptian and Umm al-Qura are also available if your mosque follows one of
those.

Pakistan is UTC+5 year round with no daylight saving, which is built in.

---

## Accuracy, honestly

The astronomy is verified against objective checks, not eyeballed. 220 tests
cover it, including the ones that actually catch errors:

- Solar declination hits ±23.44° at the solstices and 0° at the equinoxes.
- **Day length on the equinox is 12h 07m** at every latitude tested — this is
  the check that matters, and it's what caught a sign error that had put Fajr
  at 7:25 AM and Isha at 4:39 PM.
- Sunrise and sunset are symmetric about solar noon to within a minute.
- Prayer order holds for all 66 cities across all 12 months.
- Peshawar's Maghrib lands the predicted 11 minutes after Lahore's.
- Hanafi Asr is later than Shafi'i by a realistic margin.

Sample output, Islamabad, 19 September 2026 (Karachi method, Hanafi):

```
Sehri ends  4:27 AM
Fajr        4:30 AM   until 5:54 AM
Sunrise     5:54 AM
Zuhr       12:03 PM   until 4:25 PM
Asr         4:25 PM   until 6:10 PM
Maghrib     6:10 PM   until 7:33 PM
Iftar       6:12 PM
Isha        7:33 PM   until 4:31 AM
```

These match published timetables to within a couple of minutes.

**Follow your local masjid where it differs.** Mosque committees sometimes
round times or apply their own margin, particularly for Fajr and Isha, and in
Ramadan. Calculated times are a reference, not a ruling.

---

## Running the tests

No Android SDK needed — the whole calculation is plain Kotlin.

```bash
kotlinc app/src/main/java/io/frontierlabs/namaz/core/*.kt tests/Test.kt \
  -include-runtime -d /tmp/t.jar && java -jar /tmp/t.jar
```

---

## Notifications

Two kinds, both switchable in Settings, both on by default.


**Prayer time.** A notification the moment each prayer's window opens.

**Qaza warning.** Fifteen minutes before a window closes — but only if you
have not marked that prayer yet. Mark Asr at four o'clock and the 5:55 warning
never arrives. This is the one the whole feature is for.

Neither is optional in the sense that matters: both are on out of the box, and
**update alerts have no switch at all**. That one channel is the only way a
phone can learn a new build exists, and a switch there would only ever get
turned off by accident, leaving someone stranded on an old version with no way
to find out. Android's own per-channel controls still work if anyone objects.

The awkward parts are handled rather than hoped for:

- **Exact alarms.** A prayer alert five minutes late is a wrong prayer alert,
  and a qaza warning that slips past the deadline is worse than none. The app
  asks for exact alarms and falls back to inexact ones rather than failing if
  the permission is refused.
- **Midnight.** Isha's window ends at the next day's Subh Sadiq, so its qaza
  warning rings around four in the morning — the following day, about the
  previous day's prayer. Getting that distinction wrong makes the app check
  the wrong day's marked prayers; there are tests for it specifically. The
  alarms are also built from a three-day window rather than two, because at
  three in the morning the Isha still running belongs to *yesterday* and its
  warning is the very next alert due.
- **Reboots and reinstalls.** Android drops every alarm on reboot, on a clock
  or time-zone change, and when you install the next APK over the top. All
  four re-arm. So does opening the app, and so does each alarm as it fires.
- **Drift.** Prayer times move a minute or two a day, so a repeating alarm
  would be out of step within a fortnight. The app arms the next ten one-shot
  alarms instead — about a day's worth — and refills them continuously.
- **Late delivery.** If the phone sat in Doze and the alarm arrives an hour
  late, nothing is shown. "Asr is about to become qaza" at nine in the evening
  is worse than silence.

Android 13 and newer will not show anything until you allow notifications; the
app asks on first launch. If alerts still arrive late, turn off battery
optimisation for the app — some Pakistani phone brands are aggressive about
killing background alarms, and no app can override that from the inside.

**Not included:** an azan *sound*. That needs audio assets and a foreground
service, and is a separate piece of work. Also absent: Hijri dates and GPS
auto-location.

---

## Getting updates to your family

They are not on the Play Store, so nothing tells their phones a new version
exists. There is no server behind this and no Firebase account — the app asks,
rather than being told.

Once a day, and on every launch, it fetches one small JSON file from a URL you
control and compares the version number in it with its own. If yours is
higher, **a notification appears on their phone**, and tapping it goes straight
to the APK download. They install it over the top; nothing is lost.

**What you do to ship an update.** Two steps.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, push, and
   let the Actions build produce the new APK. Attach it to a **GitHub Release**
   so it has a permanent direct link.
2. Edit `update.json` in the repo: set `latestVersionCode` to the same number
   and `downloadUrl` to the new release link. Put a line in `notes` saying
   what changed — it shows inside the notification.

Within a day every phone has been told. Nobody has to be asked to check.

```json
{
  "latestVersionCode": 2,
  "latestVersionName": "1.1",
  "minSupportedVersionCode": 0,
  "downloadUrl": "https://github.com/you/repo/releases/download/v1.1/app-release.apk",
  "notes": "Adds notifications and the calendar"
}
```

`UPDATE_URL` lives in `UpdateReceiver.kt` — point it at your own raw GitHub
URL before the first build, or nothing will ever be found.

Raise `minSupportedVersionCode` to the current number **only when the old build
is genuinely unusable**. That turns the friendly prompt into a dialog that
cannot be dismissed, which is a thing to use once, not routinely.

Three honest limits:

- **Google Drive links are a poor choice.** They are not direct downloads and
  usually show an interstitial or a virus-scan warning first. A GitHub Release
  asset is a real file at a real URL, and costs nothing.
- **It fails open, deliberately.** No connection, a bad URL, a deleted file —
  all of them leave the app fully usable. An offline prayer app that refused
  to start because a server was unreachable would be far worse than one that
  never checked.
- **A block is a strong nudge, not enforcement.** Someone can decline, stay
  offline, or keep the old APK. And nothing can run after an uninstall, so
  there is no way to reach someone who has removed the app.

---

## Layout

```
app/src/main/java/io/frontierlabs/namaz/
  core/PrayerTimes.kt   the astronomy and prayer windows — pure, no I/O
  core/Cities.kt        66 Pakistani cities with coordinates
  core/Qibla.kt         great-circle bearing and distance to the Kaaba
  core/Tracker.kt       prayer status, completion and streak rules
  core/Strings.kt       English and Urdu text, city, month and weekday names
  core/Alerts.kt        which notification fires when, and when to stay quiet
  core/UpdateCheck.kt   update JSON parsing and the block/prompt decision
  MainActivity.kt       Compose UI: today, monthly chart, calendar, settings
  Prefs.kt              the stored keys, shared by the UI and the receivers
  Notifications.kt      channels and the notifications themselves
  Scheduler.kt          turns Alerts decisions into AlarmManager alarms
  AlertReceiver.kt      an alarm fired: show it if it still matters, re-arm
  BootReceiver.kt       re-arm after reboot, clock change or reinstall
  UpdateReceiver.kt     the daily new-version check
tests/Test.kt           220 tests, runnable on a plain JVM
.github/workflows/      CI that builds the APK
```
