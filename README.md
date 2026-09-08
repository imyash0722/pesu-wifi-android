# PESU WiFi for Android

Native Android companion app for PESU captive portal login, session management, and continuous keepalive. Replicates the core logic of the `pesu-wifi` CLI with a clean Material 3 interface and background Foreground Service.

---

## Features

- **Automated Login & Logout**: 1-tap sign-in and sign-out against the Cyberoam gateway (`http://192.168.254.1:8090`).
- **Robust Background Keepalive**: Runs an Android Foreground Service polling `/live` every 60s and automatically re-authenticating if the session expires.
- **Quick Settings Tile**: Add the **PESU WiFi** tile to your Android pull-down notification shade for instant 1-tap login/logout without opening the app.
- **Hardware-Backed Credential Storage**: All usernames and passwords are encrypted using Android Jetpack `EncryptedSharedPreferences` (AES-256-GCM + AES-256-SIV).
- **Multi-Account Switching**: Store multiple student/faculty accounts and easily switch active users with a single tap.
- **Desktop JSON Import/Export**: Directly paste or export `config.json` compatible with the desktop `pesu-wifi` CLI.
- **Location-Free Network Detection**: Automatically senses campus Wi-Fi connection using non-intrusive gateway probing rather than demanding sensitive GPS/Location permissions.
- **Cleartext HTTP Allowlist**: Specifically allows local HTTP traffic to `192.168.254.1` while preserving platform-wide HTTPS enforcement.

---

## App Architecture

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose + Material 3
- **Network Engine**: OkHttp 4.12 (strict timeouts, fresh TCP connection handling to prevent proxy hangs)
- **Security**: AndroidX Security Crypto (`MasterKey` AES-256)
- **Background Engine**: Android Foreground Service (`dataSync` type, Android 14 compliant)
- **Quick Settings**: Android `TileService`

---

## Opening in Android Studio

1. Open Android Studio (`/opt/android-studio/bin/studio.sh`).
2. Select **Open** and choose `/mnt/shared/stuff/projects/pesu-wifi-android`.
3. Let Gradle sync and press **Run** on your connected phone or emulator.

---

## Building from CLI

To build using the bundled Gradle wrapper (requires Java 17 and Android SDK):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew assembleDebug
```

The compiled APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

To install on a connected Android phone:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Setup on Device

1. Install the APK and launch **PESU WiFi**.
2. Grant Notification permission when prompted (required for the background keepalive service).
3. Tap **+ Add** and enter your PESU credentials.
4. When on campus Wi-Fi, tap **CONNECT (LOGIN)**.
5. Turn on **Background Keepalive** to maintain your session indefinitely.
6. (Optional) Edit Quick Settings tiles on your phone and drag **PESU WiFi** into your active tiles.
