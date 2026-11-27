package com.termux.terminal;

import java.util.Arrays;

/**
 * "\033P" is a device control string.
 */
public class DeviceControlStringTest extends TerminalTestCase {

	private static String hexEncode(String s) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
			result.append(String.format("%02X", (int) s.charAt(i)));
		return result.toString();
	}

	private void assertCapabilityResponse(String cap, String expectedResponse) {
		String input = "\033P+q" + hexEncode(cap) + "\033\\";
		assertEnteringStringGivesResponse(input, "\033P1+r" + hexEncode(cap) + "=" + hexEncode(expectedResponse) + "\033\\");
	}

	public void testReportColorsAndName() {
		// Request Termcap/Terminfo String. The string following the "q" is a list of names encoded in
		// hexadecimal (2 digits per character) separated by ; which correspond to termcap or terminfo key
		// names.
		// Two special features are also recognized, which are not key names: Co for termcap colors (or colors
		// for terminfo colors), and TN for termcap name (or name for terminfo name).
		// xterm responds with DCS 1 + r P t ST for valid requests, adding to P t an = , and the value of the
		// corresponding string that xterm would send, or DCS 0 + r P t ST for invalid requests. The strings are
		// encoded in hexadecimal (2 digits per character).
		withTerminalSized(3, 3).enterString("A");
		assertCapabilityResponse("Co", "256");
		assertCapabilityResponse("colors", "256");
		assertCapabilityResponse("TN", "xterm");
		assertCapabilityResponse("name", "xterm");
		enterString("B").assertLinesAre("AB ", "   ", "   ");
	}

	public void testReportKeys() {
		withTerminalSized(3, 3);
		assertCapabilityResponse("kB", "\033[Z");
	}

	public void testOversizedDeviceControlStringsConsumePayload() {
		withTerminalSized(8, 2).enterString("A\033P+q");
		byte[] payload = new byte[32 * 1024];
		Arrays.fill(payload, (byte) 'a');
		mTerminal.append(payload, payload.length);
		enterString("\033");
		assertLinesAre("A       ", "        ");
		enterString("\\B\033Punsupported");
		mTerminal.append(payload, payload.length);
		enterString("\033\\C").assertLinesAre("ABC     ", "        ");
		assertEquals("", mOutput.getOutputAndClear());
	}

	public void testDeviceControlQueryCancellationAndRepeatedEscape() {
		withTerminalSized(8, 2).enterString("A\033P+q436f\030B");
		enterString("\033P$q\"p\032C");
		enterString("\033P+q436f\033");
		enterString("\033[2;2HD").assertLinesAre("ABC     ", " D      ");
		assertEquals("", mOutput.getOutputAndClear());
		assertCapabilityResponse("Co", "256");
	}

	public void testMalformedSixelStaysInDiscardUntilTerminator() {
		withTerminalSized(8, 2).enterString("A\033Pq!819");
		enterString("3~SIXEL\r\n\t");
		enterString("\033\\B\033Pq\"1;1;2147483648;100~MORE\033\\C");
		enterString("\033P2147483648;1;1q~UNRECOGNIZED\033\\D");
		enterString("\033Pq#0;2;100;50;101~INVALID\033\\E");
		assertLinesAre("ABCDE   ", "        ");
		assertEquals("", mOutput.getOutputAndClear());
	}

	public void testTruncatedSixelResynchronizesAtNextOsc() {
		withTerminalSized(8, 2).enterString("A\033Pq!10");
		enterString("\033");
		enterString("]2;ready\033");
		enterString("\\B").assertLinesAre("AB      ", "        ");
		assertEquals("ready", mTerminal.getTitle());
	}

	public void testResetClearsInProgressDeviceControlString() {
		withTerminalSized(8, 2).enterString("A\033Pq!1\033");
		mTerminal.reset();
		enterString("B").assertLinesAre("AB      ", "        ");
		assertCapabilityResponse("TN", "xterm");
	}

	public void testEndOfInputAbandonsPartialCommandBeforeExitText() {
		withTerminalSized(8, 2).enterString("A\033Pq!1");
		mTerminal.finishInput();
		enterString("exited").assertLinesAre("Aexited ", "        ");
	}

	public void testFragmentedSettingQueryAndMalformedHex() {
		withTerminalSized(8, 2).enterString("\033P$q");
		enterString("\"p\033");
		assertEnteringStringGivesResponse("\\", "\033P1$r64;1\"p\033\\");
		assertEnteringStringGivesResponse("\033P+qxx;g000;6;436f\033\\", "\033P1+r436f=323536\033\\");
	}

}
