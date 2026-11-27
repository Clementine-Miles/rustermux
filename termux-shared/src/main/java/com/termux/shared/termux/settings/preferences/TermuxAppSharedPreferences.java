package com.termux.shared.termux.settings.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

public class TermuxAppSharedPreferences {

    private final SharedPreferences mSharedPreferences;
    private final TermuxConfig config;

    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE;
    private int DEFAULT_FONTSIZE;

    private TermuxAppSharedPreferences(@NonNull Context context) {
        mSharedPreferences = SharedPreferenceUtils.getPrivateSharedPreferences(context,
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION);
        config = TermuxConfig.get(context);
        setFontVariables(context);
    }

    public static TermuxAppSharedPreferences build(@NonNull Context context) {
        return new TermuxAppSharedPreferences(context);
    }

    public boolean shouldShowTerminalToolbar() { return config.snapshot().get(ConfigKeys.SHOW_EXTRA_KEYS); }

    public void setShowTerminalToolbar(boolean value) { config.set(ConfigKeys.SHOW_EXTRA_KEYS, value); }

    public boolean isTerminalMarginAdjustmentEnabled() { return config.snapshot().get(ConfigKeys.KEYBOARD_MARGIN); }

    public boolean isSoftKeyboardEnabled() { return config.snapshot().get(ConfigKeys.SOFT_KEYBOARD); }

    public void setSoftKeyboardEnabled(boolean value) { config.set(ConfigKeys.SOFT_KEYBOARD, value); }

    public boolean isSoftKeyboardEnabledOnlyIfNoHardware() { return config.snapshot().get(ConfigKeys.SOFT_KEYBOARD_WITHOUT_HARDWARE); }

    public boolean shouldKeepScreenOn() { return config.snapshot().get(ConfigKeys.KEEP_SCREEN_ON); }

    public static int[] getDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());

        int[] sizes = new int[3];

        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        sizes[1] = (int) (4f * dipInPixels); // min

        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(12 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        sizes[0] = defaultFontSize; // default

        sizes[2] = 256; // max

        return sizes;
    }

    public void setFontVariables(Context context) {
        int[] sizes = getDefaultFontSizes(context);

        DEFAULT_FONTSIZE = sizes[0];
        MIN_FONTSIZE = sizes[1];
        MAX_FONTSIZE = sizes[2];
    }

    public int getFontSize() {
        int configured = config.snapshot().get(ConfigKeys.FONT_SIZE);
        int fontSize = configured == 0 ? DEFAULT_FONTSIZE : configured;
        return DataUtils.clamp(fontSize, MIN_FONTSIZE, MAX_FONTSIZE);
    }

    public void setFontSize(int value) {
        config.set(ConfigKeys.FONT_SIZE, value);
    }

    public void changeFontSize(boolean increase) {
        int fontSize = getFontSize();

        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));

        setFontSize(fontSize);
    }



    public int getLogLevel() { return config.snapshot().get(ConfigKeys.LOG_LEVEL); }

    public int getLastNotificationId() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID);
    }

    public void setLastNotificationId(int notificationId) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, notificationId, false);
    }


    public synchronized int getAndIncrementAppShellNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetAppShellNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true);
    }

    public synchronized int getAndIncrementTerminalSessionNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetTerminalSessionNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true);
    }


    public boolean isTerminalViewKeyLoggingEnabled() { return config.snapshot().get(ConfigKeys.KEY_LOGGING); }

    public boolean areCrashReportNotificationsEnabled() { return config.snapshot().get(ConfigKeys.CRASH_NOTIFICATIONS); }
}
