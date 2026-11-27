package com.termux.app.ui;

import android.app.Application;
import android.content.Context;
import android.widget.Button;

import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.view.NativeViews;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppIconsTest {

    @Test
    public void bundledGlyphAndMissingGlyphShareLabelAndTouchTarget() {
        Context context = RuntimeEnvironment.getApplication();
        TermuxConfig config = TermuxConfig.get(context);
        Button control = new Button(context);
        String label = "Toggle keyboard";
        try {
            int keyboardGlyph = 0xf030c;
            assertTrue(config.setTransient(ConfigKeys.ICON_KEYBOARD, keyboardGlyph));
            AppIcons.apply(control, AppIcons.Icon.KEYBOARD, label);
            assertEquals(new String(Character.toChars(keyboardGlyph)), control.getText().toString());
            assertEquals(label, control.getContentDescription().toString());

            assertTrue(config.setTransient(ConfigKeys.ICON_KEYBOARD, Character.MAX_CODE_POINT));
            AppIcons.apply(control, AppIcons.Icon.KEYBOARD, label);
            assertEquals(label, control.getText().toString());
            assertEquals(label, control.getContentDescription().toString());
            assertTrue(control.getMinimumHeight() >= NativeViews.dp(context, 48));
        } finally {
            config.setTransient(ConfigKeys.ICON_KEYBOARD, null);
        }
    }
}
