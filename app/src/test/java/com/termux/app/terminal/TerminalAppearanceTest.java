package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowTypeface;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.termux.app.terminal.TerminalAppearance.CUSTOM_FILE;
import static com.termux.app.terminal.TerminalAppearance.Setting.COLOR_SCHEME;
import static com.termux.app.terminal.TerminalAppearance.Setting.FONT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class TerminalAppearanceTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Context context;
    private SharedPreferences preferences;
    private File customColors;
    private File customFont;
    private TerminalAppearance appearance;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("appearance-test", Context.MODE_PRIVATE);
        customColors = new File(temporary.getRoot(), "colors.properties");
        customFont = new File(temporary.getRoot(), "font.ttf");
        appearance = new TerminalAppearance(context, preferences, customColors, customFont);
    }

    @Test
    public void defaultPaletteAndAllEightChoicesLoad() throws Exception {
        assertEquals(8, context.getAssets().list("appearance/colors").length);
        assertPalette(appearance, 0xff282828, 0xffd4be98);
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
    public void paletteSelectionPersistsAndCanReturnToCustomFile() throws Exception {
        byte[] original = "background=#123456\nforeground=#abcdef\n".getBytes(StandardCharsets.UTF_8);
        Files.write(customColors.toPath(), original);
        assertPalette(appearance, 0xff123456, 0xffabcdef);

        selectPalette("gruvbox-light", 0xfff9f5d7, 0xff3c3836);
        TerminalAppearance reopened = new TerminalAppearance(context, preferences, customColors, customFont);
        assertPalette(reopened, 0xfff9f5d7, 0xff3c3836);
        assertArrayEquals(original, Files.readAllBytes(customColors.toPath()));

        reopened.select(COLOR_SCHEME, CUSTOM_FILE);
        assertPalette(reopened, 0xff123456, 0xffabcdef);
        Files.write(customColors.toPath(), "background=#abcdef\nforeground=#123456\n".getBytes(StandardCharsets.UTF_8));
        assertPalette(reopened, 0xffabcdef, 0xff123456);
    }

    @Test
    public void invalidCustomPaletteFallsBackToDefault() throws Exception {
        Files.write(customColors.toPath(), "invalid=true\n".getBytes(StandardCharsets.UTF_8));
        assertPalette(appearance, 0xff282828, 0xffd4be98);
    }

    @Test
    public void fontsLoadAndSelectionDoesNotModifyCustomFont() throws Exception {
        assertEquals(2, context.getAssets().list("appearance/fonts").length);
        assertEquals("appearance/fonts/HackNerdFontMono-Regular.ttf", fontSource(appearance.loadFont()));
        byte[] original = {1, 2, 3};
        Files.write(customFont.toPath(), original);
        assertEquals("font.ttf", fontSource(appearance.loadFont()));

        appearance.select(FONT, "JetBrains-Mono.ttf");
        TerminalAppearance reopened = new TerminalAppearance(context, preferences, customColors, customFont);
        assertEquals("appearance/fonts/JetBrains-Mono.ttf", fontSource(reopened.loadFont()));
        assertArrayEquals(original, Files.readAllBytes(customFont.toPath()));

        reopened.select(FONT, CUSTOM_FILE);
        assertEquals("font.ttf", fontSource(reopened.loadFont()));
    }

    private void selectPalette(String name, int background, int foreground) {
        appearance.select(COLOR_SCHEME, name + ".properties");
        assertPalette(appearance, background, foreground);
    }

    private static void assertPalette(TerminalAppearance appearance, int background, int foreground) {
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
