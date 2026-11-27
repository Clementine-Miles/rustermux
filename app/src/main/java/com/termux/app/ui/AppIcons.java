package com.termux.app.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.widget.Button;

import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.view.NativeViews;

import java.io.IOException;

public final class AppIcons {

    private static final String FONT_ASSET = "appearance/fonts/HackNerdFontMono-Regular.ttf";

    public enum Icon {
        KEYBOARD(ConfigKeys.ICON_KEYBOARD),
        SETTINGS(ConfigKeys.ICON_SETTINGS),
        NEW_SESSION(ConfigKeys.ICON_NEW_SESSION),
        SESSION_MODE(ConfigKeys.ICON_SESSION_MODE);

        private final ConfigKeys.Key<Integer> codePoint;

        Icon(ConfigKeys.Key<Integer> codePoint) {
            this.codePoint = codePoint;
        }
    }

    private static Font bundledFont;
    private static Typeface typeface;
    private static boolean loaded;
    private static final SparseBooleanArray supportedGlyphs = new SparseBooleanArray();

    private AppIcons() {}

    public static void apply(Button button, Icon icon, CharSequence label) {
        Context context = button.getContext();
        int codePoint = TermuxConfig.get(context).snapshot().get(icon.codePoint);
        String glyph = new String(Character.toChars(codePoint));
        Typeface iconTypeface = iconTypeface(context, codePoint, glyph);

        button.setAllCaps(false);
        button.setContentDescription(label);
        button.setTooltipText(label);
        button.setGravity(Gravity.CENTER);
        int minSize = NativeViews.dp(context, 48);
        button.setMinHeight(minSize);
        button.setMinWidth(minSize);
        button.setMinimumHeight(minSize);
        button.setMinimumWidth(minSize);
        button.setTypeface(iconTypeface == null ? Typeface.DEFAULT : iconTypeface);
        button.setText(iconTypeface == null ? label : glyph);
        button.setTextSize(iconTypeface == null ? 14 : 24);
        button.setIncludeFontPadding(iconTypeface == null);
        int padding = iconTypeface == null ? NativeViews.dp(context, 8) : 0;
        button.setPaddingRelative(padding, 0, padding, 0);
    }

    private static synchronized Typeface iconTypeface(Context context, int codePoint, String glyph) {
        if (!loaded) {
            try {
                bundledFont = new Font.Builder(context.getAssets(), FONT_ASSET).build();
                typeface = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(bundledFont).build()).build();
            } catch (IOException | RuntimeException e) {
                Log.w("AppIcons", "Could not load the bundled control glyphs", e);
            }
            loaded = true;
        }
        if (typeface == null) return null;

        if (supportedGlyphs.indexOfKey(codePoint) < 0) {
            Paint paint = new Paint();
            paint.setTypeface(typeface);
            paint.setTextSize(24);
            PositionedGlyphs shaped = TextRunShaper.shapeTextRun(glyph, 0, glyph.length(),
                0, glyph.length(), 0, 0, false, paint);
            // Shaping may select a system fallback font, even if Paint.hasGlyph would succeed.
            supportedGlyphs.put(codePoint, shaped.glyphCount() == 1 && shaped.getGlyphId(0) != 0 &&
                bundledFont.equals(shaped.getFont(0)));
        }
        return supportedGlyphs.get(codePoint) ? typeface : null;
    }
}
