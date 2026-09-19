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
| Isha | Twilight ends | Subh Sadiq (next Fajr) |

Azan is called at the start time, so the "start" column is your azan column.

**Sehri and Iftar** are shown on their own, with a small caution margin — three
minutes before Subh Sadiq for Sehri, two minutes after sunset for Iftar. Set
both to zero in the code if you'd rather have the exact astronomical moment.

**Also shown:** sunrise, the last third of the night for Tahajjud, and the three
times when prayer should be avoided (just after sunrise, around midday, just
before sunset).

**Monthly chart** — every day of the current month in one scrollable table,
today's row highlighted.

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

The astronomy is verified against objective checks, not eyeballed. 58 tests
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

## What isn't in it yet

**Azan notifications.** Alerting at prayer time needs exact alarms, a
foreground service, battery-optimisation exemptions and audio assets — a
meaningful piece of work on top of this. Say the word and I'll add it.

Also absent: Qibla compass (needs the magnetometer), Hijri dates, and GPS
auto-location. All doable; none included yet.

---

## Layout

```
app/src/main/java/io/frontierlabs/namaz/
  core/PrayerTimes.kt   the astronomy and prayer windows — pure, no I/O
  core/Cities.kt        66 Pakistani cities with coordinates
  MainActivity.kt       Compose UI: today, monthly chart, city picker, settings
tests/Test.kt           58 tests, runnable on a plain JVM
.github/workflows/      CI that builds the APK
```
