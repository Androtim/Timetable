# Release status

Snapshot of where distribution stands, so work can resume without
re-deriving it. Update this when the state below changes.

_Last updated: 28 August 2026 — Timetile 1.1.3 (versionCode 5)_

## Identity

| | |
|---|---|
| App name | **Timetile** (renamed from "Timetable" on 2026-08-27) |
| applicationId | `com.androtim.timetile` — **permanent**, registered with Play |
| Kotlin namespace | `com.androtim.timetable` — unchanged on purpose; Play never sees it, and renaming it would touch every file for nothing |
| Repository | `github.com/Androtim/Timetable` — still the old name; the privacy-policy and source URLs point here |
| Current version | 1.1.3 / versionCode 5, tag `v1.1.3` |

The name is deliberately **not** localised. A brand should read the same in
every language; the localised store descriptions carry the search keywords
instead.

## Toolchain

AGP 8.13.2, Gradle 8.14.3, Kotlin 2.0.21, compileSdk/targetSdk 36, minSdk 26.

Play requires new apps to target API 36 from 2026-08-31, which is why the
toolchain moved. Kotlin and androidx were deliberately left alone: they were
not required for the bump, and holding them back kept the diff to four lines.

Unit tests run with the test JVM pinned to UTC (`app/build.gradle.kts`), because
`DISPLAY_ZONE` follows the device and a zone-sensitive assertion once passed in
Paris and failed on CI.

## Google Play

Personal developer account, verified 2026-08-27.

**Done:** app created · package name registered · Play App Signing enrolled ·
store settings (category Education, tags Calendar/Events/Productivity) ·
sign-in details (no restricted parts) · content rating · advertising ID (no) ·
health apps (none) · target audience 13+ · ads declaration (none) ·
data safety (no data collected, no data shared) · privacy policy URL ·
default store listing with all graphics · closed testing release `5 (1.1.3)`.

**The gate:** a personal account needs **12 testers opted in to a closed test
for 14 continuous days** before production access can even be requested.
Internal testing does not count. The opt-in link only exists once the release
is live, and Console counts testers who *accepted* the link — being on the
email list does nothing. Recruit ~15 so one drop-out does not restart it.

**Next:** submit for review → release goes live → share the opt-in link →
reach 12 accepted → wait 14 days → apply for production.

## F-Droid

Not submitted. Independent of Play and can run in parallel; review takes weeks.

`docs/fdroid-metadata-draft.yml` is ready and targets tag `v1.1.3`. To file it:
fork `gitlab.com/fdroid/fdroiddata`, copy that file to
**`metadata/com.androtim.timetile.yml`** (F-Droid names metadata by
applicationId), strip the leading comment block, open a merge request.

Descriptions and screenshots are picked up automatically from
`fastlane/metadata/android/`, which is why that directory is in the repo. The
release build is unsigned without `keystore.properties`, which is what F-Droid
requires — they sign with their own key, so the F-Droid and Play builds are
**not** interchangeable for users.

Risk: the app needs compileSdk 36, which is recent. If F-Droid's build server
lacks that platform the build fails on their side. The fallback is holding
F-Droid on a compileSdk-35 branch — F-Droid has no target-API deadline of its
own; Play is the only reason we went to 36.

## Signing

Release keystore at `C:\Users\Androtim\keystores\timetable-release.jks`,
password in the gitignored `keystore.properties`. 4096-bit RSA, SHA-256
`E8:36:9E:DF:8A:C3:AB:25:C6:0D:58:AE:3F:AE:CD:0A:09:EB:22:8B:BD:7F:87:6C:57:03:C5:FF:CE:B0:E8:AC`,
valid to 2054. Keystores never rotate on their own.

Since Play App Signing is enrolled, this is now the **upload** key — Google
holds the app signing key and can reset a lost upload key. Console therefore
shows a different fingerprint; that is expected, not a rotation.

**Still back up the `.jks` file itself, not just the password.** The password
alone is worthless.

## Store assets

All under `fastlane/metadata/android/<locale>/`:

- `title.txt`, `short_description.txt` (≤80), `full_description.txt` (≤4000)
- `changelogs/<versionCode>.txt`
- `en-US/images/icon.png` 512×512, `featureGraphic.png` 1024×500,
  `phoneScreenshots/1..5.png` 1080×2340

Locales: `en-US`, `fr-FR`, `es-ES`. Only en-US carries images; Play and F-Droid
fall back to the default language for graphics, so they are uploaded once.

Play wants **at least 4 screenshots at 1080px+** to be eligible for promotion.

Both graphics are generated from the app's own icon vector and palette, so they
stay in step with the launcher icon. **If the app is ever renamed again, the
feature graphic must be regenerated** — it has the name baked in, and that was
missed once already.

## Privacy posture

Deliberate and verified on-device; do not "simplify" it.

Cloud backup is refused at every API level (`fullBackupContent` for API ≤30,
`dataExtractionRules` `<cloud-backup>` for 31+). Device-to-device transfer is
deliberately **allowed**, so a student changing phone keeps their notes without
anything reaching a server. Verified with `adb shell bmgr`: the cloud transport
measured every directory at 0 bytes; the D2D transport moved 443 KB
successfully.

Setting `allowBackup="false"` outright would silently cost students their notes.

The app collects nothing: only `INTERNET` and `ACCESS_NETWORK_STATE` are
declared by us. `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`
are merged in by WorkManager, and **no foreground service is ever started** —
nothing calls `setExpedited` or `setForeground`. No ads, analytics or tracking
SDKs; no `AD_ID` in the merged manifest; the only compiled-in URLs are this repo
and the demo feed.

## Calendar feeds and authentication

The app sends a bare `GET` with no headers, cookies or credentials. That covers
more schools than it sounds:

- **Anonymous URLs** — AMU and many ADE installs. Verified: the endpoint is
  literally `anonymous_cal.jsp` and returns ICS to an unauthenticated request.
- **Secret token in the URL** — Canvas, Moodle, Outlook "publish calendar",
  TimeEdit, Untis. **These already work**; the token *is* the credential.

Unsupported, in rising order of pain: HTTP Basic (~30 lines, but storing a
school password weakens the "no credentials" claim), session SSO
(CAS/Shibboleth — fragile, needs a WebView login), OAuth (Google/Microsoft
Graph — big lift, dents the "no account" positioning). Do none of it until a
real user reports a school that cannot be reached.

## Demo feed

`demo/demo.ics` — entirely fictional, one recurring `VEVENT` per weekly class
across 2026-27 (~6 KB, expands to 476 events, 2026-09-07 → 2027-06-18). Term
breaks are `EXDATE`d out, holidays are all-day entries, each semester ends with
exams. `DemoFeedTest` asserts it stays that way.

Served at
`https://raw.githubusercontent.com/Androtim/Timetable/main/demo/demo.ics`,
offered by a button on the setup screen and documented in the README.

**Never put the user's real timetable in screenshots or public artifacts.** Use
this feed. On a release build `run-as` is unavailable, so configuration must be
driven through the UI rather than by writing shared_prefs.

## Known gaps

- Ordinal `BYDAY` ("2MO" = second Monday) is read as a plain weekday;
  `BYSETPOS`/`BYWEEKNO` unimplemented. Rare in class timetables.
- `EXAM_REGEX` is French-heavy; a Dutch or Polish feed gets no red exam
  highlighting even though its session types are recognised.
- Session type is read from `SUMMARY`, falling back to `CATEGORIES`. Schools
  that put it elsewhere get no badge — which is the safe failure.
- `isMinifyEnabled = false`: no R8, so the bundle is 10.9 MB where roughly half
  is realistic. Enabling it needs the release build exercised on-device
  (especially the widget, since `RemoteViews` reflection is where R8 bites) and
  the mapping file uploaded to Play.
- Week-grid visuals: blocks are fully saturated with white text and compete for
  attention; the block is coloured by course while the room pill inside it is
  coloured by session type, so two colour systems fight; there is no
  current-time line; long titles wrap mid-word; the last hour label overflows
  its box.
