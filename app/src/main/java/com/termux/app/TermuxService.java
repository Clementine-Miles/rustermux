package com.termux.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.SessionRegistry;
import com.termux.app.terminal.SessionRegistry.SessionInfo;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalSessionServiceClient;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.net.uri.UriUtils;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.TermuxShellUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.terminal.TerminalSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns interactive PTYs and internal background commands for the app.
 * The user interacts with the session through {@link TermuxActivity}, but this service may outlive
 * the activity when the user or the system disposes of the activity. In that case the user may
 * restart {@link TermuxActivity} later to yet again access the sessions.
 * <p/>
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, {@link Service#startForeground(int, Notification)}.
 * <p/>
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * {@link #buildNotification()}.
 */
public final class TermuxService extends Service implements AppShell.AppShellClient, TermuxSession.TermuxSessionClient {

    /** This service is only bound from inside the same process and never uses IPC. */
    class LocalBinder extends Binder {
        public final TermuxService service = TermuxService.this;
    }

    private final IBinder mBinder = new LocalBinder();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SessionRegistry<TermuxSession> sessions = new SessionRegistry<>();
    private final List<AppShell> tasks = new ArrayList<>();
    private final TermuxTerminalSessionServiceClient terminalClient = new TermuxTerminalSessionServiceClient(this);

    public static final int MAX_SESSIONS = 8;
    private static final int INITIAL_COLUMNS = 80;
    private static final int INITIAL_ROWS = 24;

    private TermuxTerminalSessionActivityClient activityClient;
    private TermuxAppSharedProperties mProperties;
    private boolean activityBound;
    private boolean foreground;

    /** The wake lock and wifi lock are always acquired and released together. */
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    /** If the user has executed the {@link TERMUX_SERVICE#ACTION_STOP_SERVICE} intent. */
    boolean mWantsToStop = false;

    private static final String LOG_TAG = "TermuxService";

    @Override
    public void onCreate() {
        Logger.logVerbose(LOG_TAG, "onCreate");

        // Get Termux app SharedProperties without loading from disk since TermuxApplication handles
        // load and TermuxActivity handles reloads
        mProperties = TermuxAppSharedProperties.getProperties();

        runStartForeground();
    }

    @SuppressLint("Wakelock")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        // Run again in case service is already started and onCreate() is not called
        runStartForeground();

        String action = null;
        if (intent != null) {
            Logger.logVerboseExtended(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));
            action = intent.getAction();
        }

        if (action != null) {
            switch (action) {
                case TERMUX_SERVICE.ACTION_STOP_SERVICE:
                    Logger.logDebug(LOG_TAG, "ACTION_STOP_SERVICE intent received");
                    actionStopService();
                    break;
                case TERMUX_SERVICE.ACTION_WAKE_LOCK:
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_LOCK intent received");
                    actionAcquireWakeLock();
                    break;
                case TERMUX_SERVICE.ACTION_WAKE_UNLOCK:
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_UNLOCK intent received");
                    actionReleaseWakeLock(true);
                    break;
                case TERMUX_SERVICE.ACTION_SERVICE_EXECUTE:
                    Logger.logDebug(LOG_TAG, "ACTION_SERVICE_EXECUTE intent received");
                    actionServiceExecute(intent);
                    break;
                default:
                    Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
                    break;
            }
        }

        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.logVerbose(LOG_TAG, "onDestroy");

        if (hasInitializedPrefix()) TermuxShellUtils.cleanTemporaryFilesOnExit();

        actionReleaseWakeLock(false);
        if (!mWantsToStop)
            killAllTermuxExecutionCommands();

        TermuxShellManager.onAppExit();

        runStopForeground();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        activityBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        activityBound = false;
        detachActivity(activityClient);
        updateNotification();
        return false;
    }

    private void runStartForeground() {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification());
        foreground = true;
    }

    private void runStopForeground() {
        if (!foreground) return;
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
    }

    /** Request to stop service. */
    private void requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service");
        runStopForeground();
        stopSelf();
    }

    /** Process action to stop service. */
    private void actionStopService() {
        mWantsToStop = true;
        killAllTermuxExecutionCommands();
        if (activityClient != null) activityClient.onServiceStopping();
        requestStopService();
    }

    /** End interactive sessions while letting detached background commands finish. */
    private void killAllTermuxExecutionCommands() {
        for (SessionInfo session : sessions.snapshot()) {
            TermuxSession terminal = sessions.get(session.id);
            if (terminal != null) terminal.getTerminalSession().finishIfRunning();
        }
        sessions.clear();
        tasks.clear();
    }

    /** Process action to acquire Power and Wi-Fi WakeLocks. */
    @SuppressLint({"WakelockTimeout", "BatteryLife"})
    private void actionAcquireWakeLock() {
        if (mWakeLock != null) {
            Logger.logDebug(LOG_TAG, "Ignoring acquiring WakeLocks since they are already held");
            return;
        }

        Logger.logDebug(LOG_TAG, "Acquiring WakeLocks");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TermuxConstants.TERMUX_APP_NAME.toLowerCase() + ":service-wakelock");
        mWakeLock.acquire();

        // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TermuxConstants.TERMUX_APP_NAME.toLowerCase());
        mWifiLock.acquire();

        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
            PermissionUtils.requestDisableBatteryOptimizations(this);
        }

        updateNotification();

        Logger.logDebug(LOG_TAG, "WakeLocks acquired successfully");

    }

    /** Process action to release Power and Wi-Fi WakeLocks. */
    private void actionReleaseWakeLock(boolean updateNotification) {
        if (mWakeLock == null && mWifiLock == null) {
            Logger.logDebug(LOG_TAG, "Ignoring releasing WakeLocks since none are already held");
            return;
        }

        Logger.logDebug(LOG_TAG, "Releasing WakeLocks");

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }

        if (updateNotification)
            updateNotification();

        Logger.logDebug(LOG_TAG, "WakeLocks released successfully");
    }

    /** Start a command requested by an internal file, notification, or scheduling action. */
    private void actionServiceExecute(Intent intent) {
        if (!hasInitializedPrefix()) {
            Logger.logErrorAndShowToast(this, LOG_TAG, getString(R.string.command_requires_bootstrap));
            updateNotification();
            return;
        }
        boolean background = intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, false);
        ExecutionCommand command = new ExecutionCommand(TermuxShellManager.getNextShellId(),
            intent.getData() == null ? null : UriUtils.getUriFilePathWithFragment(intent.getData()),
            IntentUtils.getStringArrayExtraIfSet(intent, TERMUX_SERVICE.EXTRA_ARGUMENTS, null),
            null, intent.getStringExtra(TERMUX_SERVICE.EXTRA_WORKDIR),
            background ? Runner.APP_SHELL.getName() : Runner.TERMINAL_SESSION.getName(), false);

        if (background) {
            createTermuxTask(command);
        } else {
            if (startTerminal(command)) startTermuxActivity();
        }
    }

    private boolean hasInitializedPrefix() {
        try {
            return BootstrapInstallation.inspect(TermuxConstants.TERMUX_PREFIX_DIR.toPath())
                == BootstrapInstallation.State.INITIALIZED;
        } catch (IOException failure) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not inspect the package environment", failure);
            return false;
        }
    }

    private synchronized void createTermuxTask(ExecutionCommand executionCommand) {
        executionCommand.setShellCommandShellEnvironment = true;

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE)
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());

        AppShell newTermuxTask = AppShell.execute(this, executionCommand, this,
            new TermuxShellEnvironment(), null,false);
        if (newTermuxTask == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxTask command for:\n" + executionCommand.getCommandIdAndLabelLogString());
            return;
        }

        tasks.add(newTermuxTask);

        updateNotification();

    }

    /** Callback received when a TermuxTask finishes. */
    @Override
    public void onAppShellExited(final AppShell termuxTask) {
        mHandler.post(() -> {
            if (termuxTask != null) {
                ExecutionCommand executionCommand = termuxTask.getExecutionCommand();

                Logger.logVerbose(LOG_TAG, "The onTermuxTaskExited() callback called for \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxTask command");

                tasks.remove(termuxTask);
            }

            updateNotification();
        });
    }





    public boolean createSession(boolean failsafe, @Nullable String name) {
        TerminalSession active = getActiveTerminal();
        String directory = active == null ? null : active.getCwd();
        if (directory == null) directory = mProperties.getDefaultWorkingDirectory();
        if (failsafe && (directory == null || !new java.io.File(directory).isDirectory())) directory = getFilesDir().getPath();
        ExecutionCommand command = new ExecutionCommand(TermuxShellManager.getNextShellId(),
            null, null, null, directory, Runner.TERMINAL_SESSION.getName(), failsafe);
        command.shellName = name;
        return startTerminal(command);
    }

    private boolean startTerminal(ExecutionCommand command) {
        if (mWantsToStop || sessions.size() >= MAX_SESSIONS) return false;
        command.setShellCommandShellEnvironment = true;
        command.terminalTranscriptRows = mProperties.getTerminalTranscriptRows();

        TermuxSession result = TermuxSession.execute(this, command, terminalClient,
            this, new TermuxShellEnvironment(), null);
        if (result == null) return false;

        TerminalSession previous = getActiveTerminal();
        TerminalSession terminal = result.getTerminalSession();
        sessions.create(terminal.mHandle, command.shellName, result);
        try {
            // Start independently of view attachment. The visible view supplies its measured size.
            terminal.updateSize(INITIAL_COLUMNS, INITIAL_ROWS, 8, 16);
        } catch (RuntimeException exception) {
            terminal.finishIfRunning();
            sessions.creationFailed(terminal.mHandle);
            if (previous != null) sessions.select(previous.mHandle);
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start terminal session", exception);
            return false;
        }
        sessions.started(terminal.mHandle);
        notifySessionsChanged();
        updateNotification();
        return true;
    }

    public void onTerminalProcessFinished(@NonNull TerminalSession terminal) {
        TermuxSession session = sessions.get(terminal.mHandle);
        if (session != null) session.finish();
    }

    @Override
    public void onTermuxSessionExited(TermuxSession session) {
        if (sessions.exited(session.getTerminalSession().mHandle)) {
            notifySessionsChanged();
            updateNotification();
        }
    }

    public void setShellPid(TerminalSession terminal, int pid) {
        TermuxSession session = sessions.get(terminal.mHandle);
        if (session != null) session.getExecutionCommand().mPid = pid;
    }

    @Nullable
    public TerminalSession getTerminal(String id) {
        TermuxSession session = sessions.get(id);
        return session == null ? null : session.getTerminalSession();
    }

    @Nullable
    public TerminalSession getActiveTerminal() {
        TermuxSession session = sessions.active();
        return session == null ? null : session.getTerminalSession();
    }

    public List<SessionInfo> getSessions() {
        return sessions.snapshot();
    }

    public void selectSession(String id) {
        if (sessions.select(id)) notifySessionsChanged();
    }

    public void selectNeighbor(int direction) {
        if (sessions.selectNeighbor(direction)) notifySessionsChanged();
    }

    public void moveSession(String id, int direction) {
        if (sessions.move(id, direction)) notifySessionsChanged();
    }

    public void renameSession(String id, String name) {
        TermuxSession session = sessions.get(id);
        if (session == null) return;
        String normalized = name.trim();
        if (normalized.isEmpty()) normalized = null;
        if (sessions.rename(id, normalized)) {
            session.getExecutionCommand().shellName = normalized;
            session.getTerminalSession().mSessionName = normalized;
            notifySessionsChanged();
        }
    }

    public boolean terminateSession(String id) {
        TerminalSession terminal = getTerminal(id);
        if (terminal == null || !sessions.terminating(id)) return false;
        if (!terminal.finishIfRunning()) {
            sessions.terminationFailed(id);
            return false;
        }
        notifySessionsChanged();
        return true;
    }

    public void closeSession(String id) {
        if (sessions.close(id)) {
            notifySessionsChanged();
            updateNotification();
        }
    }

    private void notifySessionsChanged() {
        if (activityClient != null) activityClient.onSessionsChanged();
    }

    /** Open the terminal after an internal command when background launches are permitted. */
    private void startTermuxActivity() {
        if (PermissionUtils.validateDisplayOverOtherAppsPermission(this, true)) {
            TermuxActivity.startTermuxActivity(this);
        } else {
            Logger.showToast(this, getString(R.string.error_display_over_other_apps_permission_not_granted_to_start_terminal), true);
        }
    }





    public void attachActivity(TermuxTerminalSessionActivityClient client) {
        activityClient = client;
        terminalClient.setObserver(client);
        updateNotification();
    }

    public void detachActivity(TermuxTerminalSessionActivityClient client) {
        if (activityClient == client) {
            activityClient = null;
            terminalClient.setObserver(null);
        }
    }

    public Integer getCursorStyle() {
        return mProperties.getTerminalCursorStyle();
    }

    private Notification buildNotification() {
        Resources res = getResources();

        // Set pending intent to be launched when notification is clicked
        Intent notificationIntent = TermuxActivity.newInstance(this);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);


        // Set notification text
        int sessionCount = sessions.size();
        int taskCount = tasks.size();
        String notificationText = sessionCount + " session" + (sessionCount == 1 ? "" : "s");
        if (taskCount > 0) {
            notificationText += ", " + taskCount + " task" + (taskCount == 1 ? "" : "s");
        }

        final boolean wakeLockHeld = mWakeLock != null;
        if (wakeLockHeld) notificationText += " (wake lock held)";


        // Set notification priority
        // If holding a wake or wifi lock consider the notification of high priority since it's using power,
        // otherwise use a low priority
        int priority = (wakeLockHeld) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW;


        // Build the notification
        Notification.Builder builder =  NotificationUtils.geNotificationBuilder(this,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, priority,
            TermuxConstants.TERMUX_APP_NAME, notificationText, null,
            contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null)  return null;

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Set notification icon
        builder.setSmallIcon(R.drawable.ic_service_notification);

        // Set background color for small notification icon
        builder.setColor(0xFF607D8B);

        // TermuxSessions are always ongoing
        builder.setOngoing(true);


        // Set Exit button action
        Intent exitIntent = new Intent(this, TermuxService.class).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit), PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE));


        // Set Wakelock button actions
        String newWakeAction = wakeLockHeld ? TERMUX_SERVICE.ACTION_WAKE_UNLOCK : TERMUX_SERVICE.ACTION_WAKE_LOCK;
        Intent toggleWakeLockIntent = new Intent(this, TermuxService.class).setAction(newWakeAction);
        String actionTitle = res.getString(wakeLockHeld ? R.string.notification_action_wake_unlock : R.string.notification_action_wake_lock);
        int actionIcon = wakeLockHeld ? android.R.drawable.ic_lock_idle_lock : android.R.drawable.ic_lock_lock;
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, PendingIntent.FLAG_IMMUTABLE));


        return builder.build();
    }

    private void setupNotificationChannel() {
        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    }

    /** The empty UI can stay bound without a foreground notification. */
    private void updateNotification() {
        if (mWantsToStop) return;
        if (mWakeLock == null && sessions.size() == 0 && tasks.isEmpty()) {
            if (activityBound) runStopForeground();
            else requestStopService();
        } else if (!foreground) {
            runStartForeground();
        } else {
            getSystemService(NotificationManager.class).notify(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification());
        }
    }

    public boolean wantsToStop() {
        return mWantsToStop;
    }

}
