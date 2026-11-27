package com.termux.api;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PACKAGE_NAME;

public final class TermuxAPIConstants {

    public static final String API_NAMESPACE = TERMUX_PACKAGE_NAME + ".api";
    public static final String TERMUX_API_FILE_SHARE_URI_AUTHORITY = API_NAMESPACE + ".sharedfiles";

    private TermuxAPIConstants() {
    }
}
