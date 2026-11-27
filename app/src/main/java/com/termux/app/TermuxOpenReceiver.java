package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.net.uri.UriUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.net.uri.UriScheme;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import androidx.annotation.NonNull;

public class TermuxOpenReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxOpenReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri data = intent.getData();
        if (data == null) {
            Logger.logError(LOG_TAG, "Called without intent data");
            return;
        }

        Logger.logVerbose(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));
        Logger.logVerbose(LOG_TAG, "uri: \"" + data + "\", path: \"" + data.getPath() + "\", fragment: \"" + data.getFragment() + "\"");

        final String contentTypeExtra = intent.getStringExtra("content-type");
        final boolean useChooser = intent.getBooleanExtra("chooser", false);
        final String intentAction = Intent.ACTION_SEND.equals(intent.getAction())
            ? Intent.ACTION_SEND : Intent.ACTION_VIEW;

        String scheme = data.getScheme();
        if (scheme != null && !UriScheme.SCHEME_FILE.equals(scheme)) {
            Intent urlIntent = new Intent(intentAction, data);
            if (intentAction.equals(Intent.ACTION_SEND)) {
                urlIntent.putExtra(Intent.EXTRA_TEXT, data.toString());
                urlIntent.setData(null);
            } else if (contentTypeExtra != null) {
                urlIntent.setDataAndType(data, contentTypeExtra);
            }
            urlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(urlIntent);
            } catch (ActivityNotFoundException e) {
                Logger.logError(LOG_TAG, "No app handles the url " + data);
            }
            return;
        }

        // Get full path including fragment (anything after last "#")
        String filePath = UriUtils.getUriFilePathWithFragment(data);
        if (DataUtils.isNullOrEmpty(filePath)) {
            Logger.logError(LOG_TAG, "filePath is null or empty");
            return;
        }

        final File fileToShare;
        try {
            fileToShare = resolveSharedFile(context, filePath);
        } catch (IOException e) {
            Logger.logError(LOG_TAG, e.getMessage());
            return;
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(intentAction);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String contentTypeToUse;
        if (contentTypeExtra == null) {
            String fileName = fileToShare.getName();
            int lastDotIndex = fileName.lastIndexOf('.');
            String fileExtension = fileName.substring(lastDotIndex + 1);
            MimeTypeMap mimeTypes = MimeTypeMap.getSingleton();
            // Lower casing makes it work with e.g. "JPG":
            contentTypeToUse = mimeTypes.getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ROOT));
            if (contentTypeToUse == null) contentTypeToUse = "application/octet-stream";
        } else {
            contentTypeToUse = contentTypeExtra;
        }

        // Do not create Uri with Uri.parse() and use Uri.Builder().path(), check UriUtils.getUriFilePath().
        Uri uriToShare = UriUtils.getContentUri(TermuxConstants.TERMUX_FILE_SHARE_URI_AUTHORITY, fileToShare.getAbsolutePath());

        if (Intent.ACTION_SEND.equals(intentAction)) {
            sendIntent.putExtra(Intent.EXTRA_STREAM, uriToShare);
            sendIntent.setClipData(ClipData.newRawUri(fileToShare.getName(), uriToShare));
            sendIntent.setType(contentTypeToUse);
        } else {
            sendIntent.setDataAndType(uriToShare, contentTypeToUse);
        }

        if (useChooser) {
            sendIntent = Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            Logger.logError(LOG_TAG, "No app handles the url " + data);
        }
    }

    /** Resolve the file again at open time so URI grants cannot follow links outside allowed roots. */
    public static File resolveSharedFile(Context context, String path) throws IOException {
        if (path == null) throw new FileNotFoundException("Missing shared path");

        File file = new File(path).getCanonicalFile();
        Path canonical = file.toPath();
        if (file.isFile() && file.canRead()) {
            if (isDescendant(canonical, context.getFilesDir())) return file;
            for (File directory : context.getExternalFilesDirs(null)) {
                if (directory != null && isDescendant(canonical, directory)) return file;
            }
            StorageManager storage = context.getSystemService(StorageManager.class);
            if (storage != null) {
                for (StorageVolume volume : storage.getStorageVolumes()) {
                    File directory = volume.getDirectory();
                    if (directory != null && isVolumeFile(canonical, directory)) return file;
                }
            }
            if (isVolumeFile(canonical, Environment.getExternalStorageDirectory())) return file;
        }
        throw new FileNotFoundException("Path does not refer to a readable shared file");
    }

    private static boolean isDescendant(Path file, File directory) throws IOException {
        Path root = directory.getCanonicalFile().toPath();
        return !root.equals(file) && file.startsWith(root);
    }

    private static boolean isVolumeFile(Path file, File directory) throws IOException {
        Path root = directory.getCanonicalFile().toPath();
        if (root.equals(file) || !file.startsWith(root)) return false;
        Path child = root.relativize(file);
        if (child.getNameCount() >= 2 && child.getName(0).toString().equalsIgnoreCase("Android")) {
            String protectedDirectory = child.getName(1).toString();
            return !protectedDirectory.equalsIgnoreCase("data") && !protectedDirectory.equalsIgnoreCase("obb");
        }
        return true;
    }

    public static class ContentProvider extends android.content.ContentProvider {

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            final File file;
            try {
                file = getSharedFile(uri);
            } catch (IOException exception) {
                throw new IllegalArgumentException(exception);
            }

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
        public String getType(@NonNull Uri uri) {
            String pathComponent = uri.getLastPathSegment();
            if (pathComponent == null || pathComponent.isEmpty()) return null;

            int extIndex = pathComponent.lastIndexOf('.') + 1;
            if (extIndex > 0) {
                MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
                String ext = pathComponent.substring(extIndex).toLowerCase(Locale.ROOT);
                return mimeMap.getMimeTypeFromExtension(ext);
            }
            return null;
        }

        @Override
        public Uri insert(@NonNull Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
            if (!mode.equals("r")) {
                throw new FileNotFoundException("The shared file is read-only");
            }
            try {
                return ParcelFileDescriptor.open(getSharedFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (FileNotFoundException exception) {
                throw exception;
            } catch (IOException exception) {
                FileNotFoundException failure = new FileNotFoundException("Cannot resolve the shared path");
                failure.initCause(exception);
                throw failure;
            }
        }

        private File getSharedFile(Uri uri) throws IOException {
            return resolveSharedFile(getContext(), uri.getPath());
        }
    }

}
