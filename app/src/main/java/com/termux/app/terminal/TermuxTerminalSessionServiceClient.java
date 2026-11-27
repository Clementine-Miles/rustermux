package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxService;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;

/** Persistent PTY client. UI callbacks are optional while the terminal service is running. */
public final class TermuxTerminalSessionServiceClient extends TermuxTerminalSessionClientBase {

    private final TermuxService service;
    @Nullable private TermuxTerminalSessionActivityClient observer;

    public TermuxTerminalSessionServiceClient(TermuxService service) {
        this.service = service;
    }

    public void setObserver(@Nullable TermuxTerminalSessionActivityClient observer) {
        this.observer = observer;
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession session) {
        service.onTerminalProcessFinished(session);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
        service.setShellPid(session, pid);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return service.getCursorStyle();
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession session) {
        if (observer != null) observer.onTextChanged(session);
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession session) {
        if (observer != null) observer.onTitleChanged(session);
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (observer != null) observer.onCopyTextToClipboard(session, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (observer != null) observer.onPasteTextFromClipboard(session);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (observer != null) observer.onBell(session);
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {
        if (observer != null) observer.onColorsChanged(session);
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        if (observer != null) observer.onTerminalCursorStateChange(enabled);
    }
}
