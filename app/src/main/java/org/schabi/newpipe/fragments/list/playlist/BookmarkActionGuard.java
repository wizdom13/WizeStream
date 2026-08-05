package org.schabi.newpipe.fragments.list.playlist;

import java.util.concurrent.atomic.AtomicBoolean;

final class BookmarkActionGuard {
    private final AtomicBoolean running = new AtomicBoolean();

    boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    void finish() {
        running.set(false);
    }

    boolean isRunning() {
        return running.get();
    }
}
