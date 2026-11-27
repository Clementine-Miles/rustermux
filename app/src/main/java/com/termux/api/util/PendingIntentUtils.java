package com.termux.api.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.api.TermuxAPIConstants;

public class PendingIntentUtils {

    /**
     * Get {@link PendingIntent#FLAG_MUTABLE} flag.
     *
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     */
    public static int getPendingIntentMutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return PendingIntent.FLAG_MUTABLE;
        else
            return 0;
    }
    /**
     * Try to get the next unique {@link PendingIntent} request code that isn't already being used by
     * the app and which would create a unique {@link PendingIntent} that doesn't conflict with that
     * of any other execution commands.
     *
     * @param context The {@link Context} for operations.
     * @return Returns the request code that should be safe to use.
     */
    public synchronized static int getNextRequestCode(final Context context) {
        SharedPreferences preferences = context.getSharedPreferences(TermuxAPIConstants.API_NAMESPACE, Context.MODE_PRIVATE);
        String key = "last_pending_intent_request_code";
        int current = preferences.getInt(key, 0);
        int next = current == Integer.MAX_VALUE ? 0 : current + 1;
        preferences.edit().putInt(key, next).apply();
        return next;
    }

}
