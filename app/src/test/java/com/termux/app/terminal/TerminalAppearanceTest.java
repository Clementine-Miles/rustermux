package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;

import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowTypeface;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.termux.app.terminal.TerminalAppearance.Setting.COLOR_SCHEME;
import static com.termux.app.terminal.TerminalAppearance.Setting.FONT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class TerminalAppearanceTest {
    private Context context;
    private TermuxConfig config;
    private TerminalAppearance appearance;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        config = TermuxConfig.get(context);
        Files.write(config.file().toPath(), "version = 1\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.diagnostic(), config.reload());
        appearance = new TerminalAppearance(context);
    }

    @Test
    public void defaultPaletteAndAllEightChoicesLoad() throws Exception {
        assertEquals(8, context.getAssets().list("rustermux/palettes").length);
        assertPalette(0xff282828, 0xffd4be98);
        selectPalette("gruvbox-dark", 0xff1d2021, 0xffebdbb2);
        selectPalette("gruvbox-light", 0xfff9f5d7, 0xff3c3836);
        selectPalette("gruvbox-material-dark-hard", 0xff1d2021, 0xffd4be98);
        selectPalette("gruvbox-material-dark-medium", 0xff282828, 0xffd4be98);
        selectPalette("gruvbox-material-dark-soft", 0xff32302f, 0xffd4be98);
        selectPalette("gruvbox-material-light-hard", 0xfff9f5d7, 0xff654735);
        selectPalette("gruvbox-material-light-medium", 0xfffbf1c7, 0xff654735);
        selectPalette("gruvbox-material-light-soft", 0xfff2e5bc, 0xff654735);
    }

    @Test
    public void customColorsAndSelectionAreIndependentOfAppColors() {
        assertTrue(config.set(ConfigKeys.TERMINAL_BACKGROUND, "#123456"));
        assertTrue(config.set(ConfigKeys.TERMINAL_FOREGROUND, "#abcdef"));
        assertTrue(config.set(ConfigKeys.TERMINAL_SELECTION, "#aabbcc"));
        assertTrue(config.set(ConfigKeys.UI_BACKGROUND, "#010203"));
        selectPalette("custom", 0xff123456, 0xffabcdef);
        TerminalColorScheme colors = new TerminalColorScheme();
        appearance.applyColorScheme(colors);
        assertEquals(0xffaabbcc, colors.selectionColor());
    }

    @Test
    public void fontChoiceUsesBundledOrValidatedCustomPaths() throws Exception {
        assertEquals("appearance/fonts/HackNerdFontMono-Regular.ttf", fontSource(appearance.loadFont()));
        assertTrue(appearance.select(FONT, "jetbrains-mono"));
        assertEquals("appearance/fonts/JetBrains-Mono.ttf", fontSource(appearance.loadFont()));
        Path customFont = config.customFont("~/local.ttf").toPath();
        Files.deleteIfExists(customFont);
        try (InputStream font = context.getAssets().open("appearance/fonts/JetBrains-Mono.ttf")) {
            Files.copy(font, customFont);
        }
        assertTrue(config.set(ConfigKeys.FONT_PATH, "~/local.ttf"));
        assertTrue(appearance.select(FONT, "custom"));
        assertTrue(fontSource(appearance.loadFont()).endsWith("local.ttf"));
    }

    @Test
    public void applyingDefaultsPreservesOscOverridesInEachSession() {
        TerminalColors modified = new TerminalColors();
        TerminalColors unmodified = new TerminalColors();
        modified.tryParseColor(1, "#ff0000");
        modified.tryParseColor(TextStyle.COLOR_INDEX_BACKGROUND, "#112233");
        assertTrue(appearance.select(COLOR_SCHEME, "gruvbox-light"));
        appearance.applyColorScheme(TerminalColors.COLOR_SCHEME);
        modified.refreshDefaults();
        unmodified.refreshDefaults();
        assertEquals(0xffff0000, modified.mCurrentColors[1]);
        assertEquals(0xff112233, modified.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(0xfff9f5d7, unmodified.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        modified.reset(TextStyle.COLOR_INDEX_BACKGROUND);
        assertEquals(0xfff9f5d7, modified.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
    }

    private void selectPalette(String name, int background, int foreground) {
        assertTrue(config.diagnostic(), appearance.select(COLOR_SCHEME, name));
        assertPalette(background, foreground);
    }

    private void assertPalette(int background, int foreground) {
        TerminalColorScheme colors = new TerminalColorScheme();
        appearance.applyColorScheme(colors);
        assertEquals(background, colors.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(foreground, colors.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND]);
    }

    private static String fontSource(Typeface typeface) {
        ShadowTypeface shadow = Shadow.extract(typeface);
        return shadow.getFontDescription().getFamilyName();
    }
}
