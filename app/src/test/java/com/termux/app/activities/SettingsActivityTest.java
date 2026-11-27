package com.termux.app.activities;

import android.app.Activity;
import android.app.Application;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.termux.R;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class SettingsActivityTest {

    private TermuxConfig config;

    @Before
    public void setUp() throws Exception {
        config = TermuxConfig.get(RuntimeEnvironment.getApplication());
        restoreConfiguration();
    }

    @After
    public void restoreConfiguration() throws Exception {
        Path path = config.file().toPath();
        Files.createDirectories(path.getParent());
        Files.write(path, "version = 1\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.reload());
    }

    @Test
    public void appearanceSelectionReportsChangeAfterRecreation() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity settings = controller.get();
        setting(settings, R.string.appearance_font).performClick();
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1);

        assertEquals(Activity.RESULT_OK, shadowOf(settings).getResultCode());
        assertEquals(Activity.RESULT_OK, shadowOf(controller.recreate().get()).getResultCode());
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    public void inputTogglePersistsInTomlAtLargerFontScale() {
        RuntimeEnvironment.setFontScale(1.5f);
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity settings = controller.get();
        SwitchCompat keyboard = (SwitchCompat) setting(settings, R.string.settings_soft_keyboard);
        assertTrue(keyboard.isChecked());

        keyboard.performClick();

        assertTrue(config.reload());
        assertFalse(config.snapshot().get(ConfigKeys.SOFT_KEYBOARD));
        assertFalse(((SwitchCompat) setting(controller.recreate().get(), R.string.settings_soft_keyboard)).isChecked());
    }

    @Test
    public void failedGuiSaveLeavesUserFileAndSwitchIntact() throws Exception {
        SettingsActivity settings = Robolectric.setupActivity(SettingsActivity.class);
        Path path = config.file().toPath();
        Files.createDirectories(path.getParent());
        String invalid = "[input\nsoft-keyboard = true";
        Files.write(path, invalid.getBytes(StandardCharsets.UTF_8));
        SwitchCompat keyboard = (SwitchCompat) setting(settings, R.string.settings_soft_keyboard);
        assertTrue(keyboard.isChecked());

        keyboard.performClick();

        assertTrue(keyboard.isChecked());
        assertEquals(invalid, new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        assertNotNull(config.diagnostic());
        assertEquals(Activity.RESULT_CANCELED, shadowOf(settings).getResultCode());
    }

    private static TextView setting(SettingsActivity activity, int label) {
        TextView view = findSetting(activity.findViewById(android.R.id.content), activity.getText(label));
        if (view == null) throw new AssertionError("Missing setting " + activity.getText(label));
        return view;
    }

    private static TextView findSetting(View view, CharSequence label) {
        if (view instanceof TextView && TextUtils.equals(label, ((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findSetting(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
