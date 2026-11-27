package com.termux.shared.termux.terminal.io;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.termux.shared.logger.Logger;

public class BellHandler {
    private static volatile BellHandler instance = null;
    private static final Object lock = new Object();

    private static final String LOG_TAG = "BellHandler";

    public static BellHandler getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    VibratorManager manager = context.getSystemService(VibratorManager.class);
                    instance = new BellHandler(manager == null ? null : manager.getDefaultVibrator());
                }
            }
        }

        return instance;
    }

    private static final long DURATION = 50;
    private static final long MIN_PAUSE = 3 * DURATION;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBell = 0;
    private final Runnable bellRunnable;

    private BellHandler(final Vibrator vibrator) {
        bellRunnable = new Runnable() {
            @Override
            public void run() {
                if (vibrator != null && vibrator.hasVibrator()) {
                    try {
                        vibrator.vibrate(VibrationEffect.createOneShot(DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
                    } catch (RuntimeException e) {
                        Logger.logStackTraceWithMessage(LOG_TAG, "Failed to run vibrator", e);
                    }
                }
            }
        };
    }

    public synchronized void doBell() {
        long now = now();
        long timeSinceLastBell = now - lastBell;

        if (timeSinceLastBell < 0) {
            // there is a next bell pending; don't schedule another one
        } else if (timeSinceLastBell < MIN_PAUSE) {
            // there was a bell recently, schedule the next one
            handler.postDelayed(bellRunnable, MIN_PAUSE - timeSinceLastBell);
            lastBell = lastBell + MIN_PAUSE;
        } else {
            // the last bell was long ago, do it now
            bellRunnable.run();
            lastBell = now;
        }
    }

    private long now() {
        return SystemClock.uptimeMillis();
    }
}
