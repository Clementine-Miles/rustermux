# rustermux

Rustermux is a native Android terminal and Termux-compatible Linux userland for ARM64 phones and tablets running Android 14 or later. It runs independent PTY sessions and installs a checksum-verified AArch64 bootstrap on first launch. Use `pkg` or `apt` to install shell tools.

Tap the session tabs to switch shells or long-press a tab for session actions. **Ctrl+Alt+Shift+Space** enters session-control mode by default. Shells keep running when the Activity goes into the background. The built-in Android API commands provide features such as clipboard access, notifications, and battery status.

Edit `~/.config/rustermux/config.toml` to configure fonts, Gruvbox palettes, extra keys, and shortcuts. Run `termux-reload-settings` to apply live settings. The first load can import supported legacy settings and leaves the original files in place.

Build with JDK 17 and the pinned Android SDK using `./gradlew :app:assembleRelease`. Distributable APKs require a private release signer. An in-place update requires a matching signing identity and compatible version. The package remains `com.termux`, and the existing home and prefix paths remain in use.

[Development, configuration, and signing](docs/development.md) · [Licenses](LICENSE.md)
