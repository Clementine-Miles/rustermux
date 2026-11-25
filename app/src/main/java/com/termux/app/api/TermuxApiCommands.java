package com.termux.app.api;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Installs the APK's API and settings wrappers outside the package manager's prefix. */
public final class TermuxApiCommands {
    private static final String LOG_TAG = "TermuxApiCommands";
    private static final String ASSET_PATH = "termux-api";

    private TermuxApiCommands() {}

    public static synchronized void install(Context context) {
        File root = new File(TermuxConstants.TERMUX_APP.API_COMMANDS_DIR_PATH);
        File nativeClient = new File(context.getApplicationInfo().nativeLibraryDir, "libtermux-api-client.so");
        File marker = new File(root, "apk-state");
        File bin = new File(root, "bin");
        File libexec = new File(root, "libexec");
        File launcher = new File(libexec, "termux-api");

        try {
            if (!nativeClient.canExecute()) {
                throw new IOException("APK API executable is missing or inaccessible: " + nativeClient);
            }

            String state = nativeClient.getAbsolutePath() + "\n" +
                new File(context.getApplicationInfo().sourceDir).lastModified() + "\n";
            if (bin.isDirectory() && launcher.canExecute() && marker.isFile()) {
                try (InputStream input = new FileInputStream(marker)) {
                    if (state.equals(readText(input))) return;
                }
            }

            installAssets(context, "bin", bin);
            installAssets(context, "libexec", libexec);
            installScript(context, "shell/termux-reload-settings", new File(bin, "termux-reload-settings"));
            installLauncher(nativeClient, launcher);
            writeFile(marker, state, 0600);
        } catch (IOException | ErrnoException e) {
            Logger.logError(LOG_TAG, "Could not install the bundled Termux API commands: " + e);
        }
    }

    private static void installAssets(Context context, String subdirectory, File outputDirectory)
        throws IOException, ErrnoException {
        if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory()) {
            throw new IOException("Could not create " + outputDirectory);
        }

        String assetDirectory = ASSET_PATH + "/" + subdirectory;
        String[] assets = context.getAssets().list(assetDirectory);
        if (assets == null || assets.length == 0) {
            throw new IOException("APK assets are missing from " + assetDirectory);
        }

        for (String name : assets) {
            installScript(context, assetDirectory + "/" + name, new File(outputDirectory, name));
        }
    }

    private static void installScript(Context context, String assetPath, File destination)
        throws IOException, ErrnoException {
        try (InputStream input = context.getAssets().open(assetPath)) {
            String script = readText(input)
                .replace("@TERMUX_PREFIX@", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
                .replace("@TERMUX_API_ROOT@", TermuxConstants.TERMUX_APP.API_COMMANDS_DIR_PATH);
            writeFile(destination, script, 0700);
        }
    }

    private static void installLauncher(File client, File launcher) throws IOException, ErrnoException {
        File temporary = File.createTempFile("api-client", ".tmp", launcher.getParentFile());
        try {
            if (!temporary.delete()) throw new IOException("Could not replace " + temporary);
            Os.symlink(client.getAbsolutePath(), temporary.getAbsolutePath());
            Os.rename(temporary.getAbsolutePath(), launcher.getAbsolutePath());
        } finally {
            temporary.delete();
        }
    }

    private static void writeFile(File path, String contents, int mode) throws IOException, ErrnoException {
        File temporary = File.createTempFile("api-command", ".tmp", path.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(contents.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            Os.chmod(temporary.getAbsolutePath(), mode);
            Os.rename(temporary.getAbsolutePath(), path.getAbsolutePath());
        } finally {
            temporary.delete();
        }
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
