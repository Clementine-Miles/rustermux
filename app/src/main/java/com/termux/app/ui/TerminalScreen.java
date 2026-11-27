package com.termux.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.view.NativeViews;
import com.termux.shared.view.UiColors;
import com.termux.view.TerminalView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public final class TerminalScreen {

    public final TermuxActivityRootView root;
    public final TerminalView terminal;
    public final LinearLayout terminalContainer;
    public final ExtraKeysView extraKeys;
    public final View bottomInset;
    public final Button keyboardButton;
    public final Button settingsButton;

    private final LinearLayout controls;

    public TerminalScreen(Context context) {
        root = new TermuxActivityRootView(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        keyboardButton = controlButton(context);
        settingsButton = controlButton(context);
        controls.addView(keyboardButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        controls.addView(settingsButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        root.addView(controls, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        terminalContainer = NativeViews.column(context);
        terminal = new TerminalView(context, null);
        terminal.setDefaultFocusHighlightEnabled(false);
        terminal.setFocusableInTouchMode(true);
        GradientDrawable scrollbar = new GradientDrawable();
        scrollbar.setColor(0x66ffffff);
        scrollbar.setSize(NativeViews.dp(context, 4), 0);
        terminal.setVerticalScrollbarThumbDrawable(scrollbar);
        terminalContainer.addView(terminal, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

        extraKeys = new ExtraKeysView(context);
        extraKeys.setVisibility(View.GONE);
        terminalContainer.addView(extraKeys, new LinearLayout.LayoutParams(MATCH_PARENT, NativeViews.dp(context, 37.5f)));
        root.addView(terminalContainer, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

        bottomInset = new View(context);
        root.addView(bottomInset, new LinearLayout.LayoutParams(MATCH_PARENT, NativeViews.dp(context, 1)));

    }

    public void setSessionTabs(View tabs) {
        controls.addView(tabs, 0, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
    }

    public void setControlsVisible(boolean visible) {
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setColors(UiColors colors) {
        controls.setBackgroundColor(colors.background);
        keyboardButton.setTextColor(colors.foreground);
        settingsButton.setTextColor(colors.foreground);
        extraKeys.setBackgroundColor(colors.background);
        int pressed = ColorUtils.blendARGB(colors.background, colors.foreground, 0.12f);
        extraKeys.setButtonColors(colors.foreground, colors.accent, colors.background, pressed);
    }

    private static Button controlButton(Context context) {
        Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        button.setMaxWidth(NativeViews.dp(context, 128));
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }
}
