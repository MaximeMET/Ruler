# Ruler

[中文](README.md) | **English**

[![Release](https://img.shields.io/github/v/release/MaximeMET/Ruler?label=release)](https://github.com/MaximeMET/Ruler/releases/latest)
[![License](https://img.shields.io/github/license/MaximeMET/Ruler?label=license)](LICENSE)
[![APK size](https://img.shields.io/badge/APK-150%20KB-4f46e5)](https://github.com/MaximeMET/Ruler/releases/latest)

An open source Android app that turns your phone screen into a ruler and a protractor.
No ads, no analytics, and it never goes online on its own — just a quiet measuring tool.

**Download**: the APK from the [latest release](https://github.com/MaximeMET/Ruler/releases/latest),
about 150 KB, Android 6.0 (API 23) and up. Once installed, it can check for updates in the app.

## Why this exists

The ruler apps on the app stores are capable enough, but they open with an ad, want a network
connection before they will measure a line, and throw a rating dialog at you on the way out —
the tool itself ends up buried underneath. "Turn the screen into a ruler" is a simple enough
idea that none of that belongs in it.

So this project is a from-scratch, feature-for-feature rewrite built on one rule:
**anything that can work offline stays offline**. Every dependency that could go, went
(no ad SDKs, no analytics, no third-party libraries), no feature was cut, and the APK ended up
at 150 KB — about what a screen ruler should weigh.

## Features

| Feature | What it does |
| --- | --- |
| Full-screen ruler | Scale along the long edge of the screen; centimetres, millimetres or inches, one tick per mm or 1/8 inch, labels always upright |
| Portrait and landscape | Portrait by default; a toolbar button flips it and the choice is remembered; settings also offer the reverse of each orientation |
| Two-point ruler | Two draggable lines, each with its own padlock; tap the readout to type a length and pin both ends at once, turning the band into a ruler of an exact length |
| Rectangle | Drag a rectangle for live width, height and area; two rulers in portrait and landscape alike |
| Protractor | Half-disc dial with two needles that pin independently and can be moved together with two fingers; tap the readout to type an exact angle; the readout sits at the bottom in portrait |
| Calibration | Match a bank card's long edge (85.60 mm) against the on-screen frame, nudge with the buttons or drag the scale; the coefficient applies to every mode |
| Check for updates | Look up the latest release on GitHub and hand it to the system installer; the app only touches the network when you open this screen |

## Screenshots

| Ruler (portrait, default) | Ruler (landscape) | Rectangle | Protractor |
| --- | --- | --- | --- |
| ![portrait](docs/screenshots/portrait.png) | ![main](docs/screenshots/main.png) | ![rect](docs/screenshots/measure-rect.png) | ![protractor](docs/screenshots/protractor.png) |

| Two-point ruler (both ends pinned) | Calibration | Settings | Check for updates |
| --- | --- | --- | --- |
| ![two point](docs/screenshots/measure-two.png) | ![calibration](docs/screenshots/calibration.png) | ![settings](docs/screenshots/settings.png) | ![update](docs/screenshots/update.png) |

## Permissions and privacy

The app declares exactly two permissions, and both exist for the update check alone:

* `INTERNET` — used when you tap "Check for updates" to reach the GitHub releases API
* `REQUEST_INSTALL_PACKAGES` — used to hand the downloaded APK to the system installer

Unless you open the update screen, the app makes no network requests at all: no background
service, no analytics SDK, no account, and no measurement data ever leaves the phone.
The downloaded APK lives in the app's own cache and travels to the installer through a private
`ContentProvider`, so not even the storage permission is needed.

## Calibration and accuracy

The scale is computed from the screen's reported density:

```
pixels per tick = screen xdpi (or ydpi) / 25.4 × calibration   // centimetre / millimetre modes
pixels per tick = screen xdpi (or ydpi) / 8 × calibration      // inch mode
```

The `xdpi` / `ydpi` values a phone reports are nominal figures and are often a little off,
so it is worth calibrating once: put a bank card's long edge against the blue frame on the
calibration screen, adjust with `+` / `−` or by dragging the scale until it lines up, then save
from the top right. The coefficient applies to every mode until you change it again.

## Languages

The interface follows the system language, and any screen's settings let you pin one explicitly.
The app ships 17 translations: English, 简体中文, 繁體中文, 日本語, 한국어, Español,
Português (Brasil), Français, Deutsch, Italiano, Русский, Türkçe, العربية, हिन्दी,
Bahasa Indonesia, Tiếng Việt and ไทย.

To add another one, copy `app/src/main/res/values/strings.xml` to
`values-<language code>/strings.xml`, translate it, and add the language to `choices` in
`core/Locales.kt` and to `res/xml/locales_config.xml`. Nothing else needs to change.

## Building

You need JDK 17 and the Android SDK (`compileSdk 35`). The Gradle wrapper is included:

```bash
# debug build
./gradlew assembleDebug

# release build (signed with the local debug keystore by default; swap in your own
# keystore before publishing anywhere)
./gradlew assembleRelease
```

The APKs land in `app/build/outputs/apk/`. To install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release builds run R8 and the resource shrinker and strip Kotlin's reflection metadata,
so `app-release.apk` comes out at about 150 KB (the debug build is about 1 MB because it
is not compressed at all).

If you fork the project, point `RELEASES_API` in
`app/src/main/java/org/openruler/app/core/Updater.kt` and `SOURCE_URL` in `AboutActivity.kt`
at your own repository, so the in-app update check and the source button on the about screen
lead there.

## Project layout

```
app/src/main/java/org/openruler/app/
├── MainActivity.kt              # the measuring screen (full-screen ruler + tools)
├── CalibrationActivity.kt       # calibration
├── SettingsActivity.kt          # settings
├── AboutActivity.kt             # about / licence
├── UpdateActivity.kt            # in-app updates (check / download / install)
├── ProtractorActivity.kt        # protractor, also reachable as its own shortcut
├── BaseActivity.kt              # theme and palette
├── core/                        # units, preferences, locales, updater, palette
└── view/                        # the self-drawn ruler, band, protractor, calibration scale
```

## Provenance and licence

This is a **feature-equivalent rewrite**, not a repackaged APK. The interaction model takes
after the similar ruler app `org.nixgame.ruler` on Google Play (by Evgrafov Aleksei), but every
line of source, every icon and every screen resource here was written or drawn from scratch.
The repository contains no code, images, fonts or APK from the original app, and none of its
ads (AdMob, Pangle, AppLovin, Yandex, IronSource), Firebase / AppMetrica analytics, rating
dialogs or in-app purchases were carried over.

Released under the [MIT licence](LICENSE): use it, study it, change it, redistribute it,
commercially or otherwise.

The app is provided "as is", without warranty of any kind. Ruler accuracy depends on the
screen's nominal pixel density and on a correct calibration, so measured values are estimates
rather than certified measurements.

## Changelog

The changes in every release are listed in [CHANGELOG.md](CHANGELOG.md) (Chinese); the APKs
live on the [Releases](https://github.com/MaximeMET/Ruler/releases) page.
