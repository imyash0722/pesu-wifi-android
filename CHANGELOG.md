# PESU WiFi for Android — Changelog & Release Notes

A comprehensive, commit-by-commit record of all architectural improvements, background keepalive enhancements, permission systems, and bug fixes for **PESU WiFi for Android**.

---

## Table of Contents
- [v1.5.0 — Event-Driven OS Rules, 5 Campus SSIDs & Pure Standby Architecture](#v150--event-driven-os-rules-5-campus-ssids--pure-standby-architecture)
  - [Overview & Major Highlights](#v150-overview--major-highlights)
- [beta-v1.5.0 — Campus AP Scale & Autonomous BSSID Harvesting](#beta-v150--campus-ap-scale--autonomous-bssid-harvesting)
  - [Overview & Major Highlights](#beta-v150-overview--major-highlights)
- [v1.4.6 — Classroom Wi-Fi Stability, Tailscale Socket Resiliency & UI Polish](#v146--classroom-wi-fi-stability-tailscale-socket-resiliency--ui-polish)
  - [Overview & Major Highlights](#v146-overview--major-highlights)
- [v1.4.5 — Material You Redesign, Dual Flavors (Stable & Tester), BSSID Cataloging & Back Navigation](#v145--material-you-redesign-dual-flavors-stable--tester-bssid-cataloging--back-navigation)
  - [Overview & Major Highlights](#v145-overview--major-highlights)
- [v1.4.1 — Campus AP Auto-Reconnect, WifiNetworkSuggestion & Zero-Latency Fast Re-Auth](#v141--campus-ap-auto-reconnect-wifinetworksuggestion--zero-latency-fast-re-auth)
  - [Overview & Major Highlights](#v141-overview--major-highlights)
- [v1.4.0 — AP Roaming Recovery, Stale Session Auto-Eviction & Universal Diagnostics](#v140--ap-roaming-recovery-stale-session-auto-eviction--universal-diagnostics)
  - [Overview & Major Highlights](#v140-overview--major-highlights)
- [v1.3.0 — Resilient Socket Fallbacks, Non-Interference Mode & Diagnostic Logs](#v130--resilient-socket-fallbacks-non-interference-mode--diagnostic-logs)
  - [Overview & Major Highlights](#v130-overview--major-highlights)
- [v1.2.1 — Portal Login Detection & Network Connectivity Fix](#v121--portal-login-detection--network-connectivity-fix)
  - [Overview & Major Highlights](#v121-overview--major-highlights)
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

## v1.5.1 — Android 12+ Forward Compatibility, 16 KB Page Alignment & Nearby Wi-Fi Devices

**Release Date:** September 19, 2026  
**Git Tag:** [`v1.5.1`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.5.1)  
**Release Type:** Official Release (Stable & Tester Tracks)  
**APK Assets:**  
- `pesu-wifi-v1.5.1-stable.apk` (Production / Stable track without debug logging overhead)  
- `pesu-wifi-v1.5.1-tester.apk` (Tester track with full diagnostic logs, telemetry & AP explorer)  

### v1.5.1 Overview & Major Highlights
- **🚀 Android 13+ `NEARBY_WIFI_DEVICES` Support**: Declared `android.permission.NEARBY_WIFI_DEVICES` with `android:usesPermissionFlags="neverForLocation"`, enabling seamless Wi-Fi suggestion and connectivity handling on Android 13, 14, and 15 without requiring users to grant GPS/Location permissions.
- **🧹 Legacy Location Permission Scoping**: Limited `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` to `maxSdkVersion="32"` (Android 12 and below) and removed intrusive `ACCESS_BACKGROUND_LOCATION`, eliminating security review warnings on modern Android releases.
- **📦 Android 15 16 KB Page Alignment**: Configured AGP native packaging (`jniLibs { useLegacyPackaging = false }`) to ensure uncompressed libraries are aligned to 16 KB boundaries, satisfying Google Play and kernel requirements for Android 15+.
- **🔙 Predictive Back Gesture**: Enabled `android:enableOnBackInvokedCallback="true"` for smooth native gesture animations on Android 13, 14, and 15.

---

## v1.5.0 — Event-Driven OS Rules, 5 Campus SSIDs & Pure Standby Architecture

**Release Date:** September 19, 2026  
**Git Tag:** [`v1.5.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.5.0)  
**Release Type:** Official Release (Stable & Tester Tracks)  
**APK Assets:**  
- `pesu-wifi-v1.5.0-stable.apk` (Production / Stable track without debug logging overhead)  
- `pesu-wifi-v1.5.0-tester.apk` (Tester track with full diagnostic logs, telemetry & AP explorer)  

### v1.5.0 Overview & Major Highlights
- **⚡ Pure Event-Driven OS Rules Architecture**: Dropped the legacy foreground keepalive daemon (`WifiKeepaliveService`) and 60-second periodic CPU wake alarms (`AlarmManager`). The app now relies 100% on native Android OS primitives (`WifiNetworkSuggestion`, `ConnectivityManager` persistent `PendingIntent`, and persistent `JobScheduler`), achieving zero battery consumption while maintaining instant captive portal auto-login.
- **🏫 Comprehensive 5 Campus SSIDs Catalog**: Registered all PES University campus SSIDs across WPA2 passphrase and open profiles:
  - `PESU-EC-Campus`
  - `PESU-CIE`
  - `AMAATRA_HOSTEL`
  - `Foodcourt`
  - `pes south cafe`
- **🔄 BSSID Handover Verification & Network-Bound Router Ping**: When moving between access points (BSSID handover), [`RouterPing`](file:///home/pineapple/pesu-wifi-android/app/src/main/java/com/imyash/pesuwifi/util/RouterPing.kt) pings the router gateway directly over the Wi-Fi network interface socket factory to ensure physical link reachability before authenticating.
- **🛡️ External Wi-Fi Non-Interference Standby**: Connecting to personal hotspots (e.g. `imyk`) or home Wi-Fi automatically switches the app into passive standby mode with zero captive portal requests or interference.
- **🎯 Streamlined Always-On UI**: Removed redundant "Auto-Connect Rules" and "Background Keepalive" toggles. Auto-connect rules are always active at the OS level upon app launch and device boot.

---

## beta-v1.5.0 — Campus AP Scale & Autonomous BSSID Harvesting

**Release Date:** September 17, 2026  
**Git Tag:** [`beta-v1.5.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/beta-v1.5.0)  
**Release Type:** Pre-Release (Beta)  
**APK Assets:**  
- `pesu-wifi-beta-v1.5.0-stable.apk` (Production / Stable track with pre-seeded campus AP catalog)  
- `pesu-wifi-beta-v1.5.0-tester.apk` (Tester track with full diagnostic logs, telemetry & AP explorer)  

### beta-v1.5.0 Overview & Major Highlights
- **📡 Two-Tier Campus AP Database (`BssidDatabase`)**: Implemented a rich two-tier storage system designed to scale across the entire PES University campus (7 floors, 56+ classrooms, seminar halls, labs, food court, and library). Combines a static bundled baseline (`assets/campus_bssids.json`) with an auto-updating persistent local database in `SharedPreferences` and an in-memory `ConcurrentHashMap` for zero-latency lookups.
- **⚡ Layer-1 Zero-Latency Campus Recognition (`isCampusNetwork`)**: Added instantaneous $O(1)$ BSSID matching before roaming fallbacks. The moment the Wi-Fi radio associates with any mapped Cisco router MAC, the network is validated as campus Wi-Fi in 0 ms, even if Android 12 temporarily redacts the network SSID.
- **🔄 Cisco Enterprise Dual-Band Twin Synthesis**: Cisco enterprise APs (Aironet/Catalyst) flip bit 6 (`0x40`) on the 4th octet between 2.4 GHz and 5 GHz radios. Detecting one radio automatically derives and registers its opposite-band twin into the database, doubling mapping speed without extra scans.
- **🚶 Autonomous Background Harvesting**: Removed universal logging build gates from AP harvesting. The background keepalive service automatically captures, deduplicates, and stores all visible `PESU` APs during Wi-Fi scans, connection events, and periodic 60s heartbeats as students walk around campus.
- **🏷️ Interactive AP Explorer, Labeling & JSON Export**: Added a live Campus AP map section inside `LogsScreen`:
  - Live counter: `"Mapped APs: X (view list)"`.
  - Current router telemetry: BSSID, band (2.4G/5G), channel, RSSI, and subnet gateway.
  - Room / Floor Tagging Dialog: lets users assign friendly labels (e.g. `"GJBC 4th Floor Classroom 402"`).
  - 1-Tap Export: invokes Android's system share sheet to export the pretty-printed JSON AP database for community pooling and APK baseline bundling.
- **🛡️ Roaming Stability & Non-Blocking Async Network Calls**: Replaced blocking OkHttp network execution in `PortalApi` with non-blocking coroutine `Call.await()` and added 120-second sticky campus roaming preservation to eliminate transient "External Wi-Fi" notification flashes during classroom-to-classroom handoffs.

---

## v1.4.6 — Classroom Wi-Fi Stability, Tailscale Socket Resiliency & UI Polish

**Release Date:** September 15, 2026  
**Git Tag:** [`v1.4.6`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.6)  
**APK Assets:**  
- `pesu-wifi-v1.4.6-stable.apk` (Production / Stable track — lightweight, minimal background overhead)  
- `pesu-wifi-v1.4.6-tester.apk` (Tester track — diagnostic build with universal logging & permissions management)  

### v1.4.6 Overview & Major Highlights
- **🏫 Subnet & Gateway Heuristics (`isCampusNetwork`)**: Overcomes Android 12+ location privacy and SSID suppression (`null` or `"<unknown ssid>"`) by inspecting `LinkProperties` IPv4 interfaces for PESU campus subnets (`10.0.0.0/8`, `172.16.0.0/12`), gateways (`10.*`, `192.168.254.*`), and campus DNS (`192.168.3.2`). Ensures classroom Wi-Fi is never mistakenly labeled "External Wi-Fi" or "Disconnected".
- **🛡️ Tailscale / VPN Socket Resiliency**: When system VPN services (e.g., Tailscale) trigger an `EPERM` (Operation not permitted) error on Android's native `Network.bindSocket()`, `ResilientSocketFactory` seamlessly falls back to binding directly to the local Wi-Fi network interface IPv4 address (`10.1.*.*`), routing portal keepalive requests cleanly around VPN tunnels.
- **⏱️ DNS & Connect Timeout Hardening**: Implemented strict 3-second call timeouts in `PortalApi` to prevent OkHttp DNS resolution and gateway probes from stalling indefinitely during AP roaming transitions or VPN route changes.
- **🔄 Transient Drop Debouncing**: Separated genuine captive portal redirection signals from transient packet/DNS timeouts. Authentic portal redirects trigger immediate re-authentication, while transient timeout dips require 3 consecutive failed probes before session invalidation, eliminating false "Zombie Session" drops.
- **🔴 High-Contrast Disconnect Button**: Changed the "Disconnect" button color scheme to bold `StatusRed` (`#D32F2F`) with solid white text/icons, completely bypassing Android 12 Dynamic Theming (Material You) pastel error palette washing on Samsung One UI.
- **📱 Clearer Connection Status Chip**: Differentiates between physical Wi-Fi disconnection, external Wi-Fi, gateway unreachable, and active authentication.

---

## v1.4.5 — Material You Redesign, Dual Flavors (Stable & Tester), BSSID Cataloging & Back Navigation

**Release Date:** September 12, 2026  
**Git Tag:** [`v1.4.5`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.5)  
**APK Assets:**  
- `pesu-wifi-v1.4.5-stable.apk` (Production / Stable track — lightweight, BSSID cataloging, no verbose disk logging)  
- `pesu-wifi-v1.4.5-tester.apk` (Tester track — full universal diagnostic logging and permissions section access)  

### v1.4.5 Overview & Major Highlights
- **🎨 Material You Minimalist Redesign:** Redesigned `HomeScreen` with a clean, ambient Material 3 aesthetic conforming to system dynamic color palettes:
  - Centered hero section with a pulsating Wi-Fi emblem and tonal ambient ring during active connection.
  - Minimal status chip with live status indicator ("Authenticated" / "Connected" / "Disconnected").
  - Compact tonal active-account chip with quick chevron navigation to account manager.
  - Prominent full-width pill action button ("Connect" / "Disconnect").
  - Flat, borderless list tile for background keepalive daemon toggle (eliminates heavy card borders).
- **📦 Dual Build Flavors (Stable vs. Tester):** Added Gradle product flavors to provide tailored binaries:
  - **Stable** (`com.imyash.pesuwifi`): Production build with minimal logging overhead, quiet background operation, and no persistent permissions card on Home.
  - **Tester** (`com.imyash.pesuwifi.beta`): Diagnostic build with rotating 5MB file logging (`pesuwifi_universal.log`) and full permissions configuration section. Can be installed side-by-side with Stable.
- **🗺️ Campus AP BSSID Cataloging (`BssidDatabase`):** Lightweight persistent database collecting unique MAC addresses of all campus access points seen in scans and active connections. Powers future offline roaming optimizations without verbose disk logs.
- **⚡ 1-Tap Account Switch & Auto-Reconnect:** In Manage Accounts, tapping any account card immediately switches active credentials and authenticates with that account in one gesture. Removed redundant "Login" text buttons for a cleaner interface.
- **🔙 Android Navigation Gesture & SingleTask Stack Fix:**
  - Configured `android:launchMode="singleTask"` on `MainActivity` to eliminate duplicate Activity stacking when opened via notifications, Quick Settings tile, or launcher.
  - Centralized back navigation with `BackHandler(enabled = currentScreen != Screen.HOME)`, ensuring swiping back from Manage Accounts or Logs returns to Home, while swiping back on Home exits cleanly in a single swipe without showing duplicate Home screens.
- **✨ New App Launcher Icon:** Generated high-resolution adaptive launcher icons across all mipmap densities (`mdpi` through `xxxhdpi`) from new official branding assets.

---

## v1.4.1 — Campus AP Auto-Reconnect, WifiNetworkSuggestion & Zero-Latency Fast Re-Auth

**Release Date:** September 11, 2026  
**Git Tag:** [`v1.4.1`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.1)  
**APK Asset:** `pesu-wifi-v1.4.1.apk` (signed via APK Signature Scheme v2)  

### v1.4.1 Overview & Major Highlights
- **📡 Automatic Hands-Free Campus AP Association (`WifiNetworkSuggestion`):** Solved the issue where moving into the vicinity of an unvisited campus access point caused Wi-Fi to ungracefully disconnect without automatically reconnecting. Implemented `WifiSuggestionManager` registering `PESU-EC-Campus` as an official Android `WifiNetworkSuggestion` with `setIsAppInteractionRequired(false)`, `setIsUserInteractionRequired(false)`, `setIsInitialAutojoinEnabled(true)`, and `priority = 1000`. Android's internal `WifiNetworkSelector` now automatically evaluates and connects to any campus AP in the building hands-free.
- **⚡ Zero-Latency Fast Re-Authentication (<150ms):** Previously, a 1200ms connection debounce allowed Android's captive portal detector to send its HTTP probe before portal authentication completed, causing Android's `WifiBlocklistMonitor` to add the AP BSSID to a 5-minute blocklist (`REASON_NETWORK_VALIDATION_FAILURE`). In `handleNetworkLinkPropertiesChanged`, authentication now triggers immediately upon IPv4 assignment (`10.*`), ensuring internet connectivity is validated before the captive portal check times out.
- **🐕 15-Minute Adaptive Auto-Reconnect Watchdog:** Overhauled `triggerCampusAutoReconnectWatchdog` with an adaptive 3-phase scanning backoff schedule (burst 3s, active 8s, extended 20s up to 15 minutes) with periodic suggestion re-assertion and throttled AP scans (`WifiManager.startScan()`), covering dead zones between classrooms, floors, or buildings.
- **📲 Direct 1-Tap Wi-Fi Settings Notification Action:** When Wi-Fi drops, the persistent foreground notification dynamically presents a direct "Wi-Fi Settings" action that triggers Android's native `Settings.Panel.ACTION_WIFI` bottom sheet on Android 10+ (API 29+) without navigating away from the current foreground task.
- **🔔 Suggestion Post-Connection Broadcast Receiver:** Added broadcast receiver for `ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION` on Android 10+, triggering instant validation upon association with any suggested AP.

---

## v1.4.0 — AP Roaming Recovery, Stale Session Auto-Eviction & Universal Diagnostics

**Release Date:** September 11, 2026  
**Git Tag:** [`v1.4.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.4.0)  
**APK Asset:** `pesu-wifi-v1.4.0.apk` (signed via APK Signature Scheme v2)  

### v1.4.0 Overview & Major Highlights
- **🔄 Seamless Access Point Roaming & Supplicant Recovery:** Resolved the issue where moving between APs (across classrooms, floors, or to the canteen) caused Wi-Fi to disconnect and fail to reconnect automatically. The auto-reconnect watchdog now identifies disabled network configurations in Android's network evaluator and invokes `enableNetwork()` on the campus configuration, commanding wpa_supplicant to immediately scan and associate with the strongest campus AP.
- **🚪 Cyberoam Stale Lease Auto-Eviction:** Resolved the "Maximum Login Limit" trap where Cyberoam retains the session lease on the previous AP for 30–60s after a sudden handoff. When login fails with a limit or session error, `PortalApi` automatically executes an unauthenticated session eviction (`mode=193` / `logout.xml`), waits 600ms, and retries authentication transparently.
- **⏱️ Auto-Expiring Auth Backoff:** Eliminated permanent login pauses. If authentication fails, the service enters a temporary, self-expiring cooldown (30s for server locks, 60s for credential errors) that automatically clears upon physical router (BSSID) handoff or manual retry.
- **🛡️ Campus SSID Resilience & Zombie Session Detection:** When walking between APs, transient unreachability of the `192.168.254.1` gateway no longer causes the daemon to drop locks or enter external standby if still connected to a `PESU*` SSID. Real external route validity is verified via `http://connectivitycheck.gstatic.com/generate_204` probes to detect and purge zombie portal sessions.
- **📡 Complete BSSID, Channel & Frequency Telemetry:** Added `FOREGROUND_SERVICE_LOCATION` permission and `dataSync|location` foreground service type, enabling unredacted capture of AP BSSID, SSID, frequency, channel, RSSI, and link speed on Android 12+ (API 31+).
- **📋 Universal File-Backed Diagnostic Logger:** Upgraded `AppLogger` to write asynchronously to a rotating 5MB log file (`pesuwifi_universal.log`) with rolling backup. Upgraded `LogsScreen` with expandable monospace cards, category counters (Roam, Wi-Fi, Watchdog, Portal, Errors), keyword search, log file sharing via Android `FileProvider`, and one-tap log export.
- **⏰ Android 12+ Doze Exact Alarm Keepalive:** Implemented `KeepaliveAlarmReceiver` with short partial wake locks to bypass Doze mode and prevent `ForegroundServiceStartNotAllowedException` when the device screen is off.

---

## v1.3.0 — Resilient Socket Fallbacks, Non-Interference Mode & Diagnostic Logs

**Release Date:** September 10, 2026  
**Git Tag:** [`v1.3.0`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.3.0)  
**APK Asset:** `pesu-wifi-v1.3.0.apk` (11.39 MiB, signed via APK Signature Scheme v2)  

### v1.3.0 Overview & Major Highlights
- **🛡️ Resilient Socket Fallback (EPERM Resolution):** Resolved kernel-level socket binding error `Binding socket to network failed: EPERM (Operation not permitted)` that occurs when VPNs, Private DNS, or restricted network interfaces are active. Introduced `ResilientSocketFactory` with transparent fallback to default network sockets, and auto-recovery client retries in `PortalApi`. Added `CHANGE_NETWORK_STATE` permission.
- **🔋 Non-Interference Standby Mode:** When connected to non-college Wi-Fi (Home, Mobile Hotspot, Office) or when Wi-Fi is disconnected, the daemon automatically releases continuous `WakeLock` and `WifiLock`, suspends 60s hardware RTC wake alarms, and transitions to a peaceful idle state to eliminate battery drain. The foreground notification updates to "PESU WiFi: Paused".
- **⚡ Seamless Campus Auto-Resume:** When returning to PESU Wi-Fi, `ConnectivityManager.NetworkCallback` detects the campus portal, automatically re-engages keepalive, acquires locks, re-authenticates the active user, and resumes active watchdog monitoring without any manual intervention.
- **📋 In-App Diagnostic Logs & Export:** Added a full-featured Diagnostic Logs screen (`LogsScreen`) accessible via the TopAppBar and StatusCard. Captures timestamped, color-coded logs across API, repository, and keepalive layers. Includes keyword search, filter chips (All, Errors, Warnings, Info), 1-tap clipboard copying, and native Android system sharing (`Intent.ACTION_SEND`).

---

## v1.2.1 — Portal Login Detection & Network Connectivity Fix

**Release Date:** September 9, 2026  
**Git Tag:** [`v1.2.1`](https://github.com/imyash0722/pesu-wifi-android/releases/tag/v1.2.1)  
**APK Asset:** `pesu-wifi-v1.2.1.apk` (11.39 MiB, signed via APK Signature Scheme v2)  

### v1.2.1 Overview & Major Highlights
- **🔒 Fixed Catastrophic Login False-Positive:** Resolved a critical bug where failed logins (`status == "LOGIN"`, invalid credentials, data limit reached, or concurrent session limits) were incorrectly evaluated as successful logins due to non-empty status checks. Aligned logic with the reference Python CLI to strictly require `status == "LIVE"` or messages containing "signed in".
- **📜 Robust CDATA & Multiline XML Parsing:** Upgraded XML parsing to use `XmlPullParserFactory` with native `XmlPullParser.CDSECT` support, preventing CDATA chunks from being truncated or lost. Added entity unescaping for HTML codes such as `&#39;`.
- **🌐 Resilient Gateway & Cleartext Configuration:** Updated `isPortalOnline()` to check `/httpclient.html` across HTTP status codes `200..499`, preventing false "Portal gateway unreachable" reports when proxies intercept HTTP requests. Enabled base cleartext traffic across all campus subnets.
- **🎨 UI Polish & Deprecation Cleanup:** Migrated all Login/Logout icons to `Icons.AutoMirrored` and increased bottom scroll padding to prevent layout clipping on various screen sizes.

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
