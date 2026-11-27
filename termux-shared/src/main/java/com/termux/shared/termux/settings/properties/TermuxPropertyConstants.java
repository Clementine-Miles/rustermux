package com.termux.shared.termux.settings.properties;

import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.theme.NightMode;
import com.termux.shared.file.FileUtils;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.termux.shared.settings.properties.SharedProperties.orderedPropertyValues;

/* SPDX-License-Identifier: MIT */

public final class TermuxPropertyConstants {

    private static final String LOG_TAG = "TermuxPropertyConstants";

    /* boolean */

    /** Defines the key for whether file share receiver of the app is enabled. */
    public static final String KEY_DISABLE_FILE_SHARE_RECEIVER =  "disable-file-share-receiver"; // Default: "disable-file-share-receiver"

    /** Defines the key for whether file view receiver of the app is enabled. */
    public static final String KEY_DISABLE_FILE_VIEW_RECEIVER =  "disable-file-view-receiver"; // Default: "disable-file-view-receiver"



    /** Defines the key for whether hardware keyboard shortcuts are enabled. */
    public static final String KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS =  "disable-hardware-keyboard-shortcuts"; // Default: "disable-hardware-keyboard-shortcuts"



    /** Defines the key for whether a toast will be shown when user changes the terminal session */
    public static final String KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST =  "disable-terminal-session-change-toast"; // Default: "disable-terminal-session-change-toast"



    /** Defines the key for whether to enforce character based input to fix the issue where for some devices like Samsung, the letters might not appear until enter is pressed */
    public static final String KEY_ENFORCE_CHAR_BASED_INPUT =  "enforce-char-based-input"; // Default: "enforce-char-based-input"



    /** Defines the key for whether text for the extra keys buttons should be all capitalized automatically */
    public static final String KEY_EXTRA_KEYS_TEXT_ALL_CAPS =  "extra-keys-text-all-caps"; // Default: "extra-keys-text-all-caps"



    /** Defines the key for whether to hide soft keyboard when termux app is started */
    public static final String KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP =  "hide-soft-keyboard-on-startup"; // Default: "hide-soft-keyboard-on-startup"



    /** Defines the key for whether the {@link TermuxAmSocketServer} should be run at app startup */
    public static final String KEY_RUN_TERMUX_AM_SOCKET_SERVER =  "run-termux-am-socket-server"; // Default: "run-termux-am-socket-server"



    /** Defines the key for whether url links in terminal transcript will automatically open on click or on tap */
    public static final String KEY_TERMINAL_ONCLICK_URL_OPEN =  "terminal-onclick-url-open"; // Default: "terminal-onclick-url-open"



    /** Defines the key for whether to use black UI */
    @Deprecated
    public static final String KEY_USE_BLACK_UI =  "use-black-ui"; // Default: "use-black-ui"



    /** Defines the key for whether to use ctrl space workaround to fix the issue where ctrl+space does not work on some ROMs */
    public static final String KEY_USE_CTRL_SPACE_WORKAROUND =  "ctrl-space-workaround"; // Default: "ctrl-space-workaround"



    /** Defines the key for whether to use fullscreen */
    public static final String KEY_USE_FULLSCREEN =  "fullscreen"; // Default: "fullscreen"



    /** Defines the key for whether to use fullscreen workaround */
    public static final String KEY_USE_FULLSCREEN_WORKAROUND =  "use-fullscreen-workaround"; // Default: "use-fullscreen-workaround"





    /* int */

    /** Defines the key for the bell behaviour */
    public static final String KEY_BELL_BEHAVIOUR =  "bell-character"; // Default: "bell-character"

    public static final String VALUE_BELL_BEHAVIOUR_VIBRATE = "vibrate";
    public static final String VALUE_BELL_BEHAVIOUR_BEEP = "beep";
    public static final String VALUE_BELL_BEHAVIOUR_IGNORE = "ignore";
    public static final String DEFAULT_VALUE_BELL_BEHAVIOUR = VALUE_BELL_BEHAVIOUR_VIBRATE;

    public static final int IVALUE_BELL_BEHAVIOUR_VIBRATE = 1;
    public static final int IVALUE_BELL_BEHAVIOUR_BEEP = 2;
    public static final int IVALUE_BELL_BEHAVIOUR_IGNORE = 3;
    public static final int DEFAULT_IVALUE_BELL_BEHAVIOUR = IVALUE_BELL_BEHAVIOUR_VIBRATE;

    /** Maps bell behaviour values to their internal values. */
    public static final Map<String, Integer> MAP_BELL_BEHAVIOUR = orderedPropertyValues(values -> {
        values.put(VALUE_BELL_BEHAVIOUR_VIBRATE, IVALUE_BELL_BEHAVIOUR_VIBRATE);
        values.put(VALUE_BELL_BEHAVIOUR_BEEP, IVALUE_BELL_BEHAVIOUR_BEEP);
        values.put(VALUE_BELL_BEHAVIOUR_IGNORE, IVALUE_BELL_BEHAVIOUR_IGNORE);
    });



    /** Defines the key for the terminal cursor blink rate */
    public static final String KEY_TERMINAL_CURSOR_BLINK_RATE =  "terminal-cursor-blink-rate"; // Default: "terminal-cursor-blink-rate"
    public static final int IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN = TerminalView.TERMINAL_CURSOR_BLINK_RATE_MIN;
    public static final int IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX = TerminalView.TERMINAL_CURSOR_BLINK_RATE_MAX;
    public static final int DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE = 0;



    /** Defines the key for the terminal cursor style */
    public static final String KEY_TERMINAL_CURSOR_STYLE =  "terminal-cursor-style"; // Default: "terminal-cursor-style"

    public static final String VALUE_TERMINAL_CURSOR_STYLE_BLOCK = "block";
    public static final String VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE = "underline";
    public static final String VALUE_TERMINAL_CURSOR_STYLE_BAR = "bar";

    public static final int IVALUE_TERMINAL_CURSOR_STYLE_BLOCK = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    public static final int IVALUE_TERMINAL_CURSOR_STYLE_UNDERLINE = TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
    public static final int IVALUE_TERMINAL_CURSOR_STYLE_BAR = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
    public static final int DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;

    /** Maps terminal cursor styles to their internal values. */
    public static final Map<String, Integer> MAP_TERMINAL_CURSOR_STYLE = orderedPropertyValues(values -> {
        values.put(VALUE_TERMINAL_CURSOR_STYLE_BLOCK, IVALUE_TERMINAL_CURSOR_STYLE_BLOCK);
        values.put(VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE, IVALUE_TERMINAL_CURSOR_STYLE_UNDERLINE);
        values.put(VALUE_TERMINAL_CURSOR_STYLE_BAR, IVALUE_TERMINAL_CURSOR_STYLE_BAR);
    });




    /**
     * Defines the key for how many days old the access time should be of files that should be
     * deleted from $TMPDIR on termux exit.
     * `-1` for none, `0` for all and `> 0` for x days.
     */
    public static final String KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT =  "delete-tmpdir-files-older-than-x-days-on-exit"; // Default: "delete-tmpdir-files-older-than-x-days-on-exit"
    public static final int IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MIN = -1;
    public static final int IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX = 100000;
    public static final int DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT = 3;



    /** Defines the key for the terminal margin on left and right in dp units */
    public static final String KEY_TERMINAL_MARGIN_HORIZONTAL =  "terminal-margin-horizontal"; // Default: "terminal-margin-horizontal"
    public static final int IVALUE_TERMINAL_MARGIN_HORIZONTAL_MIN = 0;
    public static final int IVALUE_TERMINAL_MARGIN_HORIZONTAL_MAX = 100;
    public static final int DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL = 3;

    /** Defines the key for the terminal margin on top and bottom in dp units */
    public static final String KEY_TERMINAL_MARGIN_VERTICAL =  "terminal-margin-vertical"; // Default: "terminal-margin-vertical"
    public static final int IVALUE_TERMINAL_MARGIN_VERTICAL_MIN = 0;
    public static final int IVALUE_TERMINAL_MARGIN_VERTICAL_MAX = 100;
    public static final int DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL = 0;



    /** Defines the key for the terminal transcript rows */
    public static final String KEY_TERMINAL_TRANSCRIPT_ROWS =  "terminal-transcript-rows"; // Default: "terminal-transcript-rows"
    public static final int IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN;
    public static final int IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MAX;
    public static final int DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS;





    /* float */

    /** Defines the key for the terminal toolbar height */
    public static final String KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR =  "terminal-toolbar-height"; // Default: "terminal-toolbar-height"
    public static final float IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN = 0.4f;
    public static final float IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX = 3;
    public static final float DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR = 1;





    /* Integer */

    /** Defines the key for create session shortcut */
    public static final String KEY_SHORTCUT_CREATE_SESSION =  "shortcut.create-session"; // Default: "shortcut.create-session"
    /** Defines the key for next session shortcut */
    public static final String KEY_SHORTCUT_NEXT_SESSION =  "shortcut.next-session"; // Default: "shortcut.next-session"
    /** Defines the key for previous session shortcut */
    public static final String KEY_SHORTCUT_PREVIOUS_SESSION =  "shortcut.previous-session"; // Default: "shortcut.previous-session"
    /** Defines the key for rename session shortcut */
    public static final String KEY_SHORTCUT_RENAME_SESSION =  "shortcut.rename-session"; // Default: "shortcut.rename-session"

    public static final int ACTION_SHORTCUT_CREATE_SESSION = 1;
    public static final int ACTION_SHORTCUT_NEXT_SESSION = 2;
    public static final int ACTION_SHORTCUT_PREVIOUS_SESSION = 3;
    public static final int ACTION_SHORTCUT_RENAME_SESSION = 4;

    /** Maps session shortcut values to their internal actions. */
    public static final Map<String, Integer> MAP_SESSION_SHORTCUTS = orderedPropertyValues(values -> {
        values.put(KEY_SHORTCUT_CREATE_SESSION, ACTION_SHORTCUT_CREATE_SESSION);
        values.put(KEY_SHORTCUT_NEXT_SESSION, ACTION_SHORTCUT_NEXT_SESSION);
        values.put(KEY_SHORTCUT_PREVIOUS_SESSION, ACTION_SHORTCUT_PREVIOUS_SESSION);
        values.put(KEY_SHORTCUT_RENAME_SESSION, ACTION_SHORTCUT_RENAME_SESSION);
    });





    /* String */

    /** Defines the key for whether back key will behave as escape key or literal back key */
    public static final String KEY_BACK_KEY_BEHAVIOUR =  "back-key"; // Default: "back-key"

    public static final String IVALUE_BACK_KEY_BEHAVIOUR_BACK = "back";
    public static final String IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE = "escape";
    public static final String DEFAULT_IVALUE_BACK_KEY_BEHAVIOUR = IVALUE_BACK_KEY_BEHAVIOUR_BACK;

    /** Maps back key behaviour values to their internal values. */
    public static final Map<String, String> MAP_BACK_KEY_BEHAVIOUR = orderedPropertyValues(values -> {
        values.put(IVALUE_BACK_KEY_BEHAVIOUR_BACK, IVALUE_BACK_KEY_BEHAVIOUR_BACK);
        values.put(IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE, IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE);
    });



    /** Defines the key for the default working directory */
    public static final String KEY_DEFAULT_WORKING_DIRECTORY =  "default-working-directory"; // Default: "default-working-directory"
    /** Defines the default working directory */
    public static final String DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY = TermuxConstants.TERMUX_HOME_DIR_PATH;



    /** Defines the key for extra keys */
    public static final String KEY_EXTRA_KEYS =  "extra-keys"; // Default: "extra-keys"
    //public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]"; // Single row
    public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]"; // Double row

    /** Defines the key for extra keys style */
    public static final String KEY_EXTRA_KEYS_STYLE =  "extra-keys-style"; // Default: "extra-keys-style"
    public static final String DEFAULT_IVALUE_EXTRA_KEYS_STYLE = "default";



    /** Defines the key for {@link NightMode}. */
    public static final String KEY_NIGHT_MODE = "night-mode"; // Default: "night-mode"

    public static final String IVALUE_NIGHT_MODE_TRUE = NightMode.TRUE.getName();
    public static final String IVALUE_NIGHT_MODE_FALSE = NightMode.FALSE.getName();
    public static final String IVALUE_NIGHT_MODE_SYSTEM = NightMode.SYSTEM.getName();
    public static final String DEFAULT_IVALUE_NIGHT_MODE = IVALUE_NIGHT_MODE_SYSTEM;

    /** Maps {@link NightMode} values to their internal values. */
    public static final Map<String, String> MAP_NIGHT_MODE = orderedPropertyValues(values -> {
        values.put(IVALUE_NIGHT_MODE_TRUE, IVALUE_NIGHT_MODE_TRUE);
        values.put(IVALUE_NIGHT_MODE_FALSE, IVALUE_NIGHT_MODE_FALSE);
        values.put(IVALUE_NIGHT_MODE_SYSTEM, IVALUE_NIGHT_MODE_SYSTEM);
    });



    /** Defines the key for whether toggle soft keyboard request will show/hide or enable/disable keyboard */
    public static final String KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR =  "soft-keyboard-toggle-behaviour"; // Default: "soft-keyboard-toggle-behaviour"

    public static final String IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE = "show/hide";
    public static final String IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE = "enable/disable";
    public static final String DEFAULT_IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR = IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE;

    /** Maps toggle soft keyboard behaviour values to their internal values. */
    public static final Map<String, String> MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR = orderedPropertyValues(values -> {
        values.put(IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE, IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE);
        values.put(IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE, IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE);
    });



    /** Defines the key for whether volume keys will behave as virtual or literal volume keys */
    public static final String KEY_VOLUME_KEYS_BEHAVIOUR =  "volume-keys"; // Default: "volume-keys"

    public static final String IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL = "virtual";
    public static final String IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME = "volume";
    public static final String DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR = IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL;

    /** Maps volume keys behaviour values to their internal values. */
    public static final Map<String, String> MAP_VOLUME_KEYS_BEHAVIOUR = orderedPropertyValues(values -> {
        values.put(IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL, IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL);
        values.put(IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME, IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME);
    });





    /** Defines the set for keys loaded by termux
     * Setting this to {@code null} will make {@link SharedProperties} throw an exception.
     * */
    public static final Set<String> TERMUX_APP_PROPERTIES_LIST = new HashSet<>(Arrays.asList(
        /* boolean */
        KEY_DISABLE_FILE_SHARE_RECEIVER,
        KEY_DISABLE_FILE_VIEW_RECEIVER,
        KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS,
        KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST,
        KEY_ENFORCE_CHAR_BASED_INPUT,
        KEY_EXTRA_KEYS_TEXT_ALL_CAPS,
        KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP,
        KEY_RUN_TERMUX_AM_SOCKET_SERVER,
        KEY_TERMINAL_ONCLICK_URL_OPEN,
        KEY_USE_CTRL_SPACE_WORKAROUND,
        KEY_USE_FULLSCREEN,
        KEY_USE_FULLSCREEN_WORKAROUND,

        /* int */
        KEY_BELL_BEHAVIOUR,
        KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT,
        KEY_TERMINAL_CURSOR_BLINK_RATE,
        KEY_TERMINAL_CURSOR_STYLE,
        KEY_TERMINAL_MARGIN_HORIZONTAL,
        KEY_TERMINAL_MARGIN_VERTICAL,
        KEY_TERMINAL_TRANSCRIPT_ROWS,

        /* float */
        KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,

        /* Integer */
        KEY_SHORTCUT_CREATE_SESSION,
        KEY_SHORTCUT_NEXT_SESSION,
        KEY_SHORTCUT_PREVIOUS_SESSION,
        KEY_SHORTCUT_RENAME_SESSION,

        /* String */
        KEY_BACK_KEY_BEHAVIOUR,
        KEY_DEFAULT_WORKING_DIRECTORY,
        KEY_EXTRA_KEYS,
        KEY_EXTRA_KEYS_STYLE,
        KEY_NIGHT_MODE,
        KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR,
        KEY_VOLUME_KEYS_BEHAVIOUR
    ));

    /** Defines the set for keys loaded by termux that have default boolean behaviour with false as default.
     * "true" -> true
     * "false" -> false
     * default: false
     */
    public static final Set<String> TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(
        KEY_DISABLE_FILE_SHARE_RECEIVER,
        KEY_DISABLE_FILE_VIEW_RECEIVER,
        KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS,
        KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST,
        KEY_ENFORCE_CHAR_BASED_INPUT,
        KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP,
        KEY_TERMINAL_ONCLICK_URL_OPEN,
        KEY_USE_CTRL_SPACE_WORKAROUND,
        KEY_USE_FULLSCREEN,
        KEY_USE_FULLSCREEN_WORKAROUND
    ));

    /** Defines the set for keys loaded by termux that have default boolean behaviour with true as default.
     * "true" -> true
     * "false" -> false
     * default: true
     */
    public static final Set<String> TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(
        KEY_EXTRA_KEYS_TEXT_ALL_CAPS,
        KEY_RUN_TERMUX_AM_SOCKET_SERVER
    ));

    /** Defines the set for keys loaded by termux that have default inverted boolean behaviour with false as default.
     * "false" -> true
     * "true" -> false
     * default: false
     */
    public static final Set<String> TERMUX_DEFAULT_INVERETED_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(
    ));

    /** Defines the set for keys loaded by termux that have default inverted boolean behaviour with true as default.
     * "false" -> true
     * "true" -> false
     * default: true
     */
    public static final Set<String> TERMUX_DEFAULT_INVERETED_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(
    ));

}
