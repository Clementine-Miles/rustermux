# Build and verify rustermux

Install a JDK 17 and the Android SDK. In SDK Manager, install Android SDK Platform 36, Build Tools 35.0.0, Platform Tools, and NDK 29.0.14206865. Set `JAVA_HOME` and `ANDROID_HOME`, or configure the SDK location in Android Studio. The Gradle wrapper pins Gradle 9.2.1 and the project pins Android Gradle Plugin 8.13.2.

From the repository root:

```bash
TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0 ./gradlew :app:assembleDebug
./gradlew test :app:lintDebug
./gradlew :termux-shared:connectedDebugAndroidTest
```

The instrumented test requires a booted Android device or emulator. The universal debug APK is `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk`. It contains the Android 7+ bootstrap for all four supported ABIs. Gradle verifies the published bootstrap archives against the hashes in `app/build.gradle`. The wrapper uses Google's Maven repository, Maven Central, and JitPack. In the configured Codex environment, a local Gradle init script can select the Apache-2.0 Termux AM library built from tag `v2.0.0`, commit `d979ee308d52c59c6265ed9d9e254c97702ee4fb`, when JitPack is unavailable.

The APK uses application ID `com.termux`, shared UID configuration, and the existing upstream debug keystore. Android requires a matching signer for an in-place update. The release signing configuration and target SDK 28 follow the fork's upstream baseline.

## Architecture and upstream sources

| Source | Pinned commit | Used here |
| --- | --- | --- |
| Termux app | `8629e632fcb95da272221be327db653fb24befe9` | Android app, terminal libraries, shell and package bootstrap |
| Termux:Styling | `79338cb19f4b86cae4d2e81e6de60ba7f613bb9b` | Eight Gruvbox palettes and JetBrains Mono 1.0.3 |
| Nerd Fonts v3.5.1 | `b894ea7803af6aade63d60a4381e006098ec9c4d` | Hack Nerd Font Mono Regular |
| Termux:API | `fc26ce17e3badf85d4df191af364fcf7798047f1` | Android API implementations and resources |
| Termux API Package v0.60.0 | `9e7f1531e1aa4a9c1e261a2dbde4de93653c366e` | CLI wrappers, socket client protocol, USB callback |

Font checksums and upstream licenses ship under `assets/appearance/licenses`. The Android implementation's GPLv3 notice and the CLI package's MIT notice ship under `assets/termux-api`.

**Appearance.** The main app stores selections in its preferences and applies them through the existing terminal color scheme and font renderer. It defaults to Gruvbox Material Dark Medium and Hack Nerd Font Mono. Existing custom files remain authoritative until a bundled option is explicitly selected. The context menu applies changes to running sessions. `termux-reload-settings` remains available for shell-side configuration changes.

**Termux:API.** The main process initializes the API dispatcher. The APK installs 57 shell wrappers and a USB callback into its private app-owned command directory. Normal Termux sessions put that directory on `PATH`. The pinned NDK builds an executable for each ABI, which Android extracts into the APK native-library directory. The existing Termux activity-manager socket bridge sends typed arguments to a private receiver. The upstream direct `am` command is the fallback when that bridge is disabled or the wrapper cannot be executed. Android can restrict direct `am` from some application shells. Two Unix sockets carry stdin, stdout, and USB file descriptors. Both ends check socket peer UID. Commands surface connection and response-idle timeouts. `TERMUX_API_TIMEOUT` accepts seconds, with a default of 120 and an explicit 0 for unbounded runs. The integrated API supports Android 7 and later, matching the Android API module's upstream minimum.

The companion launcher, settings, and package discovery were removed. The built-in share provider uses its own nonexported authority and grants read access per URI. API notifications use CLI IDs as explicit tags and prefix channel IDs, keeping script operations separate from the main app's service notification. Biometric, DocumentFile, and AndroidX Media support biometric prompts, Storage Access Framework operations, and media notifications.

Android permissions for location and Wi-Fi, contacts and telephony, camera and microphone, sensors and nearby hardware, notifications, wallpaper, and system settings support the corresponding commands. Android controls runtime grants and special access. Notification-list access uses the system listener settings. Ordinary app access applies to device identifiers, including platform restrictions on newer versions.

## Validation

- Gradle passed all 302 JVM test executions across Debug and Release, plus one Android instrumentation test. Debug lint reported zero errors and 1,009 warnings. Most warnings mark the notification icon catalogue as unused because the API resolves icon names at runtime.
- The Android 28 x86_64 software emulator exercised bootstrap and Bash PTY startup, an APT dry run, background session resumption, live palette and rendered-font changes, selection persistence across app updates, and custom palette reload while keeping the same shell PID. From the terminal it also verified battery status, UTF-8 clipboard input and output, notification display and removal, sensors, absent infrared hardware, unknown methods, permission prompts, and a response timeout. Android skipped an invocation sent from the external shell UID.
- Native transport checks exercised typed arguments, binary streams, both USB descriptor callback forms, failed dispatch and timeouts. The four-ABI APK packaging checks are independent of physical device support. Physical Android devices, biometric and other real hardware, and individual API behavior across all Android releases were outside this verification.

The fork contains no X11 module, X server, or enabled X11 repository. The login flow uses the existing `~/.termux/motd.sh` hook for a one-time default that filters the optional X11 install line. Existing custom greeting files and hush-login settings are respected. The checksum-verified bootstrap and apt-owned greeting remain intact, including inactive entries in the general mirror catalogue. The default apt source remains the main repository. The terminal continues to support `xterm-256color`.
