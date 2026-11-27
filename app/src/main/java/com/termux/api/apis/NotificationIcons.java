package com.termux.api.apis;

import androidx.annotation.DrawableRes;

import com.termux.R;

import java.util.Locale;

/** The monochrome status-bar icons accepted by termux-notification. */
final class NotificationIcons {

    @DrawableRes static final int DEFAULT = R.drawable.ic_event_note_black_24dp;

    private NotificationIcons() {}

    @DrawableRes
    static int resolve(String name) {
        if (name == null) return DEFAULT;

        switch (name.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "bell":
            case "notifications":
            case "notifications_active":
            case "notifications_none":
                return R.drawable.ic_notifications_black_24dp;
            case "info":
            case "info_outline":
                return R.drawable.ic_info_black_24dp;
            case "success":
            case "check":
            case "check_circle":
            case "done":
            case "done_all":
                return R.drawable.ic_check_circle_black_24dp;
            case "warning":
            case "report_problem":
                return R.drawable.ic_warning_black_24dp;
            case "error":
            case "error_outline":
                return R.drawable.ic_error_black_24dp;
            case "download":
            case "file_download":
            case "cloud_download":
                return R.drawable.ic_file_download_black_24dp;
            case "upload":
            case "file_upload":
            case "cloud_upload":
                return R.drawable.ic_file_upload_black_24dp;
            case "sync":
            case "refresh":
                return R.drawable.ic_sync_black_24dp;
            case "message":
            case "chat":
            case "chat_bubble":
            case "sms":
                return R.drawable.ic_message_black_24dp;
            case "mail":
            case "email":
            case "mail_outline":
                return R.drawable.ic_email_black_24dp;
            case "timer":
            case "alarm":
            case "alarm_on":
            case "access_time":
            case "schedule":
                return R.drawable.ic_schedule_black_24dp;
            case "play":
            case "play_arrow":
            case "play_circle_filled":
                return R.drawable.ic_play_arrow_black_24dp;
            case "star":
            case "star_border":
                return R.drawable.ic_star_black_24dp;
            case "favorite":
            case "favorite_border":
            case "heart":
                return R.drawable.ic_favorite_black_24dp;
            case "terminal":
            case "code":
                return R.drawable.ic_code_black_24dp;
            case "default":
            case "note":
            case "event_note":
            default:
                return DEFAULT;
        }
    }
}
