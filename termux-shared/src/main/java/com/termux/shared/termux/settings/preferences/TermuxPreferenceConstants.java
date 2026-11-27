package com.termux.shared.termux.settings.preferences;

/* SPDX-License-Identifier: MIT */

public final class TermuxPreferenceConstants {

    /**
     * Termux app constants.
     */
    public static final class TERMUX_APP {

        /**
         * Defines the key for last used notification id.
         */
        public static final String KEY_LAST_NOTIFICATION_ID = "last_notification_id";
        public static final int DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID = 0;

        /**
         * The {@link ExecutionCommand.Runner#APP_SHELL} number after termux app process since boot.
         */
        public static final String KEY_APP_SHELL_NUMBER_SINCE_BOOT = "app_shell_number_since_boot";
        public static final int DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT = 0;

        /**
         * The {@link ExecutionCommand.Runner#TERMINAL_SESSION} number after termux app process since boot.
         */
        public static final String KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT = "terminal_session_number_since_boot";
        public static final int DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT = 0;


    }
}
