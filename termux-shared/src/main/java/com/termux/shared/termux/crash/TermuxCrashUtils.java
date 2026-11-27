package com.termux.shared.termux.crash;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.termux.shared.R;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.crash.CrashHandler;
import com.termux.shared.data.DataUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.nio.charset.Charset;

public class TermuxCrashUtils implements CrashHandler.CrashHandlerClient {

    public enum TYPE {
        UNCAUGHT_EXCEPTION,
        CAUGHT_EXCEPTION;
    }

    private final TYPE mType;

    private static final String LOG_TAG = "TermuxCrashUtils";

    TermuxCrashUtils(TYPE type) {
        mType = type;
    }

    /**
     * Set default uncaught crash handler of the app to {@link CrashHandler} for Termux app
     * to log crashes at {@link TermuxConstants#TERMUX_CRASH_LOG_FILE_PATH}.
     */
    public static void setDefaultCrashHandler(@NonNull final Context context) {
        CrashHandler.setDefaultCrashHandler(context, new TermuxCrashUtils(TYPE.UNCAUGHT_EXCEPTION));
    }

    /**
     * Set uncaught crash handler of current non-main thread to {@link CrashHandler} for Termux app
     * to log crashes at {@link TermuxConstants#TERMUX_CRASH_LOG_FILE_PATH}.
     */
    public static void setCrashHandler(@NonNull final Context context) {
        CrashHandler.setCrashHandler(context, new TermuxCrashUtils(TYPE.CAUGHT_EXCEPTION));
    }

    /**
     * Get {@link CrashHandler} that can be set as the uncaught
     * crash handler of a non-main thread to log crashes at {@link TermuxConstants#TERMUX_CRASH_LOG_FILE_PATH}.
     */
    public static CrashHandler getCrashHandler(@NonNull final Context context) {
        return CrashHandler.getCrashHandler(context, new TermuxCrashUtils(TYPE.CAUGHT_EXCEPTION));
    }

    /**
     * Log a crash to {@link TermuxConstants#TERMUX_CRASH_LOG_FILE_PATH} and notify termux app
     * by sending it the {@link TERMUX_APP.TERMUX_ACTIVITY#ACTION_NOTIFY_APP_CRASH} broadcast.
     *
     * @param context The {@link Context} for operations.
     * @param throwable The {@link Throwable} thrown for the crash.
     */
    public static void logCrash(@NonNull final Context context, final Throwable throwable) {
        if (throwable == null) return;
        CrashHandler.logCrash(context, new TermuxCrashUtils(TYPE.CAUGHT_EXCEPTION), Thread.currentThread(), throwable);
    }

    @Override
    public boolean onPreLogCrash(Context context, Thread thread, Throwable throwable) {
        return false;
    }

    @Override
    public void onPostLogCrash(Context context, Thread thread, Throwable throwable) {
        if (context == null || mType == TYPE.UNCAUGHT_EXCEPTION) return;
        Intent intent = new Intent(TERMUX_APP.TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intent.setPackage(TermuxConstants.TERMUX_PACKAGE_NAME);
        try {
            context.sendBroadcast(intent);
        } catch (RuntimeException exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to notify the terminal of a crash", exception);
        }
    }

    @NonNull
    @Override
    public String getCrashLogFilePath(Context context) {
        return TermuxConstants.TERMUX_CRASH_LOG_FILE_PATH;
    }

    @Override
    public String getAppInfoMarkdownString(Context context) {
        return TermuxUtils.getAppInfoMarkdownString(context);
    }

    /**
     * Notify the user of an app crash by reading the crash info from the crash log file
     * at {@link TermuxConstants#TERMUX_CRASH_LOG_FILE_PATH}. The crash log file would have been
     * created by {@link com.termux.shared.crash.CrashHandler}.
     *
     * If the crash log file exists and is not empty and
     * {@link TermuxPreferenceConstants.TERMUX_APP#KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED} is
     * enabled, then a notification will be shown for the crash on the
     * {@link TermuxConstants#TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME} channel, otherwise nothing will be done.
     *
     * After reading from the crash log file, it will be moved to {@link TermuxConstants#TERMUX_CRASH_LOG_BACKUP_FILE_PATH}.
     *
     * @param context The app context.
     * @param logTagParam The log tag to use for logging.
     */
    public static void notifyAppCrashFromCrashLogFile(Context context, String logTagParam) {
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;

        // If user has disabled notifications for crashes
        if (!preferences.areCrashReportNotificationsEnabled())
            return;

        new Thread() {
            @Override
            public void run() {
                notifyAppCrashFromCrashLogFileInner(context, logTagParam);
            }
        }.start();
    }

    private static synchronized void notifyAppCrashFromCrashLogFileInner(final Context context, final String logTagParam) {
        String logTag = DataUtils.getDefaultIfNull(logTagParam, LOG_TAG);

        if (!FileUtils.regularFileExists(TermuxConstants.TERMUX_CRASH_LOG_FILE_PATH, false))
            return;

        Error error;
        StringBuilder reportStringBuilder = new StringBuilder();

        // Read report string from crash log file
        error = FileUtils.readTextFromFile("crash log", TermuxConstants.TERMUX_CRASH_LOG_FILE_PATH, Charset.defaultCharset(), reportStringBuilder, false);
        if (error != null) {
            Logger.logErrorExtended(logTag, error.toString());
            return;
        }

        // Move crash log file to backup location if it exists
        error = FileUtils.moveRegularFile("crash log", TermuxConstants.TERMUX_CRASH_LOG_FILE_PATH, TermuxConstants.TERMUX_CRASH_LOG_BACKUP_FILE_PATH, true);
        if (error != null) {
            Logger.logErrorExtended(logTag, error.toString());
        }

        String reportString = reportStringBuilder.toString();

        if (reportString.isEmpty())
            return;

        Logger.logDebug(logTag, "A crash log file found at \"" + TermuxConstants.TERMUX_CRASH_LOG_FILE_PATH +  "\".");

        sendCrashReportNotification(context, logTag, null, null, reportString, false, false);
    }




    public static void sendCrashReportNotification(Context context, String logTag, CharSequence title,
                                                   String notificationText, String message,
                                                   boolean forceNotification, boolean includeDiagnostics) {
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;

        // If user has disabled notifications for crashes
        if (!preferences.areCrashReportNotificationsEnabled() && !forceNotification)
            return;

        logTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG);

        // Send a notification to show the crash log which when clicked will open the {@link ReportActivity}
        // to show the details of the crash
        if (title == null || title.toString().isEmpty())
            title = TermuxConstants.TERMUX_APP_NAME + " Crash Report";

        Logger.logDebug(logTag, "Sending \"" + title + "\" notification.");

        StringBuilder reportString = new StringBuilder(message);

        if (includeDiagnostics) {
            reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(context));
            reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
        }

        String userActionName = "crash report";

        ReportInfo reportInfo = new ReportInfo(userActionName, logTag, title.toString());
        reportInfo.setReportString(reportString.toString());
        reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString());
        reportInfo.setAddReportInfoHeaderToMarkdown(true);
        reportInfo.setReportSaveFileLabelAndPath(userActionName,
            Environment.getExternalStorageDirectory() + "/" +
                FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

        ReportActivity.NewInstanceResult result = ReportActivity.newInstance(context, reportInfo);
        if (result.contentIntent == null) return;

        // Must ensure result code for PendingIntents and id for notification are unique otherwise will override previous
        int nextNotificationId = getNextNotificationId(preferences);

        PendingIntent contentIntent = PendingIntent.getActivity(context, nextNotificationId, result.contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent deleteIntent = null;
        if (result.deleteIntent != null)
            deleteIntent = PendingIntent.getBroadcast(context, nextNotificationId, result.deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        setupCrashReportsNotificationChannel(context);
        Notification.Builder builder = NotificationUtils.geNotificationBuilder(context,
            TermuxConstants.TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_HIGH,
            title, notificationText, notificationText, contentIntent, deleteIntent,
            NotificationUtils.NOTIFICATION_MODE_VIBRATE);
        if (builder == null) return;

        builder.setShowWhen(true);
        builder.setSmallIcon(R.drawable.ic_error_notification);
        builder.setColor(0xFF607D8B);
        builder.setAutoCancel(true);

        NotificationManager manager = NotificationUtils.getNotificationManager(context);
        if (manager != null) manager.notify(nextNotificationId, builder.build());
    }

    private static synchronized int getNextNotificationId(TermuxAppSharedPreferences preferences) {
        int next = preferences.getLastNotificationId() + 1;
        if (next == TermuxConstants.TERMUX_APP_NOTIFICATION_ID) next++;
        if (next < 0) next = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID;
        preferences.setLastNotificationId(next);
        return next;
    }

    /**
     * Setup the notification channel for {@link TermuxConstants#TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID} and
     * {@link TermuxConstants#TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME}.
     *
     * @param context The {@link Context} for operations.
     */
    public static void setupCrashReportsNotificationChannel(final Context context) {
        NotificationUtils.setupNotificationChannel(context, TermuxConstants.TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
    }

}
