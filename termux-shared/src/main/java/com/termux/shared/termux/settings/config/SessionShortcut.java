package com.termux.shared.termux.settings.config;

import android.view.KeyEvent;

import androidx.annotation.Nullable;

import java.util.Locale;

/** A key chord shared by TOML validation and the native session controls. */
public final class SessionShortcut {
    private static final int CONTROL = 1;
    private static final int ALTERNATE = 1 << 1;
    private static final int SHIFT = 1 << 2;

    private final int keyCode;
    private final int character;
    private final int modifiers;

    private SessionShortcut(int keyCode, int character, int modifiers) {
        this.keyCode = keyCode;
        this.character = character;
        this.modifiers = modifiers;
    }

    public static boolean isValid(String value, boolean activation) {
        if (value == null) return false;
        if (value.isEmpty()) return true;
        SessionShortcut shortcut = parse(value);
        return shortcut != null && (!activation || Integer.bitCount(shortcut.modifiers) >= 2);
    }

    @Nullable
    public static SessionShortcut parse(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.toLowerCase(Locale.ROOT).split("\\+", -1);
        int modifiers = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            int modifier;
            switch (parts[i]) {
                case "ctrl": modifier = CONTROL; break;
                case "alt": modifier = ALTERNATE; break;
                case "shift": modifier = SHIFT; break;
                default: return null;
            }
            if ((modifiers & modifier) != 0) return null;
            modifiers |= modifier;
        }
        String key = parts[parts.length - 1];
        switch (key) {
            case "space": return new SessionShortcut(KeyEvent.KEYCODE_SPACE, ' ', modifiers);
            case "left": return new SessionShortcut(KeyEvent.KEYCODE_DPAD_LEFT, 0, modifiers);
            case "right": return new SessionShortcut(KeyEvent.KEYCODE_DPAD_RIGHT, 0, modifiers);
            case "up": return new SessionShortcut(KeyEvent.KEYCODE_DPAD_UP, 0, modifiers);
            case "down": return new SessionShortcut(KeyEvent.KEYCODE_DPAD_DOWN, 0, modifiers);
            case "enter": return new SessionShortcut(KeyEvent.KEYCODE_ENTER, '\n', modifiers);
            case "escape": return new SessionShortcut(KeyEvent.KEYCODE_ESCAPE, 27, modifiers);
            case "tab": return new SessionShortcut(KeyEvent.KEYCODE_TAB, '\t', modifiers);
            default:
                if (key.length() != 1) return null;
                char character = key.charAt(0);
                if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                    return new SessionShortcut(KeyEvent.KEYCODE_UNKNOWN, character, modifiers);
                }
                return null;
        }
    }

    public boolean matches(KeyEvent event) {
        if (event.isMetaPressed() || event.isFunctionPressed() || event.isSymPressed()) return false;
        int eventModifiers = modifiers(event.isCtrlPressed(), event.isAltPressed(), event.isShiftPressed());
        if (eventModifiers != modifiers) return false;
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) return event.getKeyCode() == keyCode;
        return Character.toLowerCase(event.getUnicodeChar(0)) == character;
    }

    /** Code points produced by an input method without a corresponding physical key event. */
    public boolean matches(int codePoint, boolean control) {
        return character != 0 && modifiers == modifiers(control, false, Character.isUpperCase(codePoint))
            && character == Character.toLowerCase(codePoint);
    }

    private static int modifiers(boolean control, boolean alt, boolean shift) {
        return (control ? CONTROL : 0) | (alt ? ALTERNATE : 0) | (shift ? SHIFT : 0);
    }
}
