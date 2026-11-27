package com.termux.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.termux.R;
import com.termux.app.terminal.TerminalAppearance;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.theme.NightMode;

import java.util.function.Consumer;

public final class SettingsActivity extends AppCompatActivity {

    private static final String APPEARANCE_CHANGED = "appearance_changed";

    private LinearLayout options;
    private TermuxAppSharedPreferences preferences;
    private boolean appearanceChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_settings);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        preferences = TermuxAppSharedPreferences.build(this);
        if (savedInstanceState != null && savedInstanceState.getBoolean(APPEARANCE_CHANGED)) {
            onAppearanceChanged();
        }

        options = findViewById(R.id.settings);
        TerminalAppearance appearance = new TerminalAppearance(this, preferences.getSharedPreferences());
        addHeading(R.string.action_terminal_appearance);
        addAction(R.string.appearance_color_scheme,
            () -> appearance.showOptions(TerminalAppearance.Setting.COLOR_SCHEME, this::onAppearanceChanged));
        addAction(R.string.appearance_font,
            () -> appearance.showOptions(TerminalAppearance.Setting.FONT, this::onAppearanceChanged));

        addHeading(R.string.settings_terminal);
        addToggle(R.string.settings_extra_keys, preferences.shouldShowTerminalToolbar(), preferences::setShowTerminalToolbar);
        addToggle(R.string.action_toggle_keep_screen_on, preferences.shouldKeepScreenOn(), preferences::setKeepScreenOn);
        addToggle(R.string.settings_keyboard_insets, preferences.isTerminalMarginAdjustmentEnabled(), preferences::setTerminalMarginAdjustment);

        addHeading(R.string.settings_input);
        addToggle(R.string.settings_soft_keyboard, preferences.isSoftKeyboardEnabled(), preferences::setSoftKeyboardEnabled);
        addToggle(R.string.settings_soft_keyboard_without_hardware,
            preferences.isSoftKeyboardEnabledOnlyIfNoHardware(), preferences::setSoftKeyboardEnabledOnlyIfNoHardware);

        addHeading(R.string.settings_storage);
        addAction(R.string.settings_app_permissions, () -> startActivity(new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));

        addHeading(R.string.settings_diagnostics);
        addAction(R.string.settings_log_level, this::showLogLevels);
        addToggle(R.string.settings_key_logging, preferences.isTerminalViewKeyLoggingEnabled(),
            preferences::setTerminalViewKeyLoggingEnabled);
        addToggle(R.string.settings_crash_notifications,
            preferences.areCrashReportNotificationsEnabled(), preferences::setCrashReportNotificationsEnabled);
        addAction(R.string.action_open_help, this::showHelp);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(APPEARANCE_CHANGED, appearanceChanged);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void onAppearanceChanged() {
        appearanceChanged = true;
        setResult(RESULT_OK);
    }

    private void addHeading(int title) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
        heading.setPaddingRelative(dp(12), dp(16), dp(12), dp(4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) heading.setAccessibilityHeading(true);
        options.addView(heading);
    }

    private void addAction(int title, Runnable action) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(title);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(48));
        button.setPaddingRelative(dp(12), dp(4), dp(12), dp(4));
        button.setOnClickListener(view -> action.run());
        options.addView(button);
    }

    private void addToggle(int title, boolean initial, Consumer<Boolean> update) {
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setText(title);
        toggle.setMinHeight(dp(48));
        toggle.setPaddingRelative(dp(12), dp(4), dp(12), dp(4));
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((view, checked) -> update.accept(checked));
        options.addView(toggle);
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
        String currentLevel = String.valueOf(preferences.getLogLevel());
        int selected = -1;
        for (int i = 0; i < levels.length; i++) {
            if (currentLevel.contentEquals(levels[i])) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_log_level)
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                preferences.setLogLevel(this, Integer.parseInt(levels[which].toString()));
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
