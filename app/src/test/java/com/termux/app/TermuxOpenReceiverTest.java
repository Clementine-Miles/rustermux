package com.termux.app;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import com.termux.shared.net.uri.UriUtils;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class TermuxOpenReceiverTest {
    private static final String AUTHORITY = TermuxConstants.TERMUX_FILE_SHARE_URI_AUTHORITY;

    @Test
    public void fileProviderRequiresUriGrants() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ProviderInfo info = context.getPackageManager().getProviderInfo(
            new ComponentName(context, TermuxOpenReceiver.ContentProvider.class), 0);

        assertEquals(AUTHORITY, info.authority);
        assertFalse(info.exported);
        assertTrue(info.grantUriPermissions);
    }

    @Test
    public void fileProviderSharesOnlyReadOnlyRegularFilesWithinItsRoots() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        TermuxOpenReceiver.ContentProvider provider =
            Robolectric.setupContentProvider(TermuxOpenReceiver.ContentProvider.class, AUTHORITY);
        File shared = new File(context.getFilesDir(), "shared.txt");
        Files.write(shared.toPath(), new byte[]{1, 2, 3});

        Uri sharedUri = UriUtils.getContentUri(AUTHORITY, shared.getAbsolutePath());
        try (ParcelFileDescriptor descriptor = provider.openFile(sharedUri, "r")) {
            assertEquals(3, descriptor.getStatSize());
        }
        try (Cursor cursor = provider.query(sharedUri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(3, cursor.getLong(0));
        }
        assertThrows(FileNotFoundException.class, () -> provider.openFile(sharedUri, "rw"));

        File lookalikeDirectory = new File(context.getFilesDir().getParentFile(), "files-extra");
        assertTrue(lookalikeDirectory.mkdir());
        File outside = new File(lookalikeDirectory, "private.txt");
        Files.write(outside.toPath(), new byte[]{4});
        Uri outsideUri = UriUtils.getContentUri(AUTHORITY, outside.getAbsolutePath());
        assertThrows(FileNotFoundException.class, () -> provider.openFile(outsideUri, "r"));
    }
}
