package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TerminalPalette;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.terminal.TerminalColorScheme;

import java.io.IOException;
import java.util.Properties;

/** Applies the current TOML palette and font through the terminal renderer. */
public final class TerminalAppearance {
    private static final String LOG_TAG = "TerminalAppearance";
    private static final String FONT_DIRECTORY = "appearance/fonts/";

    public enum Setting {
        COLOR_SCHEME(ConfigKeys.PALETTE, R.string.appearance_color_scheme,
            new Option("gruvbox-material-dark-medium", R.string.appearance_gruvbox_material_dark_medium),
            new Option("gruvbox-dark", R.string.appearance_gruvbox_dark),
            new Option("gruvbox-light", R.string.appearance_gruvbox_light),
            new Option("gruvbox-material-dark-hard", R.string.appearance_gruvbox_material_dark_hard),
            new Option("gruvbox-material-dark-soft", R.string.appearance_gruvbox_material_dark_soft),
            new Option("gruvbox-material-light-hard", R.string.appearance_gruvbox_material_light_hard),
            new Option("gruvbox-material-light-medium", R.string.appearance_gruvbox_material_light_medium),
            new Option("gruvbox-material-light-soft", R.string.appearance_gruvbox_material_light_soft),
            new Option("custom", R.string.appearance_color_custom)),
        FONT(ConfigKeys.FONT, R.string.appearance_font,
            new Option("hack-nerd-font-mono", R.string.appearance_hack_nerd_font_mono),
            new Option("jetbrains-mono", R.string.appearance_jetbrains_mono),
            new Option("custom", R.string.appearance_font_custom));

        final ConfigKeys.Key<String> key;
        final int title;
        final Option[] options;

        Setting(ConfigKeys.Key<String> key, int title, Option... options) {
            this.key = key;
            this.title = title;
            this.options = options;
        }
    }

    private static final class Option {
        final String value;
        final int title;

        Option(String value, int title) {
            this.value = value;
            this.title = title;
        }
    }

    private final Context context;
    private final TermuxConfig config;

    public TerminalAppearance(Context context) {
        this.context = context;
        this.config = TermuxConfig.get(context);
    }

    public void showOptions(Setting setting, Runnable onSelection) {
        CharSequence[] titles = new CharSequence[setting.options.length];
        String selected = config.snapshot().get(setting.key);
        int selectedIndex = 0;
        for (int i = 0; i < titles.length; i++) {
            Option option = setting.options[i];
            titles[i] = context.getText(option.title);
            if (option.value.equals(selected)) selectedIndex = i;
        }

        new AlertDialog.Builder(context)
            .setTitle(setting.title)
            .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                if (select(setting, setting.options[which].value)) {
                    dialog.dismiss();
                    onSelection.run();
                } else {
                    dialog.dismiss();
                    Toast.makeText(context, config.diagnostic(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    boolean select(Setting setting, String selection) {
        return config.set(setting.key, selection);
    }

    void applyColorScheme(TerminalColorScheme colorScheme) {
        try {
            colorScheme.updateWith(TerminalPalette.load(context, config.snapshot()));
        } catch (IOException | IllegalArgumentException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load the terminal palette", e);
            colorScheme.updateWith(new Properties());
        }
    }

    Typeface loadFont() {
        TermuxConfig.Snapshot snapshot = config.snapshot();
        String selection = snapshot.get(ConfigKeys.FONT);
        try {
            if ("custom".equals(selection)) {
                return Typeface.createFromFile(config.customFont(snapshot.get(ConfigKeys.FONT_PATH)));
            }
            String asset = "jetbrains-mono".equals(selection) ? "JetBrains-Mono.ttf" : "HackNerdFontMono-Regular.ttf";
            return Typeface.createFromAsset(context.getAssets(), FONT_DIRECTORY + asset);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load the terminal font", e);
            return Typeface.MONOSPACE;
        }
    }
}
