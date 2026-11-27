package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.app.TermuxService;
import com.termux.shared.termux.terminal.io.BellHandler;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;

import java.util.Arrays;

/** The {@link TerminalSessionClient} implementation that may require an {@link Activity} for its interface methods. */
public class TermuxTerminalSessionActivityClient extends TermuxTerminalSessionClientBase {

    private final TermuxActivity mActivity;
    private final TerminalAppearance mAppearance;

    private SoundPool mBellSoundPool;

    private int mBellSoundId;
    private Integer appliedCursorStyle;

    private static final String LOG_TAG = "TermuxTerminalSessionActivityClient";

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
        this.mAppearance = new TerminalAppearance(activity);
    }

    /** Apply the initial appearance before attaching or creating a terminal session. */
    public void onServiceConnected() {
        checkForFontAndColors();
    }

    public void onSessionsChanged() {
        TermuxService service = mActivity.getTermuxService();
        if (service == null || !mActivity.isVisible()) return;
        TerminalSession active = service.getActiveTerminal();
        if (mActivity.getTerminalView().attachSession(active) && active != null) {
            notifyOfSessionChange();
        }
        mActivity.termuxSessionListNotifyUpdated();
        updateBackgroundColor();
    }

    public void onServiceStopping() {
        mActivity.finishActivityIfNotFinishing();
    }

    public void onStart() {
        onSessionsChanged();
        mActivity.getTerminalView().onScreenUpdated();
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Just initialize the mBellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool();
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Release mBellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool();
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }



    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (!mActivity.isVisible()) return;

        if (mActivity.getCurrentSession() == changedSession) mActivity.getTerminalView().onScreenUpdated();
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mActivity.isVisible() || session != mActivity.getCurrentSession()) return;

        ShareUtils.copyTextToClipboard(mActivity, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mActivity.isVisible() || (session != null && session != mActivity.getCurrentSession())) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null && mActivity.getTerminalView().mEmulator != null)
            mActivity.getTerminalView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (!mActivity.isVisible()) return;

        switch (mActivity.getProperties().getBellBehaviour()) {
            case "vibrate":
                BellHandler.getInstance(mActivity).doBell();
                break;
            case "beep":
                loadBellSoundPool();
                if (mBellSoundPool != null)
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                break;
            case "ignore":
                // Ignore the bell character.
                break;
        }
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mActivity.getCurrentSession() == changedSession)
            updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        // Do not start cursor blinking thread if activity is not visible
        if (enabled && !mActivity.isVisible()) {
            Logger.logVerbose(LOG_TAG, "Ignoring call to start cursor blinking since activity is not visible");
            return;
        }

        // If cursor is to enabled now, then start cursor blinking if blinking is enabled
        // otherwise stop cursor blinking
        mActivity.getTerminalView().setTerminalCursorBlinkerState(enabled, false);
    }

    /**
     * Should be called when mActivity.onResetTerminalSession() is called
     */
    public void onResetTerminalSession() {
        // Ensure blinker starts again after reset if cursor blinking was disabled before reset like
        // with "tput civis" which would have called onTerminalCursorStateChange()
        mActivity.getTerminalView().setTerminalCursorBlinkerState(true, true);
    }



    /** Load mBellSoundPool */
    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build();

            try {
                mBellSoundId = mBellSoundPool.load(mActivity, com.termux.shared.R.raw.bell, 1);
            } catch (Exception e){
                // Catch java.lang.RuntimeException: Unable to resume activity {com.termux/com.termux.app.TermuxActivity}: android.content.res.Resources$NotFoundException: File res/raw/bell.ogg from drawable resource ID
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    /** Release mBellSoundPool resources */
    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }



    private void notifyOfSessionChange() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && !mActivity.getProperties().areTerminalSessionChangeToastsDisabled()) {
            String name = TextUtils.isEmpty(session.mSessionName) ? session.getTitle() : session.mSessionName;
            if (!TextUtils.isEmpty(name)) mActivity.showToast(name, false);
        }
    }

    @SuppressLint("InflateParams")
    public void renameSession(TerminalSession session) {
        if (session == null) return;
        TextInputDialogUtils.textInput(mActivity, R.string.title_rename_session, session.mSessionName,
            R.string.action_rename_session_confirm, name -> {
                TermuxService service = mActivity.getTermuxService();
                if (service != null) service.renameSession(session.mHandle, name);
            }, -1, null, -1, null, null);
    }

    private void checkForFontAndColors() {
        checkForColors();
        checkForFont();
        checkForCursorStyle();
    }

    private void checkForColors() {
        int[] previousColors = TerminalColors.COLOR_SCHEME.mDefaultColors.clone();
        mAppearance.applyColorScheme(TerminalColors.COLOR_SCHEME);

        TermuxService service = mActivity.getTermuxService();
        if (service != null && !Arrays.equals(previousColors, TerminalColors.COLOR_SCHEME.mDefaultColors)) {
            for (SessionRegistry.SessionInfo session : service.getSessions()) {
                refreshSessionColors(service.getTerminal(session.id));
            }
        }

        updateBackgroundColor();
        mActivity.getTerminalView().invalidate();
    }

    private void checkForFont() {
        mActivity.getTerminalView().setTypeface(mAppearance.loadFont());
    }

    private void checkForCursorStyle() {
        Integer style = getTerminalCursorStyle();
        if (appliedCursorStyle == null) {
            appliedCursorStyle = style;
            return;
        }
        if (appliedCursorStyle.equals(style)) return;
        appliedCursorStyle = style;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        for (SessionRegistry.SessionInfo info : service.getSessions()) {
            TerminalSession session = service.getTerminal(info.id);
            if (session != null && session.getEmulator() != null) session.getEmulator().setCursorStyle();
        }
        mActivity.getTerminalView().invalidate();
    }

    private static void refreshSessionColors(@Nullable TerminalSession session) {
        if (session != null && session.getEmulator() != null) session.getEmulator().mColors.refreshDefaults();
    }

    public void updateBackgroundColor() {
        if (!mActivity.isVisible()) return;
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            mActivity.getWindow().getDecorView().setBackgroundColor(session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }

}
