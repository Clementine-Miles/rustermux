package com.termux.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TermuxInstallerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void resolvesZipAndSymlinkEntriesWithinStaging() throws IOException {
        File staging = temporary.newFolder("usr-staging").getCanonicalFile();

        assertEquals(new File(staging, "bin/bash"), TermuxInstaller.resolveBootstrapPath(staging, "bin/bash"));
        assertEquals(new File(staging, "bin/sh"), TermuxInstaller.resolveBootstrapPath(staging, "./bin/sh"));
    }

    @Test public void rejectsTraversalAndAbsoluteEntries() throws IOException {
        File staging = temporary.newFolder("usr-staging").getCanonicalFile();
        String absolute = new File(staging, "bin/bash").getAbsolutePath();

        assertThrows(IOException.class, () -> TermuxInstaller.resolveBootstrapPath(staging, "../usr-staging-other/bash"));
        assertThrows(IOException.class, () -> TermuxInstaller.resolveBootstrapPath(staging, absolute));
        assertThrows(IOException.class, () -> TermuxInstaller.resolveBootstrapPath(staging, ""));
    }

    @Test public void rejectsSymlinkThatPointsOutsideStaging() throws IOException {
        File staging = temporary.newFolder("usr-staging").getCanonicalFile();
        File outside = temporary.newFolder("outside");
        Files.createSymbolicLink(new File(staging, "outside").toPath(), outside.toPath());

        assertThrows(IOException.class, () -> TermuxInstaller.resolveBootstrapPath(staging, "outside/file"));
    }
}
