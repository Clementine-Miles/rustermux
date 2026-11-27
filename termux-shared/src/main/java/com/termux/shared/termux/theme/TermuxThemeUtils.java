package com.termux.shared.termux.theme;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.theme.NightMode;

public class TermuxThemeUtils {

    /** Apply the current TOML night-mode setting. */
    public static void setAppNightMode(@NonNull Context context) {
        NightMode.setAppNightMode(TermuxConfig.get(context).snapshot().get(ConfigKeys.NIGHT_MODE));
    }

    /** Set name as app wide night mode value. */
    public static void setAppNightMode(@Nullable String name) {
        NightMode.setAppNightMode(name);
    }

}
