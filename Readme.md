<div align="center">


<img src="https://github.com/user-attachments/assets/1394d3d6-7cdb-4f4f-89ad-7c03ff894515" width="120" height="120" alt="Rixnar App Icon" style="border-radius: 24px;"/>

<br/>
<br/>

<img src="https://img.shields.io/badge/Platform-Android%2011%2B-brightgreen?style=for-the-badge&logo=android&logoColor=white"/>
<img src="https://img.shields.io/badge/Language-Java%20%2F%20C%2B%2B-orange?style=for-the-badge&logo=c%2B%2B&logoColor=white"/>
<img src="https://img.shields.io/badge/Architecture-Native%20JNI-blue?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-GPL%20v3-informational?style=for-the-badge&logo=gnu&logoColor=white"/>
<img src="https://img.shields.io/badge/Distribution-Sideload%20Only-yellow?style=for-the-badge&logo=android&logoColor=white"/>

# RIXNAR

### *You set the lock. You hide the key. You win.*

A hardened, self-locking Android focus enforcer built with a C++ native engine, AccessibilityService instant-kill blocking, and a credit-based unlock economy. Designed to be deliberately difficult to bypass — including by yourself.

---

</div>

## What Is This

Rixnar is a focus enforcement app for Android that makes distraction physically expensive instead of just inconvenient. It doesn't ask you to be disciplined. It removes the option.

Browsers are **permanently blocked** — no unlock, no credits, no way in. Social media, games, and streaming apps are **credit-gated** — you get 100 credits per day, and 30 minutes of access costs 50. When you're out, you're out until midnight.

The app registers itself as a **Device Administrator**. Uninstalling requires your 30-character master key. The key is shown exactly once during onboarding and never again.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Android UI Layer                  │
│  MainActivity · DashboardFragment · StatsFrag · etc │
└────────────────────────┬────────────────────────────┘
                         │ JNI Bridge (librixnar.so)
┌────────────────────────▼─────────────────────────────┐
│              C++ Native Engine (native-lib.cpp)      │
│  • BROWSERS[]  — permanent hard block list           │
│  • CREDIT_GATED[] — 50-credit / 30-min tier          │
│  • checkAppStatus() → 0 (allow) / 1 (gate) / 2 (kill)│
└────────────────────────┬─────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
┌─────────▼──────┐ ┌─────▼─────┐ ┌──────▼───────────────┐
│ Accessibility  │ │  Blocker  │ │   SharedPreferences  │
│ Service        │ │  Service  │ │   + PersistenceHelper│
│ (instant kill) │ │ (150ms    │ │   (JSON on-disk      │
│                │ │  polling) │ │    backup layer)     │
└────────────────┘ └───────────┘ └──────────────────────┘
```

**Two parallel enforcement layers** — the AccessibilityService fires instantly on window change events, the BlockerService polls every 150ms as a fallback. A browser cannot slip through both.

---

## Features

### Blocking Engine
- **54+ browsers blocked at the C++ level** — Chrome, Firefox, Brave, Edge, Tor, Vivaldi, Opera, DuckDuckGo, Samsung Browser, UC Browser, Via, Yandex, every F-Droid open-source browser, and more
- **60+ social/game/streaming apps credit-gated** — Instagram, YouTube, TikTok, WhatsApp, Telegram, Discord, Netflix, Spotify, PUBG, Roblox, and more
- **Custom app lists** — add any package name to either tier from the UI; changes take effect immediately
- **Extra browser adds go to hard block** — no way to accidentally put a browser in the soft tier

### Credit Economy
- **100 credits per day**, resets at midnight via both a scheduled AlarmManager and an elapsed-realtime clock integrity check (resistant to manual clock manipulation)
- **50 credits = 30 minutes** of access to the social tier
- Credits and unlock state stored in SharedPreferences and mirrored to an on-disk JSON file for crash resilience

### Security Layer
- **Device Admin registration** — prevents uninstall without the master key
- **30-character master key** generated with `SecureRandom` from `[0-9 a-z A-Z !"#]` during onboarding
- **SHA-256 hashed** — the plaintext key is never stored, only the hash
- **Key shown once as text + QR code** during onboarding, never displayed again
- **Onboarding verification step** — user must type the key back correctly before the app activates
- **Rate-limited revoke attempts** — 3 failures triggers a 60-second cooldown

### Statistics & Tracking
- **Calendar heat map** — green for clean days, red for slips, dim for future, pulsing ring for today
- **Slip meter** — circular arc visualization of your net performance ratio for the year
- **Streak tracking** — current streak and last streak calculated daily
- **Immutable slip log** — once a day is marked as a slip, it cannot be unmarked. Ever.
- **Custom date picker** — spinner-based month/year selector clamped to Jan 1 → today

### Persistence & Backup
- **Dual persistence** — SharedPreferences + internal JSON file (`relentless_state.json`)
- **Signed backup export** — GZIP-compressed, SHA-256 checksummed backup string
- **Import/export via system file picker** — share to any storage location
- **7-day backup reminder** via WorkManager notification

### Service Reliability
- **Foreground service** with persistent notification keeps the engine alive
- **WorkManager watchdog** pings the service every 15 minutes
- **BootReceiver** restarts the service after device reboot
- **onTaskRemoved** schedules an AlarmManager restart if the service is swiped from recents
- **Crash handler** on both the main and service processes with graceful exit

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Java, XML layouts, Material Components 3 |
| Navigation | DrawerLayout + BottomNavigationView + Fragments |
| Native engine | C++17 via Android NDK / CMake |
| JNI bridge | Custom JNI functions per-class |
| Blocking | AccessibilityService + UsageStatsManager |
| Security | SHA-256 (MessageDigest), SecureRandom, DevicePolicyManager |
| Persistence | SharedPreferences + JSON file (org.json) |
| Background work | WorkManager (PeriodicWorkRequest) |
| QR generation | ZXing |
| Calendar UI | FlexboxLayout |
| Build | Gradle, NDK 28.x, CMake 3.22 |

---

## Project Structure

```
app/src/main/
├── cpp/
│   └── native-lib.cpp          # C++ engine — block lists, credit logic, JNI exports
├── java/com/android/neverGiveUp/
│   ├── MainActivity.java        # Root activity, drawer, nav, permission flow
│   ├── OnboardingActivity.java  # First-run key generation + verification
│   ├── BlockerService.java      # Foreground service, 150ms polling loop
│   ├── RelentlessAccessibility.java # AccessibilityService instant-kill layer
│   ├── RelentlessAdminReceiver.java # Device admin callbacks
│   ├── CreditGateActivity.java  # Fullscreen credit spend overlay
│   ├── DashboardFragment.java   # Credits, timer, streak metrics
│   ├── LockedAppsFragment.java  # App list previews + bottom sheet editor
│   ├── StatisticsFragment.java  # Calendar, slip meter, date picker
│   ├── SettingsFragment.java    # Theme, backup reminders, revoke admin
│   ├── AppListBottomSheetDialog.java # Add/remove apps from block lists
│   ├── AppListAdapter.java      # RecyclerView adapter for app rows
│   ├── AppCatalog.java          # Static mirrors of C++ block lists for UI
│   ├── AppLabelUtil.java        # Package → display name resolution
│   ├── PersistenceHelper.java   # SharedPreferences ↔ JSON file sync
│   ├── BackupManager.java       # Export/import with GZIP + checksum
│   ├── BackupReminderWorker.java # 7-day backup notification
│   ├── ServiceWatchdogWorker.java # 15-min service health check
│   ├── SlipMeterView.java       # Custom circular arc view
│   ├── BootReceiver.java        # Auto-start on device boot
│   └── RestartReceiver.java     # AlarmManager-triggered service restart
└── res/
    ├── layout/                  # All XML layouts
    ├── drawable/                # Day circle drawables, card backgrounds
    └── xml/                     # Accessibility config, device admin policy
```

---

## Permissions

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is in the foreground (UsageStatsManager) |
| `BIND_ACCESSIBILITY_SERVICE` | Instant window-change blocking via AccessibilityService |
| `BIND_DEVICE_ADMIN` | Prevent uninstall without master key |
| `FOREGROUND_SERVICE` | Keep the blocking engine alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service type requirement |
| `RECEIVE_BOOT_COMPLETED` | Restart after device reboot |
| `POST_NOTIFICATIONS` | Foreground service notification + backup reminders |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the service |

---

## Build Instructions

### Prerequisites
- Android Studio Ladybug / Panda or later
- NDK 28.x (Side by side) — install via SDK Manager → SDK Tools
- CMake 3.22.1 — install via SDK Manager → SDK Tools
- Min SDK: API 30 (Android 11)
- Target SDK: API 36 (Android 16)

### Steps

```bash
# Clone the repo
git clone https://github.com/yourusername/rixnar.git
cd rixnar

# Open in Android Studio
# File → Open → select the /Relentless folder

# Sync Gradle
# Android Studio will prompt — click "Sync Now"

# Build APK
# Build → Generate App Bundles or APKs → Generate APKs

# Install on device (with USB debugging enabled)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First Run
1. Grant **Usage Access** when prompted (Settings → Apps → Special access → Usage access → Rixnar → ON)
2. Grant **Accessibility Service** (Settings → Accessibility → Rixnar → ON)
3. Activate **Device Admin** on the permission screen
4. **Write down your master key** — it is shown exactly once
5. Verify by typing the key back in the confirmation field
6. The app closes. The engine is now running silently in the background.

---

## How the Credit System Works

```
Day starts          You open Instagram      Credits check
    │                       │                    │
[100 credits]    ──→  [CreditGate screen]  ──→  [≥50 credits?]
                                                  │        │
                                                YES        NO
                                                 │         │
                                        [-50 credits]   [Locked until
                                        [30 min timer]   midnight]
                                                 │
                                        [Open Instagram]
                                                 │
                                        [Timer expires]
                                                 │
                                        [Blocked again]
```

Two unlocks per day maximum. Zero unlocks if you start the day already low from yesterday's remaining balance being reset. The math is intentionally punishing.

---

## How the Blocking Works

```
You tap Chrome
      │
      ▼
AccessibilityService fires (TYPE_WINDOW_STATE_CHANGED)
      │
      ▼
checkAppStatus("com.android.chrome")  ──→  C++ checks BROWSERS[]
      │
      ▼
Returns 2 (HARD BLOCK)
      │
      ▼
goHome() × 2  ──→  You're back on the home screen
      │
      ▼
BlockerService polling also fires within 150ms as second layer
```

Chrome never fully renders. The AccessibilityService fires before the first frame is drawn.

---

## Statistics Tracking

Rixnar tracks performance as a **net ratio against the full year window**:

```
Score = (wins − slips) / total_days_in_year × 100%
```

- **Green arc, positive %** — more clean days than slips relative to the year
- **Red arc, negative %** — slips outweighing clean days
- **Slips are immutable** — once committed, they cannot be deleted or changed

The calendar shows:
- 🟢 **Green circle** — clean past day
- 🔴 **Red circle** — slip day (locked permanently)
- 🔵 **Pulsing green ring** — today (still ongoing)
- ⬛ **Dark** — future day (not yet unlocked)

---

## Security Notes

The master key is stored only as a SHA-256 hash. If you lose it, **there is no recovery path**. You would need to factory reset or use ADB to uninstall with USB debugging:

```bash
adb shell pm uninstall com.android.neverGiveUp
```

This is intentional. The difficulty is the point.

---

## Known Limitations

- **Force Stop** from Settings disables all blocking until the app is manually reopened. Android does not allow any app to survive a force stop — this is a system-level restriction.
- **Some aggressive ROMs** (MIUI, One UI with aggressive battery optimization) may kill the foreground service faster than others. Grant battery optimization exemption when prompted.
- The UsageStatsManager polling has a small lag window (~150ms) where a banned app could briefly render before being killed. The AccessibilityService layer closes this gap for most apps.
- Credit state lives in memory (C++) and is mirrored to SharedPreferences. A service restart reloads from SharedPreferences correctly, but a clean process kill mid-transaction could theoretically result in a credit discrepancy of one unlock.

---

## Roadmap

- [ ] Fix calendar day touch target (currently on `rootItem`, should be on `tvDayNumber`)
- [ ] Fix `extra_social` key in `PersistenceHelper.SET_KEYS` (currently stored as `credit_gated_apps`)
- [ ] Lock admin settings screen only when `lock_end_date` is active, not always
- [ ] Statistics tab: block count history per app
- [ ] Widget showing today's credit balance
- [ ] Custom credit cost configuration per app tier
- [ ] Export statistics as CSV

---

## Why "Rixnar"

No particular meaning. It sounds like something that doesn't open doors for you.

---

## ⚠️ Important Disclaimer

<div align="center">

> **Rixnar is not available on the Google Play Store and will never be submitted to it.**
> The permissions it requires — Device Administrator, Accessibility Service, and Usage Stats — are intentionally powerful and would not pass Play Store review. This is by design.

</div>

### Play Protect Warning

Because Rixnar is distributed as a sideloaded APK outside of the Google Play Store, **Google Play Protect will flag it as an unverified app** when you install it. This is expected and normal behavior for any app not distributed through the Play Store.

You will likely see one or both of the following prompts:

- *"App installed from unknown source"*
- *"Play Protect can't verify this app"*

**This does not mean the app is malicious.** It means Google has not reviewed it. The source code is fully open and auditable in this repository. You are encouraged to review it, build it yourself, and verify what it does before installing.

To install Rixnar you must enable sideloading on your device:

Android 11+:  You may additionally need to confirm "Restricted Settings" for
              Accessibility and Device Admin permissions after installation.
```

### Restricted Settings (Android 13+)

On Android 13 and later, apps installed outside of the Play Store are subject to **Restricted Settings**. This means that after installing the APK, certain permissions will appear greyed out or blocked by default. You will need to explicitly allow them:

1. Go to **Settings → Apps → Rixnar**
2. Tap the **⋮ menu** (three dots) in the top-right corner
3. Select **"Allow restricted settings"**
4. Confirm — then return to grant Accessibility and Device Admin permissions normally

This is an Android security measure. It does not affect the app's functionality once permissions are granted.

### What Rixnar Actually Does With These Permissions

Rixnar requests powerful permissions because its entire purpose is enforcement — not surveillance. Here is a precise account of what each permission is used for:

| Permission | What Rixnar Uses It For | What Rixnar Does NOT Do |
|---|---|---|
| **Accessibility Service** | Detects when a blocked app opens and immediately redirects to the home screen | Does not read screen content, text input, passwords, or messages |
| **Usage Stats** | Checks which app is currently in the foreground every 150ms | Does not log browsing history, usage patterns, or send any data anywhere |
| **Device Admin** | Prevents the app from being uninstalled without the master key | Does not wipe your device, lock your screen, or enforce password policies |

**No data leaves your device. No analytics. No telemetry. No servers. No accounts.**

Everything Rixnar stores lives in a local JSON file at `files/relentless_state.json` on your device's internal storage. Nothing is transmitted externally.

---

## License

Copyright © 2025 Abhinav. All rights reserved.

This project is licensed under the **GNU General Public License v3.0**.

```
Rixnar — A hardened Android focus enforcement application
Copyright (C) 2025 Abhinav

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

### What This Means in Plain Terms

| You Can | You Cannot |
|---|---|
| ✅ Use the source code freely for personal projects | ❌ Distribute modified versions under a proprietary or closed license |
| ✅ Study, modify, and build upon this codebase | ❌ Remove copyright notices or misrepresent authorship |
| ✅ Redistribute the source code or compiled APK | ❌ Sublicense or sell this software without releasing your modifications under GPL v3 |
| ✅ Fork this repository and make it your own | ❌ Use any part of this codebase in a closed-source commercial product |

The GPL v3 license ensures this software and all derivative works remain permanently open source. If you build something with Rixnar's code, your code must also be open source under the same license.

See the full license text at [https://www.gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html).

---

<div align="center">

**Built to be used against yourself.**

*If you're reading this instead of working, you know what to do.*

<br/>

<sub>Copyright © 2025 Abhinav · GPL v3 · Not affiliated with Google, Android, or any blocked application.</sub>

</div>
