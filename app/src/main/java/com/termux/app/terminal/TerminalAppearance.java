package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalColorScheme;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Selects the inputs for the terminal's existing color and font renderer. */
public final class TerminalAppearance {

    private static final String LOG_TAG = "TerminalAppearance";
    static final String CUSTOM_FILE = "custom-file";

    public enum Setting {
        COLOR_SCHEME("terminal_color_scheme", "appearance/colors/", R.string.appearance_color_scheme,
            R.string.appearance_color_custom,
            new Option("gruvbox-material-dark-medium.properties", R.string.appearance_gruvbox_material_dark_medium),
            new Option("gruvbox-dark.properties", R.string.appearance_gruvbox_dark),
            new Option("gruvbox-light.properties", R.string.appearance_gruvbox_light),
            new Option("gruvbox-material-dark-hard.properties", R.string.appearance_gruvbox_material_dark_hard),
            new Option("gruvbox-material-dark-soft.properties", R.string.appearance_gruvbox_material_dark_soft),
            new Option("gruvbox-material-light-hard.properties", R.string.appearance_gruvbox_material_light_hard),
            new Option("gruvbox-material-light-medium.properties", R.string.appearance_gruvbox_material_light_medium),
            new Option("gruvbox-material-light-soft.properties", R.string.appearance_gruvbox_material_light_soft)),
        FONT("terminal_font", "appearance/fonts/", R.string.appearance_font,
            R.string.appearance_font_custom,
            new Option("HackNerdFontMono-Regular.ttf", R.string.appearance_hack_nerd_font_mono),
            new Option("JetBrains-Mono.ttf", R.string.appearance_jetbrains_mono));

        final String preferenceKey;
        final String assetDirectory;
        final int title;
        final int customTitle;
        final Option[] options;

        Setting(String preferenceKey, String assetDirectory, int title, int customTitle, Option... options) {
            this.preferenceKey = preferenceKey;
            this.assetDirectory = assetDirectory;
            this.title = title;
            this.customTitle = customTitle;
            this.options = options;
        }
    }

    private static final class Option {
        final String assetName;
        final int title;

        Option(String assetName, int title) {
            this.assetName = assetName;
            this.title = title;
        }
    }

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final File mCustomColors;
    private final File mCustomFont;

    public TerminalAppearance(Context context, SharedPreferences preferences) {
        this(context, preferences, TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE, TermuxConstants.TERMUX_FONT_FILE);
    }

    TerminalAppearance(Context context, SharedPreferences preferences, File customColors, File customFont) {
        mContext = context;
        mPreferences = preferences;
        mCustomColors = customColors;
        mCustomFont = customFont;
    }

    public void showOptions(Setting setting, Runnable onSelection) {
        Option[] options = setting.options;
        CharSequence[] titles = new CharSequence[options.length + 1];
        String selected = getSelection(setting);
        int selectedIndex = options.length;
        for (int i = 0; i < options.length; i++) {
            titles[i] = mContext.getText(options[i].title);
            if (options[i].assetName.equals(selected)) selectedIndex = i;
        }
        titles[options.length] = mContext.getText(setting.customTitle);

        new AlertDialog.Builder(mContext)
            .setTitle(setting.title)
            .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                select(setting, which == options.length ? CUSTOM_FILE : options[which].assetName);
                dialog.dismiss();
                onSelection.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void select(Setting setting, String selection) {
        mPreferences.edit().putString(setting.preferenceKey, selection).apply();
    }

    String getSelection(Setting setting) {
        String stored = mPreferences.getString(setting.preferenceKey, null);
        File custom = setting == Setting.COLOR_SCHEME ? mCustomColors : mCustomFont;
        if (stored == null && custom.isFile() && custom.length() > 0) return CUSTOM_FILE;
        if (CUSTOM_FILE.equals(stored)) return CUSTOM_FILE;
        for (Option option : setting.options) {
            if (option.assetName.equals(stored)) return stored;
        }
        return setting.options[0].assetName;
    }

    void applyColorScheme(TerminalColorScheme colorScheme) {
        Setting setting = Setting.COLOR_SCHEME;
        String selection = getSelection(setting);
        if (CUSTOM_FILE.equals(selection)) {
            if (mCustomColors.isFile()) {
                try (InputStream input = new FileInputStream(mCustomColors)) {
                    Properties colors = loadProperties(input);
                    if (!colors.isEmpty()) {
                        colorScheme.updateWith(colors);
                        return;
                    }
                } catch (IOException | RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Invalid custom terminal color scheme", e);
                }
            }
            selection = setting.options[0].assetName;
        }

        try (InputStream input = mContext.getAssets().open(setting.assetDirectory + selection)) {
            colorScheme.updateWith(loadProperties(input));
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load the bundled terminal color scheme", e);
            colorScheme.updateWith(new Properties());
        }
    }

    private static Properties loadProperties(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        return properties;
    }

    Typeface loadFont() {
        Setting setting = Setting.FONT;
        String selection = getSelection(setting);
        if (CUSTOM_FILE.equals(selection)) {
            if (mCustomFont.isFile() && mCustomFont.length() > 0) {
                try {
                    return Typeface.createFromFile(mCustomFont);
                } catch (RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Invalid custom terminal font", e);
                }
            }
            selection = setting.options[0].assetName;
        }

        try {
            return Typeface.createFromAsset(mContext.getAssets(), setting.assetDirectory + selection);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load the bundled terminal font", e);
            return Typeface.MONOSPACE;
        }
    }
}
