package com.termux.shared.termux;

import android.annotation.SuppressLint;


import java.io.File;

/* SPDX-License-Identifier: MIT */

/** App constants. Precompiled Termux packages depend on the existing com.termux prefix. */
public final class TermuxConstants {


    public static final String TERMUX_APP_NAME = "rustermux";
    public static final String TERMUX_PACKAGE_NAME = "com.termux";
    public static final String TERMUX_GITHUB_ISSUES_REPO_URL =
        "https://github.com/Clementine-Miles/rustermux/issues";

    /*
     * Termux app core directory paths.
     */

    /** Termux app internal private app data directory path */
    @SuppressLint("SdCardPath")
    public static final String TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME; // Default: "/data/data/com.termux"
    /** Termux app internal private app data directory */
    public static final File TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR = new File(TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH);



    /** Termux app Files directory path */
    public static final String TERMUX_FILES_DIR_PATH = TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "/files"; // Default: "/data/data/com.termux/files"
    /** Termux app Files directory */
    public static final File TERMUX_FILES_DIR = new File(TERMUX_FILES_DIR_PATH);



    /** Termux app $PREFIX directory path */
    public static final String TERMUX_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr"; // Default: "/data/data/com.termux/files/usr"
    /** Termux app $PREFIX directory */
    public static final File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/bin directory path */
    public static final String TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin"; // Default: "/data/data/com.termux/files/usr/bin"
    /** Termux app $PREFIX/bin directory */
    public static final File TERMUX_BIN_PREFIX_DIR = new File(TERMUX_BIN_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/etc directory path */
    public static final String TERMUX_ETC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/etc"; // Default: "/data/data/com.termux/files/usr/etc"
    /** Termux app $PREFIX/etc directory */
    public static final File TERMUX_ETC_PREFIX_DIR = new File(TERMUX_ETC_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/include directory path */
    public static final String TERMUX_INCLUDE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/include"; // Default: "/data/data/com.termux/files/usr/include"
    /** Termux app $PREFIX/include directory */
    public static final File TERMUX_INCLUDE_PREFIX_DIR = new File(TERMUX_INCLUDE_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/lib directory path */
    public static final String TERMUX_LIB_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/lib"; // Default: "/data/data/com.termux/files/usr/lib"
    /** Termux app $PREFIX/lib directory */
    public static final File TERMUX_LIB_PREFIX_DIR = new File(TERMUX_LIB_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/libexec directory path */
    public static final String TERMUX_LIBEXEC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/libexec"; // Default: "/data/data/com.termux/files/usr/libexec"
    /** Termux app $PREFIX/libexec directory */
    public static final File TERMUX_LIBEXEC_PREFIX_DIR = new File(TERMUX_LIBEXEC_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/share directory path */
    public static final String TERMUX_SHARE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/share"; // Default: "/data/data/com.termux/files/usr/share"
    /** Termux app $PREFIX/share directory */
    public static final File TERMUX_SHARE_PREFIX_DIR = new File(TERMUX_SHARE_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/tmp and $TMPDIR directory path */
    public static final String TERMUX_TMP_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/tmp"; // Default: "/data/data/com.termux/files/usr/tmp"
    /** Termux app $PREFIX/tmp and $TMPDIR directory */
    public static final File TERMUX_TMP_PREFIX_DIR = new File(TERMUX_TMP_PREFIX_DIR_PATH);


    /** Termux app $PREFIX/var directory path */
    public static final String TERMUX_VAR_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/var"; // Default: "/data/data/com.termux/files/usr/var"
    /** Termux app $PREFIX/var directory */
    public static final File TERMUX_VAR_PREFIX_DIR = new File(TERMUX_VAR_PREFIX_DIR_PATH);



    /** Termux app usr-staging directory path */
    public static final String TERMUX_STAGING_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr-staging"; // Default: "/data/data/com.termux/files/usr-staging"
    /** Termux app usr-staging directory */
    public static final File TERMUX_STAGING_PREFIX_DIR = new File(TERMUX_STAGING_PREFIX_DIR_PATH);



    /** Termux app $HOME directory path */
    public static final String TERMUX_HOME_DIR_PATH = TERMUX_FILES_DIR_PATH + "/home"; // Default: "/data/data/com.termux/files/home"
    /** Termux app $HOME directory */
    public static final File TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);


    /** Termux app config home directory path */
    public static final String TERMUX_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.config/termux"; // Default: "/data/data/com.termux/files/home/.config/termux"
    /** Termux app config home directory */
    public static final File TERMUX_CONFIG_HOME_DIR = new File(TERMUX_CONFIG_HOME_DIR_PATH);

    /** Termux app config $PREFIX directory path */
    public static final String TERMUX_CONFIG_PREFIX_DIR_PATH = TERMUX_ETC_PREFIX_DIR_PATH + "/termux"; // Default: "/data/data/com.termux/files/usr/etc/termux"
    /** Termux app config $PREFIX directory */
    public static final File TERMUX_CONFIG_PREFIX_DIR = new File(TERMUX_CONFIG_PREFIX_DIR_PATH);


    /** Termux app data home directory path */
    public static final String TERMUX_DATA_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.termux"; // Default: "/data/data/com.termux/files/home/.termux"
    /** Termux app data home directory */
    public static final File TERMUX_DATA_HOME_DIR = new File(TERMUX_DATA_HOME_DIR_PATH);


    /** Termux app storage home directory path */
    public static final String TERMUX_STORAGE_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/storage"; // Default: "/data/data/com.termux/files/home/storage"
    /** Termux app storage home directory */
    public static final File TERMUX_STORAGE_HOME_DIR = new File(TERMUX_STORAGE_HOME_DIR_PATH);



    /** Termux internal app files directory path */
    public static final String TERMUX_APPS_DIR_PATH = TERMUX_FILES_DIR_PATH + "/apps"; // Default: "/data/data/com.termux/files/apps"
    /** Termux internal app files directory */
    public static final File TERMUX_APPS_DIR = new File(TERMUX_APPS_DIR_PATH);



    /*
     * Termux app operational preferences and diagnostics.
     */

    /** Termux app default SharedPreferences file basename without extension */
    public static final String TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_PACKAGE_NAME + "_preferences"; // Default: "com.termux_preferences"


    /** Termux app crash log file path */
    public static final String TERMUX_CRASH_LOG_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log.md"; // Default: "/data/data/com.termux/files/home/crash_log.md"

    /** Termux app crash log backup file path */
    public static final String TERMUX_CRASH_LOG_BACKUP_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log_backup.md"; // Default: "/data/data/com.termux/files/home/crash_log_backup.md"


    /** Termux app environment file path */
    public static final String TERMUX_ENV_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env"; // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env"

    /** Termux app environment temp file path */
    public static final String TERMUX_ENV_TEMP_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env.tmp"; // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env.tmp"




    /*
     * Termux app notification variables.
     */

    /** Termux app notification channel id used by {@link TERMUX_APP.TERMUX_SERVICE} */
    public static final String TERMUX_APP_NOTIFICATION_CHANNEL_ID = "termux_notification_channel";
    /** Termux app notification channel name used by {@link TERMUX_APP.TERMUX_SERVICE} */
    public static final String TERMUX_APP_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " App";
    /** Termux app unique notification id used by {@link TERMUX_APP.TERMUX_SERVICE} */
    public static final int TERMUX_APP_NOTIFICATION_ID = 1337;

    /** Termux app notification channel id used for crash reports */
    public static final String TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID = "termux_crash_reports_notification_channel";
    /** Termux app notification channel name used for crash reports */
    public static final String TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " Crash Reports";


    /** The Uri authority for Termux app file shares */
    public static final String TERMUX_FILE_SHARE_URI_AUTHORITY = TERMUX_PACKAGE_NAME + ".files"; // Default: "com.termux.files"

    /** Environment variable prefix root for the Termux app. */
    public static final String TERMUX_ENV_PREFIX_ROOT = "TERMUX";






    /**
     * Termux app constants.
     */
    public static final class TERMUX_APP {

        /** Termux apps directory path */
        public static final String APPS_DIR_PATH = TERMUX_APPS_DIR_PATH + "/" + TERMUX_PACKAGE_NAME; // Default: "/data/data/com.termux/files/apps/com.termux"

        /** Bundled API command wrappers are installed independently of apt-managed files. */
        public static final String API_COMMANDS_DIR_PATH = APPS_DIR_PATH + "/termux-api";

        /** termux-am socket file path */
        public static final String TERMUX_AM_SOCKET_FILE_PATH = APPS_DIR_PATH + "/termux-am/am.sock"; // Default: "/data/data/com.termux/files/apps/com.termux/termux-am/am.sock"


        /** Termux app FileShareReceiverActivity class name */
        public static final String FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".app.api.file.FileShareReceiverActivity"; // Default: "com.termux.app.api.file.FileShareReceiverActivity"

        /** Termux app FileViewReceiverActivity class name */
        public static final String FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".app.api.file.FileViewReceiverActivity"; // Default: "com.termux.app.api.file.FileViewReceiverActivity"


        /** Termux app core activity name. */
        public static final String TERMUX_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxActivity"; // Default: "com.termux.app.TermuxActivity"

        /**
         * Termux app core activity.
         */
        public static final class TERMUX_ACTIVITY {

            /** Intent extra for if termux failsafe session needs to be started and is used by {@link TERMUX_ACTIVITY} and {@link TERMUX_SERVICE#ACTION_STOP_SERVICE} */
            public static final String EXTRA_FAILSAFE_SESSION = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.failsafe_session"; // Default: "com.termux.app.failsafe_session"


            /** Intent action to make termux app notify user that a crash happened. */
            public static final String ACTION_NOTIFY_APP_CRASH = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.notify_app_crash"; // Default: "com.termux.app.notify_app_crash"


            /** Intent action to make termux reload its termux session styling */
            public static final String ACTION_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style"; // Default: "com.termux.app.reload_style"
            /** Intent {@code String} extra for what to reload for the TERMUX_ACTIVITY.ACTION_RELOAD_STYLE intent. This has been deperecated. */
            @Deprecated
            public static final String EXTRA_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style"; // Default: "com.termux.app.reload_style"

            /** Intent action to make termux request storage permissions */
            public static final String ACTION_REQUEST_PERMISSIONS = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.request_storage_permissions"; // Default: "com.termux.app.request_storage_permissions"
        }





        /** Termux app core service name. */
        public static final String TERMUX_SERVICE_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxService"; // Default: "com.termux.app.TermuxService"

        /**
         * Termux app core service.
         */
        public static final class TERMUX_SERVICE {
            public static final String ACTION_STOP_SERVICE = TERMUX_PACKAGE_NAME + ".service_stop";
            public static final String ACTION_WAKE_LOCK = TERMUX_PACKAGE_NAME + ".service_wake_lock";
            public static final String ACTION_WAKE_UNLOCK = TERMUX_PACKAGE_NAME + ".service_wake_unlock";
            public static final String ACTION_SERVICE_EXECUTE = TERMUX_PACKAGE_NAME + ".service_execute";
            public static final String URI_SCHEME_SERVICE_EXECUTE = TERMUX_PACKAGE_NAME + ".file";
            public static final String EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".execute.arguments";
            public static final String EXTRA_WORKDIR = TERMUX_PACKAGE_NAME + ".execute.cwd";
            public static final String EXTRA_BACKGROUND = TERMUX_PACKAGE_NAME + ".execute.background";
        }
    }
}
