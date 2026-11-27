package com.termux.app;

import java.util.ArrayDeque;

import androidx.annotation.Nullable;

/** Pending user requests that may need to wait for a service bind or bootstrap. */
final class SessionLaunchRequests {
    private final ArrayDeque<Boolean> shortcuts = new ArrayDeque<>();
    private boolean initial;

    SessionLaunchRequests(boolean initial, @Nullable boolean[] pending) {
        this.initial = initial;
        if (pending != null) for (boolean failsafe : pending) shortcuts.addLast(failsafe);
    }

    void add(boolean failsafe) {
        shortcuts.addLast(failsafe);
    }

    @Nullable
    Boolean next(boolean alreadyHasSessions) {
        if (alreadyHasSessions) initial = false;
        if (!shortcuts.isEmpty()) return shortcuts.peekFirst();
        return initial ? Boolean.FALSE : null;
    }

    void consume() {
        shortcuts.pollFirst();
        initial = false;
    }

    void clear() {
        shortcuts.clear();
        initial = false;
    }

    boolean hasInitial() {
        return initial;
    }

    boolean[] savedRequests() {
        boolean[] pending = new boolean[shortcuts.size()];
        int index = 0;
        for (boolean failsafe : shortcuts) pending[index++] = failsafe;
        return pending;
    }
}
