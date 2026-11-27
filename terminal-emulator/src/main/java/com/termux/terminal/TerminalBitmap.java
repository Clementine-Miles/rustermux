package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;

/** A completed Sixel bitmap stored in terminal cells. */
final class TerminalBitmap {

    /** The dimensions accepted by Android Canvas. */
    static final int MAX_DIMENSION = 32766;
    /** The renderer defaults to 100 MiB on API 34 and 150 MiB starting with API 35. */
    static final int MAX_BITMAP_BYTES =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ? 150 : 100) * 1024 * 1024;
    private static final int MAX_CELL_DIMENSION = 0x1000;

    private static final String LOG_TAG = "TerminalBitmap";

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

    static TerminalBitmap build(TerminalBuffer terminalBuffer, int bitmapNum, TerminalSixel sixel,
                                int column, int row, int cellWidth, int cellHeight, int availableBytes) {
        Bitmap bitmap = sixel.getBitmap();
        if (bitmap == null || sixel.getWidth() == 0 || sixel.getHeight() == 0 ||
            cellWidth <= 0 || cellHeight <= 0) {
            return null;
        }

        int columns = Math.min(terminalBuffer.mColumns - column, (sixel.getWidth() - 1) / cellWidth + 1);
        int rows = (sixel.getHeight() - 1) / cellHeight + 1;
        if (columns <= 0 || columns > MAX_CELL_DIMENSION || rows > MAX_CELL_DIMENSION ||
            (long) columns * cellWidth > MAX_DIMENSION || (long) rows * cellHeight > MAX_DIMENSION) {
            return null;
        }

        int bitmapWidth = columns * cellWidth;
        int bitmapHeight = rows * cellHeight;
        if ((long) bitmapWidth * bitmapHeight > availableBytes / 4) return null;
        if (bitmap.getWidth() != bitmapWidth || bitmap.getHeight() != bitmapHeight) {
            bitmap = resize(terminalBuffer.getClient(), bitmap, bitmapWidth, bitmapHeight);
            if (bitmap == null) return null;
        }

        int scrollLines = 0;
        for (int y = 0; y < rows; y++) {
            if (row + y - scrollLines == terminalBuffer.mScreenRows) {
                terminalBuffer.scrollDownOneLine(0, terminalBuffer.mScreenRows, TextStyle.NORMAL);
                scrollLines++;
            }
            for (int x = 0; x < columns; x++) {
                terminalBuffer.setChar(column + x, row + y - scrollLines, '+', TextStyle.encodeTerminalBitmap(bitmapNum, x, y));
            }
        }

        return new TerminalBitmap(bitmap, cellWidth, cellHeight, rows - scrollLines);
    }

    static boolean canAllocate(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION &&
            (long) width * height <= MAX_BITMAP_BYTES / 4;
    }

    static Bitmap resize(TerminalSessionClient client, Bitmap bitmap, int width, int height) {
        if (!canAllocate(width, height)) return null;
        try {
            Bitmap resized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (bitmap != null && resized != null) {
                new Canvas(resized).drawBitmap(bitmap, 0, 0, null);
            }
            return resized;
        } catch (OutOfMemoryError | IllegalArgumentException e) {
            Logger.logError(client, LOG_TAG, "Sixel bitmap allocation failed for " + width + "×" + height);
            return null;
        }
    }
}
