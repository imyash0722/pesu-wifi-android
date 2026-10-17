# Website mockups

ADB captures from device `68201JEA308127`, package `com.imyash.pesuwifi`, captured 2026-10-17:

- `home-screen.png`: real device capture showing authenticated active campus session with latency and high-contrast controls, used on the landing page.
- `accounts-screen.png`: real account manager, retained as a reference asset.
- `step-account.png`: real empty Add Account dialog, used in setup Step 5.
- `step-battery.png`: real app-specific battery usage settings, retained as a reference asset. The guide uses the SVG because it illustrates both unrestricted battery and exact alarms.

Capture command: `adb -s 68201JEA308127 exec-out screencap -p > <file>`.

The logs screen was captured for inspection but excluded from website assets because it exposes a network identifier. `logs-screen.svg` remains the illustrative fallback. Installation, notification, AP suggestion, and combined battery/alarm illustrations remain SVGs; no permissions, accounts, or connectivity settings were changed to recreate those states. SVGs are illustrations, not device captures.

All existing illustrations remain available. Their colors now match the site's charcoal/mint palette.
