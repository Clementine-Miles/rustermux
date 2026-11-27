package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.SessionRegistry.SessionInfo;
import com.termux.app.terminal.SessionTabs;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.SessionShortcut;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.io.FullScreenWorkAround;
import com.termux.app.ui.AppIcons;
import com.termux.app.ui.TerminalScreen;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.shared.view.UiColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Terminal settings resolved from the current TOML configuration
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    private TerminalScreen mScreen;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    private SessionTabs mSessionTabs;
    private TextView mEmptySessionsMessage;
    private SessionLaunchRequests mSessionRequests;
    private boolean mBootstrapInProgress;
    private boolean mBootstrapReady;
    private boolean mServiceBound;
    private boolean mPreviousProcessHadSessions;
    private boolean mSessionControl;
    private PopupMenu mSessionMenu;
    private final Set<Integer> mAcceptedControlKeys = new HashSet<>();
    private final EnumMap<SessionAction, SessionShortcut> mSessionShortcuts = new EnumMap<>(SessionAction.class);

    private enum SessionAction {
        TOGGLE(ConfigKeys.SHORTCUT_TOGGLE_CONTROL),
        CREATE(ConfigKeys.SHORTCUT_CREATE),
        PREVIOUS(ConfigKeys.SHORTCUT_PREVIOUS),
        NEXT(ConfigKeys.SHORTCUT_NEXT),
        RENAME(ConfigKeys.SHORTCUT_RENAME),
        TERMINATE(ConfigKeys.SHORTCUT_TERMINATE),
        CLOSE(ConfigKeys.SHORTCUT_CLOSE),
        MOVE_LEFT(ConfigKeys.SHORTCUT_MOVE_LEFT),
        MOVE_RIGHT(ConfigKeys.SHORTCUT_MOVE_RIGHT),
        TAB_BAR(ConfigKeys.SHORTCUT_TOGGLE_TAB_BAR);

        final ConfigKeys.Key<String> key;

        SessionAction(ConfigKeys.Key<String> key) {
            this.key = key;
        }
    }

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final int REQUEST_SETTINGS = 100;
    private static final int CONTEXT_MENU_COMPOSE_TEXT_ID = 12;
    private static final int CONTEXT_MENU_SHOW_CONTROLS_ID = 13;
    private static final String ARG_INITIAL_SESSION = "initial_session";
    private static final String ARG_SESSION_REQUESTS = "session_requests";
    private static final String ARG_PROCESS = "session_process";
    private static final String ARG_HAD_SESSIONS = "had_sessions";
    private static final String PROCESS_MARKER = UUID.randomUUID().toString();

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        mIsActivityRecreated = savedInstanceState != null;
        if (savedInstanceState == null) {
            boolean shortcutLaunch = getIntent() != null && Intent.ACTION_RUN.equals(getIntent().getAction());
            mSessionRequests = new SessionLaunchRequests(!shortcutLaunch, null);
            if (shortcutLaunch) enqueueShortcut(getIntent());
        } else {
            mSessionRequests = new SessionLaunchRequests(savedInstanceState.getBoolean(ARG_INITIAL_SESSION),
                savedInstanceState.getBooleanArray(ARG_SESSION_REQUESTS));
            String previousProcess = savedInstanceState.getString(ARG_PROCESS);
            mPreviousProcessHadSessions = previousProcess != null && !PROCESS_MARKER.equals(previousProcess)
                && savedInstanceState.getBoolean(ARG_HAD_SESSIONS);
        }

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        mScreen = new TerminalScreen(this);
        setContentView(mScreen.root);
        refreshControlAppearance();

        mPreferences = TermuxAppSharedPreferences.build(this);

        setMargins();

        mTermuxActivityRootView = mScreen.root;
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = mScreen.bottomInset;
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setExtraKeys();
        setTerminalControls();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            mServiceBound = bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
            if (!mServiceBound)
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;
        reloadActivityStyling();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();
        mTerminalView.setKeepScreenOn(mPreferences.shouldKeepScreenOn());
        updateExtraKeysVisibility();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
        startRequestedSessions();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Show a notification if the previous run left a crash log.
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        if (mSessionMenu != null) mSessionMenu.dismiss();
        mAcceptedControlKeys.clear();
        setSessionControl(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            mTermuxService.detachActivity(mTermuxTerminalSessionActivityClient);
            mTermuxService = null;
        }
        if (mServiceBound) {
            unbindService(this);
            mServiceBound = false;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(ARG_INITIAL_SESSION, mSessionRequests.hasInitial());
        state.putBooleanArray(ARG_SESSION_REQUESTS, mSessionRequests.savedRequests());
        state.putString(ARG_PROCESS, PROCESS_MARKER);
        state.putBoolean(ARG_HAD_SESSIONS, mTermuxService != null && mTermuxService.getActiveTerminal() != null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (enqueueShortcut(intent)) startRequestedSessions();
    }

    private boolean enqueueShortcut(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_RUN.equals(intent.getAction())) return false;
        mSessionRequests.add(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false));
        return true;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        mTermuxTerminalSessionActivityClient.onServiceConnected();
        mTermuxService.attachActivity(mTermuxTerminalSessionActivityClient);
        mTermuxTerminalSessionActivityClient.onSessionsChanged();
        startRequestedSessions();
    }

    public void requestNewSession(boolean failsafe) {
        mSessionRequests.add(failsafe);
        startRequestedSessions();
    }

    private void startRequestedSessions() {
        if (mTermuxService == null || !mIsVisible || isFinishing()) return;
        Boolean failsafe;
        while ((failsafe = mSessionRequests.next(mTermuxService.getActiveTerminal() != null)) != null) {
            if (!failsafe && !mBootstrapReady) {
                if (!mBootstrapInProgress) {
                    mBootstrapInProgress = true;
                    TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
                        mBootstrapInProgress = false;
                        mBootstrapReady = true;
                        startRequestedSessions();
                    });
                }
                return;
            }

            mSessionRequests.consume();
            if (mTermuxService.getSessions().size() >= TermuxService.MAX_SESSIONS) {
                mSessionRequests.clear();
                new AlertDialog.Builder(this).setTitle(R.string.title_max_terminals_reached)
                    .setMessage(R.string.msg_max_terminals_reached).setPositiveButton(android.R.string.ok, null).show();
                return;
            }
            if (!mTermuxService.createSession(failsafe, null)) {
                showToast(getString(R.string.error_starting_session), true);
                continue;
            }
            mTerminalView.requestFocus();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        mTermuxService = null;
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        TermuxConfig config = TermuxConfig.get(this);
        config.reload();
        loadSessionShortcuts(config.snapshot());
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(mScreen.terminalContainer, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = mScreen.terminal;
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();
    }

    private void setExtraKeys() {
        mExtraKeysView = mScreen.extraKeys;
        mTerminalToolbarDefaultHeight = mExtraKeysView.getLayoutParams().height;
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);
        mExtraKeysView.setExtraKeysViewClient(mTermuxTerminalExtraKeys);
        reloadExtraKeys();
        if (mProperties.isUsingFullScreen() && mProperties.isUsingFullScreenWorkAround()) {
            FullScreenWorkAround.apply(this);
        }
        updateExtraKeysVisibility();
    }

    private void reloadExtraKeys() {
        mTermuxTerminalExtraKeys.reloadExtraKeys();
        mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
        mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo());
        int rows = mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 :
            mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        ViewGroup.LayoutParams layoutParams = mExtraKeysView.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight * rows * mProperties.getTerminalToolbarHeightScaleFactor());
        mExtraKeysView.setLayoutParams(layoutParams);
    }

    private void updateExtraKeysVisibility() {
        mExtraKeysView.setVisibility(mPreferences.shouldShowTerminalToolbar() && mTerminalView.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    public void toggleExtraKeys() {
        mPreferences.setShowTerminalToolbar(!mPreferences.shouldShowTerminalToolbar());
        updateExtraKeysVisibility();
    }

    private void refreshControlAppearance() {
        UiColors colors = UiColors.resolve(this);
        mScreen.setColors(colors);
        if (mEmptySessionsMessage != null) mEmptySessionsMessage.setTextColor(colors.foreground);
        AppIcons.apply(mScreen.keyboardButton, AppIcons.Icon.KEYBOARD, getText(R.string.action_toggle_soft_keyboard));
        AppIcons.apply(mScreen.settingsButton, AppIcons.Icon.SETTINGS, getText(R.string.action_open_settings));
    }

    private void setTerminalControls() {
        mSessionTabs = new SessionTabs(this, () -> requestNewSession(false),
            () -> setSessionControl(!mSessionControl), view -> {
                showSessionMenu();
                return true;
            }, id -> {
                if (mTermuxService != null) mTermuxService.selectSession(id);
                mTerminalView.requestFocus();
            }, this::showSessionActions);
        mScreen.setSessionTabs(mSessionTabs);

        mEmptySessionsMessage = new TextView(this);
        mEmptySessionsMessage.setText(R.string.no_terminal_sessions);
        mEmptySessionsMessage.setGravity(Gravity.CENTER);
        mScreen.terminalContainer.addView(mEmptySessionsMessage, 0,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        termuxSessionListNotifyUpdated();

        Button keyboard = mScreen.keyboardButton;
        keyboard.setOnClickListener(view -> mTermuxTerminalViewClient.onToggleSoftKeyboardRequest());

        Button settings = mScreen.settingsButton;
        settings.setOnClickListener(view -> startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS));
        mTerminalView.requestFocus();
    }

    public void showSessionMenu() {
        if (mSessionTabs == null || mSessionMenu != null) return;
        if (!showsSessionTabs()) setSessionControl(true);
        PopupMenu menu = new PopupMenu(this, mSessionTabs.modeAnchor());
        mSessionMenu = menu;
        menu.setOnDismissListener(popup -> mSessionMenu = null);
        addSessionMenuItem(menu, R.string.action_new_session, () -> requestNewSession(false));
        addSessionMenuItem(menu, R.string.action_new_session_failsafe, () -> requestNewSession(true));
        addSessionMenuItem(menu, showsSessionTabs() ? R.string.action_hide_session_tabs : R.string.action_show_session_tabs,
            this::toggleSessionTabs);

        if (mTermuxService != null) {
            for (SessionInfo info : mTermuxService.getSessions()) {
                String name = info.name == null ? getString(R.string.unnamed_session, info.number) : info.name;
                menu.getMenu().add(name).setOnMenuItemClickListener(item -> {
                    if (mTermuxService != null && mTermuxService.getTerminal(info.id) != null) {
                        mTermuxService.selectSession(info.id);
                        if (info.active) showSessionActions(info.id);
                    }
                    return true;
                });
            }
        }
        menu.show();
    }

    private void showSessionActions(String id) {
        TerminalSession terminal = mTermuxService == null ? null : mTermuxService.getTerminal(id);
        if (terminal == null) return;
        CharSequence[] actions = {getText(R.string.title_rename_session), getText(R.string.action_move_session_left),
            getText(R.string.action_move_session_right), getText(R.string.action_reset_terminal),
            getText(terminal.isRunning() ? R.string.action_terminate_session : R.string.action_close_session)};
        new AlertDialog.Builder(this).setTitle(R.string.action_manage_session).setItems(actions, (dialog, index) -> {
            if (mTermuxService == null || mTermuxService.getTerminal(id) == null) return;
            switch (index) {
                case 0: mTermuxTerminalSessionActivityClient.renameSession(terminal); break;
                case 1: mTermuxService.moveSession(id, -1); break;
                case 2: mTermuxService.moveSession(id, 1); break;
                case 3: onResetTerminalSession(terminal); break;
                case 4:
                    if (terminal.isRunning()) showKillSessionDialog(terminal);
                    else mTermuxService.closeSession(id);
                    break;
            }
        }).show();
    }

    private void addSessionMenuItem(PopupMenu menu, int title, Runnable action) {
        menu.getMenu().add(title).setOnMenuItemClickListener(item -> {
            action.run();
            return true;
        });
    }

    private boolean showsSessionTabs() {
        return TermuxConfig.get(this).snapshot().get(ConfigKeys.SHOW_TAB_BAR);
    }

    private void toggleSessionTabs() {
        if (!TermuxConfig.get(this).setTransient(ConfigKeys.SHOW_TAB_BAR, !showsSessionTabs())) {
            reportConfigError();
        }
        termuxSessionListNotifyUpdated();
    }

    private void loadSessionShortcuts(TermuxConfig.Snapshot config) {
        mSessionShortcuts.clear();
        for (SessionAction action : SessionAction.values()) {
            SessionShortcut shortcut = SessionShortcut.parse(config.get(action.key));
            if (shortcut != null) mSessionShortcuts.put(action, shortcut);
        }
    }

    private void setSessionControl(boolean enabled) {
        if (mSessionControl == enabled) return;
        mSessionControl = enabled;
        termuxSessionListNotifyUpdated();
        if (enabled) showToast(getString(R.string.session_control_help), false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && mAcceptedControlKeys.remove(key)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && handleSessionControlKey(event)) {
            mAcceptedControlKeys.add(key);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean handleSessionControlKey(KeyEvent event) {
        if (mIsInvalidState || !mIsVisible || KeyEvent.isModifierKey(event.getKeyCode()) || event.isSystem()
            && event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        SessionShortcut activation = mSessionShortcuts.get(SessionAction.TOGGLE);
        if (activation != null && activation.matches(event)) {
            if (event.getRepeatCount() == 0) setSessionControl(!mSessionControl);
            return true;
        }
        if (!mSessionControl) return false;

        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_BACK) {
            setSessionControl(false);
            return true;
        }
        if (event.getRepeatCount() > 0) return true;
        for (SessionAction action : SessionAction.values()) {
            SessionShortcut shortcut = mSessionShortcuts.get(action);
            if (action != SessionAction.TOGGLE && shortcut != null && shortcut.matches(event)) {
                runSessionAction(action);
                break;
            }
        }
        return true;
    }

    public boolean handleSessionControlCodePoint(int codePoint, boolean control) {
        if (!mSessionControl) return false;
        if (codePoint == 27) {
            setSessionControl(false);
            return true;
        }
        for (SessionAction action : SessionAction.values()) {
            SessionShortcut shortcut = mSessionShortcuts.get(action);
            if (action != SessionAction.TOGGLE && shortcut != null && shortcut.matches(codePoint, control)) {
                runSessionAction(action);
                break;
            }
        }
        return true;
    }

    private void runSessionAction(SessionAction action) {
        if (action == SessionAction.TAB_BAR) {
            toggleSessionTabs();
            return;
        }
        TermuxService service = mTermuxService;
        if (service == null) return;
        TerminalSession active = service.getActiveTerminal();
        switch (action) {
            case CREATE: requestNewSession(false); break;
            case PREVIOUS: service.selectNeighbor(-1); break;
            case NEXT: service.selectNeighbor(1); break;
            case RENAME: mTermuxTerminalSessionActivityClient.renameSession(active); break;
            case TERMINATE: if (active != null && active.isRunning()) showKillSessionDialog(active); break;
            case CLOSE: if (active != null) service.closeSession(active.mHandle); break;
            case MOVE_LEFT: if (active != null) service.moveSession(active.mHandle, -1); break;
            case MOVE_RIGHT: if (active != null) service.moveSession(active.mHandle, 1); break;
            default: break;
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (mSessionControl) setSessionControl(false);
        else finishActivityIfNotFinishing();
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        if (!showsSessionTabs()) {
            menu.add(Menu.NONE, CONTEXT_MENU_SHOW_CONTROLS_ID, Menu.NONE, R.string.action_show_session_tabs);
        }

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        if (currentSession.isRunning()) {
            menu.add(Menu.NONE, CONTEXT_MENU_COMPOSE_TEXT_ID, Menu.NONE, R.string.action_compose_text);
        }
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_export_terminal_report);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SHOW_CONTROLS_ID:
                toggleSessionTabs();
                return true;
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_COMPOSE_TEXT_ID:
                if (session != null && session.isRunning()) {
                    TextInputDialogUtils.textInput(this, R.string.action_compose_text, null,
                        R.string.action_send_text, text -> {
                            if (session.isRunning()) session.write(text.isEmpty() ? "\r" : text);
                        }, -1, null, android.R.string.cancel, null, null);
                }
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.exportTerminalReport();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            if (mTermuxService != null && !mTermuxService.terminateSession(session.mHandle)) {
                showToast(getString(R.string.error_terminating_session), true);
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    /** Request the available storage access mode before creating the shell's storage symlinks. */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK && !mIsInvalidState) {
            reloadActivityStyling();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public void termuxSessionListNotifyUpdated() {
        if (mSessionTabs == null || mEmptySessionsMessage == null) return;
        List<SessionInfo> sessions = mTermuxService == null ? Collections.emptyList() : mTermuxService.getSessions();
        boolean empty = sessions.isEmpty();
        boolean controlsVisible = showsSessionTabs() || mSessionControl || empty;
        mScreen.setControlsVisible(controlsVisible);
        mSessionTabs.render(sessions, controlsVisible, mSessionControl);
        if (!empty) mPreviousProcessHadSessions = false;
        mEmptySessionsMessage.setText(mPreviousProcessHadSessions ? R.string.sessions_lost_with_process : R.string.no_terminal_sessions);
        mEmptySessionsMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        mTerminalView.setVisibility(empty ? View.GONE : View.VISIBLE);
        updateExtraKeysVisibility();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        // Shell commands termux-reload-settings and termux-setup-storage use this action.
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        // The packaged termux-setup-storage command sends the reload action with this extra.
        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling();
                        reportConfigError();
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    public void reloadActivityStyling() {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                refreshControlAppearance();
                reloadExtraKeys();
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        termuxSessionListNotifyUpdated();
    }

    private void reportConfigError() {
        String diagnostic = TermuxConfig.get(this).diagnostic();
        if (diagnostic != null) {
            Logger.logError(LOG_TAG, diagnostic);
            showToast(diagnostic, true);
        }
    }

    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
