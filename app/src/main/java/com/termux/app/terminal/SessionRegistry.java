package com.termux.app.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;

/** Ordered set of sessions managed by the terminal service on the main thread. */
public final class SessionRegistry<T> {

    public enum State {
        CREATED, RUNNING, EXITED, TERMINATING, REMOVED
    }

    public static final class SessionInfo {
        public final String id;
        public final int number;
        @Nullable public final String name;
        public final State state;
        public final boolean active;

        private SessionInfo(Entry<?> entry, boolean active) {
            id = entry.id;
            number = entry.number;
            name = entry.name;
            state = entry.state;
            this.active = active;
        }
    }

    private static final class Entry<T> {
        final String id;
        final int number;
        final T session;
        String name;
        State state = State.CREATED;

        Entry(String id, int number, String name, T session) {
            this.id = id;
            this.number = number;
            this.name = name;
            this.session = session;
        }
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private String activeId;
    private int nextNumber = 1;

    public void create(String id, @Nullable String name, T session) {
        if (indexOf(id) >= 0) throw new IllegalArgumentException("Duplicate session ID");
        entries.add(new Entry<>(id, nextNumber++, name, session));
        activeId = id;
    }

    public List<SessionInfo> snapshot() {
        List<SessionInfo> snapshot = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries) snapshot.add(new SessionInfo(entry, entry.id.equals(activeId)));
        return Collections.unmodifiableList(snapshot);
    }

    @Nullable
    public T get(@Nullable String id) {
        int index = indexOf(id);
        return index < 0 ? null : entries.get(index).session;
    }

    @Nullable
    public T active() {
        return get(activeId);
    }

    public boolean select(String id) {
        if (indexOf(id) < 0 || id.equals(activeId)) return false;
        activeId = id;
        return true;
    }

    private boolean selectIndex(int index) {
        return index >= 0 && index < entries.size() && select(entries.get(index).id);
    }

    public boolean selectNeighbor(int direction) {
        if (entries.isEmpty()) return false;
        return selectIndex(Math.floorMod(indexOf(activeId) + direction, entries.size()));
    }

    public boolean move(String id, int direction) {
        int index = indexOf(id);
        int destination = index + direction;
        if (index < 0 || destination == index || destination < 0 || destination >= entries.size()) return false;
        entries.add(destination, entries.remove(index));
        return true;
    }

    public boolean rename(String id, @Nullable String name) {
        int index = indexOf(id);
        if (index < 0) return false;
        entries.get(index).name = name;
        return true;
    }

    public boolean started(String id) {
        return transition(id, State.CREATED, State.RUNNING);
    }

    public boolean terminating(String id) {
        return transition(id, State.RUNNING, State.TERMINATING);
    }

    public boolean terminationFailed(String id) {
        return transition(id, State.TERMINATING, State.RUNNING);
    }

    public boolean exited(String id) {
        return transition(id, State.RUNNING, State.EXITED) || transition(id, State.TERMINATING, State.EXITED);
    }

    public boolean close(String id) {
        int index = indexOf(id);
        if (index < 0 || entries.get(index).state != State.EXITED) return false;
        remove(index);
        return true;
    }

    public void creationFailed(String id) {
        int index = indexOf(id);
        if (index >= 0 && entries.get(index).state == State.CREATED) remove(index);
    }

    public void clear() {
        for (Entry<T> entry : entries) entry.state = State.REMOVED;
        entries.clear();
        activeId = null;
    }

    public int size() {
        return entries.size();
    }

    private int indexOf(@Nullable String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private boolean transition(String id, State from, State to) {
        int index = indexOf(id);
        if (index < 0 || entries.get(index).state != from) return false;
        entries.get(index).state = to;
        return true;
    }

    private void remove(int index) {
        Entry<T> entry = entries.remove(index);
        entry.state = State.REMOVED;
        if (entry.id.equals(activeId)) {
            activeId = entries.isEmpty() ? null : entries.get(Math.min(index, entries.size() - 1)).id;
        }
    }
}
