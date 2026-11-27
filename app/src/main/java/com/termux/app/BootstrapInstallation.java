package com.termux.app;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Detects the APT bootstrap and publishes a fully extracted prefix in one rename. */
final class BootstrapInstallation {

    enum State { MISSING, EMPTY, INITIALIZED, DAMAGED }

    static final class DamagedPrefixException extends IOException {
        DamagedPrefixException(Path prefix) {
            super("The existing bootstrap is incomplete at " + prefix);
        }
    }

    private BootstrapInstallation() {}

    static State inspect(Path prefix) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(prefix, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return State.MISSING;
        }
        if (isInitialized(prefix)) return State.INITIALIZED;
        if (!attributes.isDirectory()) return State.DAMAGED;

        try {
            visitScaffolding(prefix, false);
            return State.EMPTY;
        } catch (DamagedPrefixException e) {
            return State.DAMAGED;
        }
    }

    private static boolean isInitialized(Path prefix) throws IOException {
        Path status = prefix.resolve("var/lib/dpkg/status");
        if (!Files.isRegularFile(status) || Files.size(status) == 0 || !isExecutable(prefix.resolve("bin/dpkg"))) {
            return false;
        }
        for (String shell : UnixShellEnvironment.LOGIN_SHELL_BINARIES) {
            if (isExecutable(prefix.resolve("bin").resolve(shell))) return true;
        }
        return false;
    }

    private static boolean isExecutable(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }

    static void install(Path prefix, Path staging, byte[] archive) throws IOException {
        State state = inspect(prefix);
        if (state == State.INITIALIZED) return;
        if (state == State.DAMAGED) throw new DamagedPrefixException(prefix);

        Error cleanup = FileUtils.deleteFile("bootstrap staging directory", staging.toString(), true);
        if (cleanup != null) throw new IOException(cleanup.getMinimalErrorString());
        Files.createDirectory(staging, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        extract(staging, archive);
        if (!isInitialized(staging)) throw new IOException("Bootstrap archive is missing its shell or package database");

        state = inspect(prefix);
        if (state == State.INITIALIZED) return;
        if (state == State.DAMAGED) throw new DamagedPrefixException(prefix);
        if (state == State.EMPTY) visitScaffolding(prefix, true);
        Files.move(staging, prefix, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void visitScaffolding(Path prefix, boolean remove) throws IOException {
        Files.walkFileTree(prefix, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                String relative = prefix.relativize(directory).toString();
                if (!relative.isEmpty() && !relative.equals("tmp") && !relative.equals("etc") && !relative.equals("etc/termux")) {
                    throw new DamagedPrefixException(prefix);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String relative = prefix.relativize(file).toString();
                if (!attributes.isRegularFile()
                    || (!relative.equals("etc/termux/termux.env") && !relative.equals("etc/termux/termux.env.tmp"))) {
                    throw new DamagedPrefixException(prefix);
                }
                if (remove) Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                if (remove) Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void extract(Path staging, byte[] archive) throws IOException {
        Path zipPath = Files.createTempFile(staging, ".bootstrap-", ".zip");
        try {
            Files.write(zipPath, archive);
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                checkAvailableSpace(zip, Files.getFileStore(staging).getUsableSpace());
                Map<Path, Path> links = new LinkedHashMap<>();
                boolean manifestFound = false;
                for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().equals("SYMLINKS.txt")) {
                        if (manifestFound || entry.isDirectory()) throw new IOException("Invalid bootstrap symlink manifest");
                        manifestFound = true;
                        try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(entry), new CRC32());
                             BufferedReader manifest = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()))) {
                            String line;
                            while ((line = manifest.readLine()) != null) {
                                String[] pair = line.split("←", -1);
                                if (pair.length != 2 || pair[0].isEmpty()) throw new IOException("Malformed bootstrap symlink: " + line);
                                Path destination = resolveEntry(staging, pair[1]);
                                Path target = resolveLinkTarget(staging, destination, pair[0]);
                                if (links.putIfAbsent(destination, target) != null) {
                                    throw new IOException("Duplicate bootstrap symlink: " + pair[1]);
                                }
                            }
                            verifyEntry(entry, input);
                        }
                    } else {
                        extractEntry(zip, entry, resolveEntry(staging, entry.getName()));
                    }
                }
                if (!manifestFound || links.isEmpty()) throw new IOException("Bootstrap archive has no symlink manifest");
                for (Map.Entry<Path, Path> entry : links.entrySet()) {
                    Path link = resolveEntry(staging, staging.relativize(entry.getKey()).toString());
                    Files.createDirectories(link.getParent());
                    Files.createSymbolicLink(link, entry.getValue());
                }
            }
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    static void checkAvailableSpace(ZipFile zip, long available) throws IOException {
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            long size = entry.getSize();
            if (size < 0) throw new IOException("Missing size for bootstrap entry " + entry.getName());
            if (size > available) throw new IOException("Insufficient space for the bootstrap packages");
            available -= size;
        }
    }

    private static void extractEntry(ZipFile zip, ZipEntry entry, Path destination) throws IOException {
        if (entry.isDirectory()) {
            Files.createDirectories(destination);
            return;
        }
        Files.createDirectories(destination.getParent());
        try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(entry), new CRC32());
             OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            if (input.transferTo(output) != entry.getSize()) throw new IOException("Invalid size for bootstrap entry " + entry.getName());
            verifyEntry(entry, input);
        }
        String name = entry.getName();
        if (name.startsWith("bin/") || name.startsWith("libexec/")
            || name.equals("lib/apt/apt-helper") || name.startsWith("lib/apt/methods/")) {
            Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static void verifyEntry(ZipEntry entry, CheckedInputStream input) throws IOException {
        if (entry.getCrc() != input.getChecksum().getValue()) {
            throw new IOException("Invalid checksum for bootstrap entry " + entry.getName());
        }
    }

    static Path resolveEntry(Path staging, String name) throws IOException {
        try {
            Path relative = staging.getFileSystem().getPath(name);
            Path destination = staging.resolve(relative).normalize();
            if (!relative.isAbsolute() && !destination.equals(staging) && destination.startsWith(staging)) {
                for (Path component : relative) {
                    if (component.toString().equals("..")) throw new IOException("Parent traversal in bootstrap path: " + name);
                }
                Path checked = staging;
                for (Path component : staging.relativize(destination)) {
                    checked = checked.resolve(component);
                    if (Files.isSymbolicLink(checked)) throw new IOException("Symbolic link in bootstrap path: " + name);
                }
                return destination;
            }
        } catch (InvalidPathException e) {
            throw new IOException("Invalid bootstrap path: " + name, e);
        }
        throw new IOException("Bootstrap entry escapes the staging directory: " + name);
    }

    private static Path resolveLinkTarget(Path staging, Path link, String name) throws IOException {
        try {
            Path target = staging.getFileSystem().getPath(name);
            Path prefix = staging.getFileSystem().getPath(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
            boolean contained = target.isAbsolute() ? target.startsWith(prefix) && target.normalize().startsWith(prefix)
                : link.getParent().resolve(target).normalize().startsWith(staging);
            if (contained) return target;
        } catch (InvalidPathException e) {
            throw new IOException("Invalid bootstrap symlink target: " + name, e);
        }
        throw new IOException("Bootstrap symlink target escapes the prefix: " + name);
    }
}
