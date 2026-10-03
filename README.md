# PESU WiFi for Android

Native Android companion app for PESU captive portal login, session management, and continuous keepalive. Replicates the core logic of the `pesu-wifi` CLI with a clean Material 3 interface and background Foreground Service.

[![Latest Release](https://img.shields.io/github/v/release/imyash0722/pesu-wifi-android?color=blue)](https://github.com/imyash0722/pesu-wifi-android/releases/latest)
[![Website](https://img.shields.io/badge/Website-GitHub_Pages-6C6E36.svg)](https://imyash0722.github.io/pesu-wifi-android/)
[![Download Stable APK](https://img.shields.io/badge/Download-Stable_APK_v1.7.1-success.svg)](https://github.com/imyash0722/pesu-wifi-android/releases/download/v1.7.1/pesu-wifi-v1.7.1-stable.apk)
[![Download Tester APK](https://img.shields.io/badge/Download-Tester_APK_v1.7.1-orange.svg)](https://github.com/imyash0722/pesu-wifi-android/releases/download/v1.7.1/pesu-wifi-v1.7.1-tester.apk)
[![Changelog](https://img.shields.io/badge/Changelog-Release_Notes-green.svg)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **Automated Login & Logout**: 1-tap sign-in and sign-out against campus Cyberroam gateways.
- **Continuous 120-Second Jittered Keepalive Engine (v1.7)**: Foreground service keepalive running every 120s with &plusmn;30s randomized jitter (90s–150s range, clamped at 60s minimum) to prevent synchronized server floods while satisfying Cyberroam's strict Dead Client Detection (DCD).
- **Smart Strongest AP Roaming & Auto-Connect (v1.7)**: Actively scans campus BSSIDs by RSSI signal strength (`WifiSuggestionManager.findStrongestCampusAp`), prioritizes the strongest access point across campus (`priority=1000`), and automatically prompts to power on the Wi-Fi radio on Connect button tap.
- **Dynamic Multi-Gateway Probing & Amaatra Hostel Support (v1.7.1)**: Dynamically detects and probes candidate portal gateways between standard campus networks (`http://192.168.254.1:8090`) and the Amaatra Hostel portal (`http://172.16.1.1:8090` / `http://192.168.1.1:8090`), with automated captive HTTP 302/307 redirect discovery.
- **Earthy "STUDY HARD" Design Aesthetic (v1.7)**: Modernized launcher icon and landing page palette (`#675647` mocha, `#E3BD90` caramel tan, `#DFD3B5` cream, `#6C6E36` olive green).
- **Minimalist Web Landing Page (v1.7)**: Zero-dependency, responsive landing page served directly via GitHub Actions at [https://imyash0722.github.io/pesu-wifi-android/](https://imyash0722.github.io/pesu-wifi-android/).
- **OS-Level AP Switching & Zero-Latency Roam (v1.5/v1.6)**: Android system calls (`WifiNetworkSuggestion` across campus SSIDs, system-persistent `ConnectivityManager` `NetworkCallback` `PendingIntent`, and fallback `JobScheduler`) to trigger fast re-auth (<150ms) upon roaming.
- **Router Reachability Probing (`RouterPing`)**: ICMP ping and network-bound TCP socket probing (ports 8090, 80, 53) to verify router gateway reachability during AP handovers before firing HTTP authentication.
- **OEM Battery Optimization Override**: 1-tap manual whitelist confirmation resolving persistent detection hurdles on custom Android skins (Vivo FuntouchOS, Xiaomi MIUI/HyperOS, Samsung OneUI).
- **Android 12–16 Forward Compatibility**: Full support for Android 16 (API 36), runtime location & Wi-Fi device permissions without SSID redaction, 16 KB page-aligned packaging, and predictive back navigation.
- **Resilient Network Sockets (EPERM Resolution)**: Seamless fallback to standard routing if VPN or system policies prevent physical Wi-Fi interface binding.
- **Non-Interference Standby Mode**: Automatically pauses keepalive, releases WakeLocks, and cancels wake alarms on home/external Wi-Fi networks; automatically resumes when you reconnect to campus Wi-Fi.
- **In-App Diagnostic Logs**: Live timestamped log viewer with level filtering, keyword search, 1-tap copy, and Android system export sheet.
- **Quick Settings Tile**: Add the **PESU WiFi** tile to your notification pull-down shade for instant 1-tap toggle.
- **Hardware-Backed Credential Storage**: Encrypted with Android Jetpack `EncryptedSharedPreferences` (AES-256 GCM) backed by Android Keystore.
- **Two-Tier Campus AP Database & Cisco Twin Synthesis (v1.5)**: Scalable database (`assets/campus_bssids.json` + persistent storage + in-memory `ConcurrentHashMap`) mapping campus routers, automatically deriving Cisco dual-band twins (flipping bit 6 of 4th octet) for zero-latency AP recognition.
- **Cyberroam DoS Flood Protection & Account Debounce (v1.5)**: 1500ms debounce guard and UI loading locks to prevent rapid taps from triggering Cyberroam's 5-minute port 8090 brute-force block, with clean session logout before account switching.
- **Multi-Account Switching**: Store multiple student/faculty accounts and easily switch active users with a single tap.
- **Desktop JSON Import/Export**: Directly paste or export `config.json` compatible with the desktop `pesu-wifi` CLI.

---

## Network Architecture & Permissions

### Multi-Subnet & Dual-Gateway Support
* **PESU Campus (EC & RR Campus)**:
  * Client subnets: `10.x.x.x` (or `192.168.254.x`)
  * Local VLAN router gateways: `10.14.x.1`
  * Centralized Cyberoam portal: `http://192.168.254.1:8090`
* **Amaatra Hostel**:
  * Client subnet: `172.16.x.x`
  * Local default gateway: `172.16.1.1`
  * Cyberoam portal: `http://172.16.1.1:8090`
* **Automatic Dynamic Discovery**:
  * Probes candidate gateways in order of priority (`192.168.254.1:8090`, `172.16.1.1:8090`, active gateway).
  * Automatically detects new portals via HTTP 302/307 redirects on `connectivitycheck.gstatic.com/generate_204`.

### Permissions Explained
* **Location Permission (`ACCESS_FINE_LOCATION`)**:
  * Required by the Android OS (Android 8 through 16) to read Wi-Fi network names (`SSID`) and router MACs (`BSSID`).
  * Without this permission, Android strictly redacts the SSID to `<unknown ssid>` and BSSID to `02:00:00:00:00:00`, preventing the app from distinguishing campus Wi-Fi from home or hotspot networks.
* **Wi-Fi Suggestions & Switching (`WifiNetworkSuggestion`)**:
  * Uses Android's `WifiNetworkSuggestion` API to prioritize campus networks (`priority=1000`).
  * **Vivo (Funtouch OS / OriginOS)**: Displays a system notification: *"Allow PESU WiFi to suggest Wi-Fi networks?"*. Tap **Allow** once so Vivo permits automatic AP roaming.
  * **Xiaomi (MIUI / HyperOS)**: Navigate to *App Info &rarr; Other permissions &rarr; "Change Wi-Fi connectivity"* and set it to **Always allow**.

---

## App Architecture

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose + Material 3
- **Network Engine**: OkHttp 4.12 (direct Wi-Fi `SocketFactory` binding, `Proxy.NO_PROXY`, zero-idle pool)
- **Security**: AndroidX Security Crypto (`MasterKey` AES-256-GCM / AES-256-SIV)
- **Background Engine**: Android Foreground Service (`dataSync` type, Android 14+ compliant)
- **WakeLock & Watchdog**: `PowerManager.PARTIAL_WAKE_LOCK`, `WifiManager.WifiLock`, `AlarmManager` exact RTC alarms (120s &plusmn;30s jitter)
- **Quick Settings**: Android `TileService`

---

## Building from CLI

Requires Java 17 and Android SDK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Run unit tests
./gradlew test

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

## 📱 Quick Setup Guide for Students

1. **Download & Install**:
   - Download the latest **[Stable APK (v1.7.1)](https://github.com/imyash0722/pesu-wifi-android/releases/latest)**.
   - If prompted by your browser or files app, tap **Allow from this source** to permit sideload installation.
2. **Permissions on First Launch**:
   - **Notifications**: Tap **Allow** (displays ongoing connection status and keepalive heartbeat).
   - **Unrestricted Battery**: Tap **Whitelist** to stop Android from freezing the keepalive heartbeat when your screen is locked.
3. **Manufacturer Battery Settings (Crucial for 24/7 Background Keepalive)**:
   - **Vivo / iQOO**: Phone Settings → Apps → PESU WiFi → Battery → Select **"Allow high background power consumption"** (or Unrestricted). In the app, tap *"Already set in Settings? Confirm"*.
   - **Xiaomi / Redmi / POCO**: Settings → Apps → PESU WiFi → Battery saver → Select **"No restrictions"**. Enable **Autostart**.
   - **Samsung (OneUI)**: Settings → Apps → PESU WiFi → Battery → Select **"Unrestricted"**.
   - **OnePlus / Oppo / Realme (ColorOS / OxygenOS)**: Settings → Apps → PESU WiFi → Battery → Enable **"Allow background activity"** and **"Allow auto-launch"**.
4. **⚠️ Private DNS Tip**:
   - If your phone has **Private DNS** enabled (e.g. Cloudflare `1.1.1.1` or AdGuard), Android encrypts all DNS lookups through external public servers. Those public servers cannot resolve PES University's internal login portal (`http://192.168.254.1:8090`).
   - **Fix**: Go to Settings → Network & internet (or Connection & sharing) → **Private DNS** → Set to **Automatic** or **Off** while on campus.

---

## Release History & Changelog

See [**CHANGELOG.md**](CHANGELOG.md) for full commit-by-commit technical breakdowns:
- [**v1.7.1**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.7.1) — Amaatra Hostel 172.16.1.1 gateway resolution, multi-candidate gateway probing with captive redirect discovery, and modern Android (13–16) SSID/BSSID unmasking.
- [**v1.7.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.7.0) — Smart strongest AP scanning & prioritized connection, dynamic dual-gateway probing (EC Campus & Amaatra), 120s &plusmn;30s jittered keepalive daemon, updated earthy launcher icons, and minimalist GitHub Pages landing page.
- [**v1.6.1**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.6.1) — OEM external battery whitelist manual override, first-launch permissions cold-boot prompt, top bar UI declutter, and internal service hardening.
- [**v1.6.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.6.0) — Unified Android system call AP switching (v1.5) with continuous 60s keepalive engine (v1.4), RouterPing ICMP/TCP reachability verification, Android 16 (API 36) compatibility, and 16 KB page-aligned packaging.
- [**v1.5.0**](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.5.0) — Campus AP scale, Cyberoam DoS lockout protection, 1-tap debounced account switching, and resilient auto-reconnect.
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
