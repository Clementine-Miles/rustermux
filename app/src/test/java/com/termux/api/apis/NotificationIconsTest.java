package com.termux.api.apis;

import com.termux.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NotificationIconsTest {

    @Test
    public void semanticNamesAndLegacyAliasesSelectTheSameDrawable() {
        assertEquals(R.drawable.ic_notifications_black_24dp, NotificationIcons.resolve("bell"));
        assertEquals(NotificationIcons.resolve("bell"), NotificationIcons.resolve("notifications_active"));
        assertEquals(R.drawable.ic_check_circle_black_24dp, NotificationIcons.resolve("success"));
        assertEquals(NotificationIcons.resolve("success"), NotificationIcons.resolve("done_all"));
        assertEquals(R.drawable.ic_file_download_black_24dp, NotificationIcons.resolve("download"));
        assertEquals(NotificationIcons.resolve("download"), NotificationIcons.resolve(" File-Download "));
    }

    @Test
    public void blankAndUnknownNamesUseTheDefaultDrawable() {
        assertEquals(R.drawable.ic_event_note_black_24dp, NotificationIcons.resolve(null));
        assertEquals(NotificationIcons.DEFAULT, NotificationIcons.resolve(""));
        assertEquals(NotificationIcons.DEFAULT, NotificationIcons.resolve("event_note"));
        assertEquals(NotificationIcons.DEFAULT, NotificationIcons.resolve("no_such_icon"));
    }
}
