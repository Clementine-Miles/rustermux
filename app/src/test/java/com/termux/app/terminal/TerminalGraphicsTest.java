package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalGraphicsTest {

    @Test
    public void sixelOccupiesBitmapCellsAndFollowingTextIsParsed() {
        TerminalEmulator terminal = new TerminalEmulator(new TestOutput(), 8, 4, 8, 12, 100, null);
        append(terminal, "\033Pq~\033");
        append(terminal, "\\X");

        long style = terminal.getScreen().allocateFullLineIfNecessary(
            terminal.getScreen().externalToInternalRow(0)).getStyle(0);
        assertTrue(TextStyle.isTerminalBitmap(style));
        Bitmap bitmap = terminal.getScreen().getSixelBitmap(style);
        assertNotNull(bitmap);
        assertEquals(8, bitmap.getWidth());
        assertEquals(12, bitmap.getHeight());
        assertEquals(new Rect(0, 0, 8, 12), terminal.getScreen().getSixelRect(style));
        assertEquals("X", terminal.getSelectedText(0, 1, 0, 1));
    }

    @Test
    public void rasterAndRgbRepeatKeepPixelsAcrossFragments() {
        TerminalEmulator terminal = new TerminalEmulator(new TestOutput(), 8, 4, 8, 12, 100, null);
        append(terminal, "\033]10;#00ff00\007");
        append(terminal, "A\033P0;1;0q\"1;1;3;");
        append(terminal, "6#2;2;100;0;0!");
        append(terminal, "3~\033");
        append(terminal, "\\B");
        assertEquals("A", terminal.getSelectedText(0, 0, 0, 0));

        long style = terminal.getScreen().allocateFullLineIfNecessary(
            terminal.getScreen().externalToInternalRow(0)).getStyle(1);
        Bitmap bitmap = terminal.getScreen().getSixelBitmap(style);
        assertNotNull(bitmap);
        assertEquals(Color.RED, bitmap.getPixel(0, 0));
        assertEquals(Color.RED, bitmap.getPixel(2, 5));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(3, 0));

        TerminalRenderer renderer = new TerminalRenderer(16, Typeface.MONOSPACE);
        Bitmap frame = Bitmap.createBitmap((int) Math.ceil(renderer.getFontWidth() * terminal.mColumns),
            renderer.getFontLineSpacing() * terminal.mRows, Bitmap.Config.ARGB_8888);
        renderer.render(terminal, new Canvas(frame), 0, -1, -1, -1, -1);
        assertTrue("The text run before the Sixel must be drawn",
            hasPixelOfColor(frame, 0, (int) renderer.getFontWidth(), Color.GREEN));
        assertTrue("The Sixel must be drawn in the adjacent cell",
            hasPixelOfColor(frame, (int) Math.ceil(renderer.getFontWidth()),
                (int) (2 * renderer.getFontWidth()), Color.RED));

        terminal.getScreen().clearTranscript();
        assertEquals(bitmap, terminal.getScreen().getSixelBitmap(style));
        assertEquals("B", terminal.getSelectedText(1, 1, 1, 1));
    }

    @Test
    public void clipboardAcceptsUtf8AcrossFragmentsPastDefaultOscLimit() {
        TestOutput output = new TestOutput();
        TerminalEmulator terminal = new TerminalEmulator(output, 8, 4, 8, 12, 100, null);
        char[] chars = new char[18 * 1024];
        Arrays.fill(chars, 'a');
        String clipboardText = "café" + new String(chars);
        String encoded = Base64.getEncoder().encodeToString(clipboardText.getBytes(StandardCharsets.UTF_8));
        append(terminal, "\033]5");
        append(terminal, "2;c;" + encoded.substring(0, 10_000));
        append(terminal, encoded.substring(10_000) + "\033");
        append(terminal, "\\");

        assertEquals(clipboardText, output.clipboardText);
    }

    private static boolean hasPixelOfColor(Bitmap bitmap, int left, int right, int color) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = left; x < right; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) > 0 && (pixel & 0x00ffffff) == (color & 0x00ffffff)) return true;
            }
        }
        return false;
    }

    private static void append(TerminalEmulator terminal, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        terminal.append(bytes, bytes.length);
    }

    private static final class TestOutput extends TerminalOutput {
        String clipboardText;

        @Override public void onCopyTextToClipboard(String text) {
            clipboardText = text;
        }

        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }
}
