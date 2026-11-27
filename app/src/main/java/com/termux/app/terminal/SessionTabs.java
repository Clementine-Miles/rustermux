package com.termux.app.terminal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.ui.AppIcons;
import com.termux.app.terminal.SessionRegistry.SessionInfo;
import com.termux.shared.view.UiColors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The scrollable controls for the service's ordered session snapshot. */
public final class SessionTabs extends LinearLayout {
    private final Button mode;
    private final Button add;
    private final HorizontalScrollView scroll;
    private final LinearLayout items;
    private final Map<String, Button> buttons = new HashMap<>();
    private final Consumer<String> select;
    private final Consumer<String> actions;
    private String activeId;

    public SessionTabs(Context context, Runnable create, Runnable toggleControl,
                       OnLongClickListener modeActions, Consumer<String> select, Consumer<String> actions) {
        super(context);
        this.select = select;
        this.actions = actions;
        setGravity(Gravity.CENTER_VERTICAL);

        mode = button();
        mode.setOnClickListener(view -> toggleControl.run());
        mode.setOnLongClickListener(modeActions);
        addView(mode);

        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setFocusable(false);
        addView(scroll, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        items = new LinearLayout(context);
        items.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(items);

        add = button();
        add.setOnClickListener(view -> create.run());
        addView(add);
    }

    public void render(List<SessionInfo> sessions, boolean showTabs, boolean controlEnabled) {
        Context context = getContext();
        UiColors colors = UiColors.resolve(context);
        AppIcons.apply(mode, AppIcons.Icon.SESSION_MODE,
            context.getText(controlEnabled ? R.string.action_leave_session_control : R.string.action_enter_session_control));
        if (controlEnabled) mode.setText(R.string.session_control_indicator);
        AppIcons.apply(add, AppIcons.Icon.NEW_SESSION, context.getText(R.string.action_new_session));
        mode.setSelected(controlEnabled);
        style(mode, colors, controlEnabled);
        style(add, colors, false);
        mode.setTooltipText(mode.getContentDescription());
        add.setTooltipText(add.getContentDescription());
        boolean wasVisible = scroll.getVisibility() == VISIBLE;
        scroll.setVisibility(showTabs ? VISIBLE : GONE);

        String previousActive = activeId;
        activeId = null;
        Map<String, Button> previous = new HashMap<>(buttons);
        for (int i = 0; i < sessions.size(); i++) {
            SessionInfo info = sessions.get(i);
            Button tab = previous.remove(info.id);
            if (tab == null) {
                tab = button();
                tab.setOnClickListener(view -> select.accept(info.id));
                tab.setOnLongClickListener(view -> {
                    actions.accept(info.id);
                    return true;
                });
                buttons.put(info.id, tab);
            }
            if (items.indexOfChild(tab) != i) {
                items.removeView(tab);
                items.addView(tab, i);
            }

            String label = info.name == null ? context.getString(R.string.unnamed_session, info.number) : info.name;
            if (info.state == SessionRegistry.State.EXITED) label = context.getString(R.string.finished_session_name, label);
            else if (info.state == SessionRegistry.State.TERMINATING) label = context.getString(R.string.terminating_session_name, label);
            else if (info.state == SessionRegistry.State.CREATED) label = context.getString(R.string.starting_session_name, label);
            tab.setText(label);
            tab.setSelected(info.active);
            tab.setContentDescription(info.active ? context.getString(R.string.active_session_name, label) : label);
            tab.setTooltipText(tab.getContentDescription());
            style(tab, colors, info.active);
            if (info.active) activeId = info.id;
        }

        for (Map.Entry<String, Button> stale : previous.entrySet()) {
            items.removeView(stale.getValue());
            buttons.remove(stale.getKey());
        }

        if (showTabs && activeId != null && (!wasVisible || !activeId.equals(previousActive))) {
            Button active = buttons.get(activeId);
            scroll.post(() -> {
                if (active.getParent() == items) {
                    int left = active.getLeft();
                    int right = active.getRight();
                    if (left < scroll.getScrollX()) scroll.smoothScrollTo(left, 0);
                    else if (right > scroll.getScrollX() + scroll.getWidth()) {
                        scroll.smoothScrollTo(right - scroll.getWidth(), 0);
                    }
                }
            });
        }
    }

    public View modeAnchor() {
        return mode;
    }

    private Button button() {
        Button button = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMaxWidth(dp(160));
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(9), 0, dp(9), 0);
        return button;
    }

    private void style(Button button, UiColors colors, boolean selected) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(selected ? colors.foreground : colors.background);
        fill.setCornerRadius(dp(5));
        int text = selected ? colors.background : colors.foreground;
        int ripple = (text & 0x00ffffff) | 0x33000000;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), fill, null));
        button.setTextColor(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
