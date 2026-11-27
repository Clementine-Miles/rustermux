package com.termux.app.activities;

import android.app.Activity;
import android.app.Application;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class SettingsActivityTest {

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
    public void inputTogglePersistsAtLargerFontScale() {
        RuntimeEnvironment.setFontScale(1.5f);
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity settings = controller.get();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(settings);
        SwitchCompat keyboard = (SwitchCompat) setting(settings, R.string.settings_soft_keyboard);
        assertTrue(keyboard.isChecked());

        keyboard.performClick();

        assertFalse(preferences.isSoftKeyboardEnabled());
        assertFalse(((SwitchCompat) setting(controller.recreate().get(), R.string.settings_soft_keyboard)).isChecked());
    }

    private static TextView setting(SettingsActivity activity, int label) {
        LinearLayout options = activity.findViewById(R.id.settings);
        CharSequence text = activity.getText(label);
        for (int i = 0; i < options.getChildCount(); i++) {
            if (options.getChildAt(i) instanceof TextView) {
                TextView row = (TextView) options.getChildAt(i);
                if (text.equals(row.getText())) return row;
            }
        }
        throw new AssertionError("Missing setting " + text);
    }
}
