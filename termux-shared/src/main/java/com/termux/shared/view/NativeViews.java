package com.termux.shared.view;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public final class NativeViews {

    private NativeViews() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    public static Toolbar showScrollPage(AppCompatActivity activity, View content) {
        LinearLayout page = column(activity);
        Context barTheme = new ContextThemeWrapper(activity,
            androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark_ActionBar);
        Toolbar toolbar = new Toolbar(barTheme);
        toolbar.setPopupTheme(androidx.appcompat.R.style.ThemeOverlay_AppCompat_DayNight);
        toolbar.setMinimumHeight(dp(activity, 56));
        page.addView(toolbar, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.setId(android.R.id.list);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

        activity.setContentView(page);
        activity.setSupportActionBar(toolbar);
        return toolbar;
    }
}
