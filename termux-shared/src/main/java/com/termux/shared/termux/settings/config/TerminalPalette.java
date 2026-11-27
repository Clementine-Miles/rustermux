package com.termux.shared.termux.settings.config;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/** Maps bundled TOML palettes or TOML custom colors to the terminal color scheme. */
public final class TerminalPalette {
    private TerminalPalette() {}

    public static Properties load(Context context, TermuxConfig.Snapshot snapshot) throws IOException {
        String theme = snapshot.get(ConfigKeys.PALETTE);
        Properties colors;
        if ("custom".equals(theme)) {
            colors = new Properties();
            put(colors, "background", snapshot.get(ConfigKeys.TERMINAL_BACKGROUND));
            put(colors, "foreground", snapshot.get(ConfigKeys.TERMINAL_FOREGROUND));
            put(colors, "cursor", snapshot.get(ConfigKeys.TERMINAL_CURSOR));
            putAnsi(colors, snapshot.get(ConfigKeys.ANSI_COLORS));
        } else {
            try (InputStream input = context.getAssets().open("rustermux/palettes/" + theme + ".toml")) {
                colors = decode(TermuxConfig.readUtf8(input));
            }
        }
        put(colors, "selection", snapshot.get(ConfigKeys.TERMINAL_SELECTION));
        return colors;
    }

    static Properties decode(String source) throws IOException {
        TomlDocument palette = new TomlDocument(source);
        Object background = palette.parsed.get("background");
        Object foreground = palette.parsed.get("foreground");
        Object cursor = palette.parsed.get("cursor");
        Map<String, String> ansi = ConfigKeys.ANSI_COLORS.decoder.apply(palette.parsed.get("ansi"));
        if (!valid(background) || !valid(foreground) || (cursor != null && !valid(cursor)) ||
            ansi == null || ansi.size() != 16) {
            throw new IOException("Invalid bundled terminal palette");
        }
        for (int i = 0; i < 16; i++) {
            if (!ansi.containsKey(Integer.toString(i))) throw new IOException("Missing bundled ANSI index " + i);
        }
        Properties colors = new Properties();
        put(colors, "background", (String) background);
        put(colors, "foreground", (String) foreground);
        if (cursor != null) put(colors, "cursor", (String) cursor);
        putAnsi(colors, ansi);
        return colors;
    }

    private static boolean valid(Object value) {
        return value instanceof String && ConfigKeys.isColor((String) value);
    }

    private static void put(Properties properties, String key, String value) {
        if (!value.isEmpty()) properties.setProperty(key, value);
    }

    private static void putAnsi(Properties properties, Map<String, String> colors) {
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            properties.setProperty("color" + entry.getKey(), entry.getValue());
        }
    }
}
