package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.Arrays;

/** Incremental Sixel decoder for DCS q ... ST. */
final class TerminalSixel {

    private static final int[] INITIAL_COLORS = {
        0xFF000000, 0xFF3333CC, 0xFFCC2323, 0xFF33CC33, 0xFFCC33CC, 0xFF33CCCC, 0xFFCCCC33, 0xFF777777,
        0xFF444444, 0xFF565699, 0xFF994444, 0xFF569956, 0xFF995699, 0xFF569999, 0xFF999956, 0xFFCCCCCC
    };

    private static final int SIXEL_HEIGHT = 6;
    private static final int MAX_REPEAT = 8192;
    private static final int INITIAL_DIMENSION = 100;

    private final TerminalSessionClient client;
    private final int[] colors = Arrays.copyOf(INITIAL_COLORS, 256);
    private final int[] arguments = new int[5];

    private Bitmap bitmap;
    private int color = INITIAL_COLORS[0];
    private int column;
    private int row;
    private int width;
    private int height;
    private int command;
    private int argumentIndex;
    private boolean hasDigits;

    TerminalSixel(TerminalSessionClient client) {
        this.client = client;
    }

    Bitmap getBitmap() {
        return bitmap;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    boolean read(int codePoint) {
        if (command != 0) {
            if (codePoint >= '0' && codePoint <= '9') {
                int digit = codePoint - '0';
                int limit = command == '!' ? MAX_REPEAT : Integer.MAX_VALUE;
                int value = arguments[argumentIndex];
                if (value > (limit - digit) / 10) return false;
                arguments[argumentIndex] = value * 10 + digit;
                hasDigits = true;
                return true;
            }
            if (codePoint == ';') {
                int maxIndex = command == '#' ? 4 : command == '"' ? 3 : 0;
                if (argumentIndex == maxIndex) return false;
                argumentIndex++;
                hasDigits = false;
                return true;
            }
            if (command == '!') {
                if (codePoint == ' ') return true;
                command = 0;
                return hasDigits && isSixelData(codePoint) && draw(codePoint, arguments[0]);
            }
            if (!finish()) return false;
        }

        if (isSixelData(codePoint)) return draw(codePoint, 1);
        switch (codePoint) {
            case '$': // Graphics carriage return.
                column = 0;
                return true;
            case '-': // Next group of six rows.
                column = 0;
                row += SIXEL_HEIGHT;
                return row < TerminalBitmap.MAX_DIMENSION;
            case '#':
            case '"':
            case '!':
                command = codePoint;
                argumentIndex = 0;
                hasDigits = false;
                Arrays.fill(arguments, 0);
                return true;
            case ' ':
                return true;
            default:
                return false;
        }
    }

    boolean finish() {
        int current = command;
        command = 0;
        switch (current) {
            case '#':
                int index = arguments[0];
                if (index >= colors.length) return false;
                if (argumentIndex > 0) {
                    // The existing terminal supports RGB percentage color definitions.
                    if (argumentIndex != 4 || !hasDigits || arguments[1] != 2 ||
                        arguments[2] > 100 || arguments[3] > 100 || arguments[4] > 100) {
                        return false;
                    }
                    colors[index] = 0xff000000 | (arguments[2] * 255 / 100) << 16 |
                        (arguments[3] * 255 / 100) << 8 | (arguments[4] * 255 / 100);
                }
                color = colors[index];
                return true;
            case '"':
                if (argumentIndex != 3 || !hasDigits) return false;
                int rasterWidth = arguments[2];
                int rasterHeight = arguments[3];
                return rasterWidth == 0 || rasterHeight == 0 || ensureBitmap(rasterWidth, rasterHeight);
            case '!':
                return false; // A repeat must be followed by a Sixel character.
            default:
                return true;
        }
    }

    private static boolean isSixelData(int codePoint) {
        return codePoint >= '?' && codePoint <= '~';
    }

    private boolean draw(int codePoint, int repeat) {
        if (repeat == 0) return true;
        int nextColumn = column + repeat;
        int nextRow = row + SIXEL_HEIGHT;
        if (!ensureBitmap(nextColumn, nextRow)) return false;

        int pixels = codePoint - '?';
        for (; column < nextColumn; column++) {
            for (int pixel = 0; pixel < SIXEL_HEIGHT; pixel++) {
                if ((pixels & (1 << pixel)) != 0) bitmap.setPixel(column, row + pixel, color);
            }
        }
        width = Math.max(width, nextColumn);
        height = Math.max(height, nextRow);
        return true;
    }

    private boolean ensureBitmap(int minimumWidth, int minimumHeight) {
        if (!TerminalBitmap.canAllocate(minimumWidth, minimumHeight)) return false;
        int oldWidth = bitmap == null ? 0 : bitmap.getWidth();
        int oldHeight = bitmap == null ? 0 : bitmap.getHeight();
        if (oldWidth >= minimumWidth && oldHeight >= minimumHeight) return true;

        int newWidth = grownDimension(oldWidth, minimumWidth);
        int newHeight = grownDimension(oldHeight, minimumHeight);
        if (!TerminalBitmap.canAllocate(newWidth, newHeight)) {
            newWidth = Math.max(oldWidth, minimumWidth);
            newHeight = Math.max(oldHeight, minimumHeight);
        }
        bitmap = TerminalBitmap.resize(client, bitmap, newWidth, newHeight);
        return bitmap != null;
    }

    private static int grownDimension(int oldSize, int minimumSize) {
        return oldSize >= minimumSize ? oldSize :
            Math.max(minimumSize, Math.min(TerminalBitmap.MAX_DIMENSION, Math.max(INITIAL_DIMENSION, oldSize + oldSize / 2)));
    }
}
