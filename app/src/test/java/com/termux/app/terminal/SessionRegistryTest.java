package com.termux.app.terminal;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionRegistryTest {
    @Test
    public void keepsSelectionAndIdentityThroughRenameAndMove() {
        SessionRegistry<String> sessions = new SessionRegistry<>();
        sessions.create("first", null, "first PTY");
        sessions.create("second", "work", "second PTY");
        sessions.create("third", "work", "third PTY");

        assertTrue(sessions.select("first"));
        assertTrue(sessions.rename("first", "work"));
        assertTrue(sessions.move("first", 1));

        List<SessionRegistry.SessionInfo> tabs = sessions.snapshot();
        assertEquals("second", tabs.get(0).id);
        assertEquals("first", tabs.get(1).id);
        assertEquals(1, tabs.get(1).number);
        assertTrue(tabs.get(1).active);
        assertEquals("first PTY", sessions.active());
        assertTrue(sessions.selectNeighbor(1));
        assertEquals("third PTY", sessions.active());
        assertTrue(sessions.selectNeighbor(1));
        assertEquals("second PTY", sessions.active());
    }

    @Test
    public void keepsExitedSessionUntilClosedThenSelectsAdjacentOrEmpty() {
        SessionRegistry<String> sessions = new SessionRegistry<>();
        sessions.create("left", null, "left PTY");
        sessions.started("left");
        sessions.create("right", null, "right PTY");
        assertEquals(SessionRegistry.State.CREATED, sessions.snapshot().get(1).state);
        assertTrue(sessions.started("right"));
        assertFalse(sessions.close("right"));
        assertTrue(sessions.terminating("right"));
        assertFalse(sessions.terminating("right"));
        assertTrue(sessions.exited("right"));
        assertEquals("right PTY", sessions.active());
        assertTrue(sessions.close("right"));
        assertEquals("left PTY", sessions.active());
        assertTrue(sessions.exited("left"));
        assertTrue(sessions.close("left"));
        assertNull(sessions.active());
        assertTrue(sessions.snapshot().isEmpty());
    }

    @Test
    public void snapshotsAreOrderedAndIndependentOfLaterUpdates() {
        SessionRegistry<String> sessions = new SessionRegistry<>();
        sessions.create("old", "before", "first PTY");
        List<SessionRegistry.SessionInfo> prior = sessions.snapshot();
        sessions.rename("old", "after");
        assertTrue(sessions.started("old"));
        assertTrue(sessions.terminating("old"));
        assertTrue(sessions.terminationFailed("old"));
        sessions.clear();
        assertEquals("before", prior.get(0).name);
        assertEquals(SessionRegistry.State.CREATED, prior.get(0).state);
        assertTrue(sessions.snapshot().isEmpty());
    }
}
