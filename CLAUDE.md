# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

selfie-me is a single-module Android application that periodically nags the user (via a repeating notification) to take a front-camera selfie, then displays all captured selfies in a grid. It was originally written as a project for the Android Programming Coursera course and has since been migrated to a modern Android toolchain (see Toolchain below).

## Toolchain & versions

- **Android Gradle Plugin 8.7.3**, **Gradle 8.9** (see `gradle/wrapper/gradle-wrapper.properties`)
- **compileSdk 35**, **minSdk 26**, **targetSdk 35**; Java 17 source/target (`compileOptions`)
- **AndroidX** (`androidx.appcompat`, `androidx.core`) plus **Material Components** (`com.google.android.material`) — *not* the old `com.android.support` libraries
- Dependencies resolve from **google()** + **mavenCentral()**, configured centrally in `settings.gradle` via `pluginManagement` and `dependencyResolutionManagement` (the old per-module `repositories {}` / jcenter setup is gone)
- Uses modern Gradle configurations: `implementation` / `testImplementation` (not `compile` / `testCompile`)
- The module is configured with `namespace 'com.benzinger.selfieme'` in `app/build.gradle`; the `package` attribute is intentionally absent from `AndroidManifest.xml` (required by AGP 8)
- AndroidX is enabled via `android.useAndroidX=true` in `gradle.properties`

Building requires the Android SDK (compileSdk 35 / latest build-tools) and network access to **Google's Maven repo** (`dl.google.com`); AGP and all AndroidX/Material artifacts are published only there. JDK 17+ is required for AGP 8.

## Build & test commands

```bash
./gradlew assembleDebug      # build debug APK
./gradlew assembleRelease    # build release APK (proguard configured but minifyEnabled=false)
./gradlew installDebug       # install debug build on a connected device/emulator
./gradlew test               # run JVM unit tests
./gradlew clean
```

There are currently **no test sources** in the repo (only the `junit:junit:4.13.2` dependency is declared), so `test` has nothing to run. If adding tests, create `app/src/test/java/...` for JVM unit tests.

## Architecture

The entire app lives under `app/src/main/java/com/benzinger/selfieme/` and is built from four classes plus standard Android resources. The flow centers on capturing images to app-private external storage and reading them back into a grid.

- **`SelfieMainActivity`** — the launcher activity and the hub of the app. On create it:
  1. Calls `loadPics()` to scan `getExternalFilesDir(null)` and build a `List<Uri>` of existing selfie files (as `file://` URIs).
  2. Wires up a `GridView` backed by `ImageAdapter`, with a tap opening `FullScreenPicActivity` and a long-press context menu for delete.
  3. Requests the `POST_NOTIFICATIONS` runtime permission (API 33+) and calls `createAlarm()` to register a repeating `AlarmManager` broadcast.
  - Camera capture uses the `MediaStore.ACTION_IMAGE_CAPTURE` intent pattern: `createImageFile()` pre-creates a temp `.jpg` (named `selfie_<timestamp>_`) in the external files dir and stores it in the `currentFile` field. The file is shared with the camera app as a **FileProvider `content://` URI** (`MediaStore.EXTRA_OUTPUT` + `FLAG_GRANT_WRITE_URI_PERMISSION`) — handing a raw `file://` URI to another app throws `FileUriExposedException` on modern Android. `onActivityResult` either keeps the file and adds `Uri.fromFile(currentFile)` to the list (success) or deletes the pre-created file (cancel). This pre-create-then-delete pattern is intentional — don't "simplify" it away. Note the split: the camera intent uses a `content://` URI, but the list/display path uses `file://` URIs throughout (see contracts below).

- **`receivers/AlarmReceiver`** — a `BroadcastReceiver` (registered in `AndroidManifest.xml`, `exported="false"`) that posts the "Time For A New Selfie!" notification, whose content intent reopens `SelfieMainActivity`. It creates the notification channel (`selfie_reminders`, mandatory at minSdk 26) before posting.

- **`adapters/ImageAdapter`** — a `BaseAdapter` over the `List<Uri>`. It builds/recycles `ImageView`s for the grid (fixed 100x100, center-crop) via `setImageURI`. The activity mutates the *same* list instance and calls `notifyDataSetChanged()`; the adapter holds the list by reference, so add/remove happens in the activity, not the adapter.

- **`FullScreenPicActivity`** — displays one selfie full-screen. It receives the file path via the `SelfieMainActivity.FILENAME_EXTRA` intent extra and loads it with `Drawable.createFromPath`.

### Key contracts to preserve

- The list of selfies and the display path use **`file://` URIs** (`Uri.fromFile`): `loadPics()` produces them, `ImageAdapter.setImageURI` renders them, `FullScreenPicActivity` reads `Uri.getPath()`, and `deleteImageFile` matches on the `"file"` scheme. The camera's `content://` URI (via FileProvider) is used *only* transiently for `EXTRA_OUTPUT`. Keep these consistent if you touch the capture or display flow.
- The FileProvider authority is `"${applicationId}.fileprovider"`, declared in `AndroidManifest.xml` and constructed as `getPackageName() + ".fileprovider"` in `SelfieMainActivity.openCamera()` — keep them in sync. Its exposed paths are in `res/xml/file_paths.xml` (`<external-files-path>`, which maps to `getExternalFilesDir(null)`).
- The intent extra key is the constant `SelfieMainActivity.FILENAME_EXTRA`; both activities reference it.
- The alarm uses an **explicit** `Intent(this, AlarmReceiver.class)` (implicit broadcasts to manifest receivers are restricted on modern Android), and every `PendingIntent` is created with `FLAG_IMMUTABLE` (required from API 31+). Preserve both.
- Photos are stored in app-private external storage (`getExternalFilesDir(null)`); no storage permission is needed (the old `WRITE_EXTERNAL_STORAGE` declaration was removed when minSdk moved past 18). The front camera is declared **required** via `<uses-feature>`.

### Known discrepancy

The README says the reminder fires "every minute," but the alarm interval constant is `TWO_MINS = 60*1000*2` (2 minutes). If touching the reminder cadence, reconcile the README, the constant, and its misleading name.
