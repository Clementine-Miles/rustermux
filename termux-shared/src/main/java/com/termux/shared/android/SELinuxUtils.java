package com.termux.shared.android;

import android.system.ErrnoException;
import android.system.Os;

import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** SELinux labels for app and shell diagnostics. Android may withhold these values. */
public final class SELinuxUtils {
    private SELinuxUtils() {}

    public static String getContext() {
        try {
            return trimLabel(new String(Files.readAllBytes(Path.of("/proc/self/attr/current")), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            Logger.logVerbose("SELinuxUtils", "Process label unavailable: " + failure.getMessage());
            return null;
        }
    }

    public static String getFileContext(String path) {
        try {
            return trimLabel(new String(Os.getxattr(path, "security.selinux"), StandardCharsets.UTF_8));
        } catch (ErrnoException failure) {
            Logger.logVerbose("SELinuxUtils", "File label unavailable: " + failure.getMessage());
            return null;
        }
    }

    private static String trimLabel(String label) {
        int nul = label.indexOf(0);
        return (nul == -1 ? label : label.substring(0, nul)).trim();
    }
}
