package com.termux.terminal;

import android.graphics.Bitmap;
import android.os.Build;

import java.util.Properties;

/** A completed Sixel bitmap stored in terminal cells. */
final class TerminalBitmap {

    private static final String LOG_TAG = "TerminalBitmap";

    /** Maximum allocation for a Sixel bitmap, following the Android renderer's limit. */
    public static final int MAX_BITMAP_SIZE = initMaxBitmapSize();
    private static int initMaxBitmapSize() {
        // Synced with `RecordingCanvas.MAX_BITMAP_SIZE`.
        // - https://cs.android.com/android/platform/superproject/+/android-16.0.0_r1:frameworks/base/graphics/java/android/graphics/RecordingCanvas.java;l=42-50
        int defaultSize =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ?
                    150 * 1024 * 1024 : // 150 MB
                    100 * 1024 * 1024;  // 100 MB

        Properties systemProperties = AndroidUtils.getSystemProperties(LOG_TAG);
        String maxTextureSizeString = systemProperties.getProperty("ro.hwui.max_texture_allocation_size");

        if (maxTextureSizeString == null) return defaultSize;

        try {
            int maxTextureSize = Integer.parseInt(maxTextureSizeString);
            return maxTextureSize > 0 ? maxTextureSize : defaultSize;
        }
        catch (Exception e) {
            return defaultSize;
        }
    }



    final Bitmap mBitmap;
    final int mCellWidth;
    final int mCellHeight;
    final int mScrollLines;

    private TerminalBitmap(Bitmap bitmap, int cellWidth, int cellHeight, int scrollLines) {
        mBitmap = bitmap;
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mScrollLines = scrollLines;
    }

    /** Build a {@link TerminalBitmap} from a {@link TerminalSixel}. */
    static TerminalBitmap build(TerminalBuffer terminalBuffer, int bitmapNum, TerminalSixel terminalSixel,
                                       int x, int y, int cellWidth, int cellHeight) {
        try {
            Bitmap bitmap = terminalSixel.getBitmap();
            bitmap = resizeBitmapConstrained(LOG_TAG, "sixel", terminalBuffer.getClient(), bitmap,
                terminalSixel.getWidth(), terminalSixel.getHeight(), cellWidth, cellHeight,
                terminalBuffer.mColumns - x);
            if (bitmap == null) {
                Logger.logError(terminalBuffer.getClient(), LOG_TAG,
                    "Create terminal bitmap " + bitmapNum + " from terminal sixel failed");
                return null;
            }

            return buildOrThrow(terminalBuffer, bitmapNum, bitmap,
                x, y, cellWidth, cellHeight);
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) System.gc();
            Logger.logError(terminalBuffer.getClient(), LOG_TAG,
                "Create terminal bitmap " + bitmapNum + " from terminal sixel failed: " + t.getMessage());
            return null;
        }
    }


    private static TerminalBitmap buildOrThrow(TerminalBuffer terminalBuffer, int bitmapNum, Bitmap bitmap,
                                               int x, int y, int cellWidth, int cellHeight) {
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        int width = Math.min(terminalBuffer.mColumns - x, (bitmapWidth + cellWidth - 1) / cellWidth);
        int height = (bitmapHeight + cellHeight - 1) / cellHeight;
        int s = 0;

        for (int i = 0; i < height; i++) {
            if (y + i - s == terminalBuffer.mScreenRows) {
                terminalBuffer.scrollDownOneLine(0, terminalBuffer.mScreenRows, TextStyle.NORMAL);
                s++;
            }
            for (int j = 0; j < width ; j++) {
                terminalBuffer.setChar(x + j, y + i - s, '+', TextStyle.encodeTerminalBitmap(bitmapNum, j, i));
            }
        }

        if (width * cellWidth < bitmapWidth) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width * cellWidth, bitmapHeight);
        }

        return new TerminalBitmap(bitmap, cellWidth, cellHeight, height - s);
    }

    static Bitmap resizeBitmap(String logTag, String label, TerminalSessionClient client, Bitmap bitmap,
                                      int bitmapWidth, int bitmapHeight) {
        
        Bitmap newBitmap;
        try {
            int newBitmapSize = bitmapWidth * bitmapHeight * 4;
            if (newBitmapSize < 0 || newBitmapSize > MAX_BITMAP_SIZE) {
                Logger.logError(client, logTag, "The new " + label + " bitmap after resize with" +
                    " width " + bitmapWidth + " and height " + bitmapHeight +
                    " has size " + newBitmapSize + " greater than max bitmap size " + MAX_BITMAP_SIZE);
                return null;
            }

            int[] pixels = new int[bitmap.getAllocationByteCount()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

            newBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

            int newWidth = Math.min(bitmap.getWidth(), bitmapWidth);
            int newHeight = Math.min(bitmap.getHeight(), bitmapHeight);
            newBitmap.setPixels(pixels, 0, bitmap.getWidth(), 0, 0, newWidth, newHeight);
            return newBitmap;
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) System.gc();
            Logger.logError(client, logTag, "Resize " + label + " bitmap to" +
                " width " + bitmapWidth + " and height " + bitmapHeight + " failed: " + t.getMessage());
            return null;
        }
    }

    private static Bitmap resizeBitmapConstrained(String logTag, String label, TerminalSessionClient client, Bitmap bitmap,
                                                 int bitmapWidth, int bitmapHeight,
                                                 int cellWidth, int cellHeight, int columns) {
        // Width and height must be multiples of the cell width and height.
        // Bitmap should not extend beyond screen width.
        Bitmap originalBitmap = bitmap;
        if (bitmapWidth > cellWidth * columns || (bitmapWidth % cellWidth) != 0 || (bitmapHeight % cellHeight) != 0) {
            int newBitmapWidth = Math.min(cellWidth * columns, ((bitmapWidth - 1) / cellWidth) * cellWidth + cellWidth);
            int newBitmapHeight = ((bitmapHeight - 1) / cellHeight) * cellHeight + cellHeight;
            bitmap = resizeBitmap(logTag, label, client, originalBitmap, newBitmapWidth, newBitmapHeight);
            // Only a minor display glitch if resize failed.
            return bitmap != null ? bitmap : originalBitmap;
        } else {
            return originalBitmap;
        }
    }

}
