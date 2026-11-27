package com.termux.app.ui;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;

/** Glyphs from the bundled Hack Nerd Font Mono, independent of the terminal's selected font. */
public final class AppIcons {

    private static final String FONT_ASSET = "appearance/fonts/HackNerdFontMono-Regular.ttf";

    public enum Icon {
        KEYBOARD(0xF030C), // md-keyboard
        SETTINGS(0xEB51);  // cod-settings_gear

        private final String glyph;

        Icon(int codePoint) {
            glyph = new String(Character.toChars(codePoint));
        }
    }

    private AppIcons() {}

    public static void apply(Button button, Icon icon, CharSequence label) {
        button.setAllCaps(false);
        button.setText(label);
        button.setContentDescription(label);

        int minSize = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
            button.getResources().getDisplayMetrics()));
        button.setMinHeight(minSize);
        button.setMinWidth(minSize);
        button.setMinimumHeight(minSize);
        button.setMinimumWidth(minSize);

        Typeface font;
        try {
            font = Typeface.createFromAsset(button.getContext().getAssets(), FONT_ASSET);
        } catch (RuntimeException e) {
            return;
        }
        Paint paint = new Paint(button.getPaint());
        paint.setTypeface(font);
        if (!paint.hasGlyph(icon.glyph)) return;

        button.setTypeface(font);
        button.setText(icon.glyph);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPaddingRelative(0, 0, 0, 0);
    }
}
