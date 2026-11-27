package com.termux.api.apis;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.termux.R;
import com.termux.api.TermuxAPIConstants;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.app.TermuxOpenReceiver;
import com.termux.shared.logger.Logger;
import com.termux.shared.net.uri.UriUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

public class ShareAPI {

    private static final String LOG_TAG = "ShareAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final String fileExtra = intent.getStringExtra("file");
        final String titleExtra = intent.getStringExtra("title");
        final String contentTypeExtra = intent.getStringExtra("content-type");
        final boolean defaultReceiverExtra = intent.getBooleanExtra("default-receiver", false);
        final String actionExtra = intent.getStringExtra("action");

        String intentAction = Intent.ACTION_VIEW;
        if (actionExtra != null) {
            switch (actionExtra) {
                case "edit":
                    intentAction = Intent.ACTION_EDIT;
                    break;
                case "send":
                    intentAction = Intent.ACTION_SEND;
                    break;
                case "view":
                    intentAction = Intent.ACTION_VIEW;
                    break;
                default:
                    Logger.logError(LOG_TAG, "Invalid action '" + actionExtra + "', using 'view'");
                    break;
            }
        }
        final String finalIntentAction = intentAction;

        if (fileExtra == null) {
            // Read text to share from stdin.
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
                @Override
                public void writeResult(PrintWriter out) {
                    if (TextUtils.isEmpty(inputString)) {
                        out.println("Error: Nothing to share");
                        return;
                    }

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(finalIntentAction);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, inputString);
                    sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (titleExtra != null) sendIntent.putExtra(Intent.EXTRA_SUBJECT, titleExtra);
                    sendIntent.setType(contentTypeExtra == null ? "text/plain" : contentTypeExtra);

                    context.startActivity(Intent.createChooser(sendIntent, context.getResources().getText(R.string.share_file_chooser_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                }
            });
        } else {
            // Share specified file.
            ResultReturner.returnData(apiReceiver, intent, out -> {
                final File fileToShare;
                try {
                    fileToShare = TermuxOpenReceiver.resolveSharedFile(context, fileExtra);
                } catch (IOException e) {
                    out.println("ERROR: " + e.getMessage());
                    return;
                }

                Intent sendIntent = new Intent();
                sendIntent.setAction(finalIntentAction);

                // Do not create Uri with Uri.parse() and use Uri.Builder().path(), check UriUtils.getUriFilePath().
                Uri uriToShare = UriUtils.getContentUri(TermuxAPIConstants.TERMUX_API_FILE_SHARE_URI_AUTHORITY, fileToShare.getAbsolutePath());
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sendIntent.setClipData(ClipData.newRawUri(fileToShare.getName(), uriToShare));

                String contentTypeToUse = contentTypeExtra == null ? getMimeType(fileToShare.getName()) : contentTypeExtra;

                if (titleExtra != null) sendIntent.putExtra(Intent.EXTRA_SUBJECT, titleExtra);

                if (Intent.ACTION_SEND.equals(finalIntentAction)) {
                    sendIntent.putExtra(Intent.EXTRA_STREAM, uriToShare);
                    sendIntent.setType(contentTypeToUse);
                } else {
                    sendIntent.setDataAndType(uriToShare, contentTypeToUse);
                }

                if (!defaultReceiverExtra) {
                    sendIntent = Intent.createChooser(sendIntent, context.getResources().getText(R.string.share_file_chooser_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(sendIntent);
            });
        }
    }

    private static String getMimeType(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        String fileExtension = fileName.substring(lastDotIndex + 1);
        String contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ROOT));
        return contentType == null ? "application/octet-stream" : contentType;
    }

    /** Keeps the integrated API's existing URI authority and the same read-only grant policy. */
    public static class ContentProvider extends TermuxOpenReceiver.ContentProvider {}
}
