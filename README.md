# Daily Goals

A personal, offline Android habit tracker. Each goal has its own time window and
its own failure rules, and the day screen shows what is due now, what can be done
any time today and what was missed.

The app is at an early stage: it currently builds and shows the splash screen and
an empty home screen.

## Build

Requirements: JDK 17 and the Android SDK (platform 37, build-tools 37).

1. Set `JAVA_HOME` to the JDK.
2. Create `local.properties` in the project root with the SDK path, for example
   `sdk.dir=D\:/dev/android-sdk`.
3. Run `./gradlew assembleDebug` (`gradlew.bat assembleDebug` on Windows).

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Every push also builds the APK with GitHub Actions; download it from the run's
`daily-goals-debug-apk` artifact.

## Install

Copy the APK to the phone and open it, or run `adb install -r app-debug.apk` with
USB debugging on. Android asks once to allow installs from that source.

## Permissions

None yet. The app has no internet permission and never will.

## Known limits

- Android 10 or newer is required.
- The splash animation plays on Android 12 and newer; older versions show the
  still logo.
