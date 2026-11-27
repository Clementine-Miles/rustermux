package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.WindowManager;

import com.termux.R;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;

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

        try {
            BootstrapInstallation.State state = BootstrapInstallation.inspect(TERMUX_PREFIX_DIR.toPath());
            if (state == BootstrapInstallation.State.INITIALIZED) {
                finishBootstrap(activity, whenDone);
                return;
            }
            if (state == BootstrapInstallation.State.DAMAGED) {
                throw new BootstrapInstallation.DamagedPrefixException(TERMUX_PREFIX_DIR.toPath());
            }
        } catch (IOException e) {
            showBootstrapErrorDialog(activity, whenDone, e);
            return;
        }

        ProgressDialog progress = ProgressDialog.show(activity, null,
            activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread(() -> {
            try {
                synchronized (BOOTSTRAP_LOCK) {
                    if (BootstrapInstallation.inspect(TERMUX_PREFIX_DIR.toPath()) != BootstrapInstallation.State.INITIALIZED) {
                        Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");
                        BootstrapInstallation.install(TERMUX_PREFIX_DIR.toPath(), TERMUX_STAGING_PREFIX_DIR.toPath(),
                            loadZipBytes());
                        TermuxShellEnvironment.writeEnvironmentToFile(activity);
                        Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");
                    }
                }
                activity.runOnUiThread(() -> finishBootstrap(activity, whenDone));
            } catch (Exception | UnsatisfiedLinkError e) {
                showBootstrapErrorDialog(activity, whenDone, e);
            } finally {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) progress.dismiss();
                });
            }
        }, "Termux-bootstrap").start();
    }

    private static void finishBootstrap(Activity activity, Runnable whenDone) {
        if (activity.isDestroyed()) return;
        installDefaultMotd(activity);
        whenDone.run();
    }

    /** Set the stock greeting once through the login script's supported customization path. */
    private static void installDefaultMotd(Context context) {
        SharedPreferences defaults = context.getSharedPreferences("termux_shell_defaults", Context.MODE_PRIVATE);
        if (defaults.getBoolean("motd_initialized", false)) return;

        Path directory = TermuxConstants.TERMUX_DATA_HOME_DIR.toPath();
        Path destination = directory.resolve("motd.sh");
        try {
            if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(directory);
                Path bundledScript = new File(TermuxConstants.TERMUX_APP.APPS_DIR_PATH, "motd.sh").toPath();
                Path temporary = Files.createTempFile(bundledScript.getParent(), "motd-install", ".tmp");
                try {
                    try (InputStream input = context.getAssets().open("shell/motd.sh")) {
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
                    Files.move(temporary, bundledScript, StandardCopyOption.ATOMIC_MOVE);
                    try {
                        Files.createSymbolicLink(destination, bundledScript);
                    } catch (FileAlreadyExistsException ignored) {
                        // A concurrent writer owns the destination.
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            defaults.edit().putBoolean("motd_initialized", true).apply();
        } catch (IOException e) {
            Logger.logError(LOG_TAG, "Could not set the default shell greeting: " + e);
        }
    }

    private static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, Throwable error) {
        String message = Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(error));
        String reason = error.getLocalizedMessage();
        String details = error instanceof BootstrapInstallation.DamagedPrefixException
            ? activity.getString(R.string.bootstrap_prefix_damaged, TERMUX_PREFIX_DIR_PATH)
            : activity.getString(R.string.bootstrap_error_body) + (reason == null ? "" : "\n\n" + reason);
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(details)
                    .setCancelable(false)
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

        new Thread(() -> {
            try {
                Map<String, Path> links = new LinkedHashMap<>();
                links.put("shared", Environment.getExternalStorageDirectory().toPath());
                links.put("documents", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toPath());
                links.put("downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toPath());
                links.put("dcim", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toPath());
                links.put("pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toPath());
                links.put("music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toPath());
                links.put("movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toPath());
                links.put("podcasts", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).toPath());
                links.put("audiobooks", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS).toPath());
                addStorageVolumeLinks(links, "external-", context.getExternalFilesDirs(null));
                addStorageVolumeLinks(links, "media-", context.getExternalMediaDirs());
                StorageLinks.update(TermuxConstants.TERMUX_STORAGE_HOME_DIR.toPath(), links);
                Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
            } catch (Exception e) {
                Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                    "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)),
                    true, true);
            }
        }, "Termux-storage").start();
    }

    private static void addStorageVolumeLinks(Map<String, Path> links, String prefix, File[] directories) {
        if (directories == null) return;
        for (int i = 0; i < directories.length; i++) {
            if (directories[i] != null) links.put(prefix + i, directories[i].toPath());
        }
    }

    private static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    private static native byte[] getZip();

}
