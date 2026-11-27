package com.termux.terminal;

import android.app.Application;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;

import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34, shadows = TerminalAutofillTest.EnabledAutofill.class)
public class TerminalAutofillTest {
    @Test
    public void switchingTerminalsCancelsCredentialDeliveryToBothSessions() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        TerminalSession first = new TerminalSession(null, null, null, null, null, null);
        TerminalSession second = new TerminalSession(null, null, null, null, null, null);
        first.mShellPid = 1;
        second.mShellPid = 2;

        view.attachSession(first);
        view.requestAutoFillPassword();
        assertEquals(View.AUTOFILL_TYPE_TEXT, view.getAutofillType());

        view.attachSession(second);
        view.autofill(AutofillValue.forText("old value"));
        assertEquals(View.AUTOFILL_TYPE_NONE, view.getAutofillType());

        byte[] result = new byte[64];
        assertEquals(0, first.mTerminalToProcessIOQueue.read(result, false));
        assertEquals(0, second.mTerminalToProcessIOQueue.read(result, false));

        view.requestAutoFillPassword();
        view.autofill(AutofillValue.forText("café"));
        int length = second.mTerminalToProcessIOQueue.read(result, false);
        assertEquals("café", new String(result, 0, length, StandardCharsets.UTF_8));
        view.autofill(AutofillValue.forText("second delivery"));
        assertEquals(0, second.mTerminalToProcessIOQueue.read(result, false));
    }

    @Implements(AutofillManager.class)
    public static class EnabledAutofill {
        @Implementation
        protected boolean isEnabled() {
            return true;
        }

        @Implementation
        protected void requestAutofill(View view) {}

        @Implementation
        protected void cancel() {}
    }
}
