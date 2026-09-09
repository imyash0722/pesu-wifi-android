# PESU WiFi for Android

Native Android companion app for PESU captive portal login, session management, and continuous keepalive. Replicates the core logic of the `pesu-wifi` CLI with a clean Material 3 interface and background Foreground Service.

[![Latest Release](https://img.shields.io/github/v/release/imyash0722/pesu-wifi-android?color=blue)](https://github.com/imyash0722/pesu-wifi-android/releases)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.2.0)-success.svg)](https://github.com/imyash0722/pesu-wifi-android/releases/download/v1.2.0/pesu-wifi-v1.2.0.apk)
[![Changelog](https://img.shields.io/badge/Changelog-Release%20Notes-green.svg)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **Automated Login & Logout**: 1-tap sign-in and sign-out against the Cyberoam gateway (`http://192.168.254.1:8090`).
- **🚀 First-Launch Background Permission Onboarding**: Interactive first-run dialog requesting unrestricted battery optimization, notifications, and alarms so keepalive works out of the box.
- **🌐 Universal Multi-Vendor Android Support**: Multi-OEM autostart resolver supporting Xiaomi (HyperOS/MIUI), Samsung (OneUI), Oppo/Realme/OnePlus (ColorOS), Vivo/iQOO, Huawei/Honor, Asus, and Transsion. Hides autostart settings cleanly on pure stock Android (Google Pixel, Motorola).
- **🔋 Screen-Off Keepalive (Continuous WakeLock & WifiLock)**: Holds non-reference-counted `PARTIAL_WAKE_LOCK` and low-latency `WifiLock` to prevent CPU sleep and Wi-Fi radio sleep when device is locked.
- **⏰ Exact RTC Watchdog Heartbeat**: Dual-redundant `AlarmManager.setExactAndAllowWhileIdle` ticking every 60s to wake the device even through deep Doze.
- **📶 Captive Portal Validation & Mobile Data Bypass**: Explicitly binds network calls to the Wi-Fi `SocketFactory` to bypass cellular fallback, and calls `reportNetworkConnectivity(true)` to dismiss Android's "Sign in to network" banner.
- **🛡️ Interactive Permissions System**: Visual setup cards and dialogs for Notifications, Battery Optimization, and Exact Alarms.
- **Quick Settings Tile**: Add the **PESU WiFi** tile to your notification pull-down shade for instant 1-tap toggle.
- **Hardware-Backed Credential Storage**: Encrypted with Android Jetpack `EncryptedSharedPreferences` (AES-256).
- **Multi-Account Switching**: Store multiple student/faculty accounts and easily switch active users with a single tap.
- **Desktop JSON Import/Export**: Directly paste or export `config.json` compatible with the desktop `pesu-wifi` CLI.

---

## App Architecture

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose + Material 3
- **Network Engine**: OkHttp 4.12 (direct Wi-Fi `SocketFactory` binding, `Proxy.NO_PROXY`, zero-idle pool)
- **Security**: AndroidX Security Crypto (`MasterKey` AES-256-GCM / AES-256-SIV)
- **Background Engine**: Android Foreground Service (`dataSync` type, Android 14 compliant)
- **WakeLock & Watchdog**: `PowerManager.PARTIAL_WAKE_LOCK`, `WifiManager.WifiLock`, `AlarmManager` exact RTC alarms
- **Quick Settings**: Android `TileService`

---

## Building from CLI

Requires Java 17 and Android SDK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Run unit tests
./gradlew testDebugUnitTest

# Build signed release APK
./gradlew assembleRelease
```

The compiled release APK will be located at:
```text
app/build/outputs/apk/release/app-release.apk
```

To install on a connected Android phone:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## Release History & Changelog

See [**CHANGELOG.md**](CHANGELOG.md) for full commit-by-commit technical breakdowns:
- [**v1.1.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.1.0) — Universal multi-vendor OEM autostart, continuous WakeLock/WifiLock, RTC hardware watchdog, captive portal validation, and signed release APK.
- [**v1.0.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.0.0) — Initial native Compose release with automated login, foreground keepalive, and Quick Settings tile.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
