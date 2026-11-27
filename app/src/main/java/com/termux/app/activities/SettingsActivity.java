package com.termux.app.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.termux.R;
import com.termux.app.terminal.TerminalAppearance;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.NativeViews;
import com.termux.shared.view.UiColors;

public final class SettingsActivity extends AppCompatActivity {

    private static final String SETTINGS_CHANGED = "settings_changed";

    private LinearLayout options;
    private TermuxConfig config;
    private UiColors colors;
    private boolean settingsChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        config = TermuxConfig.get(this);
        colors = UiColors.resolve(this);

        if (savedInstanceState != null && savedInstanceState.getBoolean(SETTINGS_CHANGED)) {
            onSettingsChanged();
        }

        options = NativeViews.column(this);
        int inset = dp(8);
        options.setPadding(inset, inset, inset, inset);
        options.setBackgroundColor(colors.background);
        Toolbar toolbar = NativeViews.showScrollPage(this, options);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        colors.style(toolbar);

        TerminalAppearance appearance = new TerminalAppearance(this);
        addHeading(R.string.action_terminal_appearance);
        addAction(R.string.appearance_color_scheme,
            () -> appearance.showOptions(TerminalAppearance.Setting.COLOR_SCHEME, this::onSettingsChanged));
        addAction(R.string.appearance_font,
            () -> appearance.showOptions(TerminalAppearance.Setting.FONT, this::onSettingsChanged));

        addHeading(R.string.settings_terminal);
        addToggle(R.string.settings_show_tab_bar, ConfigKeys.SHOW_TAB_BAR);
        addToggle(R.string.settings_extra_keys, ConfigKeys.SHOW_EXTRA_KEYS);
        addToggle(R.string.action_toggle_keep_screen_on, ConfigKeys.KEEP_SCREEN_ON);
        addToggle(R.string.settings_keyboard_insets, ConfigKeys.KEYBOARD_MARGIN);

        addHeading(R.string.settings_input);
        addToggle(R.string.settings_soft_keyboard, ConfigKeys.SOFT_KEYBOARD);
        addToggle(R.string.settings_soft_keyboard_without_hardware, ConfigKeys.SOFT_KEYBOARD_WITHOUT_HARDWARE);

        addHeading(R.string.settings_storage);
        addAction(R.string.settings_app_permissions, () -> startActivity(new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));

        addHeading(R.string.settings_diagnostics);
        addAction(R.string.settings_config_status, () -> showConfigStatus(R.string.settings_config_status));
        addAction(R.string.settings_log_level, this::showLogLevels);
        addToggle(R.string.settings_key_logging, ConfigKeys.KEY_LOGGING);
        addToggle(R.string.settings_crash_notifications, ConfigKeys.CRASH_NOTIFICATIONS);
        addAction(R.string.action_open_help, this::showHelp);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(SETTINGS_CHANGED, settingsChanged);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void onSettingsChanged() {
        settingsChanged = true;
        setResult(RESULT_OK);
    }

    private <T> boolean updateSetting(ConfigKeys.Key<T> key, T value) {
        if (config.set(key, value)) {
            config.setTransient(key, null);
            onSettingsChanged();
            return true;
        }
        showConfigStatus(R.string.settings_config_write_failed);
        return false;
    }

    private void addHeading(int title) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
        heading.setTextColor(colors.foreground);
        heading.setPaddingRelative(dp(12), dp(16), dp(12), dp(4));
        heading.setAccessibilityHeading(true);
        options.addView(heading);
    }

    private void addAction(int title, Runnable action) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(title);
        button.setTextColor(colors.foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        styleRow(button);
        button.setOnClickListener(view -> action.run());
        options.addView(button);
    }

    private void addToggle(int title, ConfigKeys.Key<Boolean> setting) {
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setText(title);
        toggle.setTextColor(colors.foreground);
        toggle.setThumbTintList(new ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}}, new int[]{colors.accent, colors.foreground}));
        styleRow(toggle);
        toggle.setChecked(config.snapshot().get(setting));
        toggle.setOnClickListener(view -> {
            if (!updateSetting(setting, toggle.isChecked())) {
                toggle.setChecked(config.snapshot().get(setting));
            }
        });
        options.addView(toggle);
    }

    private void styleRow(TextView row) {
        row.setMinHeight(dp(48));
        row.setPaddingRelative(dp(12), dp(4), dp(12), dp(4));
    }

    private void showConfigStatus(int title) {
        String diagnostic = config.diagnostic();
        if (diagnostic == null) {
            diagnostic = getString(title == R.string.settings_config_write_failed
                ? R.string.settings_config_write_hint : R.string.settings_config_valid);
        }
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(getString(R.string.settings_config_detail, config.file().toString(), diagnostic))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void showHelp() {
        String version = PackageUtils.getVersionNameForPackage(this);
        new AlertDialog.Builder(this)
            .setTitle(version == null ? getString(R.string.application_name) : getString(R.string.settings_about, version))
            .setMessage(R.string.settings_help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void showLogLevels() {
        CharSequence[] levels = Logger.getLogLevelsArray();
        CharSequence[] labels = Logger.getLogLevelLabelsArray(this, levels, true);
        int currentLevel = config.snapshot().get(ConfigKeys.LOG_LEVEL);
        int selected = -1;
        for (int i = 0; i < levels.length; i++) {
            if (Integer.parseInt(levels[i].toString()) == currentLevel) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_log_level)
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                dialog.dismiss();
                int level = Integer.parseInt(levels[which].toString());
                if (updateSetting(ConfigKeys.LOG_LEVEL, level)) Logger.setLogLevel(this, level);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private int dp(int value) {
        return NativeViews.dp(this, value);
    }
}
