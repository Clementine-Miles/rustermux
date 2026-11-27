package com.termux.shared.termux.settings.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.shared.termux.TermuxConstants;

import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One authoritative user configuration, with immutable snapshots for its consumers. */
public final class TermuxConfig {
    static final int MAX_BYTES = 128 * 1024;
    private static final String DEFAULT_ASSET = "rustermux/default-config.toml";
    private static TermuxConfig instance;

    private final Path home;
    private final Path file;
    private final Path lastGoodFile;
    private final TomlDocument template;
    private final Supplier<LegacySettings> migration;
    private final Snapshot defaults;
    private final Map<ConfigKeys.Key<?>, Object> transientOverrides = new LinkedHashMap<>();

    private Snapshot saved;
    private boolean hasLoadedSavedConfig;
    private boolean migrationPerformed;
    private volatile Snapshot current;
    private volatile String diagnostic;

    public static synchronized TermuxConfig get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            Path home = app.getFilesDir().toPath().resolve("home");
            SharedPreferences preferences = app.getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE);
            String bundled = null;
            try (InputStream input = app.getAssets().open(DEFAULT_ASSET)) {
                bundled = readUtf8(input);
            } catch (IOException e) {
                // Static safe values keep the app usable if the bundled asset cannot be read.
            }
            Path lastGood = app.getNoBackupFilesDir().toPath().resolve("rustermux-last-good.toml");
            instance = new TermuxConfig(home, bundled, () -> LegacySettings.read(home, preferences.getAll()), lastGood);
            instance.reload();
        }
        return instance;
    }

    TermuxConfig(Path home, String bundled, Supplier<LegacySettings> migration) {
        this(home, bundled, migration, null);
    }

    TermuxConfig(Path home, String bundled, Supplier<LegacySettings> migration, Path lastGoodFile) {
        this.home = home;
        this.file = home.resolve(".config/rustermux/config.toml");
        this.lastGoodFile = lastGoodFile;
        this.migration = migration;
        TomlDocument document = null;
        Snapshot initial = new Snapshot(new LinkedHashMap<>());
        if (bundled != null) {
            try {
                document = new TomlDocument(bundled);
                if (!document.parsed.contains(ConfigKeys.VERSION.path)) throw new IOException("Missing bundled version.");
                initial = decode(document, initial, true).snapshot;
            } catch (IOException | RuntimeException e) {
                document = null;
            }
        }
        this.template = document;
        this.defaults = initial;
        this.saved = initial;
        this.current = initial;
    }

    public File file() {
        return file.toFile();
    }

    /** The latest applied values. Objects reachable from this snapshot are immutable. */
    public Snapshot snapshot() {
        return current;
    }

    /** The latest load or write diagnostic, or null after a clean operation. */
    public String diagnostic() {
        return diagnostic;
    }

    public synchronized boolean reload() {
        try {
            String source;
            String migrationWarning = null;
            try {
                source = readFile(file);
            } catch (NoSuchFileException absent) {
                Initialization initialized = initialize();
                source = initialized.contents;
                migrationWarning = initialized.warning;
            }
            Validated result = decode(new TomlDocument(source), defaults, false);
            apply(result.snapshot, source);
            diagnostic = joinWarnings(migrationWarning, result.warning);
            return true;
        } catch (IOException | RuntimeException e) {
            diagnostic = "Could not reload " + file + ". " + e.getMessage();
            restoreLastGood();
            return false;
        }
    }

    /** Edit one setting in place. A failed save leaves the source and active values intact. */
    public synchronized <T> boolean set(ConfigKeys.Key<T> key, T value) {
        T validValue = key.decoder.apply(value);
        if (key == ConfigKeys.VERSION || validValue == null) {
            diagnostic = "Invalid value for " + key.path + ".";
            return false;
        }
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) initialize();
            requireConfigDirectory();
            try (FileChannel channel = lockChannel(); FileLock ignored = channel.lock()) {
                String source = readFile(file);
                TomlDocument existing = new TomlDocument(source);
                if (existing.parsed.contains(ConfigKeys.VERSION.path)) {
                    requireVersion(existing);
                }
                TomlDocument updated = existing.set(key, validValue);
                Validated result = decode(updated, defaults, false);
                if (!source.equals(updated.text)) install(file, updated.text, true, source);
                else requireUnchanged(file, source);
                apply(result.snapshot, updated.text);
                diagnostic = result.warning;
                return true;
            }
        } catch (IOException | RuntimeException e) {
            diagnostic = "Could not save " + key.path + " in " + file + ". " + e.getMessage();
            return false;
        }
    }

    /** A process-local override. Null clears it and restores the current saved value. */
    public synchronized <T> boolean setTransient(ConfigKeys.Key<T> key, T value) {
        T validValue = value == null ? null : key.decoder.apply(value);
        if (key == ConfigKeys.VERSION || (value != null && validValue == null)) return false;
        Map<ConfigKeys.Key<?>, Object> overrides = new LinkedHashMap<>(transientOverrides);
        if (value == null) overrides.remove(key);
        else overrides.put(key, validValue);
        try {
            validateFont(saved.overlaid(overrides));
        } catch (IOException e) {
            diagnostic = e.getMessage();
            return false;
        }
        transientOverrides.clear();
        transientOverrides.putAll(overrides);
        publishSnapshot();
        return true;
    }

    public File customFont(String path) {
        return resolveHomePath(path).toFile();
    }

    public String workingDirectory(String path) {
        return resolveHomePath(path).toString();
    }

    private Path resolveHomePath(String path) {
        if ("~".equals(path)) return home;
        if (path.startsWith("~/")) return home.resolve(path.substring(2)).normalize();
        return home.resolve(path).normalize();
    }

    private void publishSnapshot() {
        current = saved.overlaid(transientOverrides);
    }

    private void apply(Snapshot snapshot, String source) {
        saved = snapshot;
        hasLoadedSavedConfig = true;
        migrationPerformed = true;
        publishSnapshot();
        if (lastGoodFile != null) {
            try {
                String previous;
                try {
                    previous = readFile(lastGoodFile);
                } catch (IOException e) {
                    previous = null;
                }
                if (!source.equals(previous)) install(lastGoodFile, source, true, null);
            } catch (IOException | RuntimeException ignored) {
                // This private recovery copy is best-effort. The authoritative file is unaffected.
            }
        }
    }

    private void restoreLastGood() {
        if (hasLoadedSavedConfig || lastGoodFile == null) return;
        try {
            saved = decode(new TomlDocument(readFile(lastGoodFile)), defaults, false).snapshot;
            hasLoadedSavedConfig = true;
            migrationPerformed = true;
            publishSnapshot();
        } catch (IOException | RuntimeException ignored) {
            // Safe bundled values are available even if the previous configuration is unavailable.
        }
    }

    private Initialization initialize() throws IOException {
        if (template == null) throw new IOException("Bundled default configuration is unavailable.");
        Files.createDirectories(home);
        createConfigDirectory(home.resolve(".config"));
        createConfigDirectory(file.getParent());
        requireConfigDirectory();
        try (FileChannel channel = lockChannel(); FileLock ignored = channel.lock()) {
            try {
                return new Initialization(readFile(file), null);
            } catch (NoSuchFileException absent) {
                // The lock serializes editors and migration; publishing a new file also uses a no-replace link.
            }
            boolean migrated = migrationPerformed || (lastGoodFile != null && Files.exists(lastGoodFile));
            LegacySettings legacy = migrated ? new LegacySettings() : migration.get();
            TomlDocument document = template;
            for (Map.Entry<ConfigKeys.Key<?>, Object> entry : legacy.values.entrySet()) {
                document = document.set(entry.getKey(), entry.getValue());
            }
            try {
                install(file, document.text, false, null);
            } catch (FileAlreadyExistsException raced) {
                return new Initialization(readFile(file), null);
            }
            return new Initialization(document.text, legacy.warning());
        }
    }

    private FileChannel lockChannel() throws IOException {
        return FileChannel.open(file.getParent().resolve(".config.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    private void requireConfigDirectory() throws IOException {
        if (!Files.isDirectory(home.resolve(".config"), LinkOption.NOFOLLOW_LINKS) ||
            !Files.isDirectory(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Use regular directories for ~/.config/rustermux.");
        }
    }

    private static void createConfigDirectory(Path path) throws IOException {
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Use a regular configuration directory at " + path + ".", e);
            }
        }
    }

    private static void requireUnchanged(Path path, String expected) throws IOException {
        if (!expected.equals(readFile(path))) {
            throw new IOException("The file changed while saving. Reload it and try again.");
        }
    }

    private static void install(Path target, String contents, boolean replace, String expected) throws IOException {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Configuration exceeds " + MAX_BYTES + " bytes.");
        Path temporary = Files.createTempFile(target.getParent(), ".config-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (replace) {
                if (expected != null) requireUnchanged(target, expected);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createLink(target, temporary);
            }
            try (FileChannel parent = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                parent.force(true);
            } catch (IOException ignored) {
                // The replacement itself is atomic; some filesystems do not support directory fsync.
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String readFile(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
            throw new IOException("Expected a regular file at " + path + ".");
        }
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return readUtf8(input);
        }
    }

    static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (output.size() + length > MAX_BYTES) throw new IOException("Configuration exceeds " + MAX_BYTES + " bytes.");
            output.write(buffer, 0, length);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Use valid UTF-8 text.", e);
        }
    }

    private Validated decode(TomlDocument document, Snapshot base, boolean bundled) throws IOException {
        requireVersion(document);
        Map<ConfigKeys.Key<?>, Object> values = new LinkedHashMap<>(base.values);
        List<String> unknown = new ArrayList<>();
        for (String path : document.parsed.dottedKeySet(true)) {
            if (path.startsWith(ConfigKeys.ANSI_COLORS.path + ".")) continue;
            Object raw = document.parsed.get(path);
            ConfigKeys.Key<?> key = ConfigKeys.SCHEMA.get(path);
            if (key != null) {
                Object decoded = key.decoder.apply(raw);
                if (decoded == null) throw invalid(document, path);
                values.put(key, decoded);
            } else if (!(raw instanceof TomlTable && isKnownSection(path))) {
                if (isKnownSection(path)) throw invalid(document, path);
                unknown.add(path);
            }
        }
        Snapshot snapshot = new Snapshot(values);
        if (!bundled) validateFont(snapshot);
        String warning = document.parsed.contains(ConfigKeys.VERSION.path) ? null : "Missing version; using version 1.";
        if (!unknown.isEmpty()) {
            Collections.sort(unknown);
            warning = joinWarnings(warning, "Unknown settings ignored: "
                + String.join(", ", unknown.subList(0, Math.min(8, unknown.size()))) + (unknown.size() > 8 ? ", …" : "."));
        }
        return new Validated(snapshot, warning);
    }

    private static boolean isKnownSection(String path) {
        for (String key : ConfigKeys.SCHEMA.keySet()) {
            if (key.startsWith(path + ".")) return true;
        }
        return false;
    }

    private void validateFont(Snapshot snapshot) throws IOException {
        if (!"custom".equals(snapshot.get(ConfigKeys.FONT))) return;
        checkFont(home, resolveHomePath(snapshot.get(ConfigKeys.FONT_PATH)));
    }

    static void checkFont(Path home, Path font) throws IOException {
        try {
            Path actual = font.toRealPath();
            if (!actual.startsWith(home.toRealPath()) || !Files.isRegularFile(actual) ||
                Files.size(actual) < 12 || Files.size(actual) > 64L * 1024 * 1024) throw new IOException();
            try (DataInputStream input = new DataInputStream(Files.newInputStream(actual, LinkOption.NOFOLLOW_LINKS))) {
                int tag = input.readInt();
                if (tag != 0x00010000 && tag != 0x4f54544f && tag != 0x74746366 && tag != 0x74727565) {
                    throw new IOException();
                }
            }
        } catch (IOException e) {
            throw new IOException("fonts.custom-path must point to a readable TrueType or OpenType font under the Termux home directory.");
        }
    }

    private static void requireVersion(TomlDocument document) throws IOException {
        if (!document.parsed.contains(ConfigKeys.VERSION.path)) return;
        Object version = document.parsed.get(ConfigKeys.VERSION.path);
        if (ConfigKeys.VERSION.decoder.apply(version) == null) {
            throw new IOException("Unsupported config version at " + document.parsed.inputPositionOf("version") + ". Expected version = 1.");
        }
    }

    private static IOException invalid(TomlDocument document, String path) {
        TomlPosition position = document.parsed.inputPositionOf(path);
        return new IOException("Invalid value for " + path + " at " + position + ". Compare the bundled defaults.");
    }

    private static String joinWarnings(String first, String second) {
        if (first == null) return second;
        if (second == null) return first;
        return first + " " + second;
    }

    public static final class Snapshot {
        private final Map<ConfigKeys.Key<?>, Object> values;

        private Snapshot(Map<ConfigKeys.Key<?>, Object> values) {
            this.values = Collections.unmodifiableMap(values);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(ConfigKeys.Key<T> key) {
            return (T) values.getOrDefault(key, key.fallback);
        }

        private Snapshot overlaid(Map<ConfigKeys.Key<?>, Object> overrides) {
            if (overrides.isEmpty()) return this;
            Map<ConfigKeys.Key<?>, Object> merged = new LinkedHashMap<>(values);
            merged.putAll(overrides);
            return new Snapshot(merged);
        }
    }

    private static final class Initialization {
        final String contents;
        final String warning;

        Initialization(String contents, String warning) {
            this.contents = contents;
            this.warning = warning;
        }
    }

    private static final class Validated {
        final Snapshot snapshot;
        final String warning;

        Validated(Snapshot snapshot, String warning) {
            this.snapshot = snapshot;
            this.warning = warning;
        }
    }
}
