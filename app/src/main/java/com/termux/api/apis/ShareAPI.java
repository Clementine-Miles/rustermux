package com.termux.api.apis;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.termux.R;
import com.termux.api.TermuxAPIConstants;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.net.uri.UriUtils;

import java.io.File;
import java.io.FileNotFoundException;
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
                    fileToShare = new File(fileExtra).getCanonicalFile();
                } catch (IOException e) {
                    out.println("ERROR: Could not resolve file path.");
                    return;
                }
                if (!(fileToShare.isFile() && fileToShare.canRead())) {
                    out.println("ERROR: Not a readable file: '" + fileToShare.getAbsolutePath() + "'");
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

    public static class ContentProvider extends android.content.ContentProvider {

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            File file = new File(uri.getPath());
            String fileName = file.getName();

            if (projection == null) {
                projection = new String[]{
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns._ID
                };
            }

            Object[] row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                String column = projection[i];
                Object value;
                switch (column) {
                    case MediaStore.MediaColumns.DISPLAY_NAME:
                        value = file.getName();
                        break;
                    case MediaStore.MediaColumns.SIZE:
                        value = file.length();
                        break;
                    case MediaStore.MediaColumns._ID:
                        value = 1;
                        break;
                    default:
                        value = null;
                }
                row[i] = value;
            }

            MatrixCursor cursor = new MatrixCursor(projection);
            cursor.addRow(row);
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            String path = uri.getPath();
            return path == null ? null : getMimeType(new File(path).getName());
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            String path = uri.getPath();
            if (!"r".equals(mode) || path == null) throw new FileNotFoundException("Share files are read-only");
            return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

}
