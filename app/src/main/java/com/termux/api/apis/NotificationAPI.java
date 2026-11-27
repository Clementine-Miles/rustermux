package com.termux.api.apis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.util.Pair;

import com.termux.api.TermuxAPIConstants;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.PendingIntentUtils;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.UUID;

public class NotificationAPI {

    private static final String LOG_TAG = "NotificationAPI";

    public static final String BIN_SH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh";
    public static final String ACTION_NOTIFICATION_REPLY = TermuxAPIConstants.API_NAMESPACE + ".NOTIFICATION_REPLY";
    private static final String CHANNEL_ID_PREFIX = "termux-api:";
    private static final String CHANNEL_ID = "termux-notification";
    private static final String CHANNEL_TITLE = "Termux API notification channel";
    private static final String KEY_TEXT_REPLY = "TERMUX_TEXT_REPLY";
    private static final String EXTRA_REPLY_ACTION = "com.termux.api.reply_action";
    private static final String EXTRA_CONTENT = "com.termux.api.notification_content";

    /**
     * Show a notification. Driven by the termux-show-notification script.
     */
    public static void onReceiveShowNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveShowNotification");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                NotificationChannel channel = new NotificationChannel(getPlatformChannelId(CHANNEL_ID),
                        CHANNEL_TITLE, priorityFromIntent(intent));
                manager.createNotificationChannel(channel);
                String customChannelId = intent.getStringExtra("channel");
                if (customChannelId != null && manager.getNotificationChannel(getPlatformChannelId(customChannelId)) == null) {
                    out.println("Notification channel not found.");
                    return;
                }

                intent.putExtra(EXTRA_CONTENT, inputString);
                Pair<NotificationCompat.Builder, String> notification = buildNotification(context, intent);
                if (!postNotification(context, notification.second, notification.first.build())) {
                    out.println("Notifications are disabled. Allow notifications for Termux in Android settings and retry.");
                }
            }
        });
    }

    public static void onReceiveChannel(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveChannel");

        ResultReturner.returnData(apiReceiver, intent, out -> {
            String channelId = intent.getStringExtra("id");
            if (TextUtils.isEmpty(channelId)) {
                out.println("Channel id not specified.");
                return;
            }

            String platformChannelId = getPlatformChannelId(channelId);
            try {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (intent.getBooleanExtra("delete", false)) {
                    manager.deleteNotificationChannel(platformChannelId);
                    out.println("Deleted channel with id \"" + channelId + "\".");
                    return;
                }

                String channelName = intent.getStringExtra("name");
                if (TextUtils.isEmpty(channelName)) {
                    out.println("Cannot create a channel without a name.");
                    return;
                }

                manager.createNotificationChannel(new NotificationChannel(platformChannelId, channelName, priorityFromIntent(intent)));
                out.println("Created channel with id \"" + channelId + "\" and name \"" + channelName + "\".");
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Could not create/delete channel: " + e.getClass().getSimpleName());
                out.println("Could not create/delete channel.");
            }
        });
    }

    private static String getPlatformChannelId(String channelId) {
        return CHANNEL_ID_PREFIX + channelId;
    }

    private static boolean postNotification(Context context, String id, Notification notification) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return false;
        try {
            manager.notify(id, 0, notification);
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    private static int priorityFromIntent(Intent intent) {
        String priorityExtra = intent.getStringExtra("priority");
        if (priorityExtra == null) priorityExtra = "default";
        int importance;
        switch (priorityExtra) {
            case "high":
            case "max":
                importance = NotificationManager.IMPORTANCE_HIGH;
                break;
            case "low":
                importance = NotificationManager.IMPORTANCE_LOW;
                break;
            case "min":
                importance = NotificationManager.IMPORTANCE_MIN;
                break;
            default:
                importance = NotificationManager.IMPORTANCE_DEFAULT;
        }
        return importance;
    }

    static Pair<NotificationCompat.Builder, String> buildNotification(final Context context, final Intent intent) {
        String priorityExtra = intent.getStringExtra("priority");
        if (priorityExtra == null) priorityExtra = "default";
        int priority;
        switch (priorityExtra) {
            case "high":
                priority = Notification.PRIORITY_HIGH;
                break;
            case "low":
                priority = Notification.PRIORITY_LOW;
                break;
            case "max":
                priority = Notification.PRIORITY_MAX;
                break;
            case "min":
                priority = Notification.PRIORITY_MIN;
                break;
            default:
                priority = Notification.PRIORITY_DEFAULT;
        }

        String title = intent.getStringExtra("title");

        String lightsArgbExtra = intent.getStringExtra("led-color");

        int ledColor = 0;

        if (lightsArgbExtra != null) {
            try {
                ledColor = Integer.parseInt(lightsArgbExtra, 16) | 0xff000000;
            } catch (NumberFormatException e) {
                Logger.logError(LOG_TAG, "Invalid LED color format! Ignoring!");
            }
        }

        int ledOnMs = intent.getIntExtra("led-on", 800);
        int ledOffMs = intent.getIntExtra("led-off", 800);

        long[] vibratePattern = intent.getLongArrayExtra("vibrate");
        boolean useSound = intent.getBooleanExtra("sound", false);
        boolean ongoing = intent.getBooleanExtra("ongoing", false);
        boolean alertOnce = intent.getBooleanExtra("alert-once", false);

        String actionExtra = intent.getStringExtra("action");

        final String notificationId = getNotificationId(intent);

        String groupKey = intent.getStringExtra("group");

        String channel = intent.getStringExtra("channel");
        if (channel == null) {
            channel = CHANNEL_ID;
        }
        String smallIconName = intent.getStringExtra("icon");
        final NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                getPlatformChannelId(channel));
        notification.setSmallIcon(NotificationIcons.resolve(smallIconName));
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setPriority(priority);
        notification.setOngoing(ongoing);
        notification.setOnlyAlertOnce(alertOnce);
        notification.setWhen(System.currentTimeMillis());
        notification.setShowWhen(true);

        String imagePath = intent.getStringExtra("image-path");
        if (imagePath != null) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                notification.setLargeIcon(myBitmap)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(myBitmap));
            }
        }

        String styleType = intent.getStringExtra("type");
        if (Objects.equals(styleType, "media")) {
            String mediaPrevious = intent.getStringExtra("media-previous");
            String mediaPause = intent.getStringExtra("media-pause");
            String mediaPlay = intent.getStringExtra("media-play");
            String mediaNext = intent.getStringExtra("media-next");

            if (mediaPrevious != null && mediaPause != null && mediaPlay != null && mediaNext != null) {
                if (smallIconName == null) {
                    notification.setSmallIcon(android.R.drawable.ic_media_play);
                }

                PendingIntent previousIntent = createAction(context, mediaPrevious);
                PendingIntent pauseIntent = createAction(context, mediaPause);
                PendingIntent playIntent = createAction(context, mediaPlay);
                PendingIntent nextIntent = createAction(context, mediaNext);

                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "previous", previousIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "pause", pauseIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "play", playIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "next", nextIntent));

                notification.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 3));
            }
        }

        if (groupKey != null) notification.setGroup(groupKey);

        if (ledColor != 0) {
            notification.setLights(ledColor, ledOnMs, ledOffMs);

            if (vibratePattern == null) {
                // Hack to make led work without vibrating.
                vibratePattern = new long[]{0};
            }
        }

        if (vibratePattern != null) {
            // Do not force the user to specify a delay first element, let it be 0.
            long[] vibrateArg = new long[vibratePattern.length + 1];
            System.arraycopy(vibratePattern, 0, vibrateArg, 1, vibratePattern.length);
            notification.setVibrate(vibrateArg);
        }

        if (useSound) notification.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);

        notification.setAutoCancel(true);

        if (actionExtra != null) {
            PendingIntent pi = createAction(context, actionExtra);
            notification.setContentIntent(pi);
        }

        for (int button = 1; button <= 3; button++) {
            String buttonText = intent.getStringExtra("button_text_" + button);
            String buttonAction = intent.getStringExtra("button_action_" + button);

            if (buttonText != null && buttonAction != null) {
                if (buttonAction.contains("$REPLY")) {
                    NotificationCompat.Action action = createReplyAction(context, intent,
                            button,
                            buttonText, buttonAction, notificationId);
                    notification.addAction(action);
                } else {
                    PendingIntent pi = createAction(context, buttonAction);
                    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_input_add, buttonText, pi));
                }
            }
        }

        String onDeleteActionExtra = intent.getStringExtra("on_delete_action");
        if (onDeleteActionExtra != null) {
            PendingIntent pi = createAction(context, onDeleteActionExtra);
            notification.setDeleteIntent(pi);
        }

        String text = intent.getStringExtra(EXTRA_CONTENT);
        if (!TextUtils.isEmpty(text)) {
            if (text.contains("\n")) {
                notification.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
            } else {
                notification.setContentText(text);
            }
        }

        return new Pair<>(notification, notificationId);
    }

    private static String getNotificationId(Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null) id = UUID.randomUUID().toString();
        return id;
    }

    public static void onReceiveRemoveNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveRemoveNotification");

        ResultReturner.noteDone(apiReceiver, intent);
        String notificationId = intent.getStringExtra("id");
        if (notificationId != null) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(notificationId, 0);
        }
    }

    static NotificationCompat.Action createReplyAction(final Context context, Intent intent,
                                                       int buttonNum,
                                                       String buttonText,
                                                       String buttonAction, String notificationId) {
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(buttonText)
                .build();

        // Build a PendingIntent for the reply action to trigger.
        PendingIntent replyPendingIntent =
                PendingIntent.getBroadcast(context,
                        buttonNum,
                        getMessageReplyIntent(context, intent, buttonNum, buttonAction, notificationId),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        // Create the reply action and add the remote input.
        return new NotificationCompat.Action.Builder(NotificationIcons.DEFAULT,
                        buttonText,
                        replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();
    }

    private static Intent getMessageReplyIntent(Context context, Intent notificationIntent,
                                                int buttonNum, String buttonAction,
                                                String notificationId) {
        Uri replyId = new Uri.Builder().scheme("termux-notification-reply")
                .appendPath(notificationId).appendPath(Integer.toString(buttonNum)).build();
        Intent replyIntent = new Intent(context, TermuxApiReceiver.class)
                .setAction(ACTION_NOTIFICATION_REPLY)
                .setData(replyId)
                .putExtra("id", notificationId)
                .putExtra(EXTRA_REPLY_ACTION, buttonAction);

        // Copy the notification options needed to render an ongoing notification after a reply.
        String[] textOptions = {"priority", "title", "led-color", "action", "group", "channel", "icon",
                "image-path", "type", "media-previous", "media-pause", "media-play", "media-next",
                "button_text_1", "button_text_2", "button_text_3", "button_action_1", "button_action_2",
                "button_action_3", "on_delete_action", EXTRA_CONTENT};
        for (String option : textOptions) {
            replyIntent.putExtra(option, notificationIntent.getStringExtra(option));
        }
        return replyIntent
                .putExtra("led-on", notificationIntent.getIntExtra("led-on", 800))
                .putExtra("led-off", notificationIntent.getIntExtra("led-off", 800))
                .putExtra("vibrate", notificationIntent.getLongArrayExtra("vibrate"))
                .putExtra("sound", notificationIntent.getBooleanExtra("sound", false))
                .putExtra("ongoing", notificationIntent.getBooleanExtra("ongoing", false))
                .putExtra("alert-once", notificationIntent.getBooleanExtra("alert-once", false));
    }


    static private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_TEXT_REPLY);
        }
        return null;
    }

    public static void onReceiveReplyToNotification(TermuxApiReceiver termuxApiReceiver,
                                                    Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveReplyToNotification");

        CharSequence reply = getMessageText(intent);

        String action = intent.getStringExtra(EXTRA_REPLY_ACTION);
        String notificationId = intent.getStringExtra("id");
        if (action == null || reply == null || notificationId == null) return;

        try {
            // Pass the user's input as a shell argument so it remains data even when it contains shell syntax.
            createAction(context, "REPLY=$1\nshift\n" + action, "termux-notification-reply", reply.toString()).send();
        } catch (PendingIntent.CanceledException e) {
            Logger.logError(LOG_TAG, "Notification reply could not start its action");
        }

        if (intent.getBooleanExtra("ongoing", false)) {
            // Re-issue the new notification to clear the spinner
            Notification repliedNotification = buildNotification(context, intent).first.build();
            postNotification(context, notificationId, repliedNotification);
        } else {
            // Cancel the notification
            NotificationManagerCompat.from(context).cancel(notificationId, 0);
        }
    }

    static Intent createExecuteIntent(String[] arguments) {
        Uri script = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(BIN_SH).build();
        Intent executionIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, script);
        executionIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arguments);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);
        return executionIntent;
    }

    static PendingIntent createAction(final Context context, String action, String... shellArguments) {
        String[] arguments = new String[shellArguments.length + 2];
        arguments[0] = "-c";
        arguments[1] = action;
        System.arraycopy(shellArguments, 0, arguments, 2, shellArguments.length);
        Intent executeIntent = createExecuteIntent(arguments);
        // Use unique request code for each action created so that pending intent extras are updated
        // and do not conflict when same action is recreated or between actions of different notifications.
        return PendingIntent.getService(context,
                PendingIntentUtils.getNextRequestCode(context), executeIntent,
                PendingIntent.FLAG_IMMUTABLE);
    }
}
