package com.termux.shared.termux.settings.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class TermuxConfigTest {
    private static final String DEFAULTS = "# bundled\nversion = 1\n[terminal]\nmargin-horizontal = 3\n"
        + "[interface]\nkeep-screen-on = false\n[sessions]\nshow-tab-bar = true\n"
        + "[colors]\npalette = \"gruvbox-material-dark-medium\"\nansi = {}\n";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void initializesAndMigratesOnlyWhenTheAuthoritativeFileIsAbsent() throws Exception {
        Path home = temp.newFolder("home").toPath();
        Path properties = home.resolve(".termux/termux.properties");
        Files.createDirectories(properties.getParent());
        String primary = "terminal-margin-horizontal=11\nunknown-legacy=ignored\n";
        write(properties, primary);
        Path secondary = home.resolve(".config/termux/termux.properties");
        Files.createDirectories(secondary.getParent());
        write(secondary, "terminal-margin-horizontal=22\n");
        Path colors = home.resolve(".termux/colors.properties");
        write(colors, "background=#123456\ncolor1=#ff0000\n");
        TermuxConfig store = new TermuxConfig(home, DEFAULTS,
            () -> LegacySettings.read(home, Map.of("screen_always_on", true)));
        assertTrue(store.diagnostic(), store.reload());
        assertEquals(11, (int) store.snapshot().get(ConfigKeys.MARGIN_HORIZONTAL));
        assertTrue(store.snapshot().get(ConfigKeys.KEEP_SCREEN_ON));
        assertEquals("custom", store.snapshot().get(ConfigKeys.PALETTE));
        assertEquals("#ff0000", store.snapshot().get(ConfigKeys.ANSI_COLORS).get("1"));
        assertEquals(primary, read(properties));

        write(properties, "terminal-margin-horizontal=99\n");
        assertTrue(store.reload());
        assertEquals(11, (int) store.snapshot().get(ConfigKeys.MARGIN_HORIZONTAL));
    }

    @Test
    public void rejectsMalformedDuplicateInvalidUtf8VersionAndValuesWithRecovery() throws Exception {
        Path home = temp.newFolder("home").toPath();
        Path lastGood = temp.getRoot().toPath().resolve("last-good.toml");
        TermuxConfig store = new TermuxConfig(home, DEFAULTS, LegacySettings::new, lastGood);
        assertTrue(store.reload());
        assertTrue(store.set(ConfigKeys.MARGIN_HORIZONTAL, 12));
        TermuxConfig.Snapshot good = store.snapshot();
        Path file = store.file().toPath();

        for (String invalid : new String[] {
            "version = 2\n",
            "version = 1\n[terminal]\nmargin-horizontal = 200\n",
            "version = 1\n[terminal]\nmargin-horizontal = 1\nmargin-horizontal = 2\n",
            "version = 1\n[colors]\nforeground = \"#oops\"\n",
            "version = 1\n[fonts]\nfamily = \"custom\"\ncustom-path = \"~/.missing.ttf\"\n",
            "version = 1\nbroken = " + repeat('[', 10000) + "1" + repeat(']', 10000)
        }) {
            write(file, invalid);
            assertFalse(store.reload());
            assertSame(good, store.snapshot());
            assertEquals(invalid, read(file));
            assertNotNull(store.diagnostic());
        }
        Files.write(file, new byte[] {(byte) 0xc3, 0x28});
        assertFalse(store.reload());
        assertTrue(store.diagnostic().contains("UTF-8"));
        assertSame(good, store.snapshot());

        TermuxConfig restarted = new TermuxConfig(home, DEFAULTS, LegacySettings::new, lastGood);
        assertFalse(restarted.reload());
        assertEquals(12, (int) restarted.snapshot().get(ConfigKeys.MARGIN_HORIZONTAL));
        assertFalse(restarted.set(ConfigKeys.SHOW_TAB_BAR, false));
        assertTrue(restarted.snapshot().get(ConfigKeys.SHOW_TAB_BAR));

        write(file, repeat('#', TermuxConfig.MAX_BYTES + 1));
        assertFalse(restarted.reload());
        assertTrue(restarted.diagnostic().contains("exceeds"));
    }

    @Test
    public void writesOnlyTheTargetValueAndPreservesUnrecognizedSettings() throws Exception {
        Path home = temp.newFolder("home").toPath();
        TermuxConfig store = new TermuxConfig(home, DEFAULTS, LegacySettings::new);
        assertTrue(store.reload());
        String source = "version = 1\r\n[interface] # heading\r\nkeep-screen-on = false  # 😀 note\r\n"
            + "third-party = [\"# not a comment\", 2]\r\n[future]\r\nname = \"copy me\"\r\n";
        write(store.file().toPath(), source);
        assertTrue(store.reload());
        assertTrue(store.diagnostic().contains("Unknown"));
        assertTrue(store.diagnostic(), store.set(ConfigKeys.KEEP_SCREEN_ON, true));
        assertEquals(source.replace("= false", "= true"), read(store.file().toPath()));
        assertTrue(store.diagnostic(), store.set(ConfigKeys.KEYBOARD_MARGIN, false));
        assertTrue(read(store.file().toPath()).contains("keyboard-insets = false\r\n"));
        assertFalse(store.snapshot().get(ConfigKeys.KEYBOARD_MARGIN));
    }

    @Test
    public void serializesSettingsChangesAndCanApplyTemporaryOverrides() throws Exception {
        TermuxConfig store = new TermuxConfig(temp.newFolder("home").toPath(), DEFAULTS, LegacySettings::new);
        assertTrue(store.reload());
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = writers.submit(() -> store.set(ConfigKeys.MARGIN_HORIZONTAL, 17));
            Future<Boolean> second = writers.submit(() -> store.set(ConfigKeys.SHOW_TAB_BAR, false));
            assertTrue(first.get());
            assertTrue(second.get());
        } finally {
            writers.shutdownNow();
        }
        assertTrue(store.reload());
        assertEquals(17, (int) store.snapshot().get(ConfigKeys.MARGIN_HORIZONTAL));
        assertFalse(store.snapshot().get(ConfigKeys.SHOW_TAB_BAR));
        String saved = read(store.file().toPath());
        assertTrue(store.setTransient(ConfigKeys.SHOW_TAB_BAR, true));
        assertTrue(store.snapshot().get(ConfigKeys.SHOW_TAB_BAR));
        assertEquals(saved, read(store.file().toPath()));
        assertTrue(store.setTransient(ConfigKeys.SHOW_TAB_BAR, null));
        assertFalse(store.snapshot().get(ConfigKeys.SHOW_TAB_BAR));
    }

    @Test
    public void doesNotWriteThroughASymlinkedConfigurationDirectory() throws Exception {
        Path home = temp.newFolder("home").toPath();
        Path outside = temp.newFolder("outside").toPath();
        Files.createSymbolicLink(home.resolve(".config"), outside);
        TermuxConfig store = new TermuxConfig(home, DEFAULTS, LegacySettings::new);
        assertFalse(store.reload());
        assertFalse(Files.exists(outside.resolve("rustermux")));
        assertFalse(store.set(ConfigKeys.SHOW_TAB_BAR, false));
        assertTrue(store.snapshot().get(ConfigKeys.SHOW_TAB_BAR));
    }

    private static void write(Path file, String source) throws IOException {
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
