package com.termux.shared.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.ColorUtils;

import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.theme.ThemeUtils;

/** Colors for application controls, independent of the active terminal palette. */
public final class UiColors {

    public final int background;
    public final int foreground;
    public final int accent;

    private UiColors(int background, int foreground, int accent) {
        this.background = background;
        this.foreground = foreground;
        this.accent = accent;
    }

    public static UiColors resolve(Context context) {
        TermuxConfig.Snapshot config = TermuxConfig.get(context).snapshot();
        int bg = color(config.get(ConfigKeys.UI_BACKGROUND),
            ThemeUtils.getSystemAttrColor(context, android.R.attr.colorBackground, Color.BLACK));
        bg = ColorUtils.setAlphaComponent(bg, 255);
        int preferred = color(config.get(ConfigKeys.UI_FOREGROUND),
            ThemeUtils.getSystemAttrColor(context, android.R.attr.textColorPrimary, Color.WHITE));
        int fg = preferred;
        if (ColorUtils.calculateContrast(fg, bg) < 4.5) {
            fg = ColorUtils.calculateContrast(Color.BLACK, bg) >= 4.5 ? Color.BLACK : Color.WHITE;
        }
        int accent = color(config.get(ConfigKeys.UI_ACCENT),
            ThemeUtils.getSystemAttrColor(context, androidx.appcompat.R.attr.colorAccent, fg));
        if (ColorUtils.calculateContrast(accent, bg) < 4.5) accent = fg;
        return new UiColors(bg, fg, accent);
    }

    public void style(Toolbar toolbar) {
        toolbar.setBackgroundColor(background);
        toolbar.setTitleTextColor(foreground);
        Drawable navigation = toolbar.getNavigationIcon();
        if (navigation != null) navigation.mutate().setTint(foreground);
        Drawable overflow = toolbar.getOverflowIcon();
        if (overflow != null) overflow.mutate().setTint(foreground);
    }

    private static int color(String value, int fallback) {
        return value.isEmpty() ? fallback : Color.parseColor(value);
    }
}
