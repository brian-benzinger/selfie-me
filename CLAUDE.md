# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

selfie-me is a single-module Android application that periodically nags the user (via a repeating notification) to take a front-camera selfie, then displays all captured selfies in a grid. It was originally written as a project for the Android Programming Coursera course and uses an older Android toolchain (see Toolchain below).

## Toolchain & versions

This project is pinned to a legacy stack — do **not** assume modern Gradle/Android conventions:

- **Android Gradle Plugin 1.3.0**, **Gradle 2.8** (see `gradle/wrapper/gradle-wrapper.properties`)
- **compileSdkVersion 23**, **buildToolsVersion 23.0.1**, **minSdkVersion 18**, **targetSdkVersion 23**
- Dependencies resolved from **jcenter** (now defunct — builds may fail to resolve dependencies without a mirror/cached artifacts)
- Uses the old `com.android.support` support libraries (appcompat-v7, design, support-v4), not AndroidX
- Gradle config uses the deprecated `compile`/`testCompile` configurations, not `implementation`/`testImplementation`

Because of the jcenter dependency and the old AGP, this project generally requires Android Studio with matching SDK components to build reliably.

## Build & test commands

```bash
./gradlew assembleDebug      # build debug APK
./gradlew assembleRelease    # build release APK (proguard configured but minifyEnabled=false)
./gradlew installDebug       # install debug build on a connected device/emulator
./gradlew test               # run JVM unit tests
./gradlew clean
```

There are currently **no test sources** in the repo (only the `junit:junit:4.12` dependency is declared), so `test` has nothing to run. If adding tests, create `app/src/test/java/...` for JVM unit tests.

## Architecture

The entire app lives under `app/src/main/java/com/benzinger/selfieme/` and is built from four classes plus standard Android resources. The flow centers on capturing images to app-private external storage and reading them back into a grid.

- **`SelfieMainActivity`** — the launcher activity and the hub of the app. On create it:
  1. Calls `loadPics()` to scan `getExternalFilesDir(null)` and build a `List<Uri>` of existing selfie files.
  2. Wires up a `GridView` backed by `ImageAdapter`, with a tap opening `FullScreenPicActivity` and a long-press context menu for delete.
  3. Calls `createAlarm()` to register a repeating `AlarmManager` broadcast.
  - Camera capture uses the standard `MediaStore.ACTION_IMAGE_CAPTURE` intent pattern: `createImageFile()` pre-creates a temp `.jpg` (named `selfie_<timestamp>_`) in external files dir, passes its `Uri` as `EXTRA_OUTPUT`, and `onActivityResult` either keeps the file (success) or deletes the pre-created file (cancel). This pre-create-then-delete pattern is intentional — don't "simplify" it away.

- **`receivers/AlarmReceiver`** — a `BroadcastReceiver` (registered in `AndroidManifest.xml`) that fires on the custom action and posts the "Time For A New Selfie!" notification, whose content intent reopens `SelfieMainActivity`.

- **`adapters/ImageAdapter`** — a `BaseAdapter` over the `List<Uri>`. It builds/recycles `ImageView`s for the grid (fixed 100x100, center-crop). The activity mutates the *same* list instance and calls `notifyDataSetChanged()`; the adapter holds the list by reference, so add/remove happens in the activity, not the adapter.

- **`FullScreenPicActivity`** — displays one selfie full-screen. It receives the file path via the `SelfieMainActivity.FILENAME_EXTRA` intent extra and loads it with `Drawable.createFromPath`.

### Key contracts to preserve

- The custom broadcast action string `com.benzinger.selfieme.receivers.NOTIFICATION_ALARM` is duplicated as `SelfieMainActivity.ACTION` and in the manifest `<receiver>` intent-filter — keep them in sync.
- The intent extra key is the constant `SelfieMainActivity.FILENAME_EXTRA`; both activities reference it.
- Photos are stored in app-private external storage (`getExternalFilesDir(null)`), which is why the `WRITE_EXTERNAL_STORAGE` permission is capped at `maxSdkVersion="18"`. The front camera is declared **required** via `<uses-feature>`.

### Known discrepancy

The README says the reminder fires "every minute," but the alarm interval constant is `TWO_MINS = 60*1000*2` (2 minutes). If touching the reminder cadence, reconcile the README, the constant, and its misleading name.
