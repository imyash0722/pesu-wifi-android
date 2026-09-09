# PESU WiFi for Android — Changelog & Release Notes

A comprehensive, commit-by-commit record of all architectural improvements, background keepalive enhancements, permission systems, and bug fixes for **PESU WiFi for Android**.

---

## Table of Contents
- [v1.2.0 — First-Launch Onboarding & Automated CI/CD Release](#v120--first-launch-onboarding--automated-cicd-release)
  - [Overview & Major Highlights](#v120-overview--major-highlights)
  - [Commit-by-Commit Technical Breakdown](#v120-commit-by-commit-technical-breakdown)
- [v1.1.0 — Universal Android Keepalive & Stability Update](#v110--universal-android-keepalive--stability-update)
  - [Overview & Major Highlights](#v110-overview--major-highlights)
  - [Commit-by-Commit Technical Breakdown](#v110-commit-by-commit-technical-breakdown)
- [v1.0.0 — Initial Release](#v100--initial-release)
  - [Overview & Major Highlights](#v100-overview--major-highlights)
  - [Commit-by-Commit Technical Breakdown](#v100-commit-by-commit-technical-breakdown)
- [Building & Release Verification](#building--release-verification)

---

## v1.2.0 — First-Launch Onboarding & Automated CI/CD Release

**Release Date:** September 9, 2026  
**Git Tag:** [`v1.2.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.2.0)  
**APK Asset:** `pesu-wifi-v1.2.0.apk` (11.39 MiB, signed via APK Signature Scheme v2)  

### v1.2.0 Overview & Major Highlights
- **🚀 First-Launch Background Permission Dialog:** Brand new contextual onboarding dialog (`FirstLaunchPermissionsDialog`) that triggers on initial app launch. Proactively requests and explains "Unrestricted Battery" optimization, "Notifications", and "Exact 60s Alarms" so keepalive functions out of the box without manual OS settings navigation.
- **🛡️ Clean Startup Experience:** Suppressed raw uncontextualized Android 13+ system notification popup in `MainActivity.onCreate` until after the onboarding explanation dialog has been shown.
- **🔄 Auto-Dismiss & Persistent Tracking:** Automatically saves user onboarding state to SharedPreferences (`pesu_wifi_settings`), seamlessly transitioning to "Done" when all essential permissions are granted.
- **🤖 Automated GitHub Actions APK Release Pipeline:** Added `.github/workflows/release.yml` for automated building, signing verification, and asset publishing directly to GitHub Releases upon git tag push.

---

## v1.1.0 — Universal Android Keepalive & Stability Update

**Release Date:** September 9, 2026  
**Git Tag:** [`v1.1.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.1.0)  
**APK Asset:** `pesu-wifi-v1.1.0.apk` (11.39 MiB, signed via APK Signature Scheme v2)  
**Commits in this Release:** 5 commits (`1ddd293` -> `0b7413b`)

### v1.1.0 Overview & Major Highlights
- **🌐 Universal Multi-Vendor Android Support:** Completely decoupled OEM autostart management from Xiaomi/MIUI. Uses dynamic runtime `PackageManager` intent resolution across Xiaomi (MIUI/HyperOS), Samsung (OneUI), Oppo/Realme/OnePlus (ColorOS), Vivo/iQOO (FuntouchOS/OriginOS), Huawei/Honor (EMUI), Transsion (Infinix/Tecno/itel), Asus, and Lenovo. Automatically hides the autostart row on pure stock Android (Google Pixel, Motorola).
- **🔋 Continuous WakeLock & WifiLock:** Holds a continuous `PowerManager.PARTIAL_WAKE_LOCK` and low-latency `WifiManager.WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY`) while the keepalive service is active, ensuring the Wi-Fi radio and CPU do not sleep when the screen is locked.
- **⏰ Dual-Redundant `AlarmManager` Watchdog Heartbeat:** Configured hardware RTC `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, ...)` ticking every 60 seconds. Wakes the CPU reliably even if aggressive OEM power managers or deep Android Doze suspend standard coroutines.
- **📶 Captive Portal OS Validation & Mobile Data Bypass:** Binds OkHttp calls directly to the Wi-Fi interface's `SocketFactory` to bypass cellular data fallback routing. Automatically triggers `reportNetworkConnectivity(true)` upon login so Android immediately dismisses the "Sign in to Wi-Fi network" notification.
- **🛡️ Interactive Background Permissions System:** Added visual `PermissionsCard` and `PermissionsRequiredDialog` guiding users through granting Notifications (Android 13+), Unrestricted Battery Optimization, and Exact Watchdog Alarms.
- **📦 Pre-Signed Release Artifact:** Configured release builds with universal APK signing for 1-tap sideloading without debug warnings.

### v1.1.0 Commit-by-Commit Technical Breakdown

#### [`3839785`](https://github.com/imyash0722/pesu-wifi-android/commit/3839785) — `feat: prompt background and battery optimization permissions on first launch`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `FirstLaunchPermissionsDialog.kt`, `PermissionManager.kt`, `HomeScreen.kt`, `MainActivity.kt`
- **Technical Detail:**
  - Added dedicated `FirstLaunchPermissionsDialog` that triggers on fresh app installations.
  - Implemented persistent first-launch detection in `PermissionManager` using `SharedPreferences` (`has_seen_first_launch_prompt`).
  - Presents clear explanations and 1-tap onboarding action buttons for Unrestricted Battery, Notifications, and Exact Alarms before enabling daemon.

#### [`0b7413b`](https://github.com/imyash0722/pesu-wifi-android/commit/0b7413b) — `feat: make OEM autostart universal across Android vendors and bump to v1.1.0`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `PermissionManager.kt`, `PermissionsCard.kt`, `HomeScreen.kt`, `MainActivity.kt`, `app/build.gradle.kts`
- **Technical Detail:**
  - Replaced vendor-locked `isMiuiDevice()` check with dynamic intent-resolution `hasAutostartSettings(context)` and `getAutostartIntent(context)`.
  - Added candidate intent mappings for:
    - Xiaomi / POCO / Redmi: `com.miui.permcenter.autostart.AutoStartManagementActivity`
    - Samsung: `com.samsung.android.sm.ui.battery.BatteryActivity`
    - Oppo / Realme / OnePlus: `com.coloros.safecenter.permission.startup.StartupAppListActivity`
    - Vivo / iQOO: `com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity`
    - Huawei / Honor: `com.huawei.systemmanager.optimize.process.ProtectActivity`
    - Transsion (Infinix, Tecno, itel): `com.transsion.phonemanager.settings.AutoRunManageActivity`
    - Asus: `com.asus.mobilemanager.autostart.AutoStartActivity`
    - Lenovo: `com.lenovo.security.purebackground.PureBackgroundActivity`
  - Renamed all UI components and callbacks from `onRequestMiuiAutostart` to `onRequestAutostart`.
  - Automatically hides autostart UI when no vendor autostart manager is detected (pure Android).
  - Bumped `versionCode = 2`, `versionName = "1.1.0"` and configured `signingConfigs.getByName("debug")` under `buildTypes.release`.

#### [`9d70a12`](https://github.com/imyash0722/pesu-wifi-android/commit/9d70a12) — `feat(permissions): add PermissionManager, PermissionsCard, and background permission flow`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `PermissionManager.kt`, `PermissionsCard.kt`, `HomeScreen.kt`, `MainActivity.kt`, `AndroidManifest.xml`
- **Technical Detail:**
  - Created `PermissionManager` to inspect live state of:
    - `POST_NOTIFICATIONS` (`ContextCompat.checkSelfPermission`)
    - Battery Optimization (`PowerManager.isIgnoringBatteryOptimizations`)
    - Exact Alarms (`AlarmManager.canScheduleExactAlarms`)
  - Added `PermissionsCard` displaying a status card with action buttons (`Allow`, `Whitelist`, `Enable`, `Settings`).
  - Added `PermissionsRequiredDialog` intercepting background daemon toggling if required permissions are missing.
  - Added permissions in `AndroidManifest.xml`: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SCHEDULE_EXACT_ALARM`.

#### [`8899468`](https://github.com/imyash0722/pesu-wifi-android/commit/8899468) — `feat: continuous WakeLock, AlarmManager watchdog, and battery optimization prompt`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `WifiKeepaliveService.kt`, `MainActivity.kt`
- **Technical Detail:**
  - Held continuous non-reference-counted `PowerManager.PARTIAL_WAKE_LOCK` (`PesuWifi:KeepaliveWakeLock`) while the foreground service runs.
  - Held continuous `WifiManager.WifiLock` (`PesuWifi:KeepaliveWifiLock`) in `WIFI_MODE_FULL_LOW_LATENCY` (Android 10+) or `WIFI_MODE_FULL_HIGH_PERF` (Android 9 and below).
  - Implemented dual-redundancy `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, ...)` dispatching `ACTION_HEARTBEAT` every 60 seconds.
  - Added a 35-second debounce window (`lastCheckTimestamp`) so the 60s coroutine loop and the alarm watchdog never collide or double-post requests.
  - Ensured complete lock release and alarm cancellation in `onDestroy()`.

#### [`c923885`](https://github.com/imyash0722/pesu-wifi-android/commit/c923885) — `fix(service): resolve networkCallback registration syntax error`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `WifiKeepaliveService.kt`
- **Technical Detail:** Cleaned up Kotlin syntax in `ConnectivityManager.NetworkCallback` lifecycle handlers and guarded unregister calls against double-free exceptions.

#### [`1ddd293`](https://github.com/imyash0722/pesu-wifi-android/commit/1ddd293) — `fix(connectivity): resolve unstable connections and random drops`
- **Author:** imyash0722 <imyash0722@users.noreply.github.com>
- **Files Modified:** `PortalRepository.kt`, `PortalApi.kt`, `WifiKeepaliveService.kt`
- **Technical Detail:**
  - Bound OkHttp's `SocketFactory` to the active Wi-Fi `Network` instance (`network.socketFactory`), preventing captive portal traffic from being routed through cellular data when Android detects "No Internet".
  - Configured `Proxy.NO_PROXY` and `ConnectionPool(0, 1, TimeUnit.NANOSECONDS)` to avoid reusing stale TCP sockets across AP transitions.
  - Called `connectivityManager.reportNetworkConnectivity(wifiNet, true)` on successful login and session verification, triggering immediate Android OS captive portal clearing.
  - Added 500ms jitter retry in `PortalApi.checkLive()`.
  - Added a 2-consecutive failure requirement in `WifiKeepaliveService` before initiating a re-login.

---

## v1.0.0 — Initial Release

**Release Date:** September 8, 2026  
**Git Tag:** [`v1.0.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.0.0)  
**Commits in this Release:** 2 commits (`3c95ef4` -> `5dbd4c8`)

### v1.0.0 Overview & Major Highlights
- **Automated Login & Logout:** 1-tap sign-in against Cyberoam captive portal (`http://192.168.254.1:8090`).
- **Background Keepalive:** Android 14 Foreground Service with persistent status notification and action buttons.
- **Quick Settings Tile:** 1-tap toggle from Android notification pull-down shade.
- **Hardware-Backed Encryption:** `EncryptedSharedPreferences` (AES-256) for secure credential storage.
- **Multi-Account Switching:** Easily add, switch, or import accounts from desktop `config.json`.
- **Location-Free Detection:** Senses Wi-Fi without requesting sensitive GPS/location permissions.

### v1.0.0 Commit-by-Commit Technical Breakdown

#### [`5dbd4c8`](https://github.com/imyash0722/pesu-wifi-android/commit/5dbd4c8) — `fix: resolve alignment typo and flow combine typing, add Java 17 to gradle.properties`
- **Files Modified:** `gradle.properties`, `PortalViewModel.kt`, UI layout files
- **Technical Detail:** Corrected Reactive Kotlin Flow combine typing across credentials and portal states, fixed UI alignment typography issues, and pinned Java 17 in Gradle properties.

#### [`3c95ef4`](https://github.com/imyash0722/pesu-wifi-android/commit/3c95ef4) — `feat(android): initial commit of PESU WiFi Android app in Kotlin + Compose`
- **Files Modified:** Entire project scaffold
- **Technical Detail:** Initial implementation of the Android application:
  - Jetpack Compose + Material 3 UI with dark Tokyo Night color palette.
  - Cyberoam authentication client (`PortalApi.kt`, `PortalRepository.kt`).
  - Android Foreground Service keepalive daemon (`WifiKeepaliveService.kt`).
  - System Quick Settings Tile (`PesuTileService.kt`).
  - Security cryptor (`EncryptedSharedPreferences`).

---

## Building & Release Verification

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Release APK
```bash
./gradlew assembleRelease
```
The resulting APK will be at `app/build/outputs/apk/release/app-release.apk`.

### Verify APK Signature Scheme
```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```
Expected output:
```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
```
