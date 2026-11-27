package com.termux.shared.termux.settings.config;

import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Schema and safe initial values for config.toml version 1. */
public final class ConfigKeys {
    static final Map<String, Key<?>> SCHEMA = new LinkedHashMap<>();

    public static final Key<Integer> VERSION = integer("version", 1, 1, 1);
    public static final Key<String> BELL = choice("terminal.bell", "vibrate", "beep", "ignore");
    public static final Key<String> CURSOR_STYLE = choice("terminal.cursor-style", "block", "underline", "bar");
    public static final Key<Integer> CURSOR_BLINK = register("terminal.cursor-blink-ms", 0,
        value -> range(value, 0, 2000, number -> number == 0 || number >= 100));
    public static final Key<Integer> TRANSCRIPT_ROWS = integer("terminal.transcript-rows", 2000, 100, 50000);
    public static final Key<Integer> MARGIN_HORIZONTAL = integer("terminal.margin-horizontal", 3, 0, 100);
    public static final Key<Integer> MARGIN_VERTICAL = integer("terminal.margin-vertical", 0, 0, 100);
    public static final Key<String> WORKING_DIRECTORY = string("terminal.working-directory", "~", ConfigKeys::isPath);
    public static final Key<Integer> TMP_CLEANUP_DAYS = integer("terminal.tmp-cleanup-days", 3, -1, 100000);
    public static final Key<Boolean> OPEN_LINK_ON_TAP = bool("terminal.open-link-on-tap", false);
    public static final Key<Boolean> AM_SOCKET = bool("terminal.am-socket", true);

    public static final Key<String> FONT = choice("fonts.family", "hack-nerd-font-mono", "jetbrains-mono", "custom");
    public static final Key<String> FONT_PATH = string("fonts.custom-path", "~/.termux/font.ttf", ConfigKeys::isFontPath);
    public static final Key<Integer> FONT_SIZE = register("fonts.size-px", 0,
        value -> range(value, 0, 256, number -> number == 0 || number >= 4));
    public static final Key<String> PALETTE = choice("colors.palette", "gruvbox-material-dark-medium",
        "gruvbox-dark", "gruvbox-light", "gruvbox-material-dark-hard", "gruvbox-material-dark-soft",
        "gruvbox-material-light-hard", "gruvbox-material-light-medium", "gruvbox-material-light-soft", "custom");
    public static final Key<String> TERMINAL_BACKGROUND = color("colors.background");
    public static final Key<String> TERMINAL_FOREGROUND = color("colors.foreground");
    public static final Key<String> TERMINAL_CURSOR = color("colors.cursor");
    public static final Key<String> TERMINAL_SELECTION = color("colors.selection");
    public static final Key<Map<String, String>> ANSI_COLORS = register("colors.ansi", Collections.emptyMap(), ConfigKeys::colors);

    public static final Key<Boolean> SOFT_KEYBOARD = bool("input.soft-keyboard", true);
    public static final Key<Boolean> SOFT_KEYBOARD_WITHOUT_HARDWARE = bool("input.soft-keyboard-without-hardware", false);
    public static final Key<Boolean> HIDE_SOFT_KEYBOARD_ON_START = bool("input.hide-soft-keyboard-on-start", false);
    public static final Key<String> KEYBOARD_TOGGLE = choice("input.keyboard-toggle", "show/hide", "enable/disable");
    public static final Key<String> BACK_KEY = choice("input.back-key", "back", "escape");
    public static final Key<String> VOLUME_KEYS = choice("input.volume-keys", "virtual", "volume");
    public static final Key<Boolean> CHAR_BASED_INPUT = bool("input.character-based-input", false);
    public static final Key<Boolean> CTRL_SPACE_WORKAROUND = bool("input.ctrl-space-workaround", false);

    public static final Key<Boolean> SHOW_EXTRA_KEYS = bool("extra-keys.visible", true);
    public static final Key<Boolean> EXTRA_KEYS_ALL_CAPS = bool("extra-keys.all-caps", true);
    public static final Key<String> EXTRA_KEYS_STYLE = choice("extra-keys.style", "default",
        "arrows-only", "arrows-all", "all", "none");
    public static final Key<Double> EXTRA_KEYS_HEIGHT = register("extra-keys.height-scale", 1.0, value -> {
        if (!(value instanceof Double || value instanceof Integer || value instanceof Long)) return null;
        double factor = ((Number) value).doubleValue();
        return factor >= 0.4 && factor <= 3 ? factor : null;
    });
    public static final Key<List<List<Object>>> EXTRA_KEYS = register("extra-keys.rows", defaultExtraKeys(),
        ConfigKeys::extraKeys);

    public static final Key<String> NIGHT_MODE = choice("interface.night-mode", "system", "true", "false");
    public static final Key<Boolean> KEEP_SCREEN_ON = bool("interface.keep-screen-on", false);
    public static final Key<Boolean> KEYBOARD_MARGIN = bool("interface.keyboard-insets", true);
    public static final Key<Boolean> FULL_SCREEN = bool("interface.fullscreen", false);
    public static final Key<Boolean> FULL_SCREEN_WORKAROUND = bool("interface.fullscreen-workaround", false);
    public static final Key<String> UI_BACKGROUND = color("interface.background");
    public static final Key<String> UI_FOREGROUND = color("interface.foreground");
    public static final Key<String> UI_ACCENT = color("interface.accent");
    public static final Key<Boolean> SHOW_TAB_BAR = bool("sessions.show-tab-bar", true);
    public static final Key<Boolean> SHOW_SESSION_TOAST = bool("sessions.show-switch-toast", true);

    public static final Key<String> SHORTCUT_TOGGLE_CONTROL = shortcut("toggle-control", "ctrl+alt+shift+space", true);
    public static final Key<String> SHORTCUT_CREATE = shortcut("create", "n", false);
    public static final Key<String> SHORTCUT_PREVIOUS = shortcut("previous", "left", false);
    public static final Key<String> SHORTCUT_NEXT = shortcut("next", "right", false);
    public static final Key<String> SHORTCUT_RENAME = shortcut("rename", "r", false);
    public static final Key<String> SHORTCUT_TERMINATE = shortcut("terminate", "x", false);
    public static final Key<String> SHORTCUT_CLOSE = shortcut("close", "c", false);
    public static final Key<String> SHORTCUT_MOVE_LEFT = shortcut("move-left", "shift+left", false);
    public static final Key<String> SHORTCUT_MOVE_RIGHT = shortcut("move-right", "shift+right", false);
    public static final Key<String> SHORTCUT_TOGGLE_TAB_BAR = shortcut("toggle-tab-bar", "b", false);

    public static final Key<Integer> LOG_LEVEL = integer("preferences.log-level", 1, 0, 3);
    public static final Key<Boolean> KEY_LOGGING = bool("preferences.key-logging", false);
    public static final Key<Boolean> CRASH_NOTIFICATIONS = bool("preferences.crash-notifications", true);
    public static final Key<Boolean> FILE_SHARE_DISABLED = bool("preferences.disable-file-share-receiver", false);
    public static final Key<Boolean> FILE_VIEW_DISABLED = bool("preferences.disable-file-view-receiver", false);

    public static final Key<Integer> ICON_KEYBOARD = icon("icons.keyboard", 0xf030c);
    public static final Key<Integer> ICON_SETTINGS = icon("icons.settings", 0xeb51);
    public static final Key<Integer> ICON_NEW_SESSION = icon("icons.new-session", 0xf067);
    public static final Key<Integer> ICON_SESSION_MODE = icon("icons.session-mode", 0xf03a);

    private ConfigKeys() {}

    /** A typed, validated setting. */
    public static final class Key<T> {
        public final String path;
        final T fallback;
        final Function<Object, T> decoder;

        private Key(String path, T fallback, Function<Object, T> decoder) {
            this.path = path;
            this.fallback = fallback;
            this.decoder = decoder;
        }
    }

    private static <T> Key<T> register(String path, T fallback, Function<Object, T> decoder) {
        Key<T> key = new Key<>(path, fallback, decoder);
        SCHEMA.put(path, key);
        return key;
    }

    private static Key<Boolean> bool(String path, boolean fallback) {
        return register(path, fallback, value -> value instanceof Boolean ? (Boolean) value : null);
    }

    private static Key<Integer> integer(String path, int fallback, int min, int max) {
        return register(path, fallback, value -> range(value, min, max, ignored -> true));
    }

    private static Key<Integer> icon(String path, int fallback) {
        return register(path, fallback, value -> range(value, 0x20, Character.MAX_CODE_POINT,
            point -> !(point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)));
    }

    private static Integer range(Object value, int min, int max, Predicate<Integer> extraCheck) {
        if (!(value instanceof Long || value instanceof Integer)) return null;
        long number = ((Number) value).longValue();
        return number >= min && number <= max && extraCheck.test((int) number) ? (int) number : null;
    }

    private static Key<String> string(String path, String fallback, Predicate<String> acceptable) {
        return register(path, fallback,
            value -> value instanceof String && acceptable.test((String) value) ? (String) value : null);
    }

    private static Key<String> choice(String path, String fallback, String... alternatives) {
        List<String> options = Arrays.asList(alternatives);
        return string(path, fallback, value -> value.equals(fallback) || options.contains(value));
    }

    private static Key<String> shortcut(String name, String fallback, boolean activation) {
        return string("shortcuts." + name, fallback, value -> SessionShortcut.isValid(value, activation));
    }

    private static Key<String> color(String path) {
        return string(path, "", value -> value.isEmpty() || isColor(value));
    }

    static boolean isColor(String value) {
        return value.matches("#[0-9a-fA-F]{6}");
    }

    private static boolean isPath(String path) {
        if (path.isEmpty() || path.length() > 4096) return false;
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isFontPath(String path) {
        if (!isPath(path)) return false;
        for (Path part : Paths.get(path)) {
            if ("..".equals(part.toString())) return false;
        }
        return true;
    }

    private static Map<String, String> colors(Object raw) {
        Map<?, ?> entries;
        if (raw instanceof TomlTable) entries = ((TomlTable) raw).toMap();
        else if (raw instanceof Map) entries = (Map<?, ?>) raw;
        else return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String && entry.getValue() instanceof String)) return null;
            String index = (String) entry.getKey();
            String color = (String) entry.getValue();
            if (!index.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(index) > 255 || !isColor(color)) return null;
            result.put(index, color);
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<List<Object>> defaultExtraKeys() {
        return extraKeys(Arrays.asList(
            Arrays.asList("ESC", "/", Map.of("key", "-", "popup", "|"), "HOME", "UP", "END", "PGUP"),
            Arrays.asList("TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN")));
    }

    private static List<List<Object>> extraKeys(Object raw) {
        List<?> rows = asList(raw);
        if (rows == null || rows.size() > 6) return null;
        List<List<Object>> result = new ArrayList<>();
        for (Object row : rows) {
            List<?> entries = asList(row);
            if (entries == null || entries.isEmpty() || entries.size() > 16) return null;
            List<Object> keys = new ArrayList<>();
            for (Object entry : entries) {
                Object key = extraKey(entry, true);
                if (key == null) return null;
                keys.add(key);
            }
            result.add(Collections.unmodifiableList(keys));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<?> asList(Object raw) {
        if (raw instanceof TomlArray) return ((TomlArray) raw).toList();
        return raw instanceof List ? (List<?>) raw : null;
    }

    private static Object extraKey(Object raw, boolean allowPopup) {
        if (raw instanceof String) {
            String key = (String) raw;
            return !key.isEmpty() && key.length() <= 512 ? key : null;
        }
        Map<?, ?> rawEntries;
        if (raw instanceof TomlTable) rawEntries = ((TomlTable) raw).toMap();
        else if (raw instanceof Map) rawEntries = (Map<?, ?>) raw;
        else return null;
        if (rawEntries.containsKey("key") == rawEntries.containsKey("macro")) return null;
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawEntries.entrySet()) {
            Object name = entry.getKey();
            Object value = entry.getValue();
            boolean stringEntry = "key".equals(name) || "macro".equals(name) || "display".equals(name);
            if (stringEntry && value instanceof String && ((String) value).length() <= 512) {
                entries.put((String) name, value);
            } else if ("popup".equals(name) && allowPopup) {
                Object popup = extraKey(value, false);
                if (popup == null) return null;
                entries.put("popup", popup);
            } else {
                return null;
            }
        }
        return Collections.unmodifiableMap(entries);
    }
}
