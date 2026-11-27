package com.termux.app.terminal.io;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.config.ConfigKeys;
import com.termux.shared.termux.settings.config.TermuxConfig;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    /**
     * Set the terminal extra keys and style.
     */
    public void reloadExtraKeys() {
        TermuxConfig.Snapshot settings = TermuxConfig.get(mActivity).snapshot();
        mExtraKeysInfo = new ExtraKeysInfo(settings.get(ConfigKeys.EXTRA_KEYS),
            settings.get(ConfigKeys.EXTRA_KEYS_STYLE), ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("SESSIONS".equals(key)) {
            mActivity.showSessionMenu();
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
