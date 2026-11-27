package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class VibrateAPI {

    private static final String LOG_TAG = "VibrateAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        new Thread() {
            @Override
            public void run() {
                VibratorManager manager = context.getSystemService(VibratorManager.class);
                Vibrator vibrator = manager == null ? null : manager.getDefaultVibrator();
                int milliseconds = intent.getIntExtra("duration_ms", 1000);
                boolean force = intent.getBooleanExtra("force", false);

                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) {
                    Logger.logError(LOG_TAG, "Audio service null");
                    return;
                }

                // Do not vibrate if "Silent" ringer mode or "Do Not Disturb" is enabled and -f/--force option is not used.
                if (vibrator != null && vibrator.hasVibrator()
                        && (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT || force)) {
                    try {
                        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE),
                            new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build());
                    } catch (Exception e) {
                        Logger.logStackTraceWithMessage(LOG_TAG, "Failed to run vibrator", e);
                    }
                }
            }
        }.start();

        ResultReturner.noteDone(apiReceiver, intent);
    }

}
