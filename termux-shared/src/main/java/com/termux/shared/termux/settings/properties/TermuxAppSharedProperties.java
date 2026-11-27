package com.termux.shared.termux.settings.properties;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.terminal.TerminalEmulator;

import java.io.File;

/** Typed terminal settings used by the service and terminal clients. */
public final class TermuxAppSharedProperties {
    private static TermuxAppSharedProperties properties;
    private final TermuxConfig config;

    private TermuxAppSharedProperties(Context context) {
        config = TermuxConfig.get(context);
    }

    public static synchronized TermuxAppSharedProperties init(Context context) {
        if (properties == null) properties = new TermuxAppSharedProperties(context);
        return properties;
    }

    public static TermuxAppSharedProperties getProperties() {
        return properties;
    }

    public void loadTermuxPropertiesFromDisk() {
        config.reload();
        Logger.setLogLevel(null, get(ConfigKeys.LOG_LEVEL));
    }

    private <T> T get(ConfigKeys.Key<T> key) {
        return config.snapshot().get(key);
    }

    public boolean isFileShareReceiverDisabled() { return get(ConfigKeys.FILE_SHARE_DISABLED); }
    public boolean isFileViewReceiverDisabled() { return get(ConfigKeys.FILE_VIEW_DISABLED); }
    public boolean areTerminalSessionChangeToastsDisabled() { return !get(ConfigKeys.SHOW_SESSION_TOAST); }
    public boolean isEnforcingCharBasedInput() { return get(ConfigKeys.CHAR_BASED_INPUT); }
    public boolean shouldExtraKeysTextBeAllCaps() { return get(ConfigKeys.EXTRA_KEYS_ALL_CAPS); }
    public boolean shouldSoftKeyboardBeHiddenOnStartup() { return get(ConfigKeys.HIDE_SOFT_KEYBOARD_ON_START); }
    public boolean shouldRunTermuxAmSocketServer() { return get(ConfigKeys.AM_SOCKET); }
    public boolean shouldOpenTerminalTranscriptURLOnClick() { return get(ConfigKeys.OPEN_LINK_ON_TAP); }
    public boolean isUsingCtrlSpaceWorkaround() { return get(ConfigKeys.CTRL_SPACE_WORKAROUND); }
    public boolean isUsingFullScreen() { return get(ConfigKeys.FULL_SCREEN); }
    public boolean isUsingFullScreenWorkAround() { return get(ConfigKeys.FULL_SCREEN_WORKAROUND); }

    public int getDeleteTMPDIRFilesOlderThanXDaysOnExit() { return get(ConfigKeys.TMP_CLEANUP_DAYS); }
    public int getTerminalCursorBlinkRate() { return get(ConfigKeys.CURSOR_BLINK); }
    public int getTerminalMarginHorizontal() { return get(ConfigKeys.MARGIN_HORIZONTAL); }
    public int getTerminalMarginVertical() { return get(ConfigKeys.MARGIN_VERTICAL); }
    public int getTerminalTranscriptRows() { return get(ConfigKeys.TRANSCRIPT_ROWS); }
    public float getTerminalToolbarHeightScaleFactor() { return get(ConfigKeys.EXTRA_KEYS_HEIGHT).floatValue(); }

    public boolean isBackKeyTheEscapeKey() { return "escape".equals(get(ConfigKeys.BACK_KEY)); }
    public boolean areVirtualVolumeKeysDisabled() { return "volume".equals(get(ConfigKeys.VOLUME_KEYS)); }
    public boolean shouldEnableDisableSoftKeyboardOnToggle() { return "enable/disable".equals(get(ConfigKeys.KEYBOARD_TOGGLE)); }
    public String getNightMode() { return get(ConfigKeys.NIGHT_MODE); }
    public String getBellBehaviour() { return get(ConfigKeys.BELL); }

    public int getTerminalCursorStyle() {
        switch (get(ConfigKeys.CURSOR_STYLE)) {
            case "underline": return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
            case "bar": return TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
            default: return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
        }
    }

    public String getDefaultWorkingDirectory() {
        String path = config.workingDirectory(get(ConfigKeys.WORKING_DIRECTORY));
        File directory = new File(path);
        return directory.isDirectory() && directory.canRead() ? path : config.workingDirectory("~");
    }
}
