package com.termux.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TermuxInstallerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void recognizesMissingScaffoldingInstalledAndDamagedPrefixes() throws IOException {
        Path prefix = root().resolve("usr");
        assertEquals(BootstrapInstallation.State.MISSING, BootstrapInstallation.inspect(prefix));

        Path config = Files.createDirectories(prefix.resolve("etc/termux"));
        Files.createDirectory(prefix.resolve("tmp"));
        Files.write(config.resolve("termux.env"), new byte[]{1});
        assertEquals(BootstrapInstallation.State.EMPTY, BootstrapInstallation.inspect(prefix));

        Path badFile = Files.write(config.resolve("custom"), new byte[]{1});
        assertEquals(BootstrapInstallation.State.DAMAGED, BootstrapInstallation.inspect(prefix));
        Files.delete(badFile);

        Path bin = Files.createDirectory(prefix.resolve("bin"));
        Path shell = Files.write(bin.resolve("bash"), new byte[]{1});
        Files.setPosixFilePermissions(shell, PosixFilePermissions.fromString("rwx------"));
        Path dpkg = Files.write(bin.resolve("dpkg"), new byte[]{1});
        Files.setPosixFilePermissions(dpkg, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(prefix.resolve("var/lib/dpkg"));
        Files.write(database.resolve("status"), new byte[]{1});
        assertEquals(BootstrapInstallation.State.INITIALIZED, BootstrapInstallation.inspect(prefix));
        Files.delete(shell);
        assertEquals(BootstrapInstallation.State.DAMAGED, BootstrapInstallation.inspect(prefix));
    }

    @Test public void recoversAfterInterruptedStagingAndPreservesHome() throws IOException {
        Path root = root();
        Path prefix = root.resolve("usr");
        Path staging = Files.createDirectory(root.resolve("usr-staging"));
        Path home = Files.createDirectory(root.resolve("home"));
        Files.write(home.resolve("retained"), new byte[]{1});
        Files.write(staging.resolve("interrupted"), new byte[]{1});
        Path config = Files.createDirectories(prefix.resolve("etc/termux"));
        Files.write(config.resolve("termux.env.tmp"), new byte[]{1});
        byte[] archive = archive("bash←./bin/sh\n");

        assertThrows(IOException.class, () -> BootstrapInstallation.install(prefix, staging, Arrays.copyOf(archive, archive.length - 8)));
        assertEquals(BootstrapInstallation.State.EMPTY, BootstrapInstallation.inspect(prefix));
        assertTrue(Files.exists(config.resolve("termux.env.tmp")));

        BootstrapInstallation.install(prefix, staging, archive);
        assertEquals(BootstrapInstallation.State.INITIALIZED, BootstrapInstallation.inspect(prefix));
        assertFalse(Files.exists(staging));
        assertFalse(Files.exists(prefix.resolve("interrupted")));
        assertTrue(Files.isSymbolicLink(prefix.resolve("bin/sh")));
        assertTrue(Files.exists(home.resolve("retained")));

        Path modified = Files.write(prefix.resolve("user-file"), new byte[]{1});
        BootstrapInstallation.install(prefix, staging, new byte[0]);
        assertTrue(Files.exists(modified));
    }

    @Test public void preservesDamagedPrefixAndItsPackageDatabase() throws IOException {
        Path root = root();
        Path prefix = root.resolve("usr");
        Path staging = root.resolve("usr-staging");
        Path database = Files.createDirectories(prefix.resolve("var/lib/dpkg"));
        Path status = Files.write(database.resolve("status"), new byte[]{3});

        assertThrows(BootstrapInstallation.DamagedPrefixException.class,
            () -> BootstrapInstallation.install(prefix, staging, archive("bash←bin/sh\n")));
        assertEquals(3, Files.readAllBytes(status)[0]);
        assertFalse(Files.exists(staging));
    }

    @Test public void rejectsInvalidArchiveEntriesAndSymlinkTargets() throws IOException {
        Path root = root();
        Path prefix = root.resolve("usr");
        Path staging = root.resolve("usr-staging");
        assertThrows(IOException.class, () -> BootstrapInstallation.install(prefix, staging, archive("../..←bin/link\n")));
        assertThrows(IOException.class, () -> BootstrapInstallation.install(prefix, staging, archive("/system/bin/sh←bin/link\n")));
        assertThrows(IOException.class, () -> BootstrapInstallation.install(prefix, staging, archive("bash←bin/sh\n", "../outside")));
        assertThrows(IOException.class, () -> BootstrapInstallation.install(prefix, staging, archive("bash←bin/sh\nbin←alias\nbash←alias/child\n")));
        assertFalse(Files.exists(root.resolve("outside")));
        assertEquals(BootstrapInstallation.State.MISSING, BootstrapInstallation.inspect(prefix));
    }

    @Test public void resolvesRelativeEntriesAndRejectsAbsoluteTraversalAndSymlinks() throws IOException {
        Path staging = Files.createDirectory(root().resolve("usr-staging"));
        assertEquals(staging.resolve("bin/bash"), BootstrapInstallation.resolveEntry(staging, "bin/bash"));
        assertEquals(staging.resolve("bin/sh"), BootstrapInstallation.resolveEntry(staging, "./bin/sh"));
        assertThrows(IOException.class, () -> BootstrapInstallation.resolveEntry(staging, "../usr-staging-other/bash"));
        assertThrows(IOException.class, () -> BootstrapInstallation.resolveEntry(staging, "bin/../bash"));
        assertThrows(IOException.class, () -> BootstrapInstallation.resolveEntry(staging, staging.resolve("bin/bash").toString()));
        assertThrows(IOException.class, () -> BootstrapInstallation.resolveEntry(staging, ""));
        Path outside = Files.createDirectory(root().resolve("outside"));
        Files.createSymbolicLink(staging.resolve("outside"), outside);
        assertThrows(IOException.class, () -> BootstrapInstallation.resolveEntry(staging, "outside/file"));
    }

    @Test public void detectsInsufficientSpaceBeforeExtraction() throws IOException {
        Path path = Files.write(root().resolve("bootstrap.zip"), archive("bash←bin/sh\n"));
        try (ZipFile zip = new ZipFile(path.toFile())) {
            assertThrows(IOException.class, () -> BootstrapInstallation.checkAvailableSpace(zip, 1));
        }
    }

    @Test public void storageLinkRefreshPreservesCustomFilesAndRestrictsGeneratedNames() throws IOException {
        Path directory = root().resolve("home/storage");
        Path oldTarget = root().resolve("old-volume");
        Path newTarget = root().resolve("new-volume");
        Map<String, Path> links = new LinkedHashMap<>();
        links.put("shared", oldTarget);
        StorageLinks.update(directory, links);
        Path custom = Files.write(directory.resolve("private-note"), new byte[]{4});
        Path collision = Files.createDirectory(directory.resolve("documents"));
        links.put("shared", newTarget);
        links.put("documents", newTarget.resolve("documents"));

        assertThrows(IOException.class, () -> StorageLinks.update(directory, links));
        assertEquals(oldTarget, Files.readSymbolicLink(directory.resolve("shared")));
        assertTrue(Files.isDirectory(collision));

        links.remove("documents");
        StorageLinks.update(directory, links);
        assertEquals(newTarget, Files.readSymbolicLink(directory.resolve("shared")));
        assertEquals(4, Files.readAllBytes(custom)[0]);
        assertTrue(Files.isDirectory(collision));
    }

    private Path root() throws IOException {
        return temporary.getRoot().getCanonicalFile().toPath();
    }

    private static byte[] archive(String manifest, String... extraEntries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "SYMLINKS.txt", manifest);
            writeEntry(zip, "bin/bash", "shell");
            writeEntry(zip, "bin/dpkg", "dpkg");
            writeEntry(zip, "var/lib/dpkg/status", "status");
            for (String name : extraEntries) writeEntry(zip, name, "data");
        }
        return output.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
