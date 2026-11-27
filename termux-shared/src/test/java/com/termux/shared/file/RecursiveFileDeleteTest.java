package com.termux.shared.file;

import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class RecursiveFileDeleteTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void clearAndDeletePreserveLinkTargetsAndReportMissingOrWrongTypes() throws IOException {
        Path root = root();
        Path outside = Files.write(root.resolve("outside"), new byte[]{1});
        Path directory = Files.createDirectory(root.resolve("target"));
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.write(nested.resolve("plain"), new byte[]{1});
        Files.createSymbolicLink(directory.resolve("link"), outside);
        Path dangling = Files.createSymbolicLink(directory.resolve("dangling"), root.resolve("missing"));
        assertNotNull(FileUtils.deleteRegularFile(null, dangling.toString(), false));
        assertTrue(Files.isSymbolicLink(dangling));
        assertNull(FileUtils.clearDirectory(directory.toString()));
        assertTrue(Files.isDirectory(directory));
        assertFalse(Files.exists(dangling, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(nested));
        assertTrue(Files.exists(outside));
        assertNull(FileUtils.deleteDirectoryFile(null, directory.toString(), false));
        assertNotNull(FileUtils.deleteDirectoryFile(null, directory.toString(), false));
        assertNull(FileUtils.deleteDirectoryFile(null, directory.toString(), true));
        assertNull(FileUtils.clearDirectory(directory.toString()));
        assertTrue(Files.isDirectory(directory));
    }

    @Test public void refusesProtectedRootsPathsWithParentLinksAndUnexpectedRootTypes() throws IOException {
        assertNotNull(FileUtils.clearDirectory("/"));
        assertNotNull(FileUtils.deleteFile(null, TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH, true));
        assertNotNull(FileUtils.clearDirectory(TermuxConstants.TERMUX_FILES_DIR_PATH));
        assertNotNull(FileUtils.clearDirectory(TermuxConstants.TERMUX_HOME_DIR_PATH));
        assertNotNull(FileUtils.deleteFile(null, TermuxConstants.TERMUX_PREFIX_DIR_PATH, true));
        assertNotNull(FileUtils.clearDirectory(TermuxConstants.TERMUX_HOME_DIR_PATH.replace("/data/data", "/data/user/0")));
        Path outside = Files.createDirectory(root().resolve("outside"));
        Path victim = Files.write(outside.resolve("victim"), new byte[]{1});
        Path link = Files.createSymbolicLink(root().resolve("link"), outside);
        assertNotNull(FileUtils.deleteFile(null, link.resolve("victim").toString(), true));
        assertNotNull(FileUtils.clearDirectory(link.toString()));
        assertThrows(IOException.class, () -> RecursiveFileDelete.delete(outside, false, false));
        assertTrue(Files.exists(victim));
        assertNull(FileUtils.deleteSymlinkFile(null, link.toString(), false));
        assertTrue(Files.exists(victim));
    }

    @Test public void reportsPartialFailuresAndContinuesWithAccessibleSiblings() throws IOException {
        Path directory = Files.createDirectory(root().resolve("target"));
        Path denied = Files.createDirectory(directory.resolve("denied"));
        Path protectedFile = Files.write(denied.resolve("keep"), new byte[]{1});
        Path ordinary = Files.write(directory.resolve("ordinary"), new byte[]{1});
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(denied);
        try {
            Files.setPosixFilePermissions(denied, PosixFilePermissions.fromString("---------"));
            assumeFalse(Files.isReadable(denied));
            IOException failure = assertThrows(IOException.class, () -> RecursiveFileDelete.delete(directory, false, true));
            assertTrue(failure.getSuppressed().length > 0);
            assertFalse(Files.exists(ordinary));
        } finally {
            Files.setPosixFilePermissions(denied, permissions);
        }
        assertTrue(Files.exists(protectedFile));
    }

    @Test public void fallsBackToTreeWalkWhenProviderHasNoSecureDirectoryStreams() throws IOException {
        Path archive = root().resolve("files.zip");
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()), Collections.singletonMap("create", "true"))) {
            Path root = Files.createDirectory(zip.getPath("/target"));
            Path nested = Files.createDirectory(root.resolve("sub"));
            Files.write(nested.resolve("regular"), new byte[]{1});
            RecursiveFileDelete.delete(root, true, true);
            assertTrue(Files.isDirectory(root));
            assertFalse(Files.exists(nested));
        }
    }

    @Test public void returnsCheckedFailureWhenSecureTraversalReachesItsDepthLimit() throws IOException {
        Path root = Files.createDirectory(root().resolve("target"));
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            assumeTrue(stream instanceof SecureDirectoryStream);
        }
        Path nested = root;
        for (int i = 0; i < 260; i++) nested = Files.createDirectory(nested.resolve("d"));
        Path retained = Files.write(nested.resolve("keep"), new byte[]{1});
        Path sibling = Files.write(root.resolve("sibling"), new byte[]{1});
        IOException failure = assertThrows(IOException.class, () -> RecursiveFileDelete.delete(root, false, true));
        assertTrue(failure.getSuppressed()[0].getMessage().contains("too deep"));
        assertTrue(Files.exists(retained));
        assertFalse(Files.exists(sibling));
    }

    @Test public void retentionSkipsNewFilesAndDoesNotVisitSymlinkDirectories() throws IOException {
        Path root = root();
        Path directory = Files.createDirectory(root.resolve("target"));
        Path nested = Files.createDirectory(directory.resolve("sub"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path old = Files.write(directory.resolve("old"), new byte[]{1});
        Path fresh = Files.write(directory.resolve("fresh"), new byte[]{1});
        Path nestedOld = Files.write(nested.resolve("old"), new byte[]{1});
        Path outsideOld = Files.write(outside.resolve("old"), new byte[]{1});
        Files.createSymbolicLink(directory.resolve("outside"), outside);
        FileTime before = FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
        for (Path file : new Path[]{old, nestedOld, outsideOld}) Files.setLastModifiedTime(file, before);

        assertNull(FileUtils.deleteFilesOlderThanXDays(null, directory.toString(), null, 1, false, FileType.REGULAR.getValue()));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(nestedOld));
        assertNull(FileUtils.deleteFilesOlderThanXDays(null, directory.toString(), path -> true, 1, false, FileType.REGULAR.getValue()));
        assertFalse(Files.exists(nestedOld));
        assertTrue(Files.exists(outsideOld));
    }

    private Path root() throws IOException {
        return temporary.getRoot().getCanonicalFile().toPath();
    }
}
