# Skriti

A personal, offline Android habit tracker. Each goal has its own time window and
its own failure rules, and the day screen shows what is due now, what can be done
any time today and what was missed.

What works today:

- Create, edit and delete goals. A goal is something to do or something to avoid,
  due every day or on chosen weekdays, all day or inside a time window.
- Failure rules, in any combination: the window ends without a tick (after a grace
  period of 0, 5, 10 or 15 minutes), the day ends without a tick, or an "I failed"
  button. Ticking during the grace time still counts and is noted as late.
- Amount goals with a target and a minimum, for example 120 minutes with a minimum of
  20. Reaching the minimum still counts as kept. Quick-log chips, a custom amount, undo
  and, for goals counted in minutes, a start and stop timer. A yes-or-no goal can also
  name a smaller minimum version.
- The day screen: a 24-hour dial with each goal's window, goals grouped by
  deadline, a week strip and a month calendar. Each goal shows its consistency over
  the last 30 days and its streaks.
- Tick a goal, or mark it as missed. Past days can be edited; future days are
  view only.
- Settings: the day-end time, how much a minimum counts towards the day's percentage,
  and backups.

Not built yet: reminders and alarms, and app blocking. The rules run while the app is
open; a miss that happened while it was closed is recorded the next time it opens.

Run the unit tests with `./gradlew testDebugUnitTest`.

## Your data and backups

All data stays on the phone. Uninstalling the app deletes it, so save a backup first
if you want to keep your history:

- Settings, "Export all data" saves a JSON file with everything. "Export CSV" saves one
  row per recorded day for use in a spreadsheet.
- Settings, "Restore backup" reads a JSON file. Choose Merge to keep what is on the
  phone and add the rest (for a day recorded on both sides the later edit wins), or
  Replace to start from the backup.
- Before the app upgrades its database, it copies the old database file next to it.

## Build

Requirements: JDK 17 and the Android SDK (platform 37, build-tools 37).

1. Set `JAVA_HOME` to the JDK.
2. Create `local.properties` in the project root with the SDK path, for example
   `sdk.dir=D\:/dev/android-sdk`.
3. Run `./gradlew assembleDebug` (`gradlew.bat assembleDebug` on Windows).

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Every push also builds the APK with GitHub Actions; download it from the run's
`skriti-debug-apk` artifact.

## Install

Copy the APK to the phone and open it, or run `adb install -r app-debug.apk` with
USB debugging on. Android asks once to allow installs from that source.

## Permissions

None yet. The app has no internet permission and never will.

## Known limits

- Android 10 or newer is required.
- The splash animation plays on Android 12 and newer; older versions show the
  still logo.
- A time window must start and end on the same day, so it cannot cross midnight.
- Goals are listed in the order they were created; they cannot be reordered yet.
- The goal form offers every day or chosen weekdays. Goals that are due a number of
  times per week or month, every few days, or on a monthly pattern are understood by
  the rules but have no screens yet.
- Changing a goal's schedule or rules changes how it is judged from then on; days that
  were already closed are not judged again.
- Debug builds have a "Time shift" control in Settings that moves the app's clock for
  testing; it does nothing in a release build.
