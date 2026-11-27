package com.termux.shared.termux.extrakeys;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** An extra key, optionally with a swipe-up action. */
public final class ExtraKeyButton {
    private final String key;
    private final boolean macro;
    private final String display;
    @Nullable private final ExtraKeyButton popup;

    ExtraKeyButton(Object value, ExtraKeysConstants.ExtraKeyDisplayMap labels,
                   ExtraKeysConstants.ExtraKeyDisplayMap aliases) {
        Map<?, ?> entry = value instanceof Map ? (Map<?, ?>) value : Collections.singletonMap("key", value);
        macro = entry.containsKey("macro");
        String action = (String) entry.get(macro ? "macro" : "key");
        String[] keys = macro ? action.split(" ") : new String[]{action};
        for (int i = 0; i < keys.length; i++) {
            keys[i] = aliases.get(keys[i], keys[i]);
        }
        key = TextUtils.join(" ", keys);
        Object customLabel = entry.get("display");
        display = customLabel != null ? (String) customLabel : Arrays.stream(keys)
            .map(token -> labels.get(token, token)).collect(Collectors.joining(" "));
        Object popupAction = entry.get("popup");
        popup = popupAction == null ? null : new ExtraKeyButton(popupAction, labels, aliases);
    }

    public String getKey() { return key; }
    public boolean isMacro() { return macro; }
    public String getDisplay() { return display; }
    @Nullable public ExtraKeyButton getPopup() { return popup; }
}
