# Development

## Release build and verification

Use JDK 17, Android SDK Platform 36, Build Tools 35.0.0, Platform Tools, and NDK 29.0.14206865. Set `JAVA_HOME` and `ANDROID_HOME`. The Gradle wrapper verifies Gradle 9.2.1, and the project uses Android Gradle Plugin 8.13.2. The GitHub Actions workflow installs the pinned command-line tools and SDK packages explicitly.

From the checkout root, run these commands.

```bash
./gradlew clean :app:assembleRelease testReleaseUnitTest lintRelease :termux-shared:assembleReleaseAndroidTest
apk=app/build/outputs/apk/release/app-release-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 "$apk"
python3 tools/verify_apk.py "$apk"
```

The verifier checks package identity, SDK levels, the ABI, the application debug flag, the embedded AArch64 bootstrap, and 16 KiB ELF load alignment. It verifies the signature when the APK is signed. The instrumented library test is a separate artifact signed by the local Android test tooling. Use `./gradlew :termux-shared:connectedReleaseAndroidTest` with a booted ARM64 Android 14+ device.

The app's minimum SDK and native API are 34, its compile SDK is 36, and its target SDK is 28. The target preserves the existing userland's ability to execute files from the app's writable private prefix. The upstream `apt-android-7` name identifies the userland archive that the build verifies and embeds.

## Signing and installation

For a signed Release APK, give Gradle the path to the private keystore and its credentials through `RUSTERMUX_KEYSTORE`, `RUSTERMUX_KEYSTORE_PASSWORD`, `RUSTERMUX_KEY_ALIAS`, and `RUSTERMUX_KEY_PASSWORD`. Supply them through the build environment or a secret store. With all four set, the output is `app/build/outputs/apk/release/app-release.apk`. Without them the output is an unsigned build artifact, which Android requires you to sign before installation.

Keep the same private signer for subsequent updates. APKs historically installed with the public development signer require users to export their data before moving to a separately signed installation. Android assigns a private UID to fresh installs on the supported platform. The legacy shared UID declaration allows Android to retain the UID of an existing compatible, same-signer installation. The application ID and prefix stay `com.termux` and `/data/data/com.termux/files/usr`.

For a device smoke test, install a privately signed APK and open a shell. Run `printf 'rustermux OK\n'`, `apt list --installed`, and `termux-battery-status`. Exercise session tabs, resize the window, reload a TOML setting, and verify clipboard and notification commands. For an in-place upgrade check, create a file and change an appearance setting first, then upgrade with the same signing identity and check the file, packages, and settings.

## Configuration

The app creates `~/.config/rustermux/config.toml` when it is missing. The [bundled defaults](../termux-shared/src/main/assets/rustermux/default-config.toml) list schema version 1 and every supported key. This example uses one of the eight bundled palettes.

```toml
version = 1

[fonts]
family = "jetbrains-mono"

[colors]
palette = "gruvbox-light"

[sessions]
show-tab-bar = true

[shortcuts]
toggle-control = "ctrl+alt+shift+space"
```

The `terminal`, `fonts`, and `colors` tables control the terminal. The `interface` table controls app colors and window presentation. Input and extra keys have separate tables, with `sessions` and `shortcuts` for session controls. The `preferences` table holds diagnostics and integration switches. The `icons` table maps Android interface actions to code points in the bundled Hack Nerd Font Mono. Android notifications use compiled drawable resources.

On first use the app imports supported values from the existing properties, custom appearance files, and app preferences, then reads the TOML file on subsequent loads. It leaves the originals in place. Missing keys inherit bundled defaults. Unknown keys are reported and preserved by Settings edits. An invalid file shows a diagnostic and keeps the last valid snapshot when available. Use a readable TrueType or OpenType file inside the Termux home directory for `fonts.custom-path`.

Run `termux-reload-settings` after editing. Most settings apply in place. A terminal palette change affects terminal colors, while an unrelated setting leaves OSC color changes in running sessions alone. Working directory and transcript size apply to new sessions. Restart the app process after changing `terminal.am-socket`.

## Session controls

Tap a tab to switch and swipe horizontally to reach more sessions. Long-press a tab to rename it, move it, or end the process. An exited tab keeps its transcript until you close it. The empty screen allows you to open a new shell. The service owns the live sessions and selection across Activity recreation. Android process death ends those processes.

**Ctrl+Alt+Shift+Space** enters or leaves session-control mode on a hardware keyboard. The session-control button provides a touch entry point. In that mode, use `n` for a new session, Left and Right to switch, Shift+Left and Shift+Right to reorder, `r` to rename, `x` to terminate, `c` to close an exited session, and `b` to toggle tabs. Escape leaves the mode. To show hidden controls by touch, long-press the terminal and choose **More → Show session tabs**. Ordinary terminal keystrokes reach the PTY while session-control mode is off. The configuration accepts different shortcuts and can disable an individual binding.

## Architecture

| Module | Responsibility |
| --- | --- |
| `app` | Native Activity views, service-owned sessions, staged bootstrap installation, and integrated Android APIs |
| `terminal-emulator` | Terminal parser, screen buffers, Sixel, and the PTY JNI layer |
| `terminal-view` | Canvas rendering, keyboard input, selection, and accessibility |
| `termux-shared` | Configuration, file and process helpers, and local socket and Activity Manager transport |

Layout hierarchies are built in Java. The Android manifests and resource XML carry package components, localized strings, themes, launcher art, notification drawables, and platform shortcuts and declarations. The integrated API uses a private receiver and validates Unix socket peer identities. Connection and response-idle timeouts remain in place. Android manages permission grants and access to protected hardware.

## Upstream sources

| Source | Pinned revision |
| --- | --- |
| [Termux app](https://github.com/termux/termux-app) | `8629e632fcb95da272221be327db653fb24befe9` |
| [Termux:Styling](https://github.com/termux/termux-styling) appearance assets | `79338cb19f4b86cae4d2e81e6de60ba7f613bb9b` |
| [Nerd Fonts v3.5.1](https://github.com/ryanoasis/nerd-fonts) Hack Nerd Font Mono | `b894ea7803af6aade63d60a4381e006098ec9c4d` |
| [Termux:API](https://github.com/termux/termux-api) Android implementation and resources | `fc26ce17e3badf85d4df191af364fcf7798047f1` |
| [Termux API Package v0.60.0](https://github.com/termux/termux-api-package) shell and native client | `9e7f1531e1aa4a9c1e261a2dbde4de93653c366e` |
| [Termux AM Library v2.0.0](https://github.com/termux/termux-am-library) Activity Manager commands | `d979ee308d52c59c6265ed9d9e254c97702ee4fb` |

See the [root license](../LICENSE.md) and the bundled Java, Android API, shell, font, and palette notices.
