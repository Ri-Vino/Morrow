# Releasing Morrow

Morrow's first public release is a **beta**: `0.1.0-beta.1` on Android, `0.1.0` (build 1) on iOS through TestFlight.
This is what's needed to ship it, and what has to be done by hand.

---

## 1. Version

| Where | Field | Now | Rule |
| --- | --- | --- | --- |
| `androidApp/build.gradle.kts` | `versionName` | `0.1.0-beta.1` | What people see |
| `androidApp/build.gradle.kts` | `versionCode` | `1` | Higher for every Play upload, even of the same version |
| `iosApp/project.yml` | `MARKETING_VERSION` | `0.1.0` | Numbers only; App Store Connect refuses `-beta` |
| `iosApp/project.yml` | `MORROW_VERSION_LABEL` | `0.1.0-beta.1` | What Settings › Legal & about shows on iPhone |
| `iosApp/project.yml` | `CURRENT_PROJECT_VERSION` | `1` | Higher for every TestFlight upload of the same version |

Keep the two platforms in step. For the next beta: `0.1.0-beta.2`, versionCode `2`, build `2`. Drop the `-beta.N` and go to `1.0.0` when it leaves beta.

When the privacy policy or terms change, update the "Last updated" date at the top of
`shared/src/commonMain/composeResources/files/legal/privacy.md` or `terms.md`.

---

## 2. Android

### Signing key (once)

Google Play signs the app it delivers with its own key (Play App Signing). You sign uploads with an **upload key** you keep. Make one, and keep the file and passwords somewhere safe and outside the repository (a password manager, plus a backup):

```bash
keytool -genkeypair -v -keystore morrow-upload.jks -alias morrow -keyalg RSA -keysize 4096 -validity 10000
```

The build reads the key from environment variables or from `~/.gradle/gradle.properties` (your user folder, not the project's):

```properties
MORROW_KEYSTORE=C:/Users/you/keys/morrow-upload.jks
MORROW_KEYSTORE_PASSWORD=...
MORROW_KEY_ALIAS=morrow
MORROW_KEY_PASSWORD=...
```

Without `MORROW_KEYSTORE`, release builds still work but come out unsigned. `*.jks`, `*.keystore` and `keystore.properties` are in `.gitignore`.

### Build

```bash
./gradlew check :androidApp:bundleRelease
```

Google Play takes the `.aab` from `androidApp/build/outputs/bundle/release/`. `assembleRelease` makes an `.apk` for installing by hand.

Release builds are shrunk with R8. **Before every upload, install the release build on a real phone** and check that an alarm rings from the lock screen, snooze and a wake-up game work, weather loads, a city search works, a timer rings, and Settings › Legal & about opens each page. R8 problems only show up when the app runs.

### Play Console (once)

1. Create the app. The package name `app.morrow` can never change after the first upload. If Play says it's taken, change `applicationId` before uploading.
2. **Testing track:** start with **Internal testing** (up to 100 testers, no review wait). A personal developer account made after November 2023 must also run a **Closed test with at least 12 testers for 14 days in a row** before it can apply for production. Open testing is Play's public "beta" listing.
3. **App content** (Policy › App content):
   - **Privacy policy:** a public URL. Once the repository is public, this works:
     `https://github.com/Ri-Vino/Morrow/blob/main/shared/src/commonMain/composeResources/files/legal/privacy.md`
     (or publish the same file with GitHub Pages).
   - **Ads:** no ads.
   - **App access:** everything is available without an account.
   - **Content rating:** fill in the questionnaire (no violence, user content, gambling or sharing of location with other users).
   - **Target audience:** adults or 13+, not designed for children.
   - **Data safety:** see below.
   - **Permission declarations:** see section 4.
4. **Store listing:** short and full description, the icon at 512 × 512, a feature graphic at 1024 × 500, and at least 2 phone screenshots. Say it's a beta in the description, and add a contact email (Play requires one).

### Data safety form

From what the app does (see `privacy.md`):

- **Does your app collect or share user data?** Yes, because the rough location and city searches leave the phone, to Open-Meteo.
- **Location › Approximate location:** collected, not shared (Open-Meteo provides the service). Optional. Purpose: App functionality. Not processed ephemerally, to be safe, since Open-Meteo's logs are outside Morrow's control.
- **App activity › In-app search history:** optional to declare. The city search text goes to Open-Meteo's place search with weather on. It's a query rather than a history, but declaring it is the safer choice.
- **Encrypted in transit:** yes (HTTPS only).
- **Can users ask for data to be deleted?** Nothing is kept by Rivino. Everything else is on the phone, deleted with the app.
- Nothing else is collected: no personal info, contacts, photos, files, audio, health, messages, device IDs, crash logs or diagnostics.
- Camera and motion are used on the phone only, so they aren't "collected".

---

## 3. iOS (TestFlight)

1. Join the **Apple Developer Program**, and create the app ID `app.morrow` (and `app.morrow.widgets` for the Live Activity).
2. In App Store Connect, create the app with the same bundle ID, primary language English, and the privacy policy URL from above.
3. On a Mac: `cd iosApp && xcodegen generate`, open `Morrow.xcodeproj`, pick your team, then **Product › Archive** and upload.
4. **Export compliance:** Morrow only uses Apple's own HTTPS, which is exempt. Answer that when asked, or add `ITSAppUsesNonExemptEncryption` = `NO` to `iosApp/iosApp/Info.plist` so you aren't asked on every upload.
5. **Privacy manifest:** Apple requires a `PrivacyInfo.xcprivacy` declaring the "required reason" APIs the app and its libraries use (for example file timestamps and system boot time from the Kotlin runtime and DataStore). Xcode's **Generate Privacy Report** on the archive shows what's missing; add the file under `iosApp/iosApp/`. It isn't there yet.
6. **App Privacy** (the label on the store page): **Location › Coarse location**, for App Functionality, not linked to the user, not used for tracking. Optionally **Search history** on the same terms. Nothing else.
7. **TestFlight:** internal testers (your team) can test straight away. **External** testers need Beta App Review once per version, with a short "What to test" note.

---

## 4. Sensitive permissions

Each of these needs the store reviewer to agree that Morrow is an alarm clock.

| Permission | Why Morrow needs it | What the store needs |
| --- | --- | --- |
| `USE_EXACT_ALARM` | Rings alarms and timers at the exact minute. Granted at install, only to alarm and calendar apps. | Play Console **exact alarm declaration**: say Morrow's core function is an alarm clock. An app that isn't one would be rejected and should use `SCHEDULE_EXACT_ALARM` instead. |
| `USE_FULL_SCREEN_INTENT` | Shows the ringing screen over the lock screen. From Android 14, Play only grants it to calling and alarm apps. | Play Console **full-screen intent declaration**: choose "Alarm". |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | `RingService` keeps the alarm playing while it rings. Allowed for apps that hold `USE_EXACT_ALARM` and use it to ring alarms. | Play Console **foreground service declaration**: the `systemExempted` type, what the user sees ("the alarm sound and ringing screen while an alarm rings"), and a link to a short video of an alarm ringing, from lock screen to stop. |
| `SYSTEM_ALERT_WINDOW` | Optional: opens the ringing screen full screen while another app is in use, instead of a banner. Users turn it on themselves in system settings. | No declaration form, but reviewers can query it. Keep the reason in the listing or review notes. Removing it would only lose the full-screen ring while the phone is in use. |
| `CAMERA` | Only for the Scan game, asked the first time it's chosen. Codes are read on the phone. | No declaration. Not "collected" on the data safety form. iOS: `NSCameraUsageDescription` is set. |
| `ACCESS_COARSE_LOCATION` | Optional, for local weather, only while the app is open. | Declared on the data safety form (above). No background location, so no location declaration. |

`SCHEDULE_EXACT_ALARM` is only requested up to Android 12L (`maxSdkVersion="32"`), where `USE_EXACT_ALARM` doesn't exist.

---

## 5. Before each release

- [ ] Version bumped on both platforms (section 1)
- [ ] `./gradlew check :shared:compileAndroidDeviceTest` passes, and the iOS workflow is green
- [ ] Release build tested by hand on a real phone (section 2)
- [ ] "Last updated" dates right, if the privacy policy or terms changed
- [ ] Release notes written for the testers
- [ ] Commit tagged, for example `git tag v0.1.0-beta.1`

## 6. Things to keep in mind

- **Open-Meteo** is free for non-commercial use, with credit (given in Settings and in the terms). If Morrow ever earns money, from ads, a price or in-app purchases, it needs an Open-Meteo API subscription first.
- **GeoNames** city data is CC BY 4.0. The credit in Settings and Legal & about must stay.
- **Third-party licences:** Settings › Legal & about › Open-source licences names the main libraries. When adding a library with a different licence (not Apache 2.0), add it to `Licences` in `shared/src/commonMain/kotlin/app/morrow/ui/about/AboutModel.kt`.
