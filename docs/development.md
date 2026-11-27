# Development

## Build and checks

Install JDK 17, Android SDK Platform 36, Build Tools 35.0.0, Platform Tools, and NDK 29.0.14206865. Set `JAVA_HOME` and `ANDROID_HOME`. The Gradle wrapper verifies Gradle 9.2.1, and the project pins Android Gradle Plugin 8.13.2. Dependencies resolve from Google Maven and Maven Central. Gradle verifies the AArch64 Termux bootstrap against its pinned SHA-256 before embedding it in the APK.

Run these commands from the checkout root.

```bash
./gradlew :app:assembleDebug test lintDebug :termux-shared:assembleDebugAndroidTest
apk=app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$apk"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 "$apk"
python3 tools/verify_apk.py "$apk"
```

`./gradlew :app:assembleRelease` produces `app/build/outputs/apk/release/app-release-unsigned.apk` for release signing.

Run `./gradlew :termux-shared:connectedDebugAndroidTest` with an ARM64 Android device connected. The JVM tests run on the development host and the APK check verifies the ELF architecture, segment alignment, and embedded bootstrap. A device is needed to verify installation, terminal input and resizing, session lifecycle, storage integration, and runtime Android permissions.

For an application smoke test, install the APK and open the terminal. Confirm a fresh installation reaches a shell, then run `printf 'rustermux OK\n'`, `apt list --installed`, and `termux-battery-status`. Open another session and switch back. Try clipboard and notification commands, including a notification with an unknown icon name. To verify an upgrade, create a file in the first session, install the new APK in place with the same signer, and check the file, installed packages, and appearance settings.

## Architecture

| Module | Responsibility |
| --- | --- |
| `app` | Activity and service lifecycle, session settings, bootstrap installation, and the integrated Android API dispatcher and command client |
| `terminal-emulator` | Terminal parsing, screen buffers, Sixel, and PTY JNI |
| `terminal-view` | Android View, Canvas rendering, keyboard input, and selection |
| `termux-shared` | File and process helpers, application settings, and the local socket and Activity Manager transport |

The shell environment lives in the application data directory under `files/home` and `files/usr`. A fresh bootstrap stages files before moving them into the final prefix. Existing environments keep their own package databases, configuration, and installed packages. The package ID remains `com.termux` to match paths used by Termux packages.

Android preserves the shared UID for existing installations during a same-signer upgrade. New Android 13 and later installations use an app-specific UID. Older platforms still use the legacy shared UID, where Android can permit same-signer packages assigned to that UID to share access.

The integrated Android API starts a private receiver through the existing Activity Manager bridge. The native client and Android process verify peer UIDs and enforce connection and response-idle deadlines. Set `TERMUX_API_TIMEOUT` in seconds to adjust the response timeout. Android controls runtime grants and access to protected hardware and data.

## Upstream sources

| Source | Pinned revision |
| --- | --- |
| [Termux app](https://github.com/termux/termux-app) | `8629e632fcb95da272221be327db653fb24befe9` |
| [Termux:Styling](https://github.com/termux/termux-styling) appearance assets | `79338cb19f4b86cae4d2e81e6de60ba7f613bb9b` |
| [Nerd Fonts v3.5.1](https://github.com/ryanoasis/nerd-fonts) Hack Nerd Font Mono | `b894ea7803af6aade63d60a4381e006098ec9c4d` |
| [Termux:API](https://github.com/termux/termux-api) Android implementation and resources | `fc26ce17e3badf85d4df191af364fcf7798047f1` |
| [Termux API Package v0.60.0](https://github.com/termux/termux-api-package) shell and native client | `9e7f1531e1aa4a9c1e261a2dbde4de93653c366e` |
| [Termux AM Library v2.0.0](https://github.com/termux/termux-am-library) Activity Manager commands | `d979ee308d52c59c6265ed9d9e254c97702ee4fb` |

See the [root license](../LICENSE.md) and bundled notices for the Java libraries, Android APIs, shell wrappers, fonts, and palettes.
