package com.termux.app.terminal;

import com.termux.shared.termux.settings.config.SessionShortcut;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionShortcutTest {
    @Test
    public void requiresExplicitActivationChordAndValidUniqueModifiers() {
        assertFalse(SessionShortcut.isValid("c", true));
        assertFalse(SessionShortcut.isValid("ctrl+c", true));
        assertFalse(SessionShortcut.isValid("ctrl+ctrl+space", true));
        assertFalse(SessionShortcut.isValid("alt+ctrl+", true));
        assertFalse(SessionShortcut.isValid("ctrl+alt+return", true));
        assertTrue(SessionShortcut.isValid("ctrl+alt+shift+space", true));
        assertTrue(SessionShortcut.isValid("", true));
    }

    @Test
    public void modeBindingsMatchLiteralInputWithoutConsumingDifferentModifiers() {
        SessionShortcut create = SessionShortcut.parse("n");
        assertTrue(create.matches('n', false));
        assertFalse(create.matches('n', true));
        assertFalse(create.matches('N', false));

        SessionShortcut shifted = SessionShortcut.parse("shift+n");
        assertTrue(shifted.matches('N', false));
        assertFalse(shifted.matches('n', false));
        assertFalse(SessionShortcut.parse("left").matches('l', false));
    }
}
