# Morrow

**A gentle alarm clock with a sun called Pip.**

Morrow wakes you with a sunrise. As your alarm rings, the sky warms from night to dawn and Pip, a small sun character, rises, yawns and smiles with you. A short wake-up game makes sure you're really up, and then a bright Morning screen starts your day with the weather and something to look forward to.

The whole app follows the time of day: its colours, sky and Pip change from morning light to high noon, golden afternoon and moonlit night.

Morrow is built with Kotlin Multiplatform and Compose Multiplatform. Android is the main platform; the iOS app shares the same code and rings through Apple's AlarmKit.

---

## Contents

- [Features](#features)
- [How it works](#how-it-works)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Running the tests](#running-the-tests)
- [Regenerating bundled assets](#regenerating-bundled-assets)
- [Permissions](#permissions)
- [Accessibility](#accessibility)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

---

## Features

### Alarms
- **Setting the time:** drag the sun around a 24-hour sky dial, tap the time to type it, or tap or hold the − and + buttons to move it in 5-minute steps.
- **Repeat days:** one-off or weekly alarms, with labels.
- **Alarms by another city's clock:** set a 09:00 New York alarm from London and it rings at 09:00 New York time, even when the two cities change their clocks on different dates.
- **Sleep estimate:** Home and Edit show how much sleep you'll get if you go to bed now.
- **Sunrise ringing screen:** a banded sky that warms over a few seconds while Pip rises, with a 10-minute safety stop.
- **Sounds:** Birdsong, Wind chimes and Soft piano. Each alarm can have its own, or use the default from Settings, with a volume ramp of 30 seconds, 1 minute or 2 minutes.

### Wake-up games
Pick one or more games and chain them into a sequence, for example 3 sums, then 4 typed sentences, then 1 unscramble.

| Game | What you do |
| --- | --- |
| **Sums** | Solve quick arithmetic on a keypad |
| **Type** | Type a short sentence exactly |
| **Unscramble** | Put a jumbled word back together |
| **Breathe** | Hold to breathe in and out slowly |
| **Shake** | Shake the phone a number of times |
| **Scan** | Scan any QR code or barcode you chose, such as one stuck on the kettle |

Difficulty can be set to **Gentle**, **Normal** or **Tough**.

### Morning
- A bright Morning screen with a sleep summary, today's weather and "one thing to look forward to", which then shows on Home.

### World
- **World clock:** city cards that show each city's time and time of day, drawn as a small sky.
- **Weather:** current conditions for where you are or a home city, plus each city on the World tab.
- **City search:** a bundled list of world cities, with smaller places found online.

### Timers
- Wind Pip round a dial to set a timer, an hour a lap, or pick a quick length or type it.
- Run several timers at once, each with an optional note. Each one shows Pip setting in a little sky as it runs down.
- When a timer ends, Pip reads its note aloud.
- Timers keep ringing on time with the screen off, and are recovered after a restart.

### Pip and the themes
- Four time-of-day themes that switch on their own as the day goes by:

| Theme | Hours | Pip |
| --- | --- | --- |
| Morning light | 05:00–10:59 | Eyes open, with rays |
| High noon | 11:00–14:59 | Sunglasses, high in the sky |
| Golden afternoon | 15:00–18:59 | Happy eyes, sinking |
| Moonlit night | 19:00–04:59 | Asleep in a nightcap, cottage window lit |

- A continuous sky behind Pip that follows the real minute of the day, with hills, a cottage, stars and the moon.
- Pip can read the weather aloud on the Morning screen.

---

## How it works

Morrow hands every alarm to the operating system, so it rings on time whether the app is open or not.

- **Android:** alarms are scheduled with `AlarmManager.setAlarmClock()` and timers with `setExactAndAllowWhileIdle()`. When one goes off, a receiver starts a short-lived ringing service and shows the ringing screen over the lock screen. Alarms are rescheduled after a reboot, a time change or a time zone change, and the database lives in device-protected storage so alarms still ring before the phone is first unlocked.
- **iOS:** alarms and timers are handed to AlarmKit (iOS 26+), which rings them like the Clock app. The alert's **Start mission** button opens Morrow straight into the wake-up game, and timer countdowns show on the Lock Screen and in the Dynamic Island through a Live Activity.

Weather comes from [Open-Meteo](https://open-meteo.com/), using rounded coordinates, and refreshes only when the app is opened and the cached copy is older than 30 minutes.

---

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin (Swift for the iOS AlarmKit bridge) |
| UI | Compose Multiplatform, with Pip, the sky and the dial drawn on `Canvas` |
| Storage | Room (KMP) with the bundled SQLite driver; DataStore for settings |
| Dependency injection | Plain constructor injection |
| Camera and codes | CameraX and ZXing on Android; AVFoundation on iOS |
| Motion | Android sensors; Core Motion on iOS |
| Fonts | Fraunces and Figtree, bundled |
| Tests | kotlin.test, Turbine, Compose UI tests under Robolectric |
| Build | Gradle with AGP 9, KSP; XcodeGen for the iOS project |

| Platform | Minimum | Target |
| --- | --- | --- |
| Android | 8.0 (API 26) | API 37 |
| iOS | 26.0 | — |

---

## Project structure

```
Morrow/
├─ shared/                       # Kotlin Multiplatform: almost all of the app
│  ├─ src/commonMain/kotlin/app/morrow/
│  │  ├─ domain/                 # Alarm, games, next occurrence, sleep maths
│  │  ├─ data/                   # Room database and repositories
│  │  ├─ alarms/                 # AlarmCoordinator: keeps the OS schedule in step with stored alarms
│  │  ├─ platform/               # Interfaces implemented per platform
│  │  ├─ theme/                  # MorrowTheme, time-of-day themes, sky curve
│  │  ├─ ui/                     # Screens and components; MorrowApp is the root
│  │  └─ missions/               # Wake-up game logic
│  ├─ src/androidMain/           # Android implementations
│  ├─ src/iosMain/               # iOS implementations, wired up by IosApp
│  └─ schemas/                   # Room schema history
├─ androidApp/                   # Android entry point and alarm engine
│  └─ src/main/kotlin/app/morrow/
│     ├─ alarm/                  # Scheduler, receivers, ring service, ring activity
│     ├─ permissions/            # Permission checks and prompts
│     └─ debug/                  # Developer tools (debug builds only)
├─ iosApp/                       # Swift entry point, AlarmKit bridge and Live Activity widget
│  └─ project.yml                # XcodeGen spec for the Xcode project
├─ tools/                        # Scripts that generate bundled assets
└─ .github/workflows/ios.yml     # Builds the iOS app on a cloud Mac
```

Screen logic lives in pure, tested `*Model` functions beside each screen, so most behaviour can be tested without a device.

---

## Getting started

### Requirements

- **JDK 21** (17 or newer works)
- **Android Studio** with the Android SDK for API 37, or the Android SDK on its own
- An Android phone or emulator running Android 8.0 or newer
- For iOS: a Mac with **Xcode 26** and [XcodeGen](https://github.com/yonaskolb/XcodeGen)

### Clone

```bash
git clone https://github.com/Ri-Vino/Morrow.git
cd Morrow
```

### Android

Open the project in Android Studio and run the `androidApp` configuration, or from the command line:

```bash
./gradlew :androidApp:assembleDebug
```

To install on a connected phone:

```bash
./gradlew :androidApp:installDebug
```

On first launch Morrow walks you through the permissions it needs to ring on time.

> On some phones (for example Samsung and Xiaomi), set Morrow's battery usage to **Unrestricted** so the system doesn't delay alarms. The app offers this on first run.

### iOS

On a Mac:

```bash
cd iosApp
xcodegen generate
open Morrow.xcodeproj
```

Pick your team under Signing & Capabilities, then build and run the **Morrow** scheme. A build phase compiles the shared Kotlin framework through Gradle, so the first build takes a while.

The iOS app can't be built on Windows or Linux. The [iOS workflow](.github/workflows/ios.yml) builds it on a GitHub-hosted Mac on every push.

---

## Running the tests

| Command | What it runs |
| --- | --- |
| `./gradlew check` | All unit tests and lint |
| `./gradlew :shared:testAndroidHostTest` | Shared unit tests on the host |
| `./gradlew :shared:connectedAndroidDeviceTest` | Room database tests on a connected phone or emulator |

The shared tests cover the parts most likely to go wrong for a real person: midnight rollover, daylight-saving changes, alarms in another city's time zone, timers that end while the phone is off, and game sequences.

---

## Regenerating bundled assets

The assets Morrow ships are generated by scripts in `tools/`. You only need these when changing the assets.

| Script | Output | Needs |
| --- | --- | --- |
| `python tools/make_sounds.py` | Alarm and timer sounds (Ogg for Android, CAF for iOS), synthesised from scratch | numpy, scipy, soundfile |
| `python tools/make_cities.py <folder>` | `cities.tsv`, the bundled city list, from GeoNames exports | — |
| `python tools/trim_fraunces.py <font>` | A trimmed Fraunces font with only the optical-size axis | fonttools |

Each script explains its inputs at the top of the file.

---

## Permissions

| Permission | Why |
| --- | --- |
| Exact alarms (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` on Android 12) | Ring alarms and timers at the exact minute |
| Notifications and full-screen intent | Show the ringing screen over the lock screen |
| Display over other apps (optional) | Open the ringing screen full screen while the phone is in use, rather than as a banner |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Reschedule alarms after a restart |
| Foreground service (system exempted) | Play the alarm while it's ringing |
| Vibrate, wake lock | Vibrate and keep the screen on while ringing |
| Camera | Only for the Scan game, asked the first time you choose it |
| Internet, approximate location | Weather and online city search; location is optional and only read while the app is open |

---

## Accessibility

- Every control has a screen-reader label, and selected states are announced.
- Layouts work at 200% font size.
- Touch targets are at least 44 dp.
- Every text and background pair meets WCAG AA contrast in all four themes.
- Animations follow the system "remove animations" setting and pause while the screen is off.
- The sky dial can be fully controlled with its ±5 minute buttons.

---

## Contributing

Issues and pull requests are welcome. Before opening a pull request:

1. Run `./gradlew check` and make sure it passes.
2. Take colours and text styles from `MorrowTheme`, and Pip and scene colours from `Illustration`, rather than hard-coding them.
3. Put screen logic in the screen's `*Model` and add tests for it.
4. Keep commit subjects short and lowercase, for example `add the Type and Unscramble games`.

---

## Credits

- **Fonts:** [Fraunces](https://fonts.google.com/specimen/Fraunces) and [Figtree](https://fonts.google.com/specimen/Figtree), under the SIL Open Font License (the licence texts ship with the app).
- **City data:** [GeoNames](https://www.geonames.org/), under CC BY 4.0.
- **Weather and geocoding:** [Open-Meteo](https://open-meteo.com/), under CC BY 4.0.

---

## License

Copyright 2026 [Rivino](https://github.com/Ri-Vino).

Morrow is licensed under the [Apache License 2.0](LICENSE); see [NOTICE](NOTICE) for what it ships under other licences. The fonts, city data and recordings keep their own licences, listed under [Credits](#credits) and in the app under **Settings › Legal & about**, which also has Morrow's privacy policy and terms of service.
