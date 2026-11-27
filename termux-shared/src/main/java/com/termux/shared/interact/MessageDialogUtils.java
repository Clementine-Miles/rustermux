package com.termux.shared.interact;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.util.Linkify;
import android.widget.TextView;

public final class MessageDialogUtils {

    private MessageDialogUtils() {}

    public static void showMessage(Context context, String title, String message,
                                   DialogInterface.OnDismissListener onDismiss) {
        showMessage(context, title, message, null, null, null, null, onDismiss);
    }

    public static void showMessage(Context context, String title, String message,
                                   String positiveText, DialogInterface.OnClickListener onPositive,
                                   String negativeText, DialogInterface.OnClickListener onNegative,
                                   DialogInterface.OnDismissListener onDismiss) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText == null ? context.getString(android.R.string.ok) : positiveText, onPositive)
            .setOnDismissListener(onDismiss);
        if (negativeText != null) builder.setNegativeButton(negativeText, onNegative);

        AlertDialog dialog = builder.show();
        TextView text = dialog.findViewById(android.R.id.message);
        if (text != null) Linkify.addLinks(text, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
    }

    public static void exitAppWithErrorMessage(Context context, String title, String message) {
        showMessage(context, title, message, dialog -> System.exit(0));
    }
}
