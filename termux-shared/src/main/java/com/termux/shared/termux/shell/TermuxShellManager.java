package com.termux.shared.termux.shell;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;


public final class TermuxShellManager {

    private TermuxShellManager() {}

    private static int SHELL_ID = 0;

    /**
     * The {@link ExecutionCommand.Runner#APP_SHELL} number after app process was started/restarted.
     */
    public static int APP_SHELL_NUMBER_SINCE_APP_START;

    /**
     * The {@link ExecutionCommand.Runner#TERMINAL_SESSION} number after app process was started/restarted.
     */
    public static int TERMINAL_SESSION_NUMBER_SINCE_APP_START;



    public synchronized static void onActionBootCompleted(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;

        // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_BOOT and
        // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_BOOT exported
        preferences.resetAppShellNumberSinceBoot();
        preferences.resetTerminalSessionNumberSinceBoot();
    }

    public static synchronized void onAppExit() {
        // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START and
        // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START exported
        APP_SHELL_NUMBER_SINCE_APP_START = 0;
        TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0;
    }

    public static synchronized int getNextShellId() {
        return SHELL_ID++;
    }

    public static synchronized int getAndIncrementAppShellNumberSinceAppStart() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int curValue = APP_SHELL_NUMBER_SINCE_APP_START;
        if (curValue < 0) curValue = Integer.MAX_VALUE;

        APP_SHELL_NUMBER_SINCE_APP_START = curValue + 1;
        if (APP_SHELL_NUMBER_SINCE_APP_START < 0) APP_SHELL_NUMBER_SINCE_APP_START = Integer.MAX_VALUE;
        return curValue;
    }

    public static synchronized int getAndIncrementTerminalSessionNumberSinceAppStart() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int curValue = TERMINAL_SESSION_NUMBER_SINCE_APP_START;
        if (curValue < 0) curValue = Integer.MAX_VALUE;

        TERMINAL_SESSION_NUMBER_SINCE_APP_START = curValue + 1;
        if (TERMINAL_SESSION_NUMBER_SINCE_APP_START < 0) TERMINAL_SESSION_NUMBER_SINCE_APP_START = Integer.MAX_VALUE;
        return curValue;
    }

}
