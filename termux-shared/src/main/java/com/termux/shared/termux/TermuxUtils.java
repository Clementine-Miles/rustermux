package com.termux.shared.termux;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.android.AndroidUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class TermuxUtils {

    private static final String LOG_TAG = "TermuxUtils";

    public static String getAppInfoMarkdownString(@NonNull Context context) {
        StringBuilder info = new StringBuilder("## ");
        info.append(PackageUtils.getAppNameForPackage(context)).append(" App Info\n");
        info.append(AndroidUtils.getAppInfoMarkdownString(context));
        AndroidUtils.appendPropertyToMarkdown(info, "TERMUX_APP_PACKAGE_MANAGER", TermuxBootstrap.PACKAGE_MANAGER);
        AndroidUtils.appendPropertyToMarkdown(info, "TERMUX_APP_PACKAGE_VARIANT", TermuxBootstrap.PACKAGE_VARIANT);

        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(context, true, true);
        if (error != null) {
            AndroidUtils.appendPropertyToMarkdown(info, "TERMUX_FILES_DIR", TermuxConstants.TERMUX_FILES_DIR_PATH);
            AndroidUtils.appendPropertyToMarkdown(info, "IS_TERMUX_FILES_DIR_ACCESSIBLE", "false - " + error.getMinimalErrorString());
        }

        String certificate = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
        if (certificate != null) {
            AndroidUtils.appendPropertyToMarkdown(info, "SIGNING_CERTIFICATE_SHA256_DIGEST", certificate);
        }
        return info.append("\n##\n").toString();
    }

    public static String getReportIssueMarkdownString() {
        return "## Report an Issue\n\n"
            + TermuxConstants.TERMUX_GITHUB_ISSUES_REPO_URL + "\n";
    }

    /**
     * Get a markdown {@link String} for APT info of the app.
     *
     * This will take a few seconds to run due to running {@code apt update} command.
     *
     * @param context The context for operations.
     * @return Returns the markdown {@link String}.
     */
    public static String geAPTInfoMarkdownString(@NonNull final Context context) {

        String aptInfoScript;
        try (InputStream inputStream = context.getResources().openRawResource(com.termux.shared.R.raw.apt_info_script)) {
            aptInfoScript = new String(inputStream.readAllBytes(), Charset.defaultCharset());
        } catch (IOException e) {
            Logger.logError(LOG_TAG, "Failed to get APT info script: " + e.getMessage());
            return null;
        }

        if (aptInfoScript == null || aptInfoScript.isEmpty()) {
            Logger.logError(LOG_TAG, "The APT info script is null or empty");
            return null;
        }

        aptInfoScript = aptInfoScript.replace("@TERMUX_PREFIX@", TermuxConstants.TERMUX_PREFIX_DIR_PATH);

        ExecutionCommand executionCommand = new ExecutionCommand(-1,
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash", null, aptInfoScript,
            null, ExecutionCommand.Runner.APP_SHELL.getName(), false);
        executionCommand.commandLabel = "APT Info Command";
        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF;
        AppShell appShell = AppShell.execute(context, executionCommand, null, new TermuxShellEnvironment(), null, true);
        if (appShell == null || !executionCommand.isSuccessful() || executionCommand.resultData.exitCode != 0) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString());
            return null;
        }

        if (!executionCommand.resultData.stderr.toString().isEmpty())
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString());

        StringBuilder markdownString = new StringBuilder();

        markdownString.append("## ").append(TermuxConstants.TERMUX_APP_NAME).append(" APT Info\n\n");
        markdownString.append(executionCommand.resultData.stdout.toString());
        markdownString.append("\n##\n");

        return markdownString.toString();
    }

    /**
     * Get a markdown {@link String} for info for termux debugging.
     *
     * @param context The context for operations.
     * @return Returns the markdown {@link String}.
     */
    public static String getTermuxDebugMarkdownString(@NonNull final Context context) {
        String statInfo = TermuxFileUtils.getTermuxFilesStatMarkdownString(context);
        String logcatInfo = getLogcatDumpMarkdownString(context);

        if (statInfo != null && logcatInfo != null)
            return statInfo + "\n\n" + logcatInfo;
        else if (statInfo != null)
            return statInfo;
        else
            return logcatInfo;

    }

    /**
     * Get a markdown {@link String} for logcat command dump.
     *
     * @param context The context for operations.
     * @return Returns the markdown {@link String}.
     */
    public static String getLogcatDumpMarkdownString(@NonNull final Context context) {
        // Build script
        // We need to prevent OutOfMemoryError since StreamGobbler StringBuilder + StringBuilder.toString()
        // may require lot of memory if dump is too large.
        // Putting a limit at 3000 lines. Assuming average 160 chars/line will result in 500KB usage
        // per object.
        // That many lines should be enough for debugging for recent issues anyways assuming termux
        // has not been granted READ_LOGS permission s.
        String logcatScript = "/system/bin/logcat -d -t 3000 2>&1";

        // Run script
        // Logging must be disabled for output of logcat command itself in StreamGobbler
        ExecutionCommand executionCommand = new ExecutionCommand(-1, "/system/bin/sh",
            null, logcatScript + "\n", "/", ExecutionCommand.Runner.APP_SHELL.getName(), true);
        executionCommand.commandLabel = "Logcat dump command";
        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF;
        AppShell appShell = AppShell.execute(context, executionCommand, null, new TermuxShellEnvironment(), null, true);
        if (appShell == null || !executionCommand.isSuccessful()) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString());
            return null;
        }

        // Build script output
        StringBuilder logcatOutput = new StringBuilder();
        logcatOutput.append("$ ").append(logcatScript);
        logcatOutput.append("\n").append(executionCommand.resultData.stdout.toString());

        boolean stderrSet = !executionCommand.resultData.stderr.toString().isEmpty();
        if (executionCommand.resultData.exitCode != 0 || stderrSet) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString());
            if (stderrSet)
                logcatOutput.append("\n").append(executionCommand.resultData.stderr.toString());
            logcatOutput.append("\n").append("exit code: ").append(executionCommand.resultData.exitCode.toString());
        }

        // Build markdown output
        StringBuilder markdownString = new StringBuilder();
        markdownString.append("## Logcat Dump\n\n");
        markdownString.append("\n\n").append(MarkdownUtils.getMarkdownCodeForString(logcatOutput.toString(), true));
        markdownString.append("\n##\n");

        return markdownString.toString();
    }




    /**
     * Get a process id of the main app process of the {@link TermuxConstants#TERMUX_PACKAGE_NAME}
     * package.
     *
     * @param context The context for operations.
     * @return Returns the process if found and running, otherwise {@code null}.
     */
    public static String getTermuxAppPID(final Context context) {
        return PackageUtils.getPackagePID(context, TermuxConstants.TERMUX_PACKAGE_NAME);
    }

}
