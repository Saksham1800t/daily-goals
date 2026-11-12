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

## Warnings

Each goal can warn once or repeatedly (every 5, 10, 15 or 30 minutes). Warnings start
when the goal's window opens, or at a chosen check time for a goal with no window. They
stop when the goal is ticked or fails, or when the day ends. The wording gets firmer
("Still unticked", then "Grace ends in 6 min" or "You are 30 minutes late"). A warning
has a Done button, and an "I failed" button for goals that use that rule.

Only the next event is scheduled, with one alarm, and every warning is shown once. The
app wakes for each warning, for each failure deadline and at midnight, and it rebuilds
its schedule from the database after a reboot, an app update, a clock change or a time
zone change. Whatever the app missed is repaired the next time it runs.

A warning uses the highest level Android currently allows: a strong alert over the lock
screen (only for goals with that switch on), a pop-up with sound, a quiet notification,
or, with notifications off, only the view inside the app. Settings, "Warnings and
permissions", shows which level is available and links to the right settings pages. The
app opens this screen once on first launch and works without any of the permissions.

## Android 17 notes

Google's Android 17 behaviour-change pages list no change to exact alarms, notification
permission, notification actions, full-screen intents, boot receivers or time-change
broadcasts. Two changes touch this kind of app, and the design avoids both: audio started
from the background is restricted, so warnings use the system notification sound and the
app plays no sound itself; and background activity launches are tightened, so the Done
and I failed buttons are broadcasts rather than activity launches, and the app opens only
when you tap a notification. The app uses no custom notification layouts, which is the
other area Android 17 limits.

## Permissions

| Permission | Used for |
|---|---|
| Notifications | Showing warnings (asked at runtime on Android 13 and newer) |
| Exact alarms | Warnings at the exact minute; without it they can be a few minutes late |
| Receive boot completed | Rebuilding the alarm after a reboot |
| Full-screen intent | The strong alert over the lock screen; Android may keep it off |

The app has no internet permission and never will.

## Known limits

- Android 10 or newer is required.
- The splash animation plays on Android 12 and newer; older versions show the
  still logo.
- If you force-stop the app from Android settings, Android cancels its alarms and sends
  it nothing until you open it again. Alarms survive normal process ends and reboots,
  and everything is rebuilt when you reopen the app, but nothing can run while it is
  stopped. This is an Android rule.
- Some phone brands (Xiaomi, Oppo, Vivo, Samsung and others) stop background apps.
  Exempt the app from battery optimisation and check any autostart list; the permissions
  screen has a shortcut.
- A strong alert over the lock screen needs Android to allow full-screen alerts for the
  app, which it may not; the warning then arrives as a pop-up with sound.
- A time window must start and end on the same day, so it cannot cross midnight.
- Goals are listed in the order they were created; they cannot be reordered yet.
- The goal form offers every day or chosen weekdays. Goals that are due a number of
  times per week or month, every few days, or on a monthly pattern are understood by
  the rules but have no screens yet.
- Changing a goal's schedule or rules changes how it is judged from then on; days that
  were already closed are not judged again.
- Debug builds have a "Time shift" control in Settings that moves the app's clock for
  testing; it does nothing in a release build.
