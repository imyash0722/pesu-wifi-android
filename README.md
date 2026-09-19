# PESU WiFi for Android

Native Android companion app for PESU captive portal login, session management, and continuous keepalive. Replicates the core logic of the `pesu-wifi` CLI with a clean Material 3 interface and background Foreground Service.

[![Latest Release](https://img.shields.io/github/v/release/imyash0722/pesu-wifi-android?color=blue)](https://github.com/imyash0722/pesu-wifi-android/releases)
[![Download Stable APK](https://img.shields.io/badge/Download-Stable_APK_v1.5.0-success.svg)](https://github.com/imyash0722/pesu-wifi-android/releases/download/v1.5.0/pesu-wifi-v1.5.0-stable.apk)
[![Download Tester APK](https://img.shields.io/badge/Download-Tester_APK_v1.5.0-orange.svg)](https://github.com/imyash0722/pesu-wifi-android/releases/download/v1.5.0/pesu-wifi-v1.5.0-tester.apk)
[![Changelog](https://img.shields.io/badge/Changelog-Release_Notes-green.svg)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **Pure Event-Driven OS Rules**: Zero-battery background architecture using native Android `WifiNetworkSuggestion`, persistent `ConnectivityManager` `PendingIntent`, and persistent `JobScheduler`.
- **5 Campus SSIDs Supported**: Pre-configured with `PESU-EC-Campus`, `PESU-CIE`, `AMAATRA_HOSTEL`, `Foodcourt`, and `pes south cafe`.
- **Automated Login & Logout**: Instant auto-authentication against the Cyberoam gateway (`http://192.168.254.1:8090`).
- **BSSID Handover Verification**: Automatically verifies link reachability with network-bound router gateway pings on access point handovers.
- **Non-Interference Standby Mode**: Automatically pauses when connected to external/home Wi-Fi networks (e.g. `imyk`); instantly resumes when re-entering campus network range.
- **In-App Diagnostic Logs & AP Explorer**: Live timestamped log viewer with level filtering, keyword search, AP mapper, and Android system export sheet.
- **Captive Portal Validation & Mobile Data Bypass**: Explicitly binds network calls to the Wi-Fi `SocketFactory` to bypass cellular fallback, and calls `reportNetworkConnectivity(true)` to dismiss Android's captive portal notification.
- **Quick Settings Tile**: Add the **PESU WiFi** tile to your notification pull-down shade for instant 1-tap toggle.
- **Hardware-Backed Credential Storage**: Encrypted with Android Jetpack `EncryptedSharedPreferences` (AES-256).
- **Multi-Account Switching**: Store multiple student/faculty accounts and easily switch active users with a single tap.

---

## App Architecture

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose + Material 3
- **Network Engine**: OkHttp 4.12 (direct Wi-Fi `SocketFactory` binding, `Proxy.NO_PROXY`, zero-idle pool)
- **Security**: AndroidX Security Crypto (`MasterKey` AES-256-GCM / AES-256-SIV)
- **Background Engine**: Pure Event-Driven OS Rules (`WifiNetworkSuggestion`, `ConnectivityManager` persistent `PendingIntent`, `JobScheduler`)
- **Quick Settings**: Android `TileService`

---

## Building from CLI

Requires Java 17 and Android SDK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Run unit tests
./gradlew testDebugUnitTest

# Build signed release APKs (both Stable and Tester)
./gradlew assembleRelease

# Or build individual flavors:
./gradlew assembleStableRelease  # Stable track
./gradlew assembleBetaRelease    # Tester track
```

The compiled release APKs will be located at:
```text
app/build/outputs/apk/stable/release/app-stable-release.apk  (Stable)
app/build/outputs/apk/beta/release/app-beta-release.apk      (Tester)
```

To install on a connected Android phone:
```bash
# Install Stable
adb install -r app/build/outputs/apk/stable/release/app-stable-release.apk

# Install Tester (side-by-side)
adb install -r app/build/outputs/apk/beta/release/app-beta-release.apk
```

---

## Release History & Changelog

See [**CHANGELOG.md**](CHANGELOG.md) for full commit-by-commit technical breakdowns:
- [**v1.4.6**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.6) — Classroom Wi-Fi subnet detection, VPN (Tailscale) interface-binding fallback, timeout hardening, transient drop debouncing, and vivid UI theme polish.
- [**v1.4.5**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.5) — Material You redesign, dual flavor builds (Stable & Tester), campus BSSID cataloging, 1-tap account switch & auto-reconnect, and Android back navigation gesture fixes.
- [**v1.4.1**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.1) — Campus AP auto-reconnect via `WifiNetworkSuggestion`, zero-latency fast re-auth (<150ms), 15-minute adaptive watchdog, and 1-tap Wi-Fi Settings panel.
- [**v1.4.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.0) — Physical AP roaming recovery, stale Cyberoam session auto-eviction, auto-expiring cooldowns, and universal file-backed diagnostics.
- [**v1.3.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.3.0) — Socket binding EPERM fallback, non-interference standby mode, and in-app logs.
- [**v1.2.1**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.2.1) — Portal login detection and cleartext traffic fixes.
- [**v1.2.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.2.0) — First-launch permission onboarding and automated CI/CD release workflow.
- [**v1.1.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.1.0) — Universal multi-vendor OEM autostart, continuous WakeLock/WifiLock, RTC hardware watchdog, captive portal validation, and signed release APK.
- [**v1.0.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.0.0) — Initial native Compose release with automated login, foreground keepalive, and Quick Settings tile.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
