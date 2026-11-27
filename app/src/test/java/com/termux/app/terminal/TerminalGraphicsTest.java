package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TextStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
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
