package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Pair;
import android.view.WindowManager;

import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

/** Installs the packaged APT bootstrap using a staging directory. */
final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";
    private static final Object BOOTSTRAP_LOCK = new Object();

    /** Performs bootstrap setup if necessary. */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;

        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;

        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (!PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage);
            return;
        }

        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }

            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.showMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage, null);
            return;
        }

        installDefaultMotd(activity);

        if (hasInstalledBootstrap()) {
            whenDone.run();
            return;
        }

        ProgressDialog progress = ProgressDialog.show(activity, null,
            activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread(() -> {
            try {
                synchronized (BOOTSTRAP_LOCK) {
                    if (!hasInstalledBootstrap()) installBootstrap(activity);
                }
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) whenDone.run();
                });
            } catch (Exception e) {
                showBootstrapErrorDialog(activity, whenDone,
                    Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));
            } finally {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) progress.dismiss();
                });
            }
        }, "Termux-bootstrap").start();
    }

    private static boolean hasInstalledBootstrap() {
        return FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)
            && !TermuxFileUtils.isTermuxPrefixDirectoryEmpty();
    }

    private static void installBootstrap(Context context) throws IOException, ErrnoException {
        if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)
                && !FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            throw new IOException("Existing path blocks bootstrap installation: " + TERMUX_PREFIX_DIR_PATH);
        }

        Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");
        requireSuccess(FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true));
        requireSuccess(TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true));
        extractBootstrap();

        if (hasInstalledBootstrap()) return;
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            removePrefixScaffolding();
        }
        Os.rename(TERMUX_STAGING_PREFIX_DIR_PATH, TERMUX_PREFIX_DIR_PATH);
        TermuxShellEnvironment.writeEnvironmentToFile(context);
        Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");
    }

    private static void removePrefixScaffolding() throws IOException {
        File prefix = TERMUX_PREFIX_DIR.getCanonicalFile();
        for (String path : TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY) {
            File file = new File(prefix, path.substring(TERMUX_PREFIX_DIR_PATH.length() + 1));
            for (File entry = file; !entry.equals(prefix); entry = entry.getParentFile()) {
                if (FileUtils.symlinkFileExists(entry.getPath())) {
                    throw new IOException("Existing link blocks bootstrap installation: " + entry);
                }
            }
            if (FileUtils.fileExists(file.getPath(), false) && !file.delete()) {
                throw new IOException("Existing data blocks bootstrap installation: " + file);
            }
            for (File parent = file.getParentFile(); !parent.equals(prefix); parent = parent.getParentFile()) {
                // Remove empty parents of scaffold files without removing other content.
                if (!FileUtils.directoryFileExists(parent.getPath(), false) || !parent.delete()) break;
            }
        }
        if (!prefix.delete()) {
            throw new IOException("Could not remove empty prefix at " + TERMUX_PREFIX_DIR_PATH);
        }
    }

    private static void extractBootstrap() throws IOException, ErrnoException {
        File staging = TERMUX_STAGING_PREFIX_DIR.getCanonicalFile();
        byte[] buffer = new byte[8192];
        List<Pair<String, File>> symlinks = new ArrayList<>();
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(loadZipBytes()))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("SYMLINKS.txt")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInput, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("←", -1);
                        if (parts.length != 2 || parts[0].isEmpty()) {
                            throw new IOException("Malformed bootstrap symlink: " + line);
                        }
                        File destination = resolveBootstrapPath(staging, parts[1]);
                        ensureDirectoryExists(destination.getParentFile());
                        symlinks.add(Pair.create(parts[0], destination));
                    }
                    continue;
                }
                File destination = resolveBootstrapPath(staging, name);
                if (entry.isDirectory()) {
                    ensureDirectoryExists(destination);
                    continue;
                }

                ensureDirectoryExists(destination.getParentFile());
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    int count;
                    while ((count = zipInput.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                if (name.startsWith("bin/") || name.startsWith("libexec")
                        || name.startsWith("lib/apt/apt-helper") || name.startsWith("lib/apt/methods")) {
                    Os.chmod(destination.getAbsolutePath(), 0700);
                }
            }
        }

        if (symlinks.isEmpty()) throw new IOException("Bootstrap archive has no symlink manifest");
        for (Pair<String, File> link : symlinks) {
            Os.symlink(link.first, link.second.getAbsolutePath());
        }
    }

    static File resolveBootstrapPath(File staging, String name) throws IOException {
        File destination = new File(staging, name).getCanonicalFile();
        if (new File(name).isAbsolute() || !destination.getPath().startsWith(staging.getPath() + File.separator)) {
            throw new IOException("Bootstrap entry escapes the staging directory: " + name);
        }
        return destination;
    }

    /** Set the stock greeting once through the login script's supported customization path. */
    private static void installDefaultMotd(Context context) {
        SharedPreferences defaults = context.getSharedPreferences("termux_shell_defaults", Context.MODE_PRIVATE);
        if (defaults.getBoolean("motd_initialized", false)) return;

        File directory = TermuxConstants.TERMUX_DATA_HOME_DIR;
        File destination = new File(directory, "motd.sh");
        File temporary = null;
        try {
            if (!destination.exists()) {
                if (!directory.mkdirs() && !directory.isDirectory()) {
                    throw new IOException("Could not create " + directory);
                }
                File bundledScript = new File(TermuxConstants.TERMUX_APP.APPS_DIR_PATH, "motd.sh");
                temporary = File.createTempFile("motd-install", ".tmp", bundledScript.getParentFile());
                try (InputStream input = context.getAssets().open("shell/motd.sh");
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                Os.chmod(temporary.getAbsolutePath(), 0700);
                Os.rename(temporary.getAbsolutePath(), bundledScript.getAbsolutePath());
                // A concurrent writer or a dangling user symlink still owns the destination.
                try {
                    Os.symlink(bundledScript.getAbsolutePath(), destination.getAbsolutePath());
                } catch (ErrnoException e) {
                    if (e.errno != OsConstants.EEXIST) throw e;
                }
            }
            defaults.edit().putBoolean("motd_initialized", true).apply();
        } catch (ErrnoException | IOException e) {
            Logger.logError(LOG_TAG, "Could not set the default shell greeting: " + e);
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);

        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body)
                    .setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                        dialog.dismiss();
                        activity.finish();
                    })
                    .setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                        dialog.dismiss();
                        TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                    }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        final String title = TermuxConstants.TERMUX_APP_NAME + " Bootstrap Error";

        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG,
            title, null, "## " + title + "\n\n" + message + "\n\n" +
                TermuxUtils.getTermuxDebugMarkdownString(activity),
            true, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        final String title = TermuxConstants.TERMUX_APP_NAME + " Setup Storage Error";

        Logger.logInfo(LOG_TAG, "Setting up storage symlinks.");

        new Thread() {
            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;

                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, LOG_TAG, error.getMessage());
                        Logger.logErrorExtended(LOG_TAG, "Setup Storage Error\n" + error.toString());
                        TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                            "## " + title + "\n\n" + Error.getErrorMarkdownString(error),
                            true, true);
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"" + Environment.getExternalStorageDirectory().getAbsolutePath() + "\".");

                    // Get primary storage root "/storage/emulated/0" symlink
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                    Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                        Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }

                    // Dir 0 should ideally be for primary storage
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                    // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053

                    // Create "Android/data/com.termux" symlinks
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    // Create "Android/media/com.termux" symlinks
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "media-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                    Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                    TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                        "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)),
                        true, true);
                }
            }
        }.start();
    }

    private static void ensureDirectoryExists(File directory) throws IOException {
        requireSuccess(FileUtils.createDirectoryFile(directory.getAbsolutePath()));
    }

    private static void requireSuccess(Error error) throws IOException {
        if (error != null) throw new IOException(Error.getMinimalErrorString(error));
    }

    private static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    private static native byte[] getZip();

}
