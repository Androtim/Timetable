# Timetable — offline school timetable + widget

Native Android app (Kotlin, Jetpack Compose) for any school publishing an iCal/ADE
feed: paste your .ics link at first launch, the app caches the whole year in a local
Room database for 100% offline access, auto-detects the group tokens in the feed so
you can filter to your groups, and shows your schedule in a size-responsive
home-screen widget (day layout when small, full week grid when large) with in-widget
navigation. Includes per-event and per-day notes, three course-color modes
(auto/single/manual), and English/French/Spanish localization.

Originally built against the AMU ADE feed — the format notes below describe that
feed and remain the reference test fixture.

## Architecture

| Layer | Implementation |
|---|---|
| iCal parsing | `data/ics/IcsParser.kt` — custom parser for ADE VEVENTs (line unfolding, text unescaping, UTC→Europe/Paris) |
| Cache | Room (`data/db/`) — the whole academic year is stored locally; sync atomically replaces the table |
| Networking | OkHttp direct `.ics` download (`data/ScheduleRepository.kt`) |
| Background sync | WorkManager (`sync/SyncWorker.kt`) — every 3 h with network constraint, plus "Refresh Now" |
| Settings | SharedPreferences (`data/Settings.kt`) — synchronous so the widget can read them |
| App UI | Compose Material 3 — day pager + week view, `Modifier.basicMarquee()` on long labels |
| Widget | Classic RemoteViews (`widget/TimetableWidgetProvider.kt`) — chosen over Glance because Glance has no marquee-capable text view |

## Feed format (observed 2026-08-26)

```
SUMMARY:R3.04 Qualité de développement TP GA2-1
LOCATION:TP I-104
DESCRIPTION:\n\nGA2-1\nDE SOLMINIHAC Pierre Alexis\n\n(Modifié le:…)
DTSTART:20261119T123000Z        ← always UTC date-time
```

- `SUMMARY` = `<code> <course name> <type> [<group>]`; exams carry the word `Examen`.
- `DESCRIPTION` lines = group tokens first, then teacher names, then a `(Modifié le:…)` trailer.
  Group tokens: half-groups `GA1-1…GB-2`, classes `Groupe A1/A2/B an2`, promo-wide `2ème année`.
  Any line that is not a known group token is treated as a teacher name.
- Group matching: a class token matches both of its half-groups; promo tokens match everyone;
  events with no recognized group token (e.g. `Ferié`, `Conseil de département`) are shown to all groups.

## Widget behaviour

- Header: `‹  Wed, Aug 26  ›` — arrows move ±1 day without opening the app;
  tapping the date jumps back to today. Per-widget date is kept in SharedPreferences.
- Body: all of the day's classes side by side (up to 8), never scrollable.
- Cards: time range, `code - name` (marquee), TP/TD/CM badge, room, teacher (marquee).
- Exams: badge hidden, card background switches to prominent red.
- The widget group is locked via *Settings → Group to show in widget* and ignores the in-app filter.

> **Platform caveat — marquee in widgets:** the card TextViews use
> `android:ellipsize="marquee"` + `marqueeRepeatLimit="marquee_forever"` +
> `singleLine="true"` as specified. Android only animates a marquee on a *selected*
> TextView, and RemoteViews provides no API to set the selected state, so on most
> launchers (including One UI) the text may sit at its start position instead of
> auto-scrolling. Text is still never ellipsized. In the app itself
> `Modifier.basicMarquee()` scrolls unconditionally.

## Server behaviour (learned the hard way)

- The ADE endpoint can take **minutes** to generate the year-long export under load,
  and its gateway returns **504 after exactly 5 minutes** — hence the 6-minute read
  timeout on the OkHttp client.
- It also **rate-limits per IP** (Tomcat 408 "Too much requests") when polled
  repeatedly — hence the 5-minute exponential backoff on the sync work requests.
  Don't test against the live server in a loop.
- Android's ICU regex engine rejects the `(?U)` inline flag that the desktop JVM
  accepts (class-load `Error`, invisible to `catch (Exception)`). Parser regexes
  must stay portable: explicit accent classes (`[ée]`, `[ôo]`), `\p{L}` boundaries,
  `\b` only next to ASCII letters. The sync worker catches `Throwable` for this reason.

## Building

```
gradlew.bat assembleDebug        # APK at app/build/outputs/apk/debug/
gradlew.bat testDebugUnitTest    # parser tests run against the real feed sample
```

Requirements: JDK 17+ and Android SDK platform 34 (`local.properties` points to the SDK).
The parser unit tests use `app/src/test/resources/amu_feed_sample.ics`, a snapshot of the
real feed taken on 2026-08-26 (443 events).

## License

GPLv3 — see [LICENSE](LICENSE).
