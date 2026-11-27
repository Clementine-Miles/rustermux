package com.termux.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionLaunchRequestsTest {
    @Test
    public void regularLaunchCreatesAtMostOneSessionAndNeverDuplicatesOnABind() {
        SessionLaunchRequests launch = new SessionLaunchRequests(true, null);
        assertEquals(Boolean.FALSE, launch.next(false));
        assertEquals(Boolean.FALSE, launch.next(false));
        launch.consume();
        assertNull(launch.next(false));

        SessionLaunchRequests recreated = new SessionLaunchRequests(launch.hasInitial(), launch.savedRequests());
        assertNull(recreated.next(false));
        assertNull(new SessionLaunchRequests(true, null).next(true));
    }

    @Test
    public void preservesEachPendingShortcutAcrossRecreation() {
        SessionLaunchRequests launch = new SessionLaunchRequests(false, null);
        launch.add(true);
        launch.add(false);
        launch.add(true);
        assertEquals(Boolean.TRUE, launch.next(true));
        launch.consume();

        SessionLaunchRequests recreated = new SessionLaunchRequests(launch.hasInitial(), launch.savedRequests());
        assertEquals(Boolean.FALSE, recreated.next(true));
        recreated.consume();
        assertEquals(Boolean.TRUE, recreated.next(true));
        recreated.consume();
        assertNull(recreated.next(true));
    }
}
