package com.termux.shared.activity.media;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.termux.shared.theme.NightMode;

public class AppCompatActivityUtils {

    /** Set activity night mode.
     *
     * @param activity The host {@link AppCompatActivity}.
     * @param name The {@link String} representing the name for a {@link NightMode}.
     * @param local If set to {@code true}, then a call to {@link AppCompatDelegate#setLocalNightMode(int)}
     *              will be made, otherwise to {@link AppCompatDelegate#setDefaultNightMode(int)}.
     */
    public static void setNightMode(AppCompatActivity activity, String name, boolean local) {
        if (name == null) return;
        NightMode nightMode = NightMode.modeOf(name);
        if (nightMode != null) {
            if (local) {
                if (activity != null) {
                    activity.getDelegate().setLocalNightMode(nightMode.getMode());
                }
            } else {
                AppCompatDelegate.setDefaultNightMode(nightMode.getMode());
            }
        }

    }

    /** Set whether back button should be shown in activity toolbar.
     *
     * @param activity The host {@link AppCompatActivity}.
     * @param showBackButtonInActionBar Set to {@code true} to enable and {@code false} to disable.
     */
    public static void setShowBackButtonInActionBar(@NonNull AppCompatActivity activity,
                                                    boolean showBackButtonInActionBar) {
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            if (showBackButtonInActionBar) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            } else {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setDisplayShowHomeEnabled(false);
            }
        }
    }

}
