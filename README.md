<div align="center">

# Namaz Timings

**Prayer times for Pakistan — accurate, offline, and free.**

[![Download](https://img.shields.io/badge/Download-NamazTimings.apk-D9B45B?style=for-the-badge&logo=android&logoColor=white)](https://github.com/mansoorahmad-47/NamazTimings/releases/latest/download/NamazTimings.apk)

[![Latest release](https://img.shields.io/github/v/release/mansoorahmad-47/NamazTimings?label=latest%20version&color=4EC27F)](https://github.com/mansoorahmad-47/NamazTimings/releases/latest)
[![Build](https://github.com/mansoorahmad-47/NamazTimings/actions/workflows/build-apk.yml/badge.svg)](https://github.com/mansoorahmad-47/NamazTimings/actions)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)](#will-it-run-on-my-phone)

*66 Pakistani cities · English and Urdu · No ads · No account · No tracking*

</div>

<!--
  NOTE: the build badge above points at `.github/workflows/build-apk.yml`.
  If your workflow file has a different name (GitHub's "new workflow" button
  creates `blank.yml`), change the filename in that badge URL or it will just
  show "no status".

  Screenshots go here once you have them. Take 3-4 on a phone, drop them in a
  folder called `screenshots/` in this repo, and uncomment the block below.

  <p align="center">
    <img src="screenshots/today.png"  width="200">
    <img src="screenshots/month.png"  width="200">
    <img src="screenshots/qibla.png"  width="200">
  </p>
-->

---

## Install it

1. Tap the gold **Download** button above. The file `NamazTimings.apk` will
   download — that link always gives you the newest version.
2. Open the downloaded file. Android will ask whether to allow installing apps
   from your browser. Say yes; it only asks the first time.
3. Tap **Install**, then open the app.
4. It will ask where you are. Tap **Use my location** and it picks your city by
   itself, or tap **Choose my city** and find it in the list. It only asks once.

That's it. Everything else works without any setup.

> **Updating from an older version?** Just install the new file over the top —
> your streaks and marked prayers are kept. If Android says *"App not
> installed"*, you have a very early copy from before the app was properly
> signed; uninstall it first, then install this one. That only happens once.

---

## What it does

**Every prayer with a start *and* an end.** The end is the part most apps leave
out, and it is the one that matters: once that time passes, the prayer becomes
**qaza** and has to be made up.

| Prayer | Starts at | Window closes at |
| --- | --- | --- |
| Fajr | Subh Sadiq | Sunrise |
| Zuhr | After solar noon | Asr |
| Asr | Shadow length rule | Maghrib |
| Maghrib | Sunset | Isha |
| Isha | Twilight ends | One minute before the next Fajr |

Azan is called at the start time, so the "starts at" column is your azan
column. **Sehri and Iftar** are shown separately, with a small caution margin —
three minutes before Subh Sadiq for Sehri, two minutes after sunset for Iftar.

**The home screen leads with the prayer you are in**, when it becomes qaza, and
a ring that empties as the time runs out, turning red in the last half hour.
The next prayer sits below it. Between sunrise and Zuhr, when no fard prayer is
due, it says so rather than inventing one.

**Notifications.** One when each prayer's time begins, and a warning fifteen
minutes before a window closes — but only if you have not marked that prayer
yet. Mark Asr at four o'clock and the 5:55 warning never arrives. Both can be
switched off in Settings.

**Tracking and streaks.** Tap a prayer to mark it prayed; tap again to undo.
Unmarked prayers turn red once their time has passed. Your streak counts
consecutive days on which all five were prayed, and your longest streak is
shown beside it — with a star when the run you are on right now is the best you
have had.

**A calendar for past days.** Tap the streak card to see a month at a time,
each day coloured by how it went — green for all five, gold for a partial day —
so the gaps are visible as gaps. Tap any past day to mark prayers you have
since made up. A qaza prayer is still prayed, so your streak recovers once you
have offered it.

**The day turns at Fajr, not at midnight.** Isha runs until Subh Sadiq, so at
one in the morning the prayer you are offering still belongs to the previous
day — and so do the four you already prayed. The app stays on that day until
Fajr instead of wiping your progress at 12:00.

**Monthly chart.** Every day of any month in one scrollable table, today's row
highlighted. Pick any month, step through years, and tap a row for that day's
complete timetable: Sehri, Tahajjud, Fajr, sunrise, Ishraq, Chasht, Zuhr, Asr,
Iftar, Maghrib and Isha, each with its start and the time its window closes,
plus all three periods when prayer should be avoided.

**A home-screen widget.** Resize it to any shape. It shows the prayer you are
in, when it started, when it becomes qaza, and a countdown that ticks live
without you opening anything — plus today's five as dots, your streak, and a
button to mark the prayer without opening the app at all. Drag it small and it
keeps the countdown and drops the rest; drag it wide and everything shows.

To add it: long-press an empty part of your home screen, tap **Widgets**, find
**Namaz Timings**, and drag it where you want. Not every launcher looks the
same, but they all work this way.

**The Islamic calendar.** Today's Hijri date sits in the header, always
visible, and a Hijri tab lays the Islamic month out against the Gregorian one —
each square showing the Hijri day large and the Gregorian day small, so the two
calendars can be read together. Below it, the occasions coming up with how many
days away each one is: Ramadan, both Eids, Ashura, Shab-e-Barat, Shab-e-Qadr,
Shab-e-Miraj, Eid Milad-un-Nabi, the Day of Arafah and the Islamic New Year.

**This date is calculated, not sighted, and it will sometimes disagree with the
date announced in Pakistan** — usually by a day, occasionally by two. The
Ruet-e-Hilal Committee declares the month on the evening the crescent is
actually seen, and weather and geography move that around; no formula can
predict it. That is not a fault to be fixed, it is the difference between a
calculation and an observation. So there is an adjustment of up to two days
either way, sitting on the calendar screen rather than buried in Settings.
Follow the announcement and set it to match.

**Qibla compass.** The true great-circle bearing to the Kaaba from your city,
with a live needle. Magnetic declination is corrected for, so the needle points
at true Qibla rather than a couple of degrees off. The bearing and distance
still show on phones with no compass sensor.

**English and Urdu.** Switch in Settings. The whole interface flips to
right-to-left in Urdu, including prayer names, month names, all 66 city names
and the provinces.

**66 cities** — Peshawar, Rawalpindi, Islamabad, Mardan, Kohat, Mingora (Swat),
Lahore, Karachi, Quetta, Faisalabad, Multan, Gujranwala, Sialkot, Abbottabad,
Bannu, D.I. Khan, Chitral, Gilgit, Skardu, Muzaffarabad, Gwadar, Turbat and the
rest. Longitude is what moves the clock: one degree is four minutes, so
Peshawar's Maghrib is about eleven minutes after Lahore's. Picking a big city
instead of your own town is the main source of error in practice, which is why
the list is long rather than tidy.

---

## Is it accurate?

The times are not downloaded and not read from a stored table. The app works
out the sun's position for your date and your city's coordinates, which is why
it works with no internet at all, for any year, forwards or backwards.

**Two settings decide everything, and both default to the Pakistani ones.**

**Asr — Hanafi.** The shadow rule: Hanafi uses a shadow twice the object's
length, the other schools use once. The difference is 30 to 60 minutes and it
is the single most common error in prayer apps. Most of Pakistan is Hanafi.
Switchable in Settings.

**Fajr and Isha — University of Islamic Sciences, Karachi.** 18° below the
horizon for both, the standard across Pakistan. Muslim World League, ISNA,
Egyptian and Umm al-Qura are also available if your mosque follows one of
those.

The calculation is covered by 272 automatic tests that run on every build,
including the ones that actually catch mistakes: day length on the equinox must
be 12h 07m at every latitude, sunrise and sunset must be symmetric about solar
noon, prayer order must hold for all 66 cities across all 12 months, Isha must
never end after Fajr begins, and every single day from 1900 to 2100 must
convert to a Hijri date and back again exactly.

Sample output — Islamabad, 19 September 2026:

```
Sehri ends  4:27 AM
Fajr        4:30 AM   until 5:54 AM
Sunrise     5:54 AM
Zuhr       12:03 PM   until 4:25 PM
Asr         4:25 PM   until 6:10 PM
Maghrib     6:10 PM   until 7:33 PM
Iftar       6:12 PM
Isha        7:33 PM   until 4:30 AM
```

These match published timetables to within a couple of minutes.

**Follow your local masjid where it differs.** Mosque committees sometimes
round times or apply their own margin, particularly for Fajr and Isha, and in
Ramadan. Calculated times are a reference, not a ruling.

---

## What it asks for, and why

Four permissions, and the app is useful even if you refuse three of them.

| Permission | What it is for |
| --- | --- |
| Notifications | Prayer time alerts and qaza warnings. Refuse it and everything still works, silently. |
| Location *(coarse)* | Only to pick the nearest city from the list, and only if you tap "Use my location". Choose your city by hand instead and this is never asked. |
| Alarms & reminders | So a prayer alert arrives at the prayer time rather than twenty minutes later. |
| Internet | Only to check whether a newer version exists. Prayer times never need it. |

**There is no account, no analytics company, no advertising, and no server
holding your data.** Your city, your language, your streaks and every prayer
you have marked are stored on your phone and nowhere else. None of it is ever
sent anywhere, and there is no way for anyone — including whoever built this —
to read it.

Two small things do leave the phone, both once a day, and both are listed here
rather than buried:

- **A check for a newer version.** The app downloads one small file that says
  what the latest version number is. It sends nothing while doing so.
- **A count, if the person who built your copy has switched it on.** The app
  sends a random number it made up for itself on first run, plus the app's
  version — nothing else. No name, no location, no city, no prayer times, no
  device details. It exists so the developer knows roughly how many phones
  still use the app, and it can be turned off in Settings. If the copy you
  have was built without a counter configured, this never happens at all and
  the switch is not even shown.

---

## Counting how many phones use it

Off by default, and entirely optional. Nothing is sent until you set it up.

Android cannot tell you how many phones have an app installed, and no app can
detect its own uninstall — nothing runs after the app is removed. So the two
numbers you can honestly have are **how many phones checked in during the last
day**, which is the real "people are using it" figure, and **how many checked
in during the last 30 days**, which is the nearest thing to "still installed".

Setting it up takes about five minutes and costs nothing:

1. Make a new Google Sheet.
2. **Extensions → Apps Script**, delete what is there, paste in the contents
   of [`tools/stats.gs`](tools/stats.gs), and save.
3. **Deploy → New deployment → Web app**, with *Execute as: Me* and *Who has
   access: Anyone*. Google will ask you to authorise it once.
4. Copy the `https://script.google.com/macros/s/.../exec` address it gives
   you, and paste it into `Stats.kt` as the value of `URL`.
5. Build and send out the new version.

Each phone then sends one request a day. The sheet keeps one row per phone,
with the first and last time it was seen. The formulas in
[`tools/stats-formulas.txt`](tools/stats-formulas.txt) turn those rows into
the numbers.

What travels is a random number generated on the phone with no connection to
the hardware, the account or the SIM, plus the app's version. Nothing else.
Reinstalling makes a new random number, which is exactly the intent: there is
no way to tie it back to a person.

---

## Updates

You do not have to check for them. The app asks once a day whether a newer
version exists, and puts a notification on your phone when there is one.
Tapping it downloads the new version; install it over the top and your streaks
are kept.

---

## Will it run on my phone?

Any phone running **Android 8.0 or newer**, which is effectively every phone
still in use. It is around 6 MB, uses no battery in the background beyond
setting its alarms, and works with no internet connection.

**If notifications arrive late**, turn off battery optimisation for Namaz
Timings in Android's settings. Some phone brands are aggressive about stopping
background alarms, and no app can get around that from the inside.

---

## Questions and problems

Something wrong with a time? A city missing? Open an
[issue](https://github.com/mansoorahmad-47/NamazTimings/issues) and say what
you expected and what you saw.

---

<div align="center">

**Developed by Mansoor Ahmad**

Free to use and share, under the [MIT licence](LICENSE).
Times are calculated, not fetched.

</div>

---

<details>
<summary><b>For developers</b> — building it yourself</summary>

<br>

Kotlin and Jetpack Compose, `minSdk 26`, `targetSdk 36`. No Play Services, no
analytics SDK, no third-party dependency beyond AndroidX and Compose.

All the logic lives in `core/` as pure Kotlin with zero Android imports, so it
can be compiled and tested on a plain JVM with no Android SDK present:

```bash
kotlinc app/src/main/java/io/frontierlabs/namaz/core/*.kt tests/Test.kt \
  -include-runtime -d /tmp/t.jar && java -jar /tmp/t.jar
```

Open the folder in Android Studio and **Build → Build APK(s)**, or push and let
the GitHub Actions workflow build it. The workflow signs the APK with a key
restored from the `KEYSTORE_BASE64` repository secret and fails loudly if that
secret is missing — a build signed with a different key cannot install over an
existing copy.

```
app/src/main/java/io/frontierlabs/namaz/
  core/PrayerTimes.kt   the astronomy and prayer windows — pure, no I/O
  core/Cities.kt        66 cities, and nearest-city lookup from a GPS fix
  core/Qibla.kt         great-circle bearing and distance to the Kaaba
  core/Tracker.kt       prayer day, status, completion and streak rules
  core/Strings.kt       English and Urdu text, city, month and weekday names
  core/Alerts.kt        which notification fires when, and when to stay quiet
  core/Hijri.kt         Islamic calendar conversion, month shape and occasions
  core/Ping.kt          the daily check-in URL, and when one is due
  core/UpdateCheck.kt   update JSON parsing and the prompt decision
  MainActivity.kt       Compose UI: today, monthly chart, calendar, settings
  Prefs.kt              the stored keys, shared by the UI and the receivers
  LocationFinder.kt     coarse location to nearest city, with a timeout
  Stats.kt              sends the check-in, if one is configured at all
  NamazWidget.kt        the home-screen widget, and how it stays current
  Notifications.kt      channels and the notifications themselves
  Scheduler.kt          turns Alerts decisions into AlarmManager alarms
  AlertReceiver.kt      an alarm fired: show it if it still matters, re-arm
  BootReceiver.kt       re-arm after reboot, clock change or reinstall
  UpdateReceiver.kt     the daily new-version check
tests/Test.kt           272 tests, runnable on a plain JVM
```

**Releasing a new version.** Bump `versionCode` and `versionName` in
`app/build.gradle.kts`, push, attach the built `NamazTimings.apk` to a GitHub
Release, then update `latestVersionCode`, `latestVersionName` and the tag in
`downloadUrl` inside `update.json`. Every phone is notified within a day.

</details>
