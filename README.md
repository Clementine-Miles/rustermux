# rustermux

Rustermux is a standalone Android terminal and Linux userland for **64-bit ARM devices running Android 7 or later**. It uses native Android views for the interface and runs each terminal session through a PTY. The first launch installs a verified AArch64 Termux bootstrap. Use `pkg` or `apt` to install additional packages.

- Open and switch between independent shell sessions, use extra keys or a hardware keyboard, and copy and paste from the terminal.
- Choose from Gruvbox and Gruvbox Material palettes, Hack Nerd Font Mono and JetBrains Mono, or your existing terminal appearance files.
- Call built-in Android APIs from the shell with commands such as `termux-battery-status`, `termux-clipboard-get`, and `termux-notification`. Android prompts when a command requires permission.

## Build and install

Use JDK 17 and the Android SDK packages listed in the [development guide](docs/development.md). From the checkout root, run `./gradlew :app:assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk` on an ARM64 device. You can also run `adb install -r app/build/outputs/apk/debug/app-debug.apk` with a device connected.

Rustermux keeps the package ID `com.termux` and the existing home and prefix paths. An in-place upgrade requires the same signing identity and a compatible installed version. The repository's debug signer is public and intended for development builds. Uninstalling an existing app removes its private data.

## Credits

Rustermux is based on Termux and includes code from Terminal Emulator for Android plus integrated code from Termux:API. See the project and asset notices for the upstream revisions, licenses, and exceptions. [License](LICENSE.md) · [Development guide](docs/development.md)
