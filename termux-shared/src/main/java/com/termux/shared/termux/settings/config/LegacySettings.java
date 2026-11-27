package com.termux.shared.termux.settings.config;

import com.termux.terminal.TerminalColors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/** Reads legacy values only when config.toml is absent. Never writes a legacy source. */
final class LegacySettings {
    final Map<ConfigKeys.Key<?>, Object> values = new LinkedHashMap<>();
    private final List<String> skipped = new ArrayList<>();

    static LegacySettings read(Path home, Map<String, ?> preferences) {
        LegacySettings migration = new LegacySettings();
        Path primary = home.resolve(".termux/termux.properties");
        Path secondary = home.resolve(".config/termux/termux.properties");
        Properties properties = migration.properties(Files.isRegularFile(primary) ? primary : secondary);
        migration.properties(properties);
        migration.preferences(home, preferences);
        return migration;
    }

    private Properties properties(Path file) {
        Properties result = new Properties();
        if (Files.isRegularFile(file)) {
            try {
                result.load(new StringReader(TermuxConfig.readFile(file.toRealPath())));
            } catch (IOException | IllegalArgumentException e) {
                skipped.add(file.getFileName().toString());
            }
        }
        return result;
    }

    private void properties(Properties source) {
        copy(source, "bell-character", ConfigKeys.BELL, LegacySettings::lower);
        copy(source, "terminal-cursor-style", ConfigKeys.CURSOR_STYLE, LegacySettings::lower);
        copy(source, "terminal-cursor-blink-rate", ConfigKeys.CURSOR_BLINK, Integer::parseInt);
        copy(source, "terminal-transcript-rows", ConfigKeys.TRANSCRIPT_ROWS, Integer::parseInt);
        copy(source, "terminal-margin-horizontal", ConfigKeys.MARGIN_HORIZONTAL, Integer::parseInt);
        copy(source, "terminal-margin-vertical", ConfigKeys.MARGIN_VERTICAL, Integer::parseInt);
        copy(source, "default-working-directory", ConfigKeys.WORKING_DIRECTORY, value -> value);
        copy(source, "delete-tmpdir-files-older-than-x-days-on-exit", ConfigKeys.TMP_CLEANUP_DAYS, Integer::parseInt);
        copy(source, "terminal-onclick-url-open", ConfigKeys.OPEN_LINK_ON_TAP, LegacySettings::bool);
        copy(source, "run-termux-am-socket-server", ConfigKeys.AM_SOCKET, LegacySettings::bool);
        copy(source, "hide-soft-keyboard-on-startup", ConfigKeys.HIDE_SOFT_KEYBOARD_ON_START, LegacySettings::bool);
        copy(source, "soft-keyboard-toggle-behaviour", ConfigKeys.KEYBOARD_TOGGLE, LegacySettings::lower);
        copy(source, "back-key", ConfigKeys.BACK_KEY, LegacySettings::lower);
        copy(source, "volume-keys", ConfigKeys.VOLUME_KEYS, LegacySettings::lower);
        copy(source, "enforce-char-based-input", ConfigKeys.CHAR_BASED_INPUT, LegacySettings::bool);
        copy(source, "ctrl-space-workaround", ConfigKeys.CTRL_SPACE_WORKAROUND, LegacySettings::bool);
        copy(source, "extra-keys-text-all-caps", ConfigKeys.EXTRA_KEYS_ALL_CAPS, LegacySettings::bool);
        copy(source, "extra-keys-style", ConfigKeys.EXTRA_KEYS_STYLE, LegacySettings::lower);
        copy(source, "terminal-toolbar-height", ConfigKeys.EXTRA_KEYS_HEIGHT, Double::parseDouble);
        copy(source, "extra-keys", ConfigKeys.EXTRA_KEYS, LegacySettings::parseExtraKeys);
        copy(source, "fullscreen", ConfigKeys.FULL_SCREEN, LegacySettings::bool);
        copy(source, "use-fullscreen-workaround", ConfigKeys.FULL_SCREEN_WORKAROUND, LegacySettings::bool);
        copy(source, "disable-terminal-session-change-toast", ConfigKeys.SHOW_SESSION_TOAST, value -> !bool(value));
        copy(source, "disable-file-share-receiver", ConfigKeys.FILE_SHARE_DISABLED, LegacySettings::bool);
        copy(source, "disable-file-view-receiver", ConfigKeys.FILE_VIEW_DISABLED, LegacySettings::bool);
        if (source.containsKey("night-mode")) {
            copy(source, "night-mode", ConfigKeys.NIGHT_MODE, LegacySettings::lower);
        } else {
            copy(source, "use-black-ui", ConfigKeys.NIGHT_MODE, value -> Boolean.toString(bool(value)));
        }
        copy(source, "shortcut.create-session", ConfigKeys.SHORTCUT_CREATE, LegacySettings::shortcut);
        copy(source, "shortcut.previous-session", ConfigKeys.SHORTCUT_PREVIOUS, LegacySettings::shortcut);
        copy(source, "shortcut.next-session", ConfigKeys.SHORTCUT_NEXT, LegacySettings::shortcut);
        copy(source, "shortcut.rename-session", ConfigKeys.SHORTCUT_RENAME, LegacySettings::shortcut);
        if ("true".equalsIgnoreCase(source.getProperty("disable-hardware-keyboard-shortcuts"))) {
            values.put(ConfigKeys.SHORTCUT_TOGGLE_CONTROL, "");
        }
    }

    private void preferences(Path home, Map<String, ?> preferences) {
        put("show_extra_keys", ConfigKeys.SHOW_EXTRA_KEYS, preferences.get("show_extra_keys"));
        put("screen_always_on", ConfigKeys.KEEP_SCREEN_ON, preferences.get("screen_always_on"));
        put("terminal_margin_adjustment", ConfigKeys.KEYBOARD_MARGIN, preferences.get("terminal_margin_adjustment"));
        put("soft_keyboard_enabled", ConfigKeys.SOFT_KEYBOARD, preferences.get("soft_keyboard_enabled"));
        put("soft_keyboard_enabled_only_if_no_hardware", ConfigKeys.SOFT_KEYBOARD_WITHOUT_HARDWARE,
            preferences.get("soft_keyboard_enabled_only_if_no_hardware"));
        put("log_level", ConfigKeys.LOG_LEVEL, preferences.get("log_level"));
        put("terminal_view_key_logging_enabled", ConfigKeys.KEY_LOGGING, preferences.get("terminal_view_key_logging_enabled"));
        put("crash_report_notifications_enabled", ConfigKeys.CRASH_NOTIFICATIONS,
            preferences.get("crash_report_notifications_enabled"));
        Object fontSize = preferences.get("fontsize");
        try {
            put("fontsize", ConfigKeys.FONT_SIZE, fontSize instanceof String ? Integer.parseInt((String) fontSize) : fontSize);
        } catch (NumberFormatException e) {
            skipped.add("fontsize");
        }

        Path customFont = home.resolve(".termux/font.ttf");
        Object oldFont = preferences.get("terminal_font");
        if (oldFont == null || "custom-file".equals(oldFont)) {
            if (Files.isRegularFile(customFont)) {
                try {
                    TermuxConfig.checkFont(home, customFont);
                    values.put(ConfigKeys.FONT, "custom");
                } catch (IOException e) {
                    skipped.add("font.ttf");
                }
            }
        } else if ("JetBrains-Mono.ttf".equals(oldFont)) {
            values.put(ConfigKeys.FONT, "jetbrains-mono");
        } else if ("HackNerdFontMono-Regular.ttf".equals(oldFont)) {
            values.put(ConfigKeys.FONT, "hack-nerd-font-mono");
        }

        Path customColors = home.resolve(".termux/colors.properties");
        Object oldColors = preferences.get("terminal_color_scheme");
        if (oldColors == null || "custom-file".equals(oldColors)) {
            Properties source = properties(customColors);
            if (!source.isEmpty()) {
                values.put(ConfigKeys.PALETTE, "custom");
                copy(source, "foreground", ConfigKeys.TERMINAL_FOREGROUND, LegacySettings::color);
                copy(source, "background", ConfigKeys.TERMINAL_BACKGROUND, LegacySettings::color);
                copy(source, "cursor", ConfigKeys.TERMINAL_CURSOR, LegacySettings::color);
                Map<String, String> ansi = new LinkedHashMap<>();
                for (int index = 0; index < 256; index++) {
                    String color = source.getProperty("color" + index);
                    if (color != null) {
                        try {
                            ansi.put(Integer.toString(index), color(color));
                        } catch (IllegalArgumentException e) {
                            skipped.add("color" + index);
                        }
                    }
                }
                if (!ansi.isEmpty()) values.put(ConfigKeys.ANSI_COLORS, ansi);
            }
        } else if (oldColors instanceof String && ((String) oldColors).endsWith(".properties")) {
            String name = (String) oldColors;
            put("terminal_color_scheme", ConfigKeys.PALETTE, name.substring(0, name.length() - ".properties".length()));
        }
    }

    private void copy(Properties properties, String name, ConfigKeys.Key<?> key, Function<String, ?> decode) {
        String value = properties.getProperty(name);
        if (value == null) return;
        try {
            put(name, key, decode.apply(value.trim()));
        } catch (IllegalArgumentException e) {
            skipped.add(name);
        }
    }

    private void put(String name, ConfigKeys.Key<?> key, Object value) {
        if (value == null) return;
        Object decoded = key.decoder.apply(value);
        if (decoded != null) values.put(key, decoded);
        else skipped.add(name);
    }

    String warning() {
        if (skipped.isEmpty()) return null;
        return "Some legacy values could not be migrated: " +
            String.join(", ", skipped.subList(0, Math.min(8, skipped.size()))) +
            (skipped.size() > 8 ? ", …" : ".") + " The original files were kept.";
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean bool(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected a boolean.");
    }

    private static String shortcut(String value) {
        return lower(value).replace(" ", "");
    }

    private static String color(String value) {
        int color = TerminalColors.parse(value);
        if (color == 0) throw new IllegalArgumentException("Invalid color.");
        return String.format(Locale.ROOT, "#%06x", color & 0xffffff);
    }

    private static Object parseExtraKeys(String value) {
        try {
            return jsonValue(new JSONArray(value), true);
        } catch (JSONException | StackOverflowError e) {
            throw new IllegalArgumentException("Invalid extra keys.", e);
        }
    }

    private static Object jsonValue(Object value, boolean action) throws JSONException {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) items.add(jsonValue(array.get(i), action));
            return items;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> items = new LinkedHashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                items.put(key, jsonValue(object.get(key), !"display".equals(key)));
            }
            return items;
        }
        return action && value instanceof String ? ((String) value).replaceAll("(?<!\\S)DRAWER(?!\\S)", "SESSIONS") : value;
    }
}
